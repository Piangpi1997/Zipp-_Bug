'use strict';

const Core = window.StudioCore;
const PROJECT_KEY = 'zipbug_studio_project';
const BACKUP_KEY = 'zipbug_studio_project_backup';
const GRID_SIZE = 8;
const AUTOSAVE_DELAY_MS = 500;
const MAX_IMPORT_BYTES = 2 * 1024 * 1024;

let project = Core.defaultProject();
let selectedIds = [];
let primarySelectedId = null;
let sequence = 0;
let gridEnabled = true;
let inspectorEditing = false;
let autosaveTimer = null;
const history = new Core.History(50);
const canvas = document.querySelector('#canvas');

function setStatus(message, kind) {
  const status = document.querySelector('#save-status');
  if (!status) return;
  status.textContent = message;
  status.className = kind || '';
}

function getActiveScreen() {
  let screen = project.screens.find((item) => item.id === project.currentScreenId);
  if (!screen) {
    if (!project.screens.length) {
      project.screens.push({ id: 'screen_main', name: 'Main', nodes: [] });
    }
    project.currentScreenId = project.screens[0].id;
    screen = project.screens[0];
  }
  return screen;
}

function getActiveNodes() {
  return getActiveScreen().nodes;
}

function getSelectedNodes() {
  const wanted = new Set(selectedIds);
  return getActiveNodes().filter((node) => wanted.has(node.id));
}

function updateSequence() {
  let maximum = 0;
  project.screens.forEach((screen) => screen.nodes.forEach((node) => {
    const digits = String(node.id || '').match(/\d+/g);
    if (digits) digits.forEach((part) => { maximum = Math.max(maximum, Number(part) || 0); });
  }));
  sequence = maximum;
}

function nextNodeId() {
  let candidate;
  const used = new Set();
  project.screens.forEach((screen) => screen.nodes.forEach((node) => used.add(node.id)));
  do {
    sequence += 1;
    candidate = 'n' + sequence;
  } while (used.has(candidate));
  return candidate;
}

function updateHistoryButtons() {
  const undoButton = document.querySelector('#undo-button');
  const redoButton = document.querySelector('#redo-button');
  if (undoButton) undoButton.disabled = !history.canUndo();
  if (redoButton) redoButton.disabled = !history.canRedo();
}

function updateSchema() {
  const schema = document.querySelector('#schema');
  if (schema) schema.textContent = JSON.stringify(project, null, 2);
}

function renderScreenSelect() {
  const select = document.querySelector('#screen-select');
  if (!select) return;
  select.replaceChildren();
  project.screens.forEach((screen) => {
    const option = document.createElement('option');
    option.value = screen.id;
    option.textContent = screen.name;
    option.selected = screen.id === project.currentScreenId;
    select.appendChild(option);
  });
}

function renderLayers() {
  const container = document.querySelector('#layers-list');
  if (!container) return;
  container.replaceChildren();
  const nodes = getActiveNodes();
  for (let index = nodes.length - 1; index >= 0; index -= 1) {
    const node = nodes[index];
    const row = document.createElement('div');
    row.className = 'layer-item' + (node.id === primarySelectedId ? ' active' : '') +
      (selectedIds.includes(node.id) && node.id !== primarySelectedId ? ' multi-active' : '');
    row.setAttribute('role', 'button');
    row.setAttribute('tabindex', '0');
    row.setAttribute('aria-pressed', selectedIds.includes(node.id) ? 'true' : 'false');
    const label = document.createElement('span');
    label.textContent = node.label;
    const type = document.createElement('small');
    type.textContent = node.type;
    row.append(label, type);
    row.addEventListener('click', (event) => selectNode(node.id, event));
    row.addEventListener('keydown', (event) => {
      if (event.key === 'Enter' || event.key === ' ') {
        event.preventDefault();
        selectNode(node.id, event);
      }
    });
    container.appendChild(row);
  }
}

function updateInspector() {
  const node = getActiveNodes().find((item) => item.id === primarySelectedId);
  const ids = ['label', 'prop-x', 'prop-y', 'prop-w', 'prop-h', 'prop-font', 'radius', 'color', 'text-color'];
  ids.forEach((id) => {
    const input = document.querySelector('#' + id);
    if (input) input.disabled = !node;
  });
  const visibilityButton = document.querySelector('#visibility-button');
  if (visibilityButton) {
    visibilityButton.disabled = !node;
    visibilityButton.textContent = node && node.visible === false ? 'Show' : 'Hide';
  }
  if (!node) return;
  const values = {
    label: node.label,
    'prop-x': Math.round(node.x),
    'prop-y': Math.round(node.y),
    'prop-w': Math.round(node.width),
    'prop-h': Math.round(node.height),
    'prop-font': Math.round(node.fontSize),
    radius: Math.round(node.radius),
    color: node.color,
    'text-color': node.textColor
  };
  Object.keys(values).forEach((id) => {
    const input = document.querySelector('#' + id);
    if (input && document.activeElement !== input) input.value = values[id];
  });
}

function updateSelectionUI() {
  canvas.querySelectorAll('.node').forEach((element) => {
    const id = element.dataset.nodeId;
    element.classList.toggle('sel', id === primarySelectedId);
    element.classList.toggle('multi-sel', selectedIds.includes(id) && id !== primarySelectedId);
  });
  renderLayers();
  updateInspector();
}

function renderNodeStyle(element, node, index) {
  element.style.left = node.x + 'px';
  element.style.top = node.y + 'px';
  element.style.width = node.width + 'px';
  element.style.height = node.height + 'px';
  element.style.backgroundColor = node.color;
  element.style.borderRadius = node.radius + 'px';
  element.style.color = node.textColor;
  element.style.fontSize = node.fontSize + 'px';
  element.style.opacity = String(node.visible === false ? 0.35 : (node.opacity === undefined ? 1 : node.opacity));
  element.style.zIndex = String(index + 1);
  element.style.borderColor = node.borderColor || '#333333';
  element.classList.toggle('hidden-node', node.visible === false);
}

function canvasGeometry() {
  const rect = canvas.getBoundingClientRect();
  const style = window.getComputedStyle(canvas);
  const borderLeft = parseFloat(style.borderLeftWidth) || 0;
  const borderTop = parseFloat(style.borderTopWidth) || 0;
  const borderRight = parseFloat(style.borderRightWidth) || 0;
  const borderBottom = parseFloat(style.borderBottomWidth) || 0;
  const contentWidth = canvas.clientWidth || Math.max(1, rect.width - borderLeft - borderRight);
  const contentHeight = canvas.clientHeight || Math.max(1, rect.height - borderTop - borderBottom);
  const logicalWidth = parseFloat(style.width) || 360;
  const logicalHeight = parseFloat(style.minHeight) || 720;
  return {
    rect: rect,
    logicalWidth: logicalWidth,
    logicalHeight: logicalHeight,
    content: { left: borderLeft, top: borderTop, width: contentWidth, height: contentHeight, right: borderRight, bottom: borderBottom }
  };
}

function eventCanvasPoint(event) {
  const geometry = canvasGeometry();
  return Core.clientToCanvasPoint(event.clientX, event.clientY, geometry.rect,
    geometry.logicalWidth, geometry.logicalHeight, geometry.content);
}

function clamp(value, minimum, maximum) {
  return Math.min(maximum, Math.max(minimum, value));
}

function snapValue(value) {
  return gridEnabled ? Core.snap(value, GRID_SIZE) : value;
}

function updateNodeElement(node) {
  const element = Array.from(canvas.querySelectorAll('.node')).find((candidate) => candidate.dataset.nodeId === node.id);
  if (element) renderNodeStyle(element, node, getActiveNodes().indexOf(node));
}

function render() {
  canvas.replaceChildren();
  canvas.classList.toggle('grid-visible', gridEnabled);
  const nodes = getActiveNodes();
  nodes.forEach((node, index) => {
    const element = document.createElement('div');
    element.className = 'node' + (node.id === primarySelectedId ? ' sel' : '') +
      (selectedIds.includes(node.id) && node.id !== primarySelectedId ? ' multi-sel' : '');
    element.dataset.nodeId = node.id;
    element.setAttribute('role', 'button');
    element.setAttribute('tabindex', '0');
    element.setAttribute('aria-label', node.type + ': ' + node.label);
    element.textContent = node.label;
    renderNodeStyle(element, node, index);

    const handle = document.createElement('div');
    handle.className = 'handle';
    handle.setAttribute('role', 'slider');
    handle.setAttribute('aria-label', 'Resize ' + node.label);
    handle.setAttribute('aria-valuetext', Math.round(node.width) + ' by ' + Math.round(node.height));
    element.appendChild(handle);

    let gesture = null;
    element.addEventListener('pointerdown', (event) => {
      if (event.button !== undefined && event.button !== 0) return;
      if (event.target === handle) return;
      event.preventDefault();
      const additive = event.ctrlKey || event.metaKey || event.shiftKey;
      const wasSelected = selectedIds.includes(node.id);
      if (additive) {
        if (wasSelected) {
          selectedIds = selectedIds.filter((id) => id !== node.id);
          if (primarySelectedId === node.id) primarySelectedId = selectedIds[selectedIds.length - 1] || null;
        } else {
          selectedIds = selectedIds.concat(node.id);
          primarySelectedId = node.id;
        }
      } else if (!wasSelected) {
        selectedIds = [node.id];
        primarySelectedId = node.id;
      }
      updateSelectionUI();
      if (!selectedIds.includes(node.id)) return;
      commitInspectorEdit();
      const point = eventCanvasPoint(event);
      const origins = getSelectedNodes().map((selected) => ({ id: selected.id, x: selected.x, y: selected.y }));
      history.begin(project);
      gesture = { pointerId: event.pointerId, start: point, origins: origins, moved: false };
      if (element.setPointerCapture) element.setPointerCapture(event.pointerId);
    });

    element.addEventListener('pointermove', (event) => {
      if (!gesture || gesture.pointerId !== event.pointerId) return;
      event.preventDefault();
      const point = eventCanvasPoint(event);
      const dx = point.x - gesture.start.x;
      const dy = point.y - gesture.start.y;
      if (!gesture.moved && Math.abs(dx) + Math.abs(dy) <= 2) return;
      gesture.moved = true;
      gesture.origins.forEach((origin) => {
        const moving = getActiveNodes().find((candidate) => candidate.id === origin.id);
        if (!moving) return;
        moving.x = snapValue(clamp(origin.x + dx, 0, 5000));
        moving.y = snapValue(clamp(origin.y + dy, 0, 5000));
        updateNodeElement(moving);
      });
      updateInspector();
      updateSchema();
    });

    const finishMove = (event) => {
      if (!gesture || gesture.pointerId !== event.pointerId) return;
      const changed = history.commit(project);
      gesture = null;
      if (element.hasPointerCapture && element.hasPointerCapture(event.pointerId)) {
        try { element.releasePointerCapture(event.pointerId); } catch (_) { /* WebView may have already released it. */ }
      }
      if (changed) {
        project.updatedAt = Date.now();
        render();
        scheduleAutosave();
      } else {
        updateInspector();
        updateSchema();
      }
      updateHistoryButtons();
    };
    element.addEventListener('pointerup', finishMove);
    element.addEventListener('pointercancel', finishMove);

    handle.addEventListener('pointerdown', (event) => {
      event.stopPropagation();
      event.preventDefault();
      if (event.button !== undefined && event.button !== 0) return;
      selectNode(node.id, null);
      commitInspectorEdit();
      const point = eventCanvasPoint(event);
      history.begin(project);
      gesture = {
        pointerId: event.pointerId,
        start: point,
        width: node.width,
        height: node.height,
        resize: true,
        moved: false
      };
      if (handle.setPointerCapture) handle.setPointerCapture(event.pointerId);
    });
    handle.addEventListener('pointermove', (event) => {
      if (!gesture || !gesture.resize || gesture.pointerId !== event.pointerId) return;
      event.preventDefault();
      const point = eventCanvasPoint(event);
      if (!gesture.moved && Math.abs(point.x - gesture.start.x) + Math.abs(point.y - gesture.start.y) <= 2) return;
      const width = clamp(gesture.width + point.x - gesture.start.x, 20, 1000);
      const height = clamp(gesture.height + point.y - gesture.start.y, 20, 1600);
      node.width = Math.max(20, snapValue(width));
      node.height = Math.max(20, snapValue(height));
      gesture.moved = gesture.moved || Math.abs(point.x - gesture.start.x) + Math.abs(point.y - gesture.start.y) > 1;
      renderNodeStyle(element, node, index);
      updateInspector();
      updateSchema();
      handle.setAttribute('aria-valuetext', Math.round(node.width) + ' by ' + Math.round(node.height));
    });
    const finishResize = (event) => {
      if (!gesture || !gesture.resize || gesture.pointerId !== event.pointerId) return;
      const changed = history.commit(project);
      gesture = null;
      if (handle.hasPointerCapture && handle.hasPointerCapture(event.pointerId)) {
        try { handle.releasePointerCapture(event.pointerId); } catch (_) { /* WebView may have already released it. */ }
      }
      if (changed) {
        project.updatedAt = Date.now();
        render();
        scheduleAutosave();
      }
      updateHistoryButtons();
    };
    handle.addEventListener('pointerup', finishResize);
    handle.addEventListener('pointercancel', finishResize);

    element.addEventListener('keydown', (event) => {
      if (event.key === 'Enter' || event.key === ' ') {
        event.preventDefault();
        selectNode(node.id, event);
      }
    });
    canvas.appendChild(element);
  });
  renderLayers();
  updateInspector();
  updateSchema();
  updateHistoryButtons();
  renderScreenSelect();
}

function selectNode(id, event) {
  const additive = event && (event.ctrlKey || event.metaKey || event.shiftKey);
  if (additive) {
    if (selectedIds.includes(id)) {
      selectedIds = selectedIds.filter((selectedId) => selectedId !== id);
      if (primarySelectedId === id) primarySelectedId = selectedIds[selectedIds.length - 1] || null;
    } else {
      selectedIds = selectedIds.concat(id);
      primarySelectedId = id;
    }
  } else {
    selectedIds = [id];
    primarySelectedId = id;
  }
  updateSelectionUI();
}

function clearSelection() {
  selectedIds = [];
  primarySelectedId = null;
  updateSelectionUI();
}

canvas.addEventListener('click', (event) => {
  if (event.target === canvas) clearSelection();
});

function beginMutation() {
  commitInspectorEdit();
  history.begin(project);
}

function finishMutation() {
  const changed = history.commit(project);
  if (changed) {
    project.updatedAt = Date.now();
    scheduleAutosave();
  }
  render();
  updateHistoryButtons();
  return changed;
}

function switchScreen(id) {
  commitInspectorEdit();
  if (!project.screens.some((screen) => screen.id === id)) return;
  project.currentScreenId = id;
  selectedIds = [];
  primarySelectedId = null;
  project.updatedAt = Date.now();
  render();
  scheduleAutosave();
}

function newScreen() {
  const name = window.prompt('New screen name:', 'Screen ' + (project.screens.length + 1));
  if (!name || !name.trim()) return;
  beginMutation();
  const id = 'screen_' + Date.now() + '_' + (project.screens.length + 1);
  project.screens.push({ id: id, name: name.trim().slice(0, 80), nodes: [] });
  project.currentScreenId = id;
  selectedIds = [];
  primarySelectedId = null;
  finishMutation();
}

function renameScreen() {
  const active = getActiveScreen();
  const name = window.prompt('Rename screen:', active.name);
  if (!name || !name.trim() || name.trim() === active.name) return;
  beginMutation();
  active.name = name.trim().slice(0, 80);
  finishMutation();
}

function duplicateScreen() {
  beginMutation();
  const active = getActiveScreen();
  const copy = JSON.parse(JSON.stringify(active));
  copy.id = 'screen_' + Date.now() + '_' + (project.screens.length + 1);
  copy.name = (active.name + ' (copy)').slice(0, 80);
  project.screens.push(copy);
  project.currentScreenId = copy.id;
  selectedIds = [];
  primarySelectedId = null;
  finishMutation();
}

function deleteScreen() {
  if (project.screens.length <= 1) {
    setStatus('Cannot delete the only screen in the project.', 'error');
    return;
  }
  if (!window.confirm('Delete the current screen and its layers?')) return;
  beginMutation();
  project.screens = project.screens.filter((screen) => screen.id !== project.currentScreenId);
  project.currentScreenId = project.screens[0].id;
  selectedIds = [];
  primarySelectedId = null;
  finishMutation();
}

function add(type) {
  if (!Core.NODE_TYPES.includes(type)) {
    setStatus('Unsupported layer type: ' + type, 'error');
    return;
  }
  const defaults = {
    Text: { width: 140, height: 40, fontSize: 16, textColor: '#ffffff', radius: 0, color: '#222222' },
    Button: { width: 160, height: 48, fontSize: 14, textColor: '#111111', radius: 12, color: '#ff6b00' },
    Input: { width: 220, height: 48, fontSize: 14, textColor: '#ffffff', radius: 8, color: '#2a2a2a' },
    Card: { width: 260, height: 140, fontSize: 14, textColor: '#ffffff', radius: 16, color: '#1e1e1e' },
    Image: { width: 160, height: 120, fontSize: 12, textColor: '#aaaaaa', radius: 12, color: '#333333' }
  };
  beginMutation();
  const cfg = defaults[type];
  const node = {
    id: nextNodeId(), type: type, label: type + ' ' + sequence,
    x: snapValue(24), y: snapValue(24 + (getActiveNodes().length % 8) * 36),
    width: cfg.width, height: cfg.height, fontSize: cfg.fontSize,
    textColor: cfg.textColor, color: cfg.color, borderColor: '#333333',
    radius: cfg.radius, opacity: 1, visible: true, zIndex: getActiveNodes().length
  };
  getActiveNodes().push(node);
  selectedIds = [node.id];
  primarySelectedId = node.id;
  finishMutation();
}

function duplicateSelected() {
  const current = getActiveNodes().find((node) => node.id === primarySelectedId);
  if (!current) return;
  beginMutation();
  const copy = JSON.parse(JSON.stringify(current));
  copy.id = nextNodeId();
  copy.label = (current.label + ' (copy)').slice(0, 300);
  copy.x = snapValue(current.x + 16);
  copy.y = snapValue(current.y + 16);
  copy.zIndex = getActiveNodes().length;
  getActiveNodes().push(copy);
  selectedIds = [copy.id];
  primarySelectedId = copy.id;
  finishMutation();
}

function deleteSelected() {
  if (!selectedIds.length) return;
  beginMutation();
  const selected = new Set(selectedIds);
  const nodes = getActiveNodes();
  const screen = getActiveScreen();
  screen.nodes = nodes.filter((node) => !selected.has(node.id));
  selectedIds = [];
  primarySelectedId = null;
  reindexNodes(screen.nodes);
  finishMutation();
}

function reindexNodes(nodes) {
  nodes.forEach((node, index) => { node.zIndex = index; });
}

function reorderPrimary(direction) {
  const nodes = getActiveNodes();
  const index = nodes.findIndex((node) => node.id === primarySelectedId);
  const target = index + direction;
  if (index < 0 || target < 0 || target >= nodes.length) return;
  beginMutation();
  const moved = nodes.splice(index, 1)[0];
  nodes.splice(target, 0, moved);
  reindexNodes(nodes);
  finishMutation();
}

function bringForward() { reorderPrimary(1); }
function sendBackward() { reorderPrimary(-1); }

function toggleVisibility() {
  const node = getActiveNodes().find((item) => item.id === primarySelectedId);
  if (!node) return;
  beginMutation();
  node.visible = node.visible === false;
  finishMutation();
}

function alignSelected(mode) {
  const nodes = getSelectedNodes();
  if (nodes.length < 2) {
    setStatus('Select at least two layers (Shift-click or Ctrl/Cmd-click) to align.', 'error');
    return;
  }
  const anchor = nodes.find((node) => node.id === primarySelectedId) || nodes[0];
  beginMutation();
  nodes.forEach((node) => {
    if (node.id === anchor.id) return;
    if (mode === 'left') node.x = snapValue(anchor.x);
    else if (mode === 'centerX') node.x = snapValue(anchor.x + anchor.width / 2 - node.width / 2);
    else if (mode === 'top') node.y = snapValue(anchor.y);
    else if (mode === 'centerY') node.y = snapValue(anchor.y + anchor.height / 2 - node.height / 2);
  });
  finishMutation();
}

function distributeSelected(axis) {
  const nodes = getSelectedNodes();
  if (nodes.length < 3) {
    setStatus('Select at least three layers to distribute them evenly.', 'error');
    return;
  }
  beginMutation();
  const horizontal = axis === 'horizontal';
  const sorted = nodes.slice().sort((left, right) => horizontal ? left.x - right.x : left.y - right.y);
  const first = sorted[0];
  const last = sorted[sorted.length - 1];
  const start = horizontal ? first.x : first.y;
  const end = horizontal ? last.x + last.width : last.y + last.height;
  const occupied = sorted.reduce((sum, node) => sum + (horizontal ? node.width : node.height), 0);
  const gap = (end - start - occupied) / (sorted.length - 1);
  let cursor = start;
  sorted.forEach((node) => {
    if (horizontal) node.x = snapValue(cursor);
    else node.y = snapValue(cursor);
    cursor += (horizontal ? node.width : node.height) + gap;
  });
  finishMutation();
}

function setSnapEnabled(enabled) {
  gridEnabled = Boolean(enabled);
  canvas.classList.toggle('grid-visible', gridEnabled);
}

function beginInspectorEdit() {
  if (!primarySelectedId) return;
  if (!inspectorEditing) {
    history.begin(project);
    inspectorEditing = true;
  }
}

function applyChanges() {
  const node = getActiveNodes().find((item) => item.id === primarySelectedId);
  if (!node) return;
  beginInspectorEdit();
  const getNumber = (id, current, minimum, maximum) => {
    const value = Number(document.querySelector('#' + id).value);
    return Number.isFinite(value) ? clamp(value, minimum, maximum) : current;
  };
  node.label = document.querySelector('#label').value.slice(0, 300);
  node.x = getNumber('prop-x', node.x, 0, 5000);
  node.y = getNumber('prop-y', node.y, 0, 5000);
  node.width = getNumber('prop-w', node.width, 20, 1000);
  node.height = getNumber('prop-h', node.height, 20, 1600);
  node.fontSize = getNumber('prop-font', node.fontSize, 8, 96);
  node.radius = getNumber('radius', node.radius, 0, 64);
  node.color = document.querySelector('#color').value;
  node.textColor = document.querySelector('#text-color').value;
  updateNodeElement(node);
  renderLayers();
  updateSchema();
}

function commitInspectorEdit() {
  if (!inspectorEditing) return;
  inspectorEditing = false;
  const changed = history.commit(project);
  if (changed) {
    project.updatedAt = Date.now();
    updateInspector();
    updateSchema();
    updateHistoryButtons();
    scheduleAutosave();
  }
}

function undo() {
  commitInspectorEdit();
  const restored = history.undo(project);
  if (!restored) return;
  project = restored;
  project.updatedAt = Date.now();
  reconcileSelection();
  render();
  scheduleAutosave();
}

function redo() {
  commitInspectorEdit();
  const restored = history.redo(project);
  if (!restored) return;
  project = restored;
  project.updatedAt = Date.now();
  reconcileSelection();
  render();
  scheduleAutosave();
}

function reconcileSelection() {
  const ids = new Set(getActiveNodes().map((node) => node.id));
  selectedIds = selectedIds.filter((id) => ids.has(id));
  if (!ids.has(primarySelectedId)) primarySelectedId = selectedIds[selectedIds.length - 1] || null;
}

function escapeHtml(value) {
  return String(value).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

function makePreviewHtml(screen) {
  const elements = screen.nodes.filter((node) => node.visible !== false).map((node) =>
    '<div style="position:absolute;left:' + node.x + 'px;top:' + node.y + 'px;width:' + node.width + 'px;height:' + node.height +
    'px;background:' + node.color + ';color:' + node.textColor + ';font-size:' + node.fontSize + 'px;border-radius:' + node.radius +
    'px;opacity:' + (node.opacity === undefined ? 1 : node.opacity) + ';display:flex;align-items:center;justify-content:center;white-space:pre-wrap;overflow-wrap:anywhere">' +
    escapeHtml(node.label) + '</div>').join('');
  return '<main style="position:relative;width:360px;min-height:720px;background:#1a1a1a;overflow:hidden">' + elements + '</main>';
}

function isValidStoredProject(raw) {
  try {
    const normalized = Core.normalizeProject(raw);
    return Core.validateProject(normalized.project).length === 0;
  } catch (_) {
    return false;
  }
}

function persistProjectNow() {
  project.updatedAt = Date.now();
  const validationErrors = Core.validateProject(project);
  if (validationErrors.length) {
    setStatus('Not saved: ' + validationErrors[0], 'error');
    return false;
  }
  const encoded = JSON.stringify(project);
  try {
    const previous = localStorage.getItem(PROJECT_KEY);
    if (previous && isValidStoredProject(previous)) localStorage.setItem(BACKUP_KEY, previous);
    localStorage.setItem(PROJECT_KEY, encoded);
  } catch (error) {
    setStatus('Autosave failed: ' + error.message + '. Use Save or export your project JSON.', 'error');
    return false;
  }
  try {
    if (window.ZipBug && typeof window.ZipBug.save === 'function') {
      const result = window.ZipBug.save(project.name, encoded, makePreviewHtml(getActiveScreen()));
      if (typeof result === 'string' && result.indexOf('ERROR:') === 0) {
        setStatus('Local autosave saved; Android save failed: ' + result.slice(6), 'error');
        return false;
      }
      setStatus('Saved on device · ' + new Date().toLocaleTimeString(), 'success');
    } else {
      setStatus('Autosaved in this WebView · ' + new Date().toLocaleTimeString(), 'success');
    }
    return true;
  } catch (error) {
    setStatus('Local autosave saved; device save failed: ' + error.message, 'error');
    return false;
  }
}

function scheduleAutosave() {
  if (autosaveTimer) window.clearTimeout(autosaveTimer);
  setStatus('Unsaved changes…', '');
  autosaveTimer = window.setTimeout(() => {
    autosaveTimer = null;
    setStatus('Saving…', '');
    persistProjectNow();
  }, AUTOSAVE_DELAY_MS);
}

function save() {
  if (autosaveTimer) {
    window.clearTimeout(autosaveTimer);
    autosaveTimer = null;
  }
  commitInspectorEdit();
  persistProjectNow();
}

function loadNativeProject() {
  try {
    if (window.ZipBug && typeof window.ZipBug.loadProject === 'function') return window.ZipBug.loadProject();
  } catch (error) {
    return '';
  }
  return '';
}

function loadInitialProject() {
  let local = '';
  let backup = '';
  try {
    local = localStorage.getItem(PROJECT_KEY) || '';
    backup = localStorage.getItem(BACKUP_KEY) || '';
  } catch (error) {
    setStatus('WebView storage is unavailable: ' + error.message, 'error');
  }
  const recovery = Core.selectRecovery([
    { source: 'local autosave', raw: local },
    { source: 'last good save', raw: backup },
    { source: 'Android project file', raw: loadNativeProject() }
  ]);
  if (recovery.project) {
    project = recovery.project;
    updateSequence();
    const rejectedText = recovery.rejected.length ? ' An invalid save was skipped; the backup is retained.' : '';
    const warningText = recovery.warnings.length ? ' ' + recovery.warnings.join(' ') : '';
    setStatus('Recovered from ' + recovery.source + '.' + warningText + rejectedText,
      recovery.warnings.length || recovery.rejected.length ? 'error' : 'success');
    if (recovery.warnings.length || recovery.source !== 'local autosave') scheduleAutosave();
    return true;
  }
  if (recovery.rejected.length) {
    setStatus('No valid saved project was found. The last good save remains available for recovery.', 'error');
  } else {
    setStatus('New project · autosave enabled', '');
  }
  project = Core.defaultProject();
  updateSequence();
  return false;
}

function restoreLastGood() {
  let raw = '';
  try { raw = localStorage.getItem(BACKUP_KEY) || ''; } catch (_) { /* The status below explains the missing backup. */ }
  if (!raw) {
    setStatus('No last-good browser save is available.', 'error');
    return;
  }
  try {
    const result = Core.normalizeProject(raw);
    beginMutation();
    project = result.project;
    selectedIds = [];
    primarySelectedId = null;
    updateSequence();
    finishMutation();
    setStatus('Restored the last good save.' + (result.warnings.length ? ' ' + result.warnings.join(' ') : ''), 'success');
  } catch (error) {
    setStatus('The backup could not be restored: ' + (error.errors || [error.message]).join(' '), 'error');
  }
}

function importProjectText(raw) {
  if (typeof raw !== 'string' || raw.length > MAX_IMPORT_BYTES) {
    setStatus('Import failed: project JSON must be smaller than 2 MB.', 'error');
    return false;
  }
  try {
    const result = Core.normalizeProject(raw);
    beginMutation();
    project = result.project;
    project.updatedAt = Date.now();
    selectedIds = [];
    primarySelectedId = null;
    updateSequence();
    finishMutation();
    setStatus('Imported project successfully.' + (result.warnings.length ? ' ' + result.warnings.join(' ') : ''),
      result.warnings.length ? '' : 'success');
    return true;
  } catch (error) {
    const errors = error.errors || [error.message];
    setStatus('Import failed: ' + errors.slice(0, 3).join(' '), 'error');
    return false;
  }
}

function importFromFile(file) {
  if (!file) return;
  if (file.size > MAX_IMPORT_BYTES) {
    setStatus('Import failed: choose a JSON file smaller than 2 MB.', 'error');
    document.querySelector('#project-import').value = '';
    return;
  }
  const reader = new FileReader();
  reader.onload = () => {
    importProjectText(String(reader.result || ''));
    document.querySelector('#project-import').value = '';
  };
  reader.onerror = () => {
    setStatus('Import failed: the selected file could not be read.', 'error');
    document.querySelector('#project-import').value = '';
  };
  reader.readAsText(file);
}

function exportXml() {
  const active = getActiveScreen();
  try {
    const xml = Core.exportXml(project, active);
    if (window.ZipBug && typeof window.ZipBug.exportXml === 'function') {
      const result = window.ZipBug.exportXml(project.name + '_' + active.name, xml);
      if (typeof result === 'string' && result.indexOf('ERROR:') === 0) throw new Error(result.slice(6));
      setStatus('Android XML exported for ' + active.name + '.', 'success');
    } else {
      setStatus('XML export ready. Device export is unavailable in this preview.', '');
      window.alert(xml);
    }
  } catch (error) {
    setStatus((error.errors || [error.message]).slice(0, 3).join(' '), 'error');
  }
}

function exportCompose() {
  const active = getActiveScreen();
  try {
    const source = Core.exportCompose(project, active);
    if (window.ZipBug && typeof window.ZipBug.exportCompose === 'function') {
      const result = window.ZipBug.exportCompose(project.name + '_' + active.name, source);
      if (typeof result === 'string' && result.indexOf('ERROR:') === 0) throw new Error(result.slice(6));
      setStatus('Jetpack Compose source exported for ' + active.name + '.', 'success');
    } else {
      setStatus('Compose export ready. Device export is unavailable in this preview.', '');
      window.alert(source);
    }
  } catch (error) {
    setStatus((error.errors || [error.message]).slice(0, 3).join(' '), 'error');
  }
}

window.addEventListener('keydown', (event) => {
  const target = event.target;
  const typing = target && /INPUT|TEXTAREA|SELECT/.test(target.tagName);
  const modifier = event.ctrlKey || event.metaKey;
  if (modifier && event.key.toLowerCase() === 'z' && !typing) {
    event.preventDefault();
    if (event.shiftKey) redo(); else undo();
  } else if (modifier && event.key.toLowerCase() === 'y' && !typing) {
    event.preventDefault();
    redo();
  } else if (modifier && event.key.toLowerCase() === 'd' && !typing) {
    event.preventDefault();
    duplicateSelected();
  } else if ((event.key === 'Delete' || event.key === 'Backspace') && !typing) {
    event.preventDefault();
    deleteSelected();
  }
});

const recoveredProject = loadInitialProject();
renderScreenSelect();
if (!recoveredProject && getActiveNodes().length === 0) add('Button');
else render();
