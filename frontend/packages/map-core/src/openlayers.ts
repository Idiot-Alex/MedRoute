import Feature from "ol/Feature.js";
import type { FeatureLike } from "ol/Feature.js";
import LineString from "ol/geom/LineString.js";
import Point from "ol/geom/Point.js";
import ImageLayer from "ol/layer/Image.js";
import VectorLayer from "ol/layer/Vector.js";
import Projection from "ol/proj/Projection.js";
import ImageStatic from "ol/source/ImageStatic.js";
import VectorSource from "ol/source/Vector.js";
import {
  Circle as CircleStyle,
  Fill,
  Stroke,
  Style,
  Text,
} from "ol/style.js";
import { floorExtent, pixelToMap } from "./coordinates";
import { graphForFloor, nodeMap } from "./graph";
import type {
  DraftGraph,
  Floor,
  MapSelection,
  RouteSegment,
} from "./types";

export interface GraphSources {
  edges: VectorSource<Feature<LineString>>;
  nodes: VectorSource<Feature<Point>>;
  stops: VectorSource<Feature<Point>>;
  pois: VectorSource<Feature<Point>>;
  sketch: VectorSource<Feature<LineString>>;
}

export interface GraphLayers {
  edges: VectorLayer<VectorSource<Feature<LineString>>>;
  nodes: VectorLayer<VectorSource<Feature<Point>>>;
  stops: VectorLayer<VectorSource<Feature<Point>>>;
  pois: VectorLayer<VectorSource<Feature<Point>>>;
  sketch: VectorLayer<VectorSource<Feature<LineString>>>;
}

export interface FloorLayerBundle {
  projection: Projection;
  image: ImageLayer<ImageStatic>;
  sources: GraphSources;
  layers: GraphLayers;
}

function isSelected(
  feature: Feature,
  selection: MapSelection | null,
): boolean {
  return Boolean(
    selection &&
      feature.get("kind") === selection.kind &&
      feature.get("objectId") === selection.id,
  );
}

const edgeStyle = (feature: FeatureLike): Style[] => {
  const selected = Boolean(feature.get("selected"));
  const enabled = feature.get("enabled") !== false;
  const accessible = feature.get("accessible") !== false;
  const color = !enabled
    ? "#9ca3af"
    : accessible
      ? "#087f8c"
      : "#c2410c";
  return [
    new Style({
      stroke: new Stroke({
        color: selected ? "#fbbf24" : "rgba(255,255,255,0.92)",
        width: selected ? 9 : 7,
      }),
    }),
    new Style({
      stroke: new Stroke({
        color,
        width: selected ? 5 : 3,
        lineDash: enabled ? undefined : [8, 6],
      }),
    }),
  ];
};

const nodeStyle = (feature: FeatureLike): Style => {
  const selected = Boolean(feature.get("selected"));
  const edgeStart = Boolean(feature.get("edgeStart"));
  const connector = feature.get("nodeType") === "connector_stop";
  return new Style({
    image: new CircleStyle({
      radius: selected || edgeStart ? 8 : 6,
      fill: new Fill({
        color: connector ? "#e0f2fe" : "#ffffff",
      }),
      stroke: new Stroke({
        color: edgeStart ? "#d97706" : selected ? "#0f172a" : "#087f8c",
        width: selected || edgeStart ? 3 : 2,
      }),
    }),
  });
};

const stopStyle = (feature: FeatureLike): Style => {
  const selected = Boolean(feature.get("selected"));
  return new Style({
    image: new CircleStyle({
      radius: selected ? 15 : 12,
      fill: new Fill({ color: "rgba(255,255,255,0.01)" }),
      stroke: new Stroke({
        color: selected ? "#0f172a" : "#0369a1",
        width: selected ? 4 : 3,
        lineDash: [3, 3],
      }),
    }),
  });
};

const poiStyle = (feature: FeatureLike): Style => {
  const selected = Boolean(feature.get("selected"));
  return new Style({
    image: new CircleStyle({
      radius: selected ? 10 : 8,
      fill: new Fill({ color: selected ? "#fbbf24" : "#dc2626" }),
      stroke: new Stroke({ color: "#ffffff", width: 3 }),
    }),
    text: new Text({
      text: String(feature.get("name") ?? ""),
      offsetY: -19,
      font: "600 12px system-ui, sans-serif",
      fill: new Fill({ color: "#111827" }),
      stroke: new Stroke({ color: "rgba(255,255,255,0.98)", width: 4 }),
      padding: [2, 3, 2, 3],
    }),
    zIndex: selected ? 20 : 10,
  });
};

const sketchStyle = new Style({
  stroke: new Stroke({
    color: "#d97706",
    width: 3,
    lineDash: [8, 6],
  }),
});

const routeStyle = new Style({
  stroke: new Stroke({
    color: "#dc2626",
    width: 6,
  }),
});

export function createFloorProjection(floor: Floor): Projection {
  return new Projection({
    code: `medroute:${floor.mapRevision.id}`,
    units: "pixels",
    extent: floorExtent(floor),
  });
}

export function createFloorLayerBundle(
  floor: Floor,
  imageUrl: string,
): FloorLayerBundle {
  const projection = createFloorProjection(floor);
  const sources: GraphSources = {
    edges: new VectorSource(),
    nodes: new VectorSource(),
    stops: new VectorSource(),
    pois: new VectorSource(),
    sketch: new VectorSource(),
  };
  const layers: GraphLayers = {
    edges: new VectorLayer({
      source: sources.edges,
      style: edgeStyle,
      zIndex: 10,
    }),
    nodes: new VectorLayer({
      source: sources.nodes,
      style: nodeStyle,
      zIndex: 20,
    }),
    stops: new VectorLayer({
      source: sources.stops,
      style: stopStyle,
      zIndex: 25,
    }),
    pois: new VectorLayer({
      source: sources.pois,
      style: poiStyle,
      declutter: true,
      zIndex: 30,
    }),
    sketch: new VectorLayer({
      source: sources.sketch,
      style: sketchStyle,
      zIndex: 40,
    }),
  };
  return {
    projection,
    image: new ImageLayer({
      source: new ImageStatic({
        url: imageUrl,
        projection,
        imageExtent: floorExtent(floor),
      }),
      zIndex: 0,
    }),
    sources,
    layers,
  };
}

export function refreshGraphSources(
  sources: GraphSources,
  graph: DraftGraph,
  floor: Floor,
  selection: MapSelection | null,
  edgeStartId: string | null,
): void {
  const current = graphForFloor(graph, floor.id);
  const nodes = nodeMap(current.nodes);
  sources.edges.clear();
  sources.nodes.clear();
  sources.stops.clear();
  sources.pois.clear();

  for (const edge of current.edges) {
    const from = nodes.get(edge.fromNodeId);
    const to = nodes.get(edge.toNodeId);
    if (!from || !to) {
      continue;
    }
    const feature = new Feature({
      geometry: new LineString([
        pixelToMap([from.x, from.y], floor.mapRevision.imageHeight),
        pixelToMap([to.x, to.y], floor.mapRevision.imageHeight),
      ]),
      kind: "edge",
      objectId: edge.id,
      enabled: edge.enabled,
      accessible: edge.accessible,
    });
    feature.setId(`edge:${edge.id}`);
    feature.set("selected", isSelected(feature, selection));
    sources.edges.addFeature(feature);
  }

  for (const node of current.nodes) {
    const feature = new Feature({
      geometry: new Point(
        pixelToMap([node.x, node.y], floor.mapRevision.imageHeight),
      ),
      kind: "node",
      objectId: node.id,
      nodeType: node.type,
      enabled: node.enabled,
      edgeStart: node.id === edgeStartId,
    });
    feature.setId(`node:${node.id}`);
    feature.set("selected", isSelected(feature, selection));
    sources.nodes.addFeature(feature);
  }

  for (const stop of graph.connectorStops.filter(
    (candidate) => candidate.floorId === floor.id,
  )) {
    const node = nodes.get(stop.nodeId);
    if (!node) {
      continue;
    }
    const feature = new Feature({
      geometry: new Point(
        pixelToMap([node.x, node.y], floor.mapRevision.imageHeight),
      ),
      kind: "stop",
      objectId: stop.id,
    });
    feature.setId(`stop:${stop.id}`);
    feature.set("selected", isSelected(feature, selection));
    sources.stops.addFeature(feature);
  }

  for (const poi of current.pois) {
    const feature = new Feature({
      geometry: new Point(
        pixelToMap([poi.x, poi.y], floor.mapRevision.imageHeight),
      ),
      kind: "poi",
      objectId: poi.id,
      name: poi.name,
      enabled: poi.enabled,
    });
    feature.setId(`poi:${poi.id}`);
    feature.set("selected", isSelected(feature, selection));
    sources.pois.addFeature(feature);
  }
}

export function setEdgeSketch(
  source: VectorSource<Feature<LineString>>,
  from: [number, number] | null,
  to: [number, number] | null,
): void {
  source.clear();
  if (!from || !to) {
    return;
  }
  source.addFeature(
    new Feature({
      geometry: new LineString([from, to]),
    }),
  );
}

export function createRouteLayer(
  segment: RouteSegment | null,
  floor: Floor,
): VectorLayer<VectorSource<Feature<LineString>>> {
  const source = new VectorSource<Feature<LineString>>();
  if (segment && segment.points.length >= 2) {
    source.addFeature(
      new Feature({
        geometry: new LineString(
          segment.points.map((point) =>
            pixelToMap([point.x, point.y], floor.mapRevision.imageHeight),
          ),
        ),
      }),
    );
  }
  return new VectorLayer({
    source,
    style: routeStyle,
    zIndex: 20,
  });
}
