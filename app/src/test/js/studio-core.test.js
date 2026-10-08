'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const StudioCore = require('../../main/assets/studio/studio-core.js');

test('maps client coordinates through zoom and canvas border to logical canvas units', () => {
  const point = StudioCore.clientToCanvasPoint(
    160, 115,
    { left: 100, top: 50, width: 184, height: 364 },
    360, 720,
    { left: 2, top: 2, width: 180, height: 360 }
  );
  assert.deepEqual(point, { x: 116, y: 126 });
});

test('snaps positions and sizes to the configured grid', () => {
  assert.equal(StudioCore.snap(13, 8), 16);
  assert.equal(StudioCore.snap(11, 8), 8);
  assert.equal(StudioCore.snap(-5, 8), -8);
  assert.equal(StudioCore.snap(13, 0), 16);
});

test('history commits one transaction and supports undo and redo', () => {
  const history = new StudioCore.History(10);
  const initial = { label: 'Before' };
  const changed = { label: 'After' };
  history.begin(initial);
  history.commit(changed);
  assert.equal(history.canUndo(), true);
  assert.equal(history.canRedo(), false);
  assert.deepEqual(history.undo(changed), initial);
  assert.equal(history.canRedo(), true);
  assert.deepEqual(history.redo(initial), changed);
  assert.equal(history.canUndo(), true);
});

test('history ignores unchanged transactions and clears redo after a new edit', () => {
  const history = new StudioCore.History();
  const state = { value: 1 };
  history.begin(state);
  assert.equal(history.commit({ value: 1 }), false);
  history.begin(state);
  history.commit({ value: 2 });
  history.undo({ value: 2 });
  history.begin({ value: 1 });
  history.commit({ value: 3 });
  assert.equal(history.canRedo(), false);
});

test('recovers the newest valid autosave and reports corrupt candidates', () => {
  const backup = JSON.stringify({
    id: 'p', name: 'Recovered', updatedAt: 20,
    screens: [{ id: 's', name: 'Main', nodes: [] }]
  });
  const native = JSON.stringify({
    id: 'p', name: 'Older native', updatedAt: 10,
    screens: [{ id: 's', name: 'Main', nodes: [] }]
  });
  const recovery = StudioCore.selectRecovery([
    { source: 'local autosave', raw: '{broken' },
    { source: 'backup', raw: backup },
    { source: 'Android file', raw: native }
  ]);
  assert.equal(recovery.source, 'backup');
  assert.equal(recovery.project.name, 'Recovered');
  assert.equal(recovery.rejected.length, 1);
  assert.match(recovery.rejected[0].errors[0], /valid JSON/);
});

test('migrates the legacy components schema and preserves component properties', () => {
  const migrated = StudioCore.normalizeProject({
    id: 'legacy-project', name: 'Old project',
    screens: [{
      id: 'screen_a', name: 'Profile', components: [{
        id: 'legacy-button', type: 'Button', name: 'Save', text: 'Save changes',
        x: 16, y: 28, width: 120, height: 48, fillColor: '#1e88e5',
        textColor: '#ffffff', cornerRadius: 10, isVisible: true
      }, { id: 'legacy-title', type: 'Text', name: 'Profile title', text: '' }]
    }]
  });
  assert.equal(migrated.project.schemaVersion, 2);
  assert.equal(migrated.project.screens[0].nodes[0].label, 'Save changes');
  assert.equal(migrated.project.screens[0].nodes[0].color, '#1e88e5');
  assert.equal(migrated.project.screens[0].nodes[0].radius, 10);
  assert.equal(migrated.project.screens[0].nodes[1].label, 'Profile title');
  assert.ok(migrated.warnings.some((warning) => /legacy components/.test(warning)));
});

test('rejects malformed JSON and unsupported node types with actionable errors', () => {
  assert.throws(() => StudioCore.normalizeProject('{'), /not valid JSON/);
  assert.throws(() => StudioCore.normalizeProject({
    screens: [{ id: 's', name: 'Main', nodes: [{ id: 'n1', type: 'Script' }] }]
  }), /unsupported type.*Text, Button, Input, Card, or Image/i);
});

test('XML and Compose exports escape user text and omit hidden nodes', () => {
  const normalized = StudioCore.normalizeProject({
    id: 'p', name: 'Export', screens: [{ id: 's', name: 'Home', nodes: [
      { id: 'title', type: 'Text', label: 'A&B <Home>', x: 8, y: 16, width: 160, height: 40 },
      { id: 'secret', type: 'Text', label: 'Hidden', visible: false }
    ] }]
  });
  const screen = normalized.project.screens[0];
  const xml = StudioCore.exportXml(normalized.project, screen);
  const compose = StudioCore.exportCompose(normalized.project, screen);
  assert.match(xml, /A&amp;B &lt;Home&gt;/);
  assert.doesNotMatch(xml, /Hidden/);
  assert.match(compose, /A&B <Home>/);
  assert.doesNotMatch(compose, /Hidden/);
  assert.match(compose, /fun HomeScreen\(\)/);
});

test('export validation reports invalid identifiers before generating code', () => {
  const normalized = StudioCore.normalizeProject({
    id: 'p', name: 'Invalid export',
    screens: [{ id: 's', name: 'Home', nodes: [{ id: 'node', type: 'Text', label: 'Title' }] }]
  });
  normalized.project.screens[0].nodes[0].id = 'invalid id';
  assert.throws(() => StudioCore.exportXml(normalized.project, normalized.project.screens[0]),
    /Cannot export Android XML:.*ID must start/i);
  assert.throws(() => StudioCore.exportCompose(normalized.project, normalized.project.screens[0]),
    /Cannot export Jetpack Compose:.*ID must start/i);
});
