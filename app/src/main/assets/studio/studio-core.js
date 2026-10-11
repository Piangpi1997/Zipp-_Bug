(function(root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else root.ZipBugStudioCore = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function() {
  'use strict';

  const SCHEMA_VERSION = 2;
  const ALLOWED_TYPES = ['Text', 'Button', 'Input', 'Card', 'Image'];
  const DEVICE_PRESETS = {
    small: { w: 360, h: 640 },
    standard: { w: 390, h: 844 },
    large: { w: 412, h: 915 }
  };

  const defaults = {
    Text: { width: 140, height: 40, fontSize: 16, textColor: '#FFFFFF', color: '#222222', radius: 0 },
    Button: { width: 160, height: 48, fontSize: 14, textColor: '#111111', color: '#FF6B00', radius: 12 },
    Input: { width: 220, height: 48, fontSize: 14, textColor: '#FFFFFF', color: '#2A2A2A', radius: 8 },
    Card: { width: 260, height: 140, fontSize: 14, textColor: '#FFFFFF', color: '#1E1E1E', radius: 16 },
    Image: { width: 160, height: 120, fontSize: 12, textColor: '#AAAAAA', color: '#333333', radius: 12 }
  };

  function clone(value) {
    return JSON.parse(JSON.stringify(value));
  }

  function valueOr(value, fallback) {
    return value === undefined || value === null || value === '' ? fallback : value;
  }

  function numberOrDefault(value, fallback) {
    if (value === undefined || value === null || value === '') return fallback;
    if (typeof value === 'number') return value;
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : value;
  }

  function normalizeNode(raw, index) {
    const node = Object.assign({}, raw || {});
    const type = valueOr(node.type, 'Text');
    const cfg = defaults[type] || defaults.Text;
    node.id = valueOr(node.id, '');
    node.type = type;
    node.label = valueOr(node.label, valueOr(node.text, valueOr(node.name, type + ' ' + (index + 1))));
    node.x = numberOrDefault(node.x, 24);
    node.y = numberOrDefault(node.y, 24 + (index * 16));
    node.width = numberOrDefault(node.width, cfg.width);
    node.height = numberOrDefault(node.height, cfg.height);
    node.fontSize = numberOrDefault(node.fontSize, cfg.fontSize);
    node.textColor = valueOr(node.textColor, cfg.textColor);
    node.color = valueOr(node.color, valueOr(node.fillColor, cfg.color));
    node.radius = numberOrDefault(node.radius, numberOrDefault(node.cornerRadius, cfg.radius));
    node.zIndex = numberOrDefault(node.zIndex, index);
    return node;
  }

  function migrateProject(rawProject) {
    const input = rawProject && typeof rawProject === 'object' ? rawProject : {};
    const result = Object.assign({}, input);
    const rawScreens = Array.isArray(input.screens) ? input.screens : [];
    const screens = rawScreens.map(function(rawScreen, screenIndex) {
      const screen = Object.assign({}, rawScreen || {});
      const rawNodes = Array.isArray(screen.nodes)
        ? screen.nodes
        : (Array.isArray(screen.components) ? screen.components : []);
      screen.id = valueOr(screen.id, 'screen_' + (screenIndex + 1));
      screen.name = valueOr(screen.name, 'Screen ' + (screenIndex + 1));
      screen.nodes = rawNodes.map(normalizeNode);
      delete screen.components;
      return screen;
    });

    if (screens.length === 0) screens.push({ id: 'screen_main', name: 'Main', nodes: [] });

    result.schemaVersion = SCHEMA_VERSION;
    result.id = valueOr(input.id, 'proj_studio');
    result.name = valueOr(input.name, 'ZipBug Studio');
    result.screens = screens;
    result.currentScreenId = screens.some(function(s) { return s.id === input.currentScreenId; })
      ? input.currentScreenId
      : screens[0].id;

    const preview = Object.assign({}, input.preview || {});
    preview.devicePreset = DEVICE_PRESETS[valueOr(preview.devicePreset, input.devicePreset)]
      ? valueOr(preview.devicePreset, input.devicePreset)
      : 'standard';
    preview.gridSnap = preview.gridSnap !== false;
    result.preview = preview;
    if (typeof result.updatedAt !== 'number') result.updatedAt = 0;
    delete result.devicePreset;
    return result;
  }

  function pathIssue(path, message) {
    return { path: path, message: message };
  }

  function isFiniteNumber(value) {
    return typeof value === 'number' && Number.isFinite(value);
  }

  function isHexColor(value) {
    return typeof value === 'string' && /^#[0-9A-Fa-f]{6}$/.test(value);
  }

  function composeFunctionName(name) {
    let base = String(name || '').replace(/[^A-Za-z0-9_]/g, '');
    if (!base) base = 'Screen';
    if (/^[0-9]/.test(base)) base = 'Screen' + base;
    return base + 'Screen';
  }

  function validateProject(project, options) {
    const opts = options || {};
    const errors = [];
    const warnings = [];
    if (!project || typeof project !== 'object') {
      return { valid: false, errors: [pathIssue('$', 'project must be an object')], warnings: [] };
    }
    if (!Array.isArray(project.screens) || project.screens.length === 0) {
      errors.push(pathIssue('screens', 'must contain at least one screen'));
      return { valid: false, errors: errors, warnings: warnings };
    }

    const screenIds = new Set();
    const composeNames = new Set();
    project.screens.forEach(function(screen, screenIndex) {
      const sp = 'screens[' + screenIndex + ']';
      if (!screen || typeof screen !== 'object') {
        errors.push(pathIssue(sp, 'must be an object'));
        return;
      }
      if (typeof screen.id !== 'string' || !screen.id.trim()) errors.push(pathIssue(sp + '.id', 'must be a non-empty string'));
      else if (screenIds.has(screen.id)) errors.push(pathIssue(sp + '.id', 'duplicate screen id "' + screen.id + '"'));
      else screenIds.add(screen.id);
      if (typeof screen.name !== 'string' || !screen.name.trim()) errors.push(pathIssue(sp + '.name', 'must be a non-empty string'));

      if (opts.compose) {
        const fn = composeFunctionName(screen.name);
        if (composeNames.has(fn)) errors.push(pathIssue(sp + '.name', 'produces duplicate Compose function ' + fn));
        else composeNames.add(fn);
      }

      if (!Array.isArray(screen.nodes)) {
        errors.push(pathIssue(sp + '.nodes', 'must be an array'));
        return;
      }

      if (opts.screenId && screen.id !== opts.screenId) return;
      const nodeIds = new Set();
      screen.nodes.forEach(function(node, nodeIndex) {
        const np = sp + '.nodes[' + nodeIndex + ']';
        if (!node || typeof node !== 'object') {
          errors.push(pathIssue(np, 'must be an object'));
          return;
        }
        if (typeof node.id !== 'string' || !/^[a-z][a-z0-9_]*$/.test(node.id)) {
          errors.push(pathIssue(np + '.id', 'must match Android id pattern [a-z][a-z0-9_]*'));
        } else if (nodeIds.has(node.id)) {
          errors.push(pathIssue(np + '.id', 'duplicate id "' + node.id + '" in this screen'));
        } else nodeIds.add(node.id);

        if (!ALLOWED_TYPES.includes(node.type)) errors.push(pathIssue(np + '.type', 'unsupported type "' + node.type + '"'));
        ['x', 'y', 'width', 'height'].forEach(function(field) {
          if (!isFiniteNumber(node[field])) errors.push(pathIssue(np + '.' + field, 'must be a finite number'));
        });
        if (isFiniteNumber(node.x) && node.x < 0) errors.push(pathIssue(np + '.x', 'must be >= 0'));
        if (isFiniteNumber(node.y) && node.y < 0) errors.push(pathIssue(np + '.y', 'must be >= 0'));
        if (isFiniteNumber(node.width) && node.width <= 0) errors.push(pathIssue(np + '.width', 'must be > 0'));
        if (isFiniteNumber(node.height) && node.height <= 0) errors.push(pathIssue(np + '.height', 'must be > 0'));
        if (!isFiniteNumber(node.fontSize) || node.fontSize < 1 || node.fontSize > 200) errors.push(pathIssue(np + '.fontSize', 'must be a number from 1 to 200'));
        if (!isFiniteNumber(node.radius) || node.radius < 0) errors.push(pathIssue(np + '.radius', 'must be a number >= 0'));
        if (typeof node.label !== 'string') errors.push(pathIssue(np + '.label', 'must be a string'));
        if (!isHexColor(node.color)) errors.push(pathIssue(np + '.color', 'must be #RRGGBB'));
        if (!isHexColor(node.textColor)) errors.push(pathIssue(np + '.textColor', 'must be #RRGGBB'));

        const presetKey = project.preview && DEVICE_PRESETS[project.preview.devicePreset]
          ? project.preview.devicePreset
          : 'standard';
        const dim = DEVICE_PRESETS[presetKey];
        if (isFiniteNumber(node.x) && isFiniteNumber(node.width) && node.x + node.width > dim.w) {
          warnings.push(pathIssue(np + '.width', 'extends beyond ' + presetKey + ' preview width ' + dim.w + 'dp'));
        }
        if (isFiniteNumber(node.y) && isFiniteNumber(node.height) && node.y + node.height > dim.h) {
          warnings.push(pathIssue(np + '.height', 'extends beyond ' + presetKey + ' preview height ' + dim.h + 'dp'));
        }
      });
    });

    if (opts.screenId && !project.screens.some(function(s) { return s.id === opts.screenId; })) {
      errors.push(pathIssue('currentScreenId', 'screen "' + opts.screenId + '" does not exist'));
    }
    return { valid: errors.length === 0, errors: errors, warnings: warnings };
  }

  function xmlEscape(value) {
    return String(value == null ? '' : value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&apos;');
  }

  function kotlinString(value) {
    return String(value == null ? '' : value)
      .replace(/\\/g, '\\\\')
      .replace(/"/g, '\\"')
      .replace(/\r/g, '\\r')
      .replace(/\n/g, '\\n')
      .replace(/\t/g, '\\t')
      .replace(/\$/g, '\\$');
  }

  function composeColor(hex, fallback) {
    const clean = isHexColor(hex) ? hex.substring(1).toUpperCase() : fallback.substring(1).toUpperCase();
    return '0xFF' + clean;
  }

  function screenById(project, screenId) {
    return project.screens.find(function(s) { return s.id === screenId; }) || null;
  }

  function generateAndroidXml(project, screenId) {
    const validation = validateProject(project, { screenId: screenId });
    if (!validation.valid) return { ok: false, errors: validation.errors, warnings: validation.warnings };
    const screen = screenById(project, screenId);
    const lines = [
      '<?xml version="1.0" encoding="utf-8"?>',
      '<androidx.constraintlayout.widget.ConstraintLayout',
      '    xmlns:android="http://schemas.android.com/apk/res/android"',
      '    xmlns:app="http://schemas.android.com/apk/res-auto"',
      '    android:layout_width="match_parent"',
      '    android:layout_height="match_parent"',
      '    android:background="#121212">',
      ''
    ];

    screen.nodes.forEach(function(n) {
      const common = [
        '        android:id="@+id/' + n.id + '"',
        '        android:layout_width="' + Math.round(n.width) + 'dp"',
        '        android:layout_height="' + Math.round(n.height) + 'dp"',
        '        android:layout_marginStart="' + Math.round(n.x) + 'dp"',
        '        android:layout_marginTop="' + Math.round(n.y) + 'dp"',
        '        app:layout_constraintStart_toStartOf="parent"',
        '        app:layout_constraintTop_toTopOf="parent"'
      ];
      if (n.type === 'Text') {
        lines.push('    <TextView');
        lines.push.apply(lines, common);
        lines.push('        android:text="' + xmlEscape(n.label) + '"');
        lines.push('        android:textColor="' + n.textColor + '"');
        lines.push('        android:textSize="' + Math.round(n.fontSize) + 'sp" />', '');
      } else if (n.type === 'Button') {
        lines.push('    <com.google.android.material.button.MaterialButton');
        lines.push.apply(lines, common);
        lines.push('        android:text="' + xmlEscape(n.label) + '"');
        lines.push('        android:textColor="' + n.textColor + '"');
        lines.push('        app:backgroundTint="' + n.color + '"');
        lines.push('        app:cornerRadius="' + Math.round(n.radius) + 'dp" />', '');
      } else if (n.type === 'Input') {
        lines.push('    <EditText');
        lines.push.apply(lines, common);
        lines.push('        android:hint="' + xmlEscape(n.label) + '"');
        lines.push('        android:textColor="' + n.textColor + '"');
        lines.push('        android:textColorHint="' + n.textColor + '"');
        lines.push('        android:backgroundTint="' + n.color + '" />', '');
      } else if (n.type === 'Card') {
        lines.push('    <com.google.android.material.card.MaterialCardView');
        lines.push.apply(lines, common);
        lines.push('        app:cardBackgroundColor="' + n.color + '"');
        lines.push('        app:cardCornerRadius="' + Math.round(n.radius) + 'dp" />', '');
      } else if (n.type === 'Image') {
        lines.push('    <ImageView');
        lines.push.apply(lines, common);
        lines.push('        android:background="' + n.color + '"');
        lines.push('        android:contentDescription="' + xmlEscape(n.label) + '"');
        lines.push('        android:scaleType="centerCrop" />', '');
      }
    });
    lines.push('</androidx.constraintlayout.widget.ConstraintLayout>', '');
    return { ok: true, content: lines.join('\n'), screenName: screen.name, warnings: validation.warnings };
  }

  function generateCompose(project) {
    const validation = validateProject(project, { compose: true });
    if (!validation.valid) return { ok: false, errors: validation.errors, warnings: validation.warnings };
    const lines = [
      'package com.zipbug.generated.ui', '',
      'import androidx.compose.foundation.background',
      'import androidx.compose.foundation.layout.*',
      'import androidx.compose.foundation.shape.RoundedCornerShape',
      'import androidx.compose.material3.*',
      'import androidx.compose.runtime.*',
      'import androidx.compose.ui.Alignment',
      'import androidx.compose.ui.Modifier',
      'import androidx.compose.ui.graphics.Color',
      'import androidx.compose.ui.unit.dp',
      'import androidx.compose.ui.unit.sp', ''
    ];

    project.screens.forEach(function(screen) {
      const fn = composeFunctionName(screen.name);
      lines.push('@Composable', 'fun ' + fn + '() {');
      screen.nodes.filter(function(n) { return n.type === 'Input'; }).forEach(function(n) {
        lines.push('    var input_' + n.id + ' by remember { mutableStateOf("") }');
      });
      if (screen.nodes.some(function(n) { return n.type === 'Input'; })) lines.push('');
      lines.push('    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF121212))) {');
      screen.nodes.forEach(function(n) {
        const modifier = 'Modifier.offset(x = ' + Math.round(n.x) + '.dp, y = ' + Math.round(n.y) + '.dp).size(width = ' + Math.round(n.width) + '.dp, height = ' + Math.round(n.height) + '.dp)';
        const textColor = composeColor(n.textColor, '#FFFFFF');
        const fillColor = composeColor(n.color, '#1E1E1E');
        const label = kotlinString(n.label);
        if (n.type === 'Text') {
          lines.push('        Text(text = "' + label + '", color = Color(' + textColor + '), fontSize = ' + Math.round(n.fontSize) + '.sp, modifier = ' + modifier + ')');
        } else if (n.type === 'Button') {
          lines.push('        Button(');
          lines.push('            onClick = { },');
          lines.push('            shape = RoundedCornerShape(' + Math.round(n.radius) + '.dp),');
          lines.push('            colors = ButtonDefaults.buttonColors(containerColor = Color(' + fillColor + ')),');
          lines.push('            modifier = ' + modifier);
          lines.push('        ) { Text("' + label + '", color = Color(' + textColor + ')) }');
        } else if (n.type === 'Input') {
          lines.push('        OutlinedTextField(');
          lines.push('            value = input_' + n.id + ',');
          lines.push('            onValueChange = { input_' + n.id + ' = it },');
          lines.push('            placeholder = { Text("' + label + '") },');
          lines.push('            textStyle = LocalTextStyle.current.copy(color = Color(' + textColor + '), fontSize = ' + Math.round(n.fontSize) + '.sp),');
          lines.push('            shape = RoundedCornerShape(' + Math.round(n.radius) + '.dp),');
          lines.push('            colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Color(' + fillColor + '), focusedContainerColor = Color(' + fillColor + ')),');
          lines.push('            modifier = ' + modifier);
          lines.push('        )');
        } else if (n.type === 'Card') {
          lines.push('        Card(');
          lines.push('            shape = RoundedCornerShape(' + Math.round(n.radius) + '.dp),');
          lines.push('            colors = CardDefaults.cardColors(containerColor = Color(' + fillColor + ')),');
          lines.push('            modifier = ' + modifier);
          lines.push('        ) { Box(modifier = Modifier.fillMaxSize()) }');
        } else if (n.type === 'Image') {
          lines.push('        Box(modifier = ' + modifier + '.background(Color(' + fillColor + '), RoundedCornerShape(' + Math.round(n.radius) + '.dp)), contentAlignment = Alignment.Center) {');
          lines.push('            Text("' + label + '", color = Color(' + textColor + '))');
          lines.push('        }');
        }
      });
      lines.push('    }', '}', '');
    });
    return { ok: true, content: lines.join('\n'), warnings: validation.warnings };
  }

  function formatIssues(issues, limit) {
    const max = limit || 6;
    return (issues || []).slice(0, max).map(function(issue) {
      return issue.path + ': ' + issue.message;
    }).join('\n') + ((issues || []).length > max ? '\n… +' + ((issues || []).length - max) + ' more' : '');
  }

  return {
    SCHEMA_VERSION: SCHEMA_VERSION,
    ALLOWED_TYPES: ALLOWED_TYPES,
    DEVICE_PRESETS: DEVICE_PRESETS,
    migrateProject: migrateProject,
    validateProject: validateProject,
    generateAndroidXml: generateAndroidXml,
    generateCompose: generateCompose,
    formatIssues: formatIssues,
    xmlEscape: xmlEscape,
    kotlinString: kotlinString,
    composeFunctionName: composeFunctionName,
    clone: clone
  };
});
