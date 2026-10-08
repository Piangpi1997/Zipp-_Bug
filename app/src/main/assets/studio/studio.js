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
const canvas = document.querySelector('#canvas');
const undoStack = [];
const redoStack = [];

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
        // update sequence counter to avoid collision
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
    Text: '#222222',
    Button: '#ff6b00',
    Input: '#2a2a2a',
    Card: '#1e1e1e',
    Image: '#333333'
  };

  const defaultSizes = {
    Text: { w: 140, h: 40, font: 16, tc: '#ffffff', r: 0 },
    Button: { w: 160, h: 48, font: 14, tc: '#111111', r: 12 },
    Input: { w: 220, h: 48, font: 14, tc: '#ffffff', r: 8 },
    Card: { w: 260, h: 140, font: 14, tc: '#ffffff', r: 16 },
    Image: { w: 160, h: 120, font: 12, tc: '#aaaaaa', r: 12 }
  };

  const cfg = defaultSizes[type] || { w: 120, h: 48, font: 14, tc: '#ffffff', r: 8 };

  const node = {
    id: 'n' + sequence,
    type: type,
    label: type + ' ' + sequence,
    x: 30,
    y: 30 + ((sequence % 8) * 36),
    width: cfg.w,
    height: cfg.h,
    fontSize: cfg.font,
    textColor: cfg.tc,
    color: defaultColors[type] || '#ff6b00',
    radius: cfg.r,
    zIndex: sequence
  };

  getActiveNodes().push(node);
  selected = node.id;
  render();
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
  copy.x = Math.min(300, current.x + 16);
  copy.y = Math.min(500, current.y + 16);
  copy.zIndex = sequence;
  nodes.push(copy);
  selected = copy.id;
  render();
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

    if (node.type === 'Input') {
      el.style.border = '1px solid #555';
    }

    el.textContent = node.label;

    // Resize handle
    const handle = document.createElement('div');
    handle.className = 'handle';
    el.appendChild(handle);

    // Click to select
    el.onclick = function(e) {
      e.stopPropagation();
      selectNode(node.id);
    };

    // Drag to move
    let startX = 0, startY = 0, origX = 0, origY = 0;
    el.onpointerdown = function(e) {
      if (e.target === handle) return;
      el.setPointerCapture(e.pointerId);
      startX = e.clientX;
      startY = e.clientY;
      origX = node.x;
      origY = node.y;
      selectNode(node.id);
    };

    el.onpointermove = function(e) {
      if (!el.hasPointerCapture(e.pointerId)) return;
      node.x = Math.max(0, origX + (e.clientX - startX));
      node.y = Math.max(0, origY + (e.clientY - startY));
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

    // Drag to resize
    let startW = 0, startH = 0, resizeStartX = 0, resizeStartY = 0;
    handle.onpointerdown = function(e) {
      e.stopPropagation();
      handle.setPointerCapture(e.pointerId);
      resizeStartX = e.clientX;
      resizeStartY = e.clientY;
      startW = node.width;
      startH = node.height;
    };

    handle.onpointermove = function(e) {
      if (!handle.hasPointerCapture(e.pointerId)) return;
      node.width = Math.max(30, startW + (e.clientX - resizeStartX));
      node.height = Math.max(20, startH + (e.clientY - resizeStartY));
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
  let xml = '<?xml version="1.0" encoding="utf-8"?>\n' +
    '<androidx.constraintlayout.widget.ConstraintLayout\n' +
    '    xmlns:android="http://schemas.android.com/apk/res/android"\n' +
    '    xmlns:app="http://schemas.android.com/apk/res-auto"\n' +
    '    android:layout_width="match_parent"\n' +
    '    android:layout_height="match_parent"\n' +
    '    android:background="#121212">\n\n';

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
      '    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF121212))) {\n';
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
  let html = '<main style="position:relative;width:360px;min-height:640px;background:#1a1a1a">';
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

// Initial setup: load from persistence or add default node
loadPersistedProject();
renderScreenSelect();
if (getActiveNodes().length === 0) {
  add('Button');
} else {
  render();
}
