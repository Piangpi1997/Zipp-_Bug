'use strict';
const assert = require('assert');
const core = require('../app/src/main/assets/studio/studio-core.js');

(function migratesLegacyComponentsWithoutDroppingCustomFields() {
  const migrated = core.migrateProject({
    id: 'legacy',
    name: 'Legacy',
    screens: [{
      id: 'screen_old',
      name: 'Old',
      customScreenFlag: true,
      components: [{
        id: 'hero_text',
        type: 'Text',
        name: 'Hero',
        text: 'Hello',
        x: '16', y: 24, width: 120, height: 40,
        fillColor: '#112233', cornerRadius: 4,
        customNodeFlag: 'keep-me'
      }]
    }]
  });
  assert.strictEqual(migrated.schemaVersion, 2);
  assert.strictEqual(migrated.screens[0].components, undefined);
  assert.strictEqual(migrated.screens[0].customScreenFlag, true);
  assert.strictEqual(migrated.screens[0].nodes[0].label, 'Hello');
  assert.strictEqual(migrated.screens[0].nodes[0].x, 16);
  assert.strictEqual(migrated.screens[0].nodes[0].color, '#112233');
  assert.strictEqual(migrated.screens[0].nodes[0].radius, 4);
  assert.strictEqual(migrated.screens[0].nodes[0].customNodeFlag, 'keep-me');
})();

(function reportsActionableFieldPaths() {
  const p = core.migrateProject({
    screens: [{ id: 's1', name: 'Main', nodes: [{ id: 'Bad-ID', type: 'Text', label: 'x', x: -1, y: 0, width: 0, height: 40, fontSize: 14, radius: 0, color: '#ffffff', textColor: '#ffffff' }] }]
  });
  const result = core.validateProject(p);
  assert.strictEqual(result.valid, false);
  const paths = result.errors.map(e => e.path);
  assert(paths.includes('screens[0].nodes[0].id'));
  assert(paths.includes('screens[0].nodes[0].x'));
  assert(paths.includes('screens[0].nodes[0].width'));
})();

(function xmlEscapesUserContent() {
  const p = core.migrateProject({
    screens: [{ id: 's1', name: 'Main', nodes: [{ id: 'title_text', type: 'Text', label: 'A&B <C> "D"', x: 0, y: 0, width: 120, height: 40, fontSize: 14, radius: 0, color: '#222222', textColor: '#ffffff' }] }],
    currentScreenId: 's1'
  });
  const out = core.generateAndroidXml(p, 's1');
  assert.strictEqual(out.ok, true);
  assert(out.content.includes('A&amp;B &lt;C&gt; &quot;D&quot;'));
})();

(function composeUsesRealControlsAndEscapesStrings() {
  const p = core.migrateProject({
    screens: [{ id: 's1', name: 'Main', nodes: [
      { id: 'submit_btn', type: 'Button', label: 'Save "now"', x: 16, y: 16, width: 160, height: 48, fontSize: 14, radius: 12, color: '#ff6b00', textColor: '#111111' },
      { id: 'name_input', type: 'Input', label: 'Name', x: 16, y: 80, width: 220, height: 48, fontSize: 14, radius: 8, color: '#2a2a2a', textColor: '#ffffff' }
    ] }]
  });
  const out = core.generateCompose(p);
  assert.strictEqual(out.ok, true);
  assert(out.content.includes('Button('));
  assert(out.content.includes('OutlinedTextField('));
  assert(out.content.includes('var input_name_input by remember'));
  assert(out.content.includes('Save \\"now\\"'));
})();

(function composeRejectsDuplicateGeneratedFunctionNames() {
  const p = core.migrateProject({screens: [
    {id:'a', name:'My Screen', nodes:[]},
    {id:'b', name:'My-Screen', nodes:[]}
  ]});
  const result = core.validateProject(p, {compose:true});
  assert.strictEqual(result.valid, false);
  assert(result.errors.some(e => e.path === 'screens[1].name'));
})();

console.log('Studio core tests passed');
