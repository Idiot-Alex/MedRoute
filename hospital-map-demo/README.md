# Hospital Map Demo

This directory contains the phase-one single-floor route guidance demo.

## Files

- `index.html`: UI, SVG map rendering, route calculation, and step rendering.
- `editor.html`: minimal graph editor for nodes, edges, POIs, and JSON export.
- `graph-data.js`: floor metadata, default route, path nodes, path edges, and POIs.

## Run Locally

Open `index.html` directly in a browser. No server, database, or build step is required.

Open `editor.html` to edit the same demo graph. The editor changes data in memory only; use "生成 JSON" to export the edited graph.

To load graph data from the phase-two backend instead of `graph-data.js`, run the server and open:

```text
index.html?api=http://127.0.0.1:8080
```

If the backend request fails, the page falls back to the local demo data.

Remote graph defaults are optional. If a remote `startPoiId` or `endPoiId` is
missing or does not exist in that graph, the page selects available remote POIs
instead of assuming the local Demo IDs. Quick-route buttons are disabled when
their POIs are not present.

## Current Demo Scope

The route page supports:

- POI search by name, category, and keywords
- start/destination selection and swap
- common route presets for quick demonstrations
- normal shortest route and accessible route modes
- route calculation that ignores disabled path edges and avoids non-accessible edges in accessible mode
- route calculation that honors `forward` and `both` edge direction and never exposes `staff` edges
- step-by-step text using path node names, distance, walk time, and edge remarks
- graceful messages for empty POI data, missing POI-node bindings, invalid node coordinates, and dangling edges
- a responsive SVG map that scales down on narrow screens

The room and corridor illustration is still the fixed `1000 × 620` phase-one
Demo floor plan. Remote node and POI coordinates must use this same coordinate
system; loading arbitrary floor-plan artwork is outside the current Demo scope.

The editor supports maintaining operational path edge fields:

- `accessible`: whether the edge can be used by accessible routes
- `status`: `enabled`, `disabled`, `construction`, `staff_only`, or `night_closed`
- `remark`: operational note shown in route steps

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
- edit selected node, edge, or POI fields, including edge accessibility, status, and remark
- delete selected objects and keep default start/destination POI IDs consistent
- export the current graph as JSON

A POI requires at least one path node. If every node has been deleted, the
editor asks the user to create a node before adding another POI.
