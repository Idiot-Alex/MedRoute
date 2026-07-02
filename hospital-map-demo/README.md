# Hospital Map Demo

This directory contains the phase-one single-floor route guidance demo.

## Files

- `index.html`: UI, SVG map rendering, route calculation, and step rendering.
- `editor.html`: minimal graph editor for nodes, edges, POIs, and JSON export.
- `graph-data.js`: floor metadata, default route, path nodes, path edges, and POIs.

## Run Locally

Open `index.html` directly in a browser. No server, database, or build step is required.

Open `editor.html` to edit the same demo graph. The editor changes data in memory only; use "生成 JSON" to export the edited graph.

## Data Shape

`graph-data.js` exposes `window.MED_ROUTE_GRAPH`:

```js
{
  floor: {},
  defaults: {},
  nodes: [],
  edges: [],
  pois: []
}
```

This shape is intentionally close to the future backend response for:

```http
GET /api/hospitals/{hospitalId}/floors/{floorId}/graph
```

When the backend is available, replace `graph-data.js` with a fetch call while keeping the UI and routing logic largely unchanged.

## Editor Scope

The editor currently supports:

- add path nodes by clicking the map
- connect two nodes as a path edge
- add POIs and bind them to the nearest node
- edit selected node, edge, or POI fields
- delete selected objects
- export the current graph as JSON
