(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  if (root) root.StudioCore = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  'use strict';

  const SCHEMA_VERSION = 2;
  const NODE_TYPES = ['Text', 'Button', 'Input', 'Card', 'Image'];
  const MAX_SCREENS = 100;
  const MAX_NODES_PER_SCREEN = 1000;
  const ID_PATTERN = /^[A-Za-z_][A-Za-z0-9_]*$/;
  const COLOR_PATTERN = /^#[0-9a-f]{6}$/i;

  class StudioValidationError extends Error {
    constructor(errors) {
      super(errors.join('\n'));
      this.name = 'StudioValidationError';
      this.errors = errors;
    }
  }

  function defaultProject() {
    return {
      id: 'proj_studio',
      name: 'ZipBug Studio',
      schemaVersion: SCHEMA_VERSION,
      updatedAt: 0,
      currentScreenId: 'screen_main',
      screens: [{ id: 'screen_main', name: 'Main', nodes: [] }]
    };
  }

  function boundedNumber(value, fallback, minimum, maximum, field, errors) {
    if (value === undefined || value === null || value === '') return fallback;
    const number = Number(value);
    if (!Number.isFinite(number)) {
      errors.push(field + ' must be a finite number.');
      return fallback;
    }
    return Math.min(maximum, Math.max(minimum, number));
  }

  function safeText(value, fallback, maxLength, field, errors) {
    if (value === undefined || value === null) return fallback;
    if (typeof value !== 'string' && typeof value !== 'number') {
      errors.push(field + ' must be text.');
      return fallback;
    }
    const text = String(value).trim();
    if (text.length > maxLength) {
      errors.push(field + ' must be ' + maxLength + ' characters or fewer.');
      return fallback;
    }
    return text || fallback;
  }

  function canonicalType(value) {
    const type = String(value || '').trim().toLowerCase();
    const aliases = {
      text: 'Text', textview: 'Text', label: 'Text',
      button: 'Button', materialbutton: 'Button',
      input: 'Input', edittext: 'Input', textfield: 'Input',
      card: 'Card', container: 'Card',
      image: 'Image', imageview: 'Image', view: 'Card'
    };
    return aliases[type] || '';
  }

  function safeId(value, fallback, seenIds, field, warnings) {
    let id = typeof value === 'string' ? value.trim() : '';
    if (!id) id = fallback;
    const original = id;
    id = id.replace(/[^A-Za-z0-9_]/g, '_');
    if (!/^[A-Za-z_]/.test(id)) id = '_' + id;
    if (!id) id = fallback;
    if (original !== id) warnings.push(field + ' was normalized to a safe identifier.');
    let unique = id;
    let suffix = 2;
    while (seenIds.has(unique)) unique = id + '_' + suffix++;
    if (unique !== id) warnings.push(field + ' was renamed because its identifier was duplicated.');
    seenIds.add(unique);
    return unique;
  }

  function normalizeProject(input) {
    let source = input;
    if (typeof source === 'string') {
      try {
        source = JSON.parse(source);
      } catch (error) {
        throw new StudioValidationError(['The project file is not valid JSON: ' + error.message]);
      }
    }
    if (!source || typeof source !== 'object' || Array.isArray(source)) {
      throw new StudioValidationError(['A project must be a JSON object.']);
    }
    if (!Array.isArray(source.screens)) {
      throw new StudioValidationError(['The project must contain a screens array.']);
    }
    if (source.screens.length > MAX_SCREENS) {
      throw new StudioValidationError(['A project can contain at most ' + MAX_SCREENS + ' screens.']);
    }

    const warnings = [];
    const errors = [];
    if (Number(source.schemaVersion || 1) !== SCHEMA_VERSION) {
      warnings.push('Migrated the project to Studio schema version ' + SCHEMA_VERSION + '.');
    }
    const projectId = safeId(source.id, 'proj_studio', new Set(), 'Project ID', warnings);
    const name = safeText(source.name, 'ZipBug Studio', 80, 'Project name', errors);
    const screens = [];
    const usedScreenIds = new Set();

    source.screens.forEach(function (screen, screenIndex) {
      if (!screen || typeof screen !== 'object' || Array.isArray(screen)) {
        errors.push('Screen ' + (screenIndex + 1) + ' must be an object.');
        return;
      }
      const screenId = safeId(screen.id, 'screen_' + (screenIndex + 1), usedScreenIds,
        'Screen ' + (screenIndex + 1) + ' ID', warnings);
      const screenName = safeText(screen.name, 'Screen ' + (screenIndex + 1), 80,
        'Screen ' + (screenIndex + 1) + ' name', errors);
      let oldNodes = screen.nodes;
      if (!Array.isArray(oldNodes) && Array.isArray(screen.components)) {
        oldNodes = screen.components;
        warnings.push('Migrated legacy components on screen "' + screenName + '" to nodes.');
      }
      if (oldNodes === undefined) oldNodes = [];
      if (!Array.isArray(oldNodes)) {
        errors.push('Nodes on screen "' + screenName + '" must be an array.');
        oldNodes = [];
      }
      if (oldNodes.length > MAX_NODES_PER_SCREEN) {
        errors.push('Screen "' + screenName + '" can contain at most ' + MAX_NODES_PER_SCREEN + ' nodes.');
      }
      const usedNodeIds = new Set();
      const nodes = [];
      oldNodes.slice(0, MAX_NODES_PER_SCREEN).forEach(function (node, nodeIndex) {
        const nodeField = 'Screen "' + screenName + '", node ' + (nodeIndex + 1);
        if (!node || typeof node !== 'object' || Array.isArray(node)) {
          errors.push(nodeField + ' must be an object.');
          return;
        }
        const type = canonicalType(node.type);
        if (!type) {
          errors.push(nodeField + ' has an unsupported type. Use Text, Button, Input, Card, or Image.');
          return;
        }
        const id = safeId(node.id, 'node_' + (screenIndex + 1) + '_' + (nodeIndex + 1),
          usedNodeIds, nodeField + ' ID', warnings);
        const labelValue = typeof node.label === 'string' && node.label.trim() ? node.label :
          (typeof node.text === 'string' && node.text.trim() ? node.text : node.name);
        const label = safeText(labelValue, type, 300, nodeField + ' label', errors);
        const fill = node.color !== undefined ? node.color :
          (node.fillColor !== undefined ? node.fillColor : '#ff6b00');
        const text = node.textColor !== undefined ? node.textColor : '#ffffff';
        const color = String(fill || '').trim();
        const textColor = String(text || '').trim();
        if (!COLOR_PATTERN.test(color)) errors.push(nodeField + ' fill color must be a 6-digit hex color such as #336699.');
        if (!COLOR_PATTERN.test(textColor)) errors.push(nodeField + ' text color must be a 6-digit hex color such as #ffffff.');
        const border = node.borderColor === undefined ? '#333333' : String(node.borderColor).trim();
        if (!COLOR_PATTERN.test(border)) errors.push(nodeField + ' border color must be a 6-digit hex color.');

        const x = boundedNumber(node.x, 24, 0, 5000, nodeField + ' x position', errors);
        const y = boundedNumber(node.y, 24, 0, 5000, nodeField + ' y position', errors);
        const width = boundedNumber(node.width, 140, 20, 1000, nodeField + ' width', errors);
        const height = boundedNumber(node.height, 48, 20, 1600, nodeField + ' height', errors);
        const fontSize = boundedNumber(node.fontSize, 14, 8, 96, nodeField + ' font size', errors);
        const radius = boundedNumber(node.radius !== undefined ? node.radius : node.cornerRadius,
          8, 0, 128, nodeField + ' corner radius', errors);
        const opacity = boundedNumber(node.opacity, 1, 0, 1, nodeField + ' opacity', errors);
        const visibleValue = node.visible !== undefined ? node.visible : node.isVisible;
        if (visibleValue !== undefined && typeof visibleValue !== 'boolean') {
          errors.push(nodeField + ' visibility must be true or false.');
        }
        nodes.push({
          id: id,
          type: type,
          label: label,
          x: x,
          y: y,
          width: width,
          height: height,
          fontSize: fontSize,
          textColor: COLOR_PATTERN.test(textColor) ? textColor.toLowerCase() : '#ffffff',
          color: COLOR_PATTERN.test(color) ? color.toLowerCase() : '#ff6b00',
          borderColor: COLOR_PATTERN.test(border) ? border.toLowerCase() : '#333333',
          radius: radius,
          opacity: opacity,
          visible: visibleValue !== false,
          zIndex: nodeIndex
        });
      });
      screens.push({ id: screenId, name: screenName, nodes: nodes });
    });

    if (screens.length === 0 && errors.length === 0) {
      screens.push({ id: 'screen_main', name: 'Main', nodes: [] });
      warnings.push('Added a default Main screen because the project had no screens.');
    }
    if (errors.length > 0) throw new StudioValidationError(errors);

    const screenIds = new Set(screens.map(function (screen) { return screen.id; }));
    const currentScreenId = screenIds.has(source.currentScreenId) ? source.currentScreenId : screens[0].id;
    const updatedAt = Number(source.updatedAt);
    return {
      project: {
        id: projectId,
        name: name,
        schemaVersion: SCHEMA_VERSION,
        updatedAt: Number.isFinite(updatedAt) && updatedAt >= 0 ? updatedAt : 0,
        currentScreenId: currentScreenId,
        screens: screens
      },
      warnings: Array.from(new Set(warnings))
    };
  }

  function validateProject(project) {
    const errors = [];
    if (!project || typeof project !== 'object' || !Array.isArray(project.screens)) {
      return ['Project state is missing or malformed.'];
    }
    if (project.screens.length === 0 || project.screens.length > MAX_SCREENS) {
      errors.push('The project must contain between 1 and ' + MAX_SCREENS + ' screens.');
    }
    project.screens.forEach(function (screen, screenIndex) {
      if (!screen || typeof screen !== 'object' || !Array.isArray(screen.nodes)) {
        errors.push('Screen ' + (screenIndex + 1) + ' must contain a nodes array.');
        return;
      }
      if (screen.nodes.length > MAX_NODES_PER_SCREEN) {
        errors.push('Screen "' + screen.name + '" exceeds the ' + MAX_NODES_PER_SCREEN + ' node limit.');
      }
      screen.nodes.forEach(function (node, nodeIndex) {
        const where = 'Screen "' + screen.name + '", node ' + (nodeIndex + 1);
        if (!NODE_TYPES.includes(node.type)) errors.push(where + ' has an unsupported type.');
        if (!ID_PATTERN.test(String(node.id || ''))) errors.push(where + ' ID must start with a letter or underscore and contain only letters, numbers, and underscores.');
        ['x', 'y', 'width', 'height', 'fontSize', 'radius'].forEach(function (field) {
          if (!Number.isFinite(Number(node[field]))) errors.push(where + ' ' + field + ' must be a finite number.');
        });
        if (!(Number(node.width) >= 20) || !(Number(node.height) >= 20)) errors.push(where + ' must be at least 20 by 20 dp.');
        if (!COLOR_PATTERN.test(String(node.color || ''))) errors.push(where + ' fill color is invalid.');
        if (!COLOR_PATTERN.test(String(node.textColor || ''))) errors.push(where + ' text color is invalid.');
      });
    });
    return errors;
  }

  function selectRecovery(candidates) {
    const valid = [];
    const rejected = [];
    (candidates || []).forEach(function (candidate, index) {
      if (!candidate || candidate.raw === undefined || candidate.raw === null || candidate.raw === '') return;
      try {
        const result = normalizeProject(candidate.raw);
        valid.push({
          project: result.project,
          source: candidate.source || ('candidate ' + (index + 1)),
          warnings: result.warnings,
          order: index
        });
      } catch (error) {
        rejected.push({ source: candidate.source || ('candidate ' + (index + 1)), errors: error.errors || [error.message] });
      }
    });
    valid.sort(function (left, right) {
      const timeDifference = right.project.updatedAt - left.project.updatedAt;
      return timeDifference || left.order - right.order;
    });
    return {
      project: valid.length ? valid[0].project : null,
      source: valid.length ? valid[0].source : null,
      warnings: valid.length ? valid[0].warnings : [],
      rejected: rejected
    };
  }

  function clientToCanvasPoint(clientX, clientY, rect, logicalWidth, logicalHeight, content) {
    const insets = content || {};
    const left = Number(insets.left) || 0;
    const top = Number(insets.top) || 0;
    const contentWidth = Number(insets.width) || Math.max(1, rect.width - left - (Number(insets.right) || 0));
    const contentHeight = Number(insets.height) || Math.max(1, rect.height - top - (Number(insets.bottom) || 0));
    const scaleX = Number(logicalWidth) / contentWidth;
    const scaleY = Number(logicalHeight) / contentHeight;
    return {
      x: (clientX - rect.left - left) * scaleX,
      y: (clientY - rect.top - top) * scaleY
    };
  }

  function snap(value, gridSize) {
    const step = Number(gridSize) > 0 ? Number(gridSize) : 8;
    return Math.round(Number(value) / step) * step;
  }

  class History {
    constructor(limit) {
      this.limit = limit || 50;
      this.undoStack = [];
      this.redoStack = [];
      this.pending = null;
    }
    begin(state) {
      if (this.pending === null) this.pending = JSON.stringify(state);
    }
    commit(state) {
      if (this.pending === null) return false;
      const after = JSON.stringify(state);
      if (after === this.pending) {
        this.pending = null;
        return false;
      }
      this.undoStack.push(this.pending);
      if (this.undoStack.length > this.limit) this.undoStack.shift();
      this.pending = null;
      this.redoStack.length = 0;
      return true;
    }
    cancel() { this.pending = null; }
    undo(state) {
      this.cancel();
      if (this.undoStack.length === 0) return null;
      this.redoStack.push(JSON.stringify(state));
      return JSON.parse(this.undoStack.pop());
    }
    redo(state) {
      this.cancel();
      if (this.redoStack.length === 0) return null;
      this.undoStack.push(JSON.stringify(state));
      return JSON.parse(this.redoStack.pop());
    }
    canUndo() { return this.undoStack.length > 0; }
    canRedo() { return this.redoStack.length > 0; }
  }

  function escapeXml(value) {
    return String(value).replace(/&/g, '&amp;').replace(/</g, '&lt;')
      .replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&apos;');
  }

  function kotlinString(value) {
    return String(value).replace(/\\/g, '\\\\').replace(/"/g, '\\"')
      .replace(/\$/g, '\\$').replace(/\r/g, '\\r').replace(/\n/g, '\\n');
  }

  function exportErrors(project, screen) {
    const errors = validateProject(project);
    if (!screen || !Array.isArray(screen.nodes)) errors.push('Select a valid screen to export.');
    return errors;
  }

  function exportXml(project, screen) {
    const errors = exportErrors(project, screen);
    if (errors.length) throw new StudioValidationError(errors.map(function (error) { return 'Cannot export Android XML: ' + error; }));
    let xml = '<?xml version="1.0" encoding="utf-8"?>\n' +
      '<androidx.constraintlayout.widget.ConstraintLayout\n' +
      '    xmlns:android="http://schemas.android.com/apk/res/android"\n' +
      '    xmlns:app="http://schemas.android.com/apk/res-auto"\n' +
      '    android:layout_width="match_parent"\n' +
      '    android:layout_height="match_parent"\n' +
      '    android:background="#121212">\n\n';

    screen.nodes.filter(function (node) { return node.visible !== false; }).forEach(function (node) {
      const width = Math.round(node.width) + 'dp';
      const height = Math.round(node.height) + 'dp';
      const position = '        android:layout_marginStart="' + Math.round(node.x) + 'dp"\n' +
        '        android:layout_marginTop="' + Math.round(node.y) + 'dp"\n' +
        '        app:layout_constraintStart_toStartOf="parent"\n' +
        '        app:layout_constraintTop_toTopOf="parent"';
      if (node.type === 'Text') {
        xml += '    <TextView\n        android:id="@+id/' + node.id + '"\n' +
          '        android:layout_width="' + width + '"\n        android:layout_height="' + height + '"\n' +
          '        android:text="' + escapeXml(node.label) + '"\n' +
          '        android:textColor="' + node.textColor + '"\n' +
          '        android:textSize="' + Math.round(node.fontSize) + 'sp"\n' + position + ' />\n\n';
      } else if (node.type === 'Button') {
        xml += '    <com.google.android.material.button.MaterialButton\n        android:id="@+id/' + node.id + '"\n' +
          '        android:layout_width="' + width + '"\n        android:layout_height="' + height + '"\n' +
          '        android:text="' + escapeXml(node.label) + '"\n' +
          '        android:textColor="' + node.textColor + '"\n        android:backgroundTint="' + node.color + '"\n' +
          '        app:cornerRadius="' + Math.round(node.radius) + 'dp"\n' + position + ' />\n\n';
      } else if (node.type === 'Input') {
        xml += '    <EditText\n        android:id="@+id/' + node.id + '"\n' +
          '        android:layout_width="' + width + '"\n        android:layout_height="' + height + '"\n' +
          '        android:hint="' + escapeXml(node.label) + '"\n        android:textColor="' + node.textColor + '"\n' +
          '        android:backgroundTint="' + node.color + '"\n' + position + ' />\n\n';
      } else {
        xml += '    <View\n        android:id="@+id/' + node.id + '"\n' +
          '        android:layout_width="' + width + '"\n        android:layout_height="' + height + '"\n' +
          '        android:background="' + node.color + '"\n' + position + ' />\n\n';
      }
    });
    return xml + '</androidx.constraintlayout.widget.ConstraintLayout>\n';
  }

  function composeColor(color) {
    return 'Color(0xFF' + color.slice(1).toUpperCase() + ')';
  }

  function exportCompose(project, screen) {
    const errors = exportErrors(project, screen);
    if (errors.length) throw new StudioValidationError(errors.map(function (error) { return 'Cannot export Jetpack Compose: ' + error; }));
    let functionName = screen.name.replace(/[^A-Za-z0-9]/g, '') || 'Screen';
    if (!/^[A-Za-z_]/.test(functionName)) functionName = 'Screen' + functionName;
    functionName += 'Screen';
    let code = 'package com.zipbug.generated.ui\n\n' +
      'import androidx.compose.foundation.background\n' +
      'import androidx.compose.foundation.layout.*\n' +
      'import androidx.compose.foundation.shape.RoundedCornerShape\n' +
      'import androidx.compose.material3.*\n' +
      'import androidx.compose.runtime.Composable\n' +
      'import androidx.compose.ui.Modifier\n' +
      'import androidx.compose.ui.graphics.Color\n' +
      'import androidx.compose.ui.unit.dp\n' +
      'import androidx.compose.ui.unit.sp\n\n' +
      '@Composable\nfun ' + functionName + '() {\n' +
      '    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF121212))) {\n';

    screen.nodes.filter(function (node) { return node.visible !== false; }).forEach(function (node) {
      const modifier = 'Modifier.offset(x = ' + Math.round(node.x) + '.dp, y = ' + Math.round(node.y) + '.dp)' +
        '.size(width = ' + Math.round(node.width) + '.dp, height = ' + Math.round(node.height) + '.dp)';
      if (node.type === 'Text') {
        code += '        Text(text = "' + kotlinString(node.label) + '", color = ' + composeColor(node.textColor) +
          ', fontSize = ' + Math.round(node.fontSize) + '.sp, modifier = ' + modifier + ')\n';
      } else if (node.type === 'Button') {
        code += '        Button(onClick = {}, shape = RoundedCornerShape(' + Math.round(node.radius) + '.dp), ' +
          'colors = ButtonDefaults.buttonColors(containerColor = ' + composeColor(node.color) + '), modifier = ' + modifier + ') {\n' +
          '            Text(text = "' + kotlinString(node.label) + '", color = ' + composeColor(node.textColor) + ')\n' +
          '        }\n';
      } else if (node.type === 'Input') {
        code += '        OutlinedTextField(value = "", onValueChange = {}, placeholder = { Text("' +
          kotlinString(node.label) + '") }, shape = RoundedCornerShape(' + Math.round(node.radius) + '.dp), modifier = ' + modifier + ')\n';
      } else {
        code += '        Box(modifier = ' + modifier + '.background(' + composeColor(node.color) +
          ', RoundedCornerShape(' + Math.round(node.radius) + '.dp))) // ' + node.type + ': ' + kotlinString(node.label) + '\n';
      }
    });
    return code + '    }\n}\n';
  }

  return {
    SCHEMA_VERSION: SCHEMA_VERSION,
    NODE_TYPES: NODE_TYPES.slice(),
    StudioValidationError: StudioValidationError,
    defaultProject: defaultProject,
    normalizeProject: normalizeProject,
    validateProject: validateProject,
    selectRecovery: selectRecovery,
    clientToCanvasPoint: clientToCanvasPoint,
    snap: snap,
    History: History,
    escapeXml: escapeXml,
    exportXml: exportXml,
    exportCompose: exportCompose
  };
});
