'use strict';

const StudioCore = window.ZipBugStudioCore;
if (!StudioCore) throw new Error('ZipBugStudioCore failed to load');

if (window.ZipBug) {
  document.documentElement.classList.add('zipbug-android');
}

let project = StudioCore.migrateProject({
  id: 'proj_studio',
  name: 'ZipBug Studio',
  screens: [{ id: 'screen_main', name: 'Main', nodes: [] }],
  currentScreenId: 'screen_main',
  preview: { devicePreset: 'standard', gridSnap: true }
});

let selected = null;
let sequence = 0;
let gridSnapEnabled = true;
let currentDevicePreset = 'standard';
let canvasScale = 1;
let inspectorSnapshot = null;
let autosaveTimer = null;

const GRID_SIZE = 8;
const DEVICE_PRESETS = StudioCore.DEVICE_PRESETS;
const undoStack = [];
const redoStack = [];
const canvas = document.querySelector('#canvas');
const canvasStage = document.querySelector('#canvas-stage');
const canvasWrapper = document.querySelector('#canvas-wrapper');
const statusEl = document.querySelector('#studio-status');

function setStatus(message, kind) {
  if (!statusEl) return;
  statusEl.textContent = message;
  statusEl.className = kind || '';
}

function snapshot() {
  return JSON.stringify(project);
}

function flushNativeAutosave() {
  if (autosaveTimer) {
    clearTimeout(autosaveTimer);
    autosaveTimer = null;
  }
  try {
    if (window.ZipBug && typeof window.ZipBug.autosave === 'function') {
      window.ZipBug.autosave(JSON.stringify(project));
    }
  } catch (e) {
    console.warn('Native recovery autosave failed:', e);
  }
}

function scheduleNativeAutosave() {
  if (autosaveTimer) clearTimeout(autosaveTimer);
  autosaveTimer = setTimeout(flushNativeAutosave, 250);
}

function persistProject() {
  try {
    localStorage.setItem('zipbug_studio_project', JSON.stringify(project));
  } catch (e) {
    console.warn('LocalStorage save failed:', e);
  }
  scheduleNativeAutosave();
}

function touchProject() {
  project.schemaVersion = StudioCore.SCHEMA_VERSION;
  project.updatedAt = Date.now();
}

function pushSnapshot(value) {
  if (!value) return;
  if (undoStack.length === 0 || undoStack[undoStack.length - 1] !== value) {
    undoStack.push(value);
    if (undoStack.length > 50) undoStack.shift();
  }
  redoStack.length = 0;
  updateHistoryButtons();
}

function pushState() {
  pushSnapshot(snapshot());
}

function updateHistoryButtons() {
  const undoBtn = document.querySelector('#undo-btn');
  const redoBtn = document.querySelector('#redo-btn');
  if (undoBtn) undoBtn.disabled = undoStack.length === 0;
  if (redoBtn) redoBtn.disabled = redoStack.length === 0;
}

function updateSelectionActionButtons() {
  const hasSelection = !!getSelectedNode();
  ['duplicate-layer-btn', 'delete-layer-btn', 'align-left-btn', 'align-center-btn',
   'align-right-btn', 'align-top-btn', 'align-bottom-btn'].forEach(function(id) {
    const button = document.querySelector('#' + id);
    if (button) button.disabled = !hasSelection;
  });
  const canDistribute = getActiveNodes().length >= 3;
  ['distribute-h-btn', 'distribute-v-btn'].forEach(function(id) {
    const button = document.querySelector('#' + id);
    if (button) button.disabled = !canDistribute;
  });
}

function safeParse(raw) {
  if (!raw || typeof raw !== 'string') return null;
  try {
    return JSON.parse(raw);
  } catch (e) {
    return null;
  }
}

function loadPersistedProject() {
  let localRaw = null;
  let nativeRaw = null;
  try { localRaw = localStorage.getItem('zipbug_studio_project'); } catch (e) { console.warn(e); }
  try {
    if (window.ZipBug && typeof window.ZipBug.loadProject === 'function') nativeRaw = window.ZipBug.loadProject();
  } catch (e) {
    console.warn('Native recovery load failed:', e);
  }

  const localParsed = safeParse(localRaw);
  const nativeParsed = safeParse(nativeRaw);
  const candidates = [];
  if (localParsed) candidates.push({ source: 'local autosave', value: localParsed, updatedAt: Number(localParsed.updatedAt) || 0 });
  if (nativeParsed) candidates.push({ source: 'recovery storage', value: nativeParsed, updatedAt: Number(nativeParsed.updatedAt) || 0 });
  if (candidates.length === 0) return 'New project ready';

  candidates.sort(function(a, b) { return b.updatedAt - a.updatedAt; });
  const chosen = candidates[0];
  project = StudioCore.migrateProject(chosen.value);
  return 'Recovered ' + chosen.source + ' · schema v' + project.schemaVersion;
}

function recalculateSequence() {
  let maxSeq = 0;
  project.screens.forEach(function(screen) {
    screen.nodes.forEach(function(node) {
      const match = String(node.id || '').match(/^n(\d+)$/);
      if (match) maxSeq = Math.max(maxSeq, Number(match[1]));
    });
  });
  sequence = maxSeq;
}

function getActiveScreen() {
  let screen = project.screens.find(function(s) { return s.id === project.currentScreenId; });
  if (!screen) {
    project.currentScreenId = project.screens[0].id;
    screen = project.screens[0];
  }
  return screen;
}

function getActiveNodes() {
  return getActiveScreen().nodes;
}

function getSelectedNode() {
  return getActiveNodes().find(function(n) { return n.id === selected; }) || null;
}

function applyPreviewFromProject() {
  const preview = project.preview || {};
  currentDevicePreset = DEVICE_PRESETS[preview.devicePreset] ? preview.devicePreset : 'standard';
  gridSnapEnabled = preview.gridSnap !== false;
  const deviceSelect = document.querySelector('#device-select');
  const snapCheck = document.querySelector('#grid-snap-check');
  if (deviceSelect) deviceSelect.value = currentDevicePreset;
  if (snapCheck) snapCheck.checked = gridSnapEnabled;
  canvas.classList.toggle('grid-on', gridSnapEnabled);
}

function getDeviceDimensions() {
  return DEVICE_PRESETS[currentDevicePreset] || DEVICE_PRESETS.standard;
}

function updateCanvasScale() {
  const dim = getDeviceDimensions();
  const availableWidth = Math.max(220, (canvasWrapper ? canvasWrapper.clientWidth : dim.w) - 24);
  canvasScale = Math.min(1, availableWidth / dim.w);
  canvas.style.width = dim.w + 'px';
  canvas.style.height = dim.h + 'px';
  canvas.style.transform = 'scale(' + canvasScale + ')';
  canvasStage.style.width = Math.ceil(dim.w * canvasScale) + 'px';
  canvasStage.style.height = Math.ceil(dim.h * canvasScale) + 'px';
}

function switchDevicePreset(presetKey) {
  const nextPreset = DEVICE_PRESETS[presetKey] ? presetKey : 'standard';
  if (nextPreset === currentDevicePreset) return;
  pushState();
  currentDevicePreset = nextPreset;
  project.preview = project.preview || {};
  project.preview.devicePreset = currentDevicePreset;
  touchProject();
  persistProject();
  updateCanvasScale();
  renderCanvas();
  updateSchema();
  setStatus('Preview · ' + currentDevicePreset + ' ' + getDeviceDimensions().w + '×' + getDeviceDimensions().h + 'dp', 'ok');
}

function toggleGridSnap(enabled) {
  const nextEnabled = !!enabled;
  if (nextEnabled === gridSnapEnabled) return;
  pushState();
  gridSnapEnabled = nextEnabled;
  project.preview = project.preview || {};
  project.preview.gridSnap = gridSnapEnabled;
  canvas.classList.toggle('grid-on', gridSnapEnabled);
  touchProject();
  persistProject();
  setStatus(gridSnapEnabled ? 'Grid snap enabled · 8dp' : 'Grid snap disabled', 'ok');
}

function snap(value) {
  if (!gridSnapEnabled) return Math.round(value);
  return Math.round(value / GRID_SIZE) * GRID_SIZE;
}

function clampPosition(node) {
  const dim = getDeviceDimensions();
  node.x = Math.max(0, Math.min(snap(node.x), Math.max(0, dim.w - node.width)));
  node.y = Math.max(0, Math.min(snap(node.y), Math.max(0, dim.h - node.height)));
}

function undo() {
  if (undoStack.length === 0) {
    setStatus('Nothing to undo', 'warn');
    return;
  }
  redoStack.push(snapshot());
  project = StudioCore.migrateProject(JSON.parse(undoStack.pop()));
  touchProject();
  applyPreviewFromProject();
  if (!getSelectedNode()) selected = null;
  persistProject();
  renderScreenSelect();
  render();
  updateHistoryButtons();
  setStatus('Undo applied · autosaved', 'ok');
}

function redo() {
  if (redoStack.length === 0) {
    setStatus('Nothing to redo', 'warn');
    return;
  }
  undoStack.push(snapshot());
  project = StudioCore.migrateProject(JSON.parse(redoStack.pop()));
  touchProject();
  applyPreviewFromProject();
  if (!getSelectedNode()) selected = null;
  persistProject();
  renderScreenSelect();
  render();
  updateHistoryButtons();
  setStatus('Redo applied · autosaved', 'ok');
}

function renderScreenSelect() {
  const select = document.querySelector('#screen-select');
  if (!select) return;
  select.innerHTML = '';
  project.screens.forEach(function(screen) {
    const option = document.createElement('option');
    option.value = screen.id;
    option.textContent = screen.name;
    option.selected = screen.id === project.currentScreenId;
    select.appendChild(option);
  });
}

function switchScreen(id) {
  if (!project.screens.some(function(s) { return s.id === id; })) return;
  project.currentScreenId = id;
  selected = null;
  touchProject();
  persistProject();
  render();
}

function uniqueScreenId() {
  let i = Date.now();
  while (project.screens.some(function(s) { return s.id === 'screen_' + i; })) i++;
  return 'screen_' + i;
}

function newScreen() {
  const name = prompt('New Screen Name:', 'Screen ' + (project.screens.length + 1));
  if (!name || !name.trim()) return;
  pushState();
  const screen = { id: uniqueScreenId(), name: name.trim(), nodes: [] };
  project.screens.push(screen);
  project.currentScreenId = screen.id;
  selected = null;
  touchProject();
  persistProject();
  renderScreenSelect();
  render();
  setStatus('Screen created · ' + screen.name, 'ok');
}

function renameScreen() {
  const active = getActiveScreen();
  const value = prompt('Rename Screen:', active.name);
  if (!value || !value.trim() || value.trim() === active.name) return;
  pushState();
  active.name = value.trim();
  touchProject();
  persistProject();
  renderScreenSelect();
  updateSchema();
  setStatus('Screen renamed · autosaved', 'ok');
}

function duplicateScreen() {
  const active = getActiveScreen();
  pushState();
  const copy = StudioCore.clone(active);
  copy.id = uniqueScreenId();
  copy.name = active.name + ' Copy';
  copy.nodes = copy.nodes.map(function(node) {
    sequence++;
    const result = Object.assign({}, node);
    result.id = 'n' + sequence;
    return result;
  });
  project.screens.push(copy);
  project.currentScreenId = copy.id;
  selected = null;
  touchProject();
  persistProject();
  renderScreenSelect();
  render();
  setStatus('Screen duplicated · ' + copy.name, 'ok');
}

function deleteScreen() {
  if (project.screens.length <= 1) {
    setStatus('Cannot delete the only screen', 'warn');
    return;
  }
  if (!confirm('Delete current screen? This can be undone.')) return;
  pushState();
  project.screens = project.screens.filter(function(s) { return s.id !== project.currentScreenId; });
  project.currentScreenId = project.screens[0].id;
  selected = null;
  touchProject();
  persistProject();
  renderScreenSelect();
  render();
  setStatus('Screen deleted · Undo is available', 'ok');
}

function createNode(type) {
  sequence++;
  const presets = {
    Text: { w: 140, h: 40, font: 16, tc: '#FFFFFF', bg: '#222222', r: 0 },
    Button: { w: 160, h: 48, font: 14, tc: '#111111', bg: '#FF6B00', r: 12 },
    Input: { w: 220, h: 48, font: 14, tc: '#FFFFFF', bg: '#2A2A2A', r: 8 },
    Card: { w: 260, h: 140, font: 14, tc: '#FFFFFF', bg: '#1E1E1E', r: 16 },
    Image: { w: 160, h: 120, font: 12, tc: '#AAAAAA', bg: '#333333', r: 12 }
  };
  const cfg = presets[type] || presets.Text;
  const dim = getDeviceDimensions();
  return {
    id: 'n' + sequence,
    type: type,
    label: type + ' ' + sequence,
    x: snap(Math.min(32, Math.max(0, dim.w - cfg.w))),
    y: snap(Math.min(32 + ((sequence % 7) * 40), Math.max(0, dim.h - cfg.h))),
    width: cfg.w,
    height: cfg.h,
    fontSize: cfg.font,
    textColor: cfg.tc,
    color: cfg.bg,
    radius: cfg.r,
    zIndex: getActiveNodes().length
  };
}

function add(type) {
  if (!StudioCore.ALLOWED_TYPES.includes(type)) return;
  pushState();
  const node = createNode(type);
  getActiveNodes().push(node);
  selected = node.id;
  touchProject();
  persistProject();
  render();
  setStatus(type + ' added · drag or resize it', 'ok');
}

function duplicateSelected() {
  const current = getSelectedNode();
  if (!current) {
    setStatus('Select a layer before duplicating', 'warn');
    return;
  }
  pushState();
  sequence++;
  const copy = StudioCore.clone(current);
  copy.id = 'n' + sequence;
  copy.label = current.label + ' Copy';
  copy.x = current.x + 16;
  copy.y = current.y + 16;
  clampPosition(copy);
  getActiveNodes().push(copy);
  selected = copy.id;
  touchProject();
  persistProject();
  render();
  setStatus('Layer duplicated · ' + copy.label, 'ok');
}

function deleteSelected() {
  if (!selected) {
    setStatus('Select a layer before deleting', 'warn');
    return;
  }
  pushState();
  const screen = getActiveScreen();
  screen.nodes = screen.nodes.filter(function(n) { return n.id !== selected; });
  selected = null;
  touchProject();
  persistProject();
  render();
  setStatus('Layer deleted · Undo is available', 'ok');
}

function moveLayerById(id, delta) {
  const nodes = getActiveNodes();
  const index = nodes.findIndex(function(n) { return n.id === id; });
  const target = index + delta;
  if (index < 0 || target < 0 || target >= nodes.length) return;
  pushState();
  const node = nodes.splice(index, 1)[0];
  nodes.splice(target, 0, node);
  nodes.forEach(function(n, i) { n.zIndex = i; });
  selected = id;
  touchProject();
  persistProject();
  render();
  setStatus('Layer order updated · autosaved', 'ok');
}

function moveSelectedLayer(delta) {
  if (!selected) {
    setStatus('Select a layer before reordering', 'warn');
    return;
  }
  moveLayerById(selected, delta);
}

function moveSelectedToEdge(edge) {
  const nodes = getActiveNodes();
  const index = nodes.findIndex(function(n) { return n.id === selected; });
  if (index < 0) {
    setStatus('Select a layer before reordering', 'warn');
    return;
  }
  const target = edge === 'front' ? nodes.length - 1 : 0;
  if (index === target) return;
  pushState();
  const node = nodes.splice(index, 1)[0];
  nodes.splice(target, 0, node);
  nodes.forEach(function(n, i) { n.zIndex = i; });
  touchProject();
  persistProject();
  render();
  setStatus(edge === 'front' ? 'Moved to front' : 'Moved to back', 'ok');
}

function alignSelected(side) {
  const node = getSelectedNode();
  if (!node) {
    setStatus('Select a layer before aligning', 'warn');
    return;
  }
  pushState();
  const dim = getDeviceDimensions();
  if (side === 'left') node.x = 16;
  if (side === 'center') node.x = Math.max(0, (dim.w - node.width) / 2);
  if (side === 'right') node.x = Math.max(0, dim.w - node.width - 16);
  if (side === 'top') node.y = 16;
  if (side === 'bottom') node.y = Math.max(0, dim.h - node.height - 16);
  clampPosition(node);
  touchProject();
  persistProject();
  render();
  setStatus('Aligned ' + side + ' · autosaved', 'ok');
}

function distributeNodes(direction) {
  const nodes = getActiveNodes();
  if (nodes.length < 3) {
    setStatus('Distribute requires at least 3 layers on this screen', 'warn');
    return;
  }
  pushState();
  if (direction === 'h') {
    const sorted = nodes.slice().sort(function(a, b) { return (a.x + a.width / 2) - (b.x + b.width / 2); });
    const first = sorted[0].x + sorted[0].width / 2;
    const last = sorted[sorted.length - 1].x + sorted[sorted.length - 1].width / 2;
    const step = (last - first) / (sorted.length - 1);
    sorted.forEach(function(node, index) {
      if (index === 0 || index === sorted.length - 1) return;
      node.x = snap(first + (step * index) - node.width / 2);
      clampPosition(node);
    });
  } else {
    const sorted = nodes.slice().sort(function(a, b) { return (a.y + a.height / 2) - (b.y + b.height / 2); });
    const first = sorted[0].y + sorted[0].height / 2;
    const last = sorted[sorted.length - 1].y + sorted[sorted.length - 1].height / 2;
    const step = (last - first) / (sorted.length - 1);
    sorted.forEach(function(node, index) {
      if (index === 0 || index === sorted.length - 1) return;
      node.y = snap(first + (step * index) - node.height / 2);
      clampPosition(node);
    });
  }
  touchProject();
  persistProject();
  render();
  setStatus(direction === 'h' ? 'Distributed horizontally' : 'Distributed vertically', 'ok');
}

function selectNode(id) {
  selected = id;
  refreshSelectionVisuals();
  renderLayers();
  updateInspector();
  updateSelectionActionButtons();
}

function clearSelection() {
  selected = null;
  refreshSelectionVisuals();
  renderLayers();
  updateInspector();
  updateSelectionActionButtons();
}

function refreshSelectionVisuals() {
  canvas.querySelectorAll('.node').forEach(function(el) {
    el.classList.toggle('sel', el.dataset.nodeId === selected);
  });
}

function beginGestureHistory() {
  return snapshot();
}

function commitGestureHistory(before, changed, label) {
  if (!changed) return;
  pushSnapshot(before);
  touchProject();
  persistProject();
  updateInspector();
  updateSchema();
  setStatus(label + ' · autosaved', 'ok');
}

function renderCanvas() {
  canvas.innerHTML = '';
  const nodes = getActiveNodes();
  nodes.forEach(function(node, index) {
    node.zIndex = index;
    const el = document.createElement('div');
    el.className = 'node' + (selected === node.id ? ' sel' : '');
    el.dataset.nodeId = node.id;
    el.style.left = node.x + 'px';
    el.style.top = node.y + 'px';
    el.style.width = node.width + 'px';
    el.style.height = node.height + 'px';
    el.style.background = node.color;
    el.style.borderRadius = node.radius + 'px';
    el.style.color = node.textColor;
    el.style.fontSize = node.fontSize + 'px';
    el.style.zIndex = String(index + 1);
    if (node.type === 'Input') el.style.border = '1px solid #666';
    el.textContent = node.label;

    const handle = document.createElement('div');
    handle.className = 'handle';
    handle.setAttribute('aria-label', 'Resize ' + node.label);
    el.appendChild(handle);

    el.onclick = function(event) {
      event.stopPropagation();
      selectNode(node.id);
    };

    let dragPointerId = null;
    let dragStartX = 0;
    let dragStartY = 0;
    let dragOriginX = 0;
    let dragOriginY = 0;
    let dragBefore = null;
    let dragChanged = false;

    el.onpointerdown = function(event) {
      if (event.target === handle) return;
      event.preventDefault();
      selectNode(node.id);
      dragPointerId = event.pointerId;
      dragStartX = event.clientX;
      dragStartY = event.clientY;
      dragOriginX = node.x;
      dragOriginY = node.y;
      dragBefore = beginGestureHistory();
      dragChanged = false;
      el.setPointerCapture(event.pointerId);
    };

    el.onpointermove = function(event) {
      if (dragPointerId !== event.pointerId || !el.hasPointerCapture(event.pointerId)) return;
      const dx = (event.clientX - dragStartX) / canvasScale;
      const dy = (event.clientY - dragStartY) / canvasScale;
      if (!dragChanged && Math.abs(dx) < 1 && Math.abs(dy) < 1) return;
      dragChanged = true;
      node.x = dragOriginX + dx;
      node.y = dragOriginY + dy;
      clampPosition(node);
      el.style.left = node.x + 'px';
      el.style.top = node.y + 'px';
    };

    function endDrag(event) {
      if (dragPointerId !== event.pointerId) return;
      if (el.hasPointerCapture(event.pointerId)) el.releasePointerCapture(event.pointerId);
      dragPointerId = null;
      commitGestureHistory(dragBefore, dragChanged, 'Layer moved');
    }
    el.onpointerup = endDrag;
    el.onpointercancel = endDrag;

    let resizePointerId = null;
    let resizeStartX = 0;
    let resizeStartY = 0;
    let resizeWidth = 0;
    let resizeHeight = 0;
    let resizeBefore = null;
    let resizeChanged = false;

    handle.onpointerdown = function(event) {
      event.preventDefault();
      event.stopPropagation();
      selectNode(node.id);
      resizePointerId = event.pointerId;
      resizeStartX = event.clientX;
      resizeStartY = event.clientY;
      resizeWidth = node.width;
      resizeHeight = node.height;
      resizeBefore = beginGestureHistory();
      resizeChanged = false;
      handle.setPointerCapture(event.pointerId);
    };

    handle.onpointermove = function(event) {
      if (resizePointerId !== event.pointerId || !handle.hasPointerCapture(event.pointerId)) return;
      const dx = (event.clientX - resizeStartX) / canvasScale;
      const dy = (event.clientY - resizeStartY) / canvasScale;
      if (!resizeChanged && Math.abs(dx) < 1 && Math.abs(dy) < 1) return;
      resizeChanged = true;
      const dim = getDeviceDimensions();
      const maxWidth = Math.max(30, dim.w - node.x);
      const maxHeight = Math.max(20, dim.h - node.y);
      node.width = Math.min(maxWidth, Math.max(30, snap(resizeWidth + dx)));
      node.height = Math.min(maxHeight, Math.max(20, snap(resizeHeight + dy)));
      el.style.width = node.width + 'px';
      el.style.height = node.height + 'px';
    };

    function endResize(event) {
      if (resizePointerId !== event.pointerId) return;
      if (handle.hasPointerCapture(event.pointerId)) handle.releasePointerCapture(event.pointerId);
      resizePointerId = null;
      commitGestureHistory(resizeBefore, resizeChanged, 'Layer resized');
    }
    handle.onpointerup = endResize;
    handle.onpointercancel = endResize;

    canvas.appendChild(el);
  });
  canvas.classList.toggle('grid-on', gridSnapEnabled);
}

function renderLayers() {
  const container = document.querySelector('#layers-list');
  if (!container) return;
  container.innerHTML = '';
  const nodes = getActiveNodes();
  if (nodes.length === 0) {
    const empty = document.createElement('div');
    empty.className = 'empty-hint';
    empty.textContent = 'No layers yet. Add Text, Button, Input, Card, or Image.';
    container.appendChild(empty);
    return;
  }
  for (let i = nodes.length - 1; i >= 0; i--) {
    const node = nodes[i];
    const row = document.createElement('div');
    row.className = 'layer-item' + (node.id === selected ? ' active' : '');

    const name = document.createElement('span');
    name.className = 'layer-name';
    name.textContent = node.label;
    row.appendChild(name);

    const meta = document.createElement('span');
    meta.className = 'layer-meta';
    const type = document.createElement('small');
    type.textContent = node.type;
    meta.appendChild(type);

    const down = document.createElement('button');
    down.className = 'layer-move';
    down.textContent = 'Down';
    down.disabled = i === 0;
    down.onclick = function(event) {
      event.stopPropagation();
      selected = node.id;
      moveLayerById(node.id, -1);
    };
    meta.appendChild(down);

    const up = document.createElement('button');
    up.className = 'layer-move';
    up.textContent = 'Up';
    up.disabled = i === nodes.length - 1;
    up.onclick = function(event) {
      event.stopPropagation();
      selected = node.id;
      moveLayerById(node.id, 1);
    };
    meta.appendChild(up);

    row.appendChild(meta);
    row.onclick = function() { selectNode(node.id); };
    container.appendChild(row);
  }
}

function updateSchema() {
  const schema = document.querySelector('#schema');
  if (schema) schema.textContent = JSON.stringify(project, null, 2);
}

function setInspectorDisabled(disabled) {
  ['label', 'color', 'radius', 'prop-w', 'prop-h', 'prop-font', 'text-color'].forEach(function(id) {
    const input = document.querySelector('#' + id);
    if (input) input.disabled = disabled;
  });
}

function updateInspector() {
  const node = getSelectedNode();
  setInspectorDisabled(!node);
  const values = node ? {
    label: node.label,
    color: node.color,
    radius: node.radius,
    'prop-w': Math.round(node.width),
    'prop-h': Math.round(node.height),
    'prop-font': Math.round(node.fontSize),
    'text-color': node.textColor
  } : {
    label: '', color: '#ff6b00', radius: 0, 'prop-w': '', 'prop-h': '', 'prop-font': '', 'text-color': '#ffffff'
  };
  Object.keys(values).forEach(function(id) {
    const input = document.querySelector('#' + id);
    if (input) input.value = values[id];
  });
  updateSchema();
}

function beginInspectorEdit() {
  if (!getSelectedNode() || inspectorSnapshot) return;
  inspectorSnapshot = snapshot();
}

function applyChanges() {
  const node = getSelectedNode();
  if (!node) return;
  if (!inspectorSnapshot) inspectorSnapshot = snapshot();

  const next = {
    label: document.querySelector('#label').value,
    color: document.querySelector('#color').value,
    radius: Number(document.querySelector('#radius').value),
    width: Number(document.querySelector('#prop-w').value),
    height: Number(document.querySelector('#prop-h').value),
    fontSize: Number(document.querySelector('#prop-font').value),
    textColor: document.querySelector('#text-color').value
  };
  const changed = Object.keys(next).some(function(key) { return node[key] !== next[key]; });
  if (!changed) return;

  Object.assign(node, next);
  touchProject();
  persistProject();
  renderCanvas();
  renderLayers();
  updateSchema();
  setStatus('Editing ' + (node.label || node.id) + ' · autosaved', 'ok');
}

function commitInspectorEdit() {
  if (!inspectorSnapshot) return;
  const before = inspectorSnapshot;
  inspectorSnapshot = null;
  if (before !== snapshot()) {
    pushSnapshot(before);
    setStatus('Inspector edit committed · Undo is available', 'ok');
  }
}

function render() {
  updateCanvasScale();
  renderCanvas();
  renderLayers();
  updateInspector();
  updateHistoryButtons();
  updateSelectionActionButtons();
}

function showValidationFailure(result, target) {
  const details = StudioCore.formatIssues(result.errors, 8);
  setStatus(target + ' blocked · ' + (result.errors[0] ? result.errors[0].path + ': ' + result.errors[0].message : 'invalid schema'), 'error');
  alert(target + ' blocked. Fix these fields first:\n\n' + details);
}

function exportXml() {
  const active = getActiveScreen();
  const result = StudioCore.generateAndroidXml(project, active.id);
  if (!result.ok) {
    showValidationFailure(result, 'XML export');
    return;
  }
  let nativeResult = 'OK';
  try {
    if (window.ZipBug && typeof window.ZipBug.exportXml === 'function') {
      nativeResult = window.ZipBug.exportXml(active.name, JSON.stringify(project), result.content);
    }
  } catch (e) {
    setStatus('XML export failed · ' + e.message, 'error');
    return;
  }
  if (typeof nativeResult === 'string' && nativeResult.indexOf('ERROR:') === 0) {
    setStatus(nativeResult.substring(6), 'error');
    return;
  }
  const warning = result.warnings && result.warnings.length ? ' · ' + result.warnings.length + ' preview warning(s)' : '';
  setStatus('XML exported · ' + active.name + '.xml' + warning, warning ? 'warn' : 'ok');
}

function exportCompose() {
  const result = StudioCore.generateCompose(project);
  if (!result.ok) {
    showValidationFailure(result, 'Compose export');
    return;
  }
  const active = getActiveScreen();
  let nativeResult = 'OK';
  try {
    if (window.ZipBug && typeof window.ZipBug.exportCompose === 'function') {
      nativeResult = window.ZipBug.exportCompose(active.name, JSON.stringify(project), result.content);
    }
  } catch (e) {
    setStatus('Compose export failed · ' + e.message, 'error');
    return;
  }
  if (typeof nativeResult === 'string' && nativeResult.indexOf('ERROR:') === 0) {
    setStatus(nativeResult.substring(6), 'error');
    return;
  }
  const warning = result.warnings && result.warnings.length ? ' · ' + result.warnings.length + ' preview warning(s)' : '';
  setStatus('Compose exported · ' + active.name + '.kt' + warning, warning ? 'warn' : 'ok');
}

function buildPreviewHtml() {
  const active = getActiveScreen();
  const dim = getDeviceDimensions();
  let html = '<main style="position:relative;width:' + dim.w + 'px;min-height:' + dim.h + 'px;background:#1a1a1a">';
  active.nodes.forEach(function(node) {
    html += '<div style="position:absolute;left:' + Math.round(node.x) + 'px;top:' + Math.round(node.y) + 'px;width:' + Math.round(node.width) + 'px;height:' + Math.round(node.height) + 'px;background:' + node.color + ';color:' + node.textColor + ';font-size:' + Math.round(node.fontSize) + 'px;border-radius:' + Math.round(node.radius) + 'px;display:flex;align-items:center;justify-content:center">' + StudioCore.xmlEscape(node.label) + '</div>';
  });
  return html + '</main>';
}

function save() {
  touchProject();
  persistProject();
  const active = getActiveScreen();
  try {
    if (window.ZipBug && typeof window.ZipBug.save === 'function') {
      window.ZipBug.save(active.name, JSON.stringify(project), buildPreviewHtml());
      setStatus('Saved project · ' + active.name + ' · recovery updated', 'ok');
    } else {
      setStatus('Saved to browser storage · native bridge unavailable', 'warn');
    }
  } catch (e) {
    setStatus('Save failed · ' + e.message, 'error');
  }
}

function seedDefaultNode() {
  const node = createNode('Button');
  getActiveNodes().push(node);
  selected = node.id;
  touchProject();
  persistProject();
}

canvas.addEventListener('pointerdown', function(event) {
  if (event.target === canvas) clearSelection();
});

document.addEventListener('keydown', function(event) {
  const tag = document.activeElement ? document.activeElement.tagName : '';
  const editing = tag === 'INPUT' || tag === 'SELECT' || tag === 'TEXTAREA';
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'z') {
    event.preventDefault();
    if (event.shiftKey) redo(); else undo();
    return;
  }
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'y') {
    event.preventDefault();
    redo();
    return;
  }
  if (!editing && (event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'd') {
    event.preventDefault();
    duplicateSelected();
    return;
  }
  if (!editing && (event.key === 'Delete' || event.key === 'Backspace')) {
    event.preventDefault();
    deleteSelected();
  }
});

const recoveryMessage = loadPersistedProject();
recalculateSequence();
applyPreviewFromProject();
renderScreenSelect();
if (getActiveNodes().length === 0) seedDefaultNode();
render();
setStatus(recoveryMessage, recoveryMessage.indexOf('Recovered') === 0 ? 'ok' : '');

window.flushRecovery = flushNativeAutosave;
document.addEventListener('visibilitychange', function() {
  if (document.hidden) flushNativeAutosave();
});
window.addEventListener('pagehide', flushNativeAutosave);
window.addEventListener('resize', updateCanvasScale);
if (typeof ResizeObserver !== 'undefined' && canvasWrapper) {
  new ResizeObserver(updateCanvasScale).observe(canvasWrapper);
}
