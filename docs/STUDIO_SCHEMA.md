# LIKEFIGMA Studio Schema

Studio JSON is the provider-independent source of truth for the preview, inspector, autosave/recovery, and Android XML / Jetpack Compose generators.

## Version 2

```json
{
  "id": "proj_studio",
  "name": "ZipBug Studio",
  "schemaVersion": 2,
  "updatedAt": 0,
  "currentScreenId": "screen_main",
  "screens": [
    {
      "id": "screen_main",
      "name": "Main",
      "nodes": [
        {
          "id": "n1",
          "type": "Button",
          "label": "Continue",
          "x": 24,
          "y": 40,
          "width": 160,
          "height": 48,
          "fontSize": 14,
          "textColor": "#111111",
          "color": "#ff6b00",
          "borderColor": "#333333",
          "radius": 12,
          "opacity": 1,
          "visible": true,
          "zIndex": 0
        }
      ]
    }
  ]
}
```

Allowed node types are `Text`, `Button`, `Input`, `Card`, and `Image`. `x`, `y`, `width`, and `height` are logical canvas dp-like units; the canvas is 360 by 720 logical units. Node order in `nodes` is back-to-front. `visible` controls preview and generated output; hidden nodes remain editable in Layers.

## Legacy migration

Projects with no `schemaVersion` or schema version 1 are migrated in memory to version 2. The earlier Android model shape (`screens[].components[]`) is accepted and converted to `nodes[]`; `name` / `text` map to `label`, `fillColor` maps to `color`, `cornerRadius` maps to `radius`, and `isVisible` maps to `visible`. Missing optional properties receive safe defaults. Duplicate or unsafe identifiers are normalized with a user-visible migration notice. A migrated project is autosaved in the current format after recovery.

Malformed JSON, unsupported node types, invalid color values, invalid numeric values, and oversized imports are rejected with actionable errors rather than partially imported. Imports are limited to 2 MB, 100 screens, and 1,000 nodes per screen.

## Interaction and persistence

Selection supports Shift/Ctrl/Cmd multi-select. Dragging and resizing convert client pointer coordinates through the canvas' rendered scale and border into logical canvas units. Position and size snapping use an 8-unit grid when enabled. Editing, alignment, distribution, layer order, and screen changes update the same project state that renders the preview, inspector, and schema pane.

Undo/redo stores project snapshots at edit transaction boundaries. Browser autosave is debounced; the previous valid browser project is retained as a last-good recovery copy. On startup, the newest valid local autosave, backup, or Android project file is used. Android bridge writes validate the project envelope and size before storing files in app-private storage.

## Exports

Exports operate on the active screen. XML and Compose generation validate project/node fields before writing output, omit hidden nodes, and escape user-controlled text. XML is parsed by the Android bridge before it is saved. Invalid exports show the validation reason in the editor status line.
