# Hospital Map Demo

This directory contains the phase-one single-floor route guidance demo.

## Files

- `index.html`: UI, SVG map rendering, route calculation, and step rendering.
- `editor.html`: minimal graph editor for nodes, edges, POIs, and JSON export.
- `graph-data.js`: floor metadata, default route, path nodes, path edges, and POIs.
- `multifloor.html`: mobile-first navigation UI that renders the backend route over per-floor images.
- `multifloor.js` / `multifloor.css`: backend integration and responsive map tool UI.
- `admin.html` / `admin.js` / `admin.css`: desktop-first persistent multi-floor map authoring,
  validation, publishing, rollback, and operation-closure workspace.
- `assets/hndfsrmyy/`: clearly marked local test floor-plan assets and source notes.

## Run Locally

Open `index.html` directly in a browser. No server, database, or build step is required.

Open `editor.html` to edit the same demo graph. The editor changes data in memory only; use "生成 JSON" to export the edited graph.

For the multi-floor test page, start the Spring Boot service first, then serve the
repository root and open:

```text
http://127.0.0.1:4173/hospital-map-demo/multifloor.html
```

Serve the repository root with:

```bash
python3 -m http.server 4173
```

The map-maintenance workspace is available at:

```text
http://127.0.0.1:4173/hospital-map-demo/admin.html
```

The default backend address is `http://127.0.0.1:8080`. To use a different one:

```text
multifloor.html?api=http://127.0.0.1:8080
admin.html?api=http://127.0.0.1:8080
```

The page reads `GET /api/buildings/{buildingId}/navigation-context` for floors,
image dimensions, and the active `releaseId`, then reads
`GET /api/buildings/{buildingId}/pois` from the same release before sending route
requests to `POST /api/routes`. It does not duplicate route calculation in the browser.

The admin page reads and writes release workspaces through `/api/admin`. It edits
drafts only, uploads a separate PNG/JPEG for each floor, validates before publish,
can reactivate a historical release, and manages temporary route or connector
closures. PostgreSQL and the backend are required; admin edits are not kept only
in browser memory.

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

`multifloor.html` is a separate, explicitly test-only surface. Its three images
use a `1000 × 800` coordinate system, and its source, permissions caveat, and
synthetic connector assumptions are documented in `assets/hndfsrmyy/README.md`.
The persistent admin workspace can replace each floor image and uses the actual
image dimensions returned by the backend. The old single-floor page remains for
prototype graph-editor work.

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
