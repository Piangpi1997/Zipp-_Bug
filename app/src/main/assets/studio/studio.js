let project = {
  id: 'proj_studio',
  name: 'ZipBug Studio',
  screens: [
    { id: 'screen_main', name: 'Main', nodes: [] }
  ],
  currentScreenId: 'screen_main'
};

let selected = null;
let sequence = 0;
let isPreviewMode = false;
const canvas = document.querySelector('#canvas');
const undoStack = [];
const redoStack = [];

let gridSnapEnabled = true;
const GRID_SIZE = 8;

const DEVICE_PRESETS = {
  small: { w: 320, h: 560 },
  standard: { w: 340, h: 620 },
  large: { w: 360, h: 680 }
};
let currentDevicePreset = 'standard';

function navigateBack() {
  if (window.ZipBug && window.ZipBug.navigateBack) {
    window.ZipBug.navigateBack();
  }
}

function openSheet(sheetId) {
  closeAllSheets();
  const backdrop = document.getElementById('sheet-backdrop');
  const sheet = document.getElementById(sheetId);
  if (backdrop) backdrop.classList.remove('hidden');
  if (sheet) sheet.classList.remove('hidden');
}

function closeAllSheets() {
  const backdrop = document.getElementById('sheet-backdrop');
  if (backdrop) backdrop.classList.add('hidden');
  document.querySelectorAll('.bottom-sheet').forEach(el => {
    el.classList.add('hidden');
  });
}

function togglePreviewMode() {
  isPreviewMode = !isPreviewMode;
  document.body.classList.toggle('preview-mode', isPreviewMode);
  const btn = document.getElementById('btn-preview-toggle');
  if (btn) {
    btn.textContent = isPreviewMode ? '✎ Edit' : '▶ Preview';
    btn.classList.toggle('active', isPreviewMode);
  }
  if (isPreviewMode) {
    closeAllSheets();
    save();
  }
}

function clearSelectionIfBackdrop(e) {
  if (e.target.id === 'canvas-stage' || e.target.id === 'canvas-wrapper' || e.target.id === 'canvas') {
    selected = null;
    render();
  }
}

function addAndClose(type) {
  add(type);
  closeAllSheets();
}

function switchDevicePreset(presetKey) {
  const preset = DEVICE_PRESETS[presetKey] || DEVICE_PRESETS.standard;
  currentDevicePreset = presetKey;
  if (canvas) {
    canvas.style.width = preset.w + 'px';
    canvas.style.minHeight = preset.h + 'px';
    canvas.style.height = preset.h + 'px';
  }
}

function toggleGridSnap(enabled) {
  gridSnapEnabled = !!enabled;
}

function snap(val) {
  if (!gridSnapEnabled) return Math.round(val);
  return Math.round(val / GRID_SIZE) * GRID_SIZE;
}

function getDeviceDimensions() {
  return DEVICE_PRESETS[currentDevicePreset] || DEVICE_PRESETS.standard;
}

function alignSelected(side) {
  const nodes = getActiveNodes();
  const node = nodes.find(n => n.id === selected);
  if (!node) return;
  pushState();
  const dim = getDeviceDimensions();
  switch (side) {
    case 'left':
      node.x = 16;
      break;
    case 'center':
      node.x = Math.max(0, Math.round((dim.w - node.width) / 2));
      break;
    case 'right':
      node.x = Math.max(0, dim.w - node.width - 16);
      break;
    case 'top':
      node.y = 16;
      break;
    case 'bottom':
      node.y = Math.max(0, dim.h - node.height - 16);
      break;
  }
  if (gridSnapEnabled) {
    node.x = snap(node.x);
    node.y = snap(node.y);
  }
  render();
}

function distributeNodes(direction) {
  const nodes = getActiveNodes();
  if (nodes.length < 3) {
    alert('At least 3 layers are required to distribute.');
    return;
  }
  pushState();
  if (direction === 'v') {
    const sorted = [...nodes].sort((a, b) => a.y - b.y);
    const minY = sorted[0].y;
    const lastNode = sorted[sorted.length - 1];
    const maxY = lastNode.y;
    const totalSpan = maxY - minY;
    const step = totalSpan / (sorted.length - 1);
    sorted.forEach((n, idx) => {
      if (idx > 0 && idx < sorted.length - 1) {
        n.y = snap(minY + (step * idx));
      }
    });
  } else if (direction === 'h') {
    const sorted = [...nodes].sort((a, b) => a.x - b.x);
    const minX = sorted[0].x;
    const lastNode = sorted[sorted.length - 1];
    const maxX = lastNode.x;
    const totalSpan = maxX - minX;
    const step = totalSpan / (sorted.length - 1);
    sorted.forEach((n, idx) => {
      if (idx > 0 && idx < sorted.length - 1) {
        n.x = snap(minX + (step * idx));
      }
    });
  }
  render();
}

function validateSchema(screen) {
  if (!screen) return { valid: false, error: 'No active screen found.' };
  if (!Array.isArray(screen.nodes)) return { valid: false, error: 'Screen nodes must be an array.' };
  const allowedTypes = ['Text', 'Button', 'Input', 'Card', 'Image'];
  for (let i = 0; i < screen.nodes.length; i++) {
    const n = screen.nodes[i];
    if (!n.id || typeof n.id !== 'string') {
      return { valid: false, error: 'Node #' + i + ' has missing or invalid id.' };
    }
    if (!allowedTypes.includes(n.type)) {
      return { valid: false, error: 'Node ' + n.id + ' has invalid type: ' + n.type };
    }
    if (typeof n.width !== 'number' || n.width <= 0 || isNaN(n.width)) {
      return { valid: false, error: 'Node ' + n.id + ' has invalid width: ' + n.width };
    }
    if (typeof n.height !== 'number' || n.height <= 0 || isNaN(n.height)) {
      return { valid: false, error: 'Node ' + n.id + ' has invalid height: ' + n.height };
    }
    if (typeof n.x !== 'number' || isNaN(n.x)) {
      return { valid: false, error: 'Node ' + n.id + ' has invalid x position: ' + n.x };
    }
    if (typeof n.y !== 'number' || isNaN(n.y)) {
      return { valid: false, error: 'Node ' + n.id + ' has invalid y position: ' + n.y };
    }
  }
  return { valid: true };
}

function getActiveScreen() {
  let screen = project.screens.find(s => s.id === project.currentScreenId);
  if (!screen) {
    if (project.screens.length === 0) {
      project.screens.push({ id: 'screen_1', name: 'Main', nodes: [] });
    }
    project.currentScreenId = project.screens[0].id;
    screen = project.screens[0];
  }
  return screen;
}

function getActiveNodes() {
  return getActiveScreen().nodes;
}

function persistProject() {
  try {
    localStorage.setItem('zipbug_studio_project', JSON.stringify(project));
  } catch (e) {
    console.warn('LocalStorage save failed:', e);
  }
}

function loadPersistedProject() {
  try {
    const raw = localStorage.getItem('zipbug_studio_project');
    if (raw) {
      const parsed = JSON.parse(raw);
      if (parsed && Array.isArray(parsed.screens) && parsed.screens.length > 0) {
        project = parsed;
        let maxSeq = 0;
        project.screens.forEach(s => {
          s.nodes.forEach(n => {
            const num = parseInt((n.id || '').replace(/\D/g, ''), 10);
            if (!isNaN(num) && num > maxSeq) maxSeq = num;
          });
        });
        sequence = maxSeq;
      }
    }
  } catch (e) {
    console.warn('LocalStorage load failed:', e);
  }
}

function pushState() {
  undoStack.push(JSON.stringify(project));
  if (undoStack.length > 30) undoStack.shift();
  redoStack.length = 0;
  persistProject();
}

function undo() {
  if (undoStack.length === 0) return;
  redoStack.push(JSON.stringify(project));
  project = JSON.parse(undoStack.pop());
  const activeNodes = getActiveNodes();
  if (!activeNodes.find(n => n.id === selected)) selected = null;
  renderScreenSelect();
  render();
}

function redo() {
  if (redoStack.length === 0) return;
  undoStack.push(JSON.stringify(project));
  project = JSON.parse(redoStack.pop());
  renderScreenSelect();
  render();
}

function renderScreenSelect() {
  const sel = document.querySelector('#screen-select');
  const pillLabel = document.querySelector('#current-screen-label');
  const active = getActiveScreen();
  if (pillLabel && active) {
    pillLabel.textContent = active.name;
  }
  if (!sel) return;
  sel.innerHTML = '';
  project.screens.forEach(s => {
    const opt = document.createElement('option');
    opt.value = s.id;
    opt.textContent = s.name;
    if (s.id === project.currentScreenId) opt.selected = true;
    sel.appendChild(opt);
  });
}

function switchScreen(id) {
  project.currentScreenId = id;
  selected = null;
  persistProject();
  renderScreenSelect();
  render();
}

function newScreen() {
  const name = prompt('New Screen Name:', 'Screen ' + (project.screens.length + 1));
  if (!name) return;
  pushState();
  const id = 'screen_' + Date.now();
  project.screens.push({ id: id, name: name.trim(), nodes: [] });
  project.currentScreenId = id;
  selected = null;
  renderScreenSelect();
  render();
}

function renameScreen() {
  const active = getActiveScreen();
  const newName = prompt('Rename Screen:', active.name);
  if (!newName || !newName.trim()) return;
  pushState();
  active.name = newName.trim();
  renderScreenSelect();
  render();
}

function duplicateScreen() {
  const active = getActiveScreen();
  pushState();
  const copy = JSON.parse(JSON.stringify(active));
  copy.id = 'screen_' + Date.now();
  copy.name = active.name + ' (copy)';
  project.screens.push(copy);
  project.currentScreenId = copy.id;
  selected = null;
  renderScreenSelect();
  render();
}

function deleteScreen() {
  if (project.screens.length <= 1) {
    alert('Cannot delete the only screen in the project.');
    return;
  }
  if (!confirm('Delete current screen?')) return;
  pushState();
  project.screens = project.screens.filter(s => s.id !== project.currentScreenId);
  project.currentScreenId = project.screens[0].id;
  selected = null;
  renderScreenSelect();
  render();
}

function add(type) {
  pushState();
  sequence++;
  const defaultColors = {
    Text: '#1E212B',
    Button: '#FF6B00',
    Input: '#242836',
    Card: '#1E212B',
    Image: '#282C3A'
  };

  const defaultSizes = {
    Text: { w: 160, h: 40, font: 16, tc: '#F8FAFC', r: 8 },
    Button: { w: 180, h: 48, font: 14, tc: '#0D0E12', r: 14 },
    Input: { w: 230, h: 48, font: 14, tc: '#F8FAFC', r: 10 },
    Card: { w: 260, h: 140, font: 14, tc: '#F8FAFC', r: 18 },
    Image: { w: 180, h: 120, font: 12, tc: '#94A3B8', r: 14 }
  };

  const cfg = defaultSizes[type] || { w: 140, h: 48, font: 14, tc: '#F8FAFC', r: 10 };

  const node = {
    id: 'n' + sequence,
    type: type,
    label: type + ' ' + sequence,
    x: 24,
    y: 24 + ((sequence % 7) * 40),
    width: cfg.w,
    height: cfg.h,
    fontSize: cfg.font,
    textColor: cfg.tc,
    color: defaultColors[type] || '#FF6B00',
    radius: cfg.r,
    zIndex: sequence
  };

  getActiveNodes().push(node);
  selectNode(node.id);
}

function duplicateSelected() {
  const nodes = getActiveNodes();
  const current = nodes.find(n => n.id === selected);
  if (!current) return;
  pushState();
  sequence++;
  const copy = JSON.parse(JSON.stringify(current));
  copy.id = 'n' + sequence;
  copy.label = current.label + ' (copy)';
  copy.x = Math.min(240, current.x + 16);
  copy.y = Math.min(460, current.y + 16);
  copy.zIndex = sequence;
  nodes.push(copy);
  selectNode(copy.id);
}

function deleteSelected() {
  if (!selected) return;
  pushState();
  const screen = getActiveScreen();
  screen.nodes = screen.nodes.filter(n => n.id !== selected);
  selected = null;
  render();
}

function bringForward() {
  const nodes = getActiveNodes();
  const idx = nodes.findIndex(n => n.id === selected);
  if (idx < 0 || idx >= nodes.length - 1) return;
  pushState();
  const tmp = nodes[idx];
  nodes[idx] = nodes[idx + 1];
  nodes[idx + 1] = tmp;
  render();
}

function sendBackward() {
  const nodes = getActiveNodes();
  const idx = nodes.findIndex(n => n.id === selected);
  if (idx <= 0) return;
  pushState();
  const tmp = nodes[idx];
  nodes[idx] = nodes[idx - 1];
  nodes[idx - 1] = tmp;
  render();
}

function render() {
  canvas.innerHTML = '';
  const nodes = getActiveNodes();

  const layersCountEl = document.getElementById('layers-count-label');
  if (layersCountEl) {
    layersCountEl.textContent = 'Layers (' + nodes.length + ')';
  }

  const ctxBar = document.getElementById('contextual-bar');
  const ctxLabel = document.getElementById('ctx-node-label');
  const activeSelNode = nodes.find(n => n.id === selected);
  if (ctxBar) {
    if (activeSelNode && !isPreviewMode) {
      ctxBar.classList.remove('hidden');
      if (ctxLabel) ctxLabel.textContent = activeSelNode.label || activeSelNode.type;
    } else {
      ctxBar.classList.add('hidden');
    }
  }

  nodes.forEach(function(node, index) {
    const el = document.createElement('div');
    el.className = 'node' + (selected === node.id ? ' sel' : '');
    el.style.left = node.x + 'px';
    el.style.top = node.y + 'px';
    el.style.width = node.width + 'px';
    el.style.height = node.height + 'px';
    el.style.background = node.color;
    el.style.borderRadius = node.radius + 'px';
    el.style.color = node.textColor;
    el.style.fontSize = node.fontSize + 'px';
    el.style.zIndex = index + 1;

    if (node.type === 'Input' || node.type === 'Card') {
      el.style.border = '1px solid #3A4054';
    }

    el.textContent = node.label;

    const handle = document.createElement('div');
    handle.className = 'handle';
    el.appendChild(handle);

    el.onclick = function(e) {
      e.stopPropagation();
      selectNode(node.id);
    };

    let startX = 0, startY = 0, origX = 0, origY = 0;
    el.onpointerdown = function(e) {
      if (isPreviewMode || e.target === handle) return;
      el.setPointerCapture(e.pointerId);
      startX = e.clientX;
      startY = e.clientY;
      origX = node.x;
      origY = node.y;
      selectNode(node.id);
    };

    el.onpointermove = function(e) {
      if (!el.hasPointerCapture(e.pointerId)) return;
      node.x = snap(Math.max(0, origX + (e.clientX - startX)));
      node.y = snap(Math.max(0, origY + (e.clientY - startY)));
      el.style.left = node.x + 'px';
      el.style.top = node.y + 'px';
    };

    el.onpointerup = function(e) {
      if (el.hasPointerCapture(e.pointerId)) {
        el.releasePointerCapture(e.pointerId);
        persistProject();
        updateInspector();
      }
    };

    let startW = 0, startH = 0, resizeStartX = 0, resizeStartY = 0;
    handle.onpointerdown = function(e) {
      if (isPreviewMode) return;
      e.stopPropagation();
      handle.setPointerCapture(e.pointerId);
      resizeStartX = e.clientX;
      resizeStartY = e.clientY;
      startW = node.width;
      startH = node.height;
    };

    handle.onpointermove = function(e) {
      if (!handle.hasPointerCapture(e.pointerId)) return;
      node.width = snap(Math.max(30, startW + (e.clientX - resizeStartX)));
      node.height = snap(Math.max(20, startH + (e.clientY - resizeStartY)));
      el.style.width = node.width + 'px';
      el.style.height = node.height + 'px';
    };

    handle.onpointerup = function(e) {
      if (handle.hasPointerCapture(e.pointerId)) {
        handle.releasePointerCapture(e.pointerId);
        persistProject();
        updateInspector();
      }
    };

    canvas.appendChild(el);
  });

  renderLayers();
  updateInspector();
  persistProject();
}

function selectNode(id) {
  selected = id;
  const nodes = getActiveNodes();
  const node = nodes.find(n => n.id === id);
  if (node) {
    document.querySelector('#label').value = node.label;
    document.querySelector('#color').value = node.color;
    document.querySelector('#radius').value = node.radius;
    document.querySelector('#prop-w').value = Math.round(node.width);
    document.querySelector('#prop-h').value = Math.round(node.height);
    document.querySelector('#prop-font').value = Math.round(node.fontSize);
    document.querySelector('#text-color').value = node.textColor || '#ffffff';
    const sub = document.querySelector('#prop-selected-subtitle');
    if (sub) sub.textContent = node.type + ' (' + node.id + ')';
  }
  render();
}

function renderLayers() {
  const container = document.querySelector('#layers-list');
  if (!container) return;
  container.innerHTML = '';
  const nodes = getActiveNodes();
  for (let i = nodes.length - 1; i >= 0; i--) {
    const n = nodes[i];
    const row = document.createElement('div');
    row.className = 'layer-item' + (n.id === selected ? ' active' : '');
    row.innerHTML = '<span>' + n.label + '</span><small>' + n.type + '</small>';
    row.onclick = function() {
      selectNode(n.id);
    };
    container.appendChild(row);
  }
}

function updateInspector() {
  const schemaEl = document.querySelector('#schema');
  if (schemaEl) {
    schemaEl.textContent = JSON.stringify(project, null, 2);
  }
}

function applyChanges() {
  const nodes = getActiveNodes();
  const node = nodes.find(n => n.id === selected);
  if (!node) return;

  node.label = document.querySelector('#label').value;
  node.color = document.querySelector('#color').value;
  node.radius = Number(document.querySelector('#radius').value);
  node.width = Number(document.querySelector('#prop-w').value) || node.width;
  node.height = Number(document.querySelector('#prop-h').value) || node.height;
  node.fontSize = Number(document.querySelector('#prop-font').value) || node.fontSize;
  node.textColor = document.querySelector('#text-color').value;

  render();
}

function exportXml() {
  const active = getActiveScreen();
  const val = validateSchema(active);
  if (!val.valid) {
    alert('Schema Validation Error:\n' + val.error + '\nExport aborted to prevent invalid Android code generation.');
    return;
  }

  let xml = '<?xml version="1.0" encoding="utf-8"?>\n' +
    '<androidx.constraintlayout.widget.ConstraintLayout\n' +
    '    xmlns:android="http://schemas.android.com/apk/res/android"\n' +
    '    xmlns:app="http://schemas.android.com/apk/res-auto"\n' +
    '    android:layout_width="match_parent"\n' +
    '    android:layout_height="match_parent"\n' +
    '    android:background="#0D0E12">\n\n';

  active.nodes.forEach(function(n) {
    if (n.type === 'Text') {
      xml += '    <TextView\n' +
        '        android:id="@+id/' + n.id + '"\n' +
        '        android:layout_width="' + Math.round(n.width) + 'dp"\n' +
        '        android:layout_height="' + Math.round(n.height) + 'dp"\n' +
        '        android:text="' + n.label + '"\n' +
        '        android:textColor="' + n.textColor + '"\n' +
        '        android:textSize="' + Math.round(n.fontSize) + 'sp"\n' +
        '        android:layout_marginStart="' + Math.round(n.x) + 'dp"\n' +
        '        android:layout_marginTop="' + Math.round(n.y) + 'dp"\n' +
        '        app:layout_constraintStart_toStartOf="parent"\n' +
        '        app:layout_constraintTop_toTopOf="parent" />\n\n';
    } else if (n.type === 'Button') {
      xml += '    <com.google.android.material.button.MaterialButton\n' +
        '        android:id="@+id/' + n.id + '"\n' +
        '        android:layout_width="' + Math.round(n.width) + 'dp"\n' +
        '        android:layout_height="' + Math.round(n.height) + 'dp"\n' +
        '        android:text="' + n.label + '"\n' +
        '        app:cornerRadius="' + Math.round(n.radius) + 'dp"\n' +
        '        android:layout_marginStart="' + Math.round(n.x) + 'dp"\n' +
        '        android:layout_marginTop="' + Math.round(n.y) + 'dp"\n' +
        '        app:layout_constraintStart_toStartOf="parent"\n' +
        '        app:layout_constraintTop_toTopOf="parent" />\n\n';
    } else {
      xml += '    <View\n' +
        '        android:id="@+id/' + n.id + '"\n' +
        '        android:layout_width="' + Math.round(n.width) + 'dp"\n' +
        '        android:layout_height="' + Math.round(n.height) + 'dp"\n' +
        '        android:background="' + n.color + '"\n' +
        '        android:layout_marginStart="' + Math.round(n.x) + 'dp"\n' +
        '        android:layout_marginTop="' + Math.round(n.y) + 'dp"\n' +
        '        app:layout_constraintStart_toStartOf="parent"\n' +
        '        app:layout_constraintTop_toTopOf="parent" />\n\n';
    }
  });

  xml += '</androidx.constraintlayout.widget.ConstraintLayout>';

  if (window.ZipBug && window.ZipBug.exportXml) {
    window.ZipBug.exportXml(active.name, xml);
  } else {
    alert('Exported XML for ' + active.name + ':\n' + xml.slice(0, 300) + '…');
  }
}

function exportCompose() {
  const active = getActiveScreen();
  const val = validateSchema(active);
  if (!val.valid) {
    alert('Schema Validation Error:\n' + val.error + '\nExport aborted to prevent invalid Android code generation.');
    return;
  }

  let comp = 'package com.zipbug.generated.ui\n\n' +
    'import androidx.compose.foundation.background\n' +
    'import androidx.compose.foundation.layout.*\n' +
    'import androidx.compose.material3.*\n' +
    'import androidx.compose.runtime.Composable\n' +
    'import androidx.compose.ui.Modifier\n' +
    'import androidx.compose.ui.graphics.Color\n' +
    'import androidx.compose.ui.unit.dp\n\n';

  project.screens.forEach(s => {
    const fnName = s.name.replace(/[^a-zA-Z0-9]/g, '') || 'Screen';
    comp += '@Composable\nfun ' + fnName + 'Screen() {\n' +
      '    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0D0E12))) {\n';
    s.nodes.forEach(n => {
      comp += '        // ' + n.type + ' (' + n.label + ')\n' +
        '        Box(modifier = Modifier.offset(x = ' + Math.round(n.x) + '.dp, y = ' + Math.round(n.y) + '.dp)' +
        '.size(width = ' + Math.round(n.width) + '.dp, height = ' + Math.round(n.height) + '.dp))\n';
    });
    comp += '    }\n}\n\n';
  });

  if (window.ZipBug && window.ZipBug.exportCompose) {
    window.ZipBug.exportCompose(getActiveScreen().name, comp);
  } else {
    alert('Exported Compose:\n' + comp);
  }
}

function save() {
  const active = getActiveScreen();
  let html = '<main style="position:relative;width:360px;min-height:640px;background:#16181F">';
  active.nodes.forEach(function(node) {
    html += '<div style="position:absolute;left:' +
      node.x + 'px;top:' + node.y +
      'px;width:' + node.width +
      'px;height:' + node.height +
      'px;background:' + node.color +
      ';color:' + node.textColor +
      ';font-size:' + node.fontSize +
      'px;border-radius:' + node.radius +
      'px;display:flex;align-items:center;justify-content:center">' +
      node.label + '</div>';
  });
  html += '</main>';

  persistProject();

  if (window.ZipBug) {
    ZipBug.save(
      active.name,
      JSON.stringify(project),
      html
    );
  }
}

loadPersistedProject();
renderScreenSelect();
if (getActiveNodes().length === 0) {
  add('Button');
} else {
  render();
}
