import type {
  Connector,
  ConnectorStop,
  DraftGraph,
  Floor,
  MapSelection,
  PathEdge,
  PathNode,
  Poi,
  VerticalLink,
} from "./types";
import { clampPixel, type PixelCoordinate } from "./coordinates";

export interface FloorGraph {
  nodes: PathNode[];
  edges: PathEdge[];
  pois: Poi[];
  stopNodeIds: Set<string>;
}

export interface GraphDependency {
  kind: MapSelection["kind"];
  id: string;
  label: string;
  relation: string;
}

export function graphForFloor(
  graph: DraftGraph,
  floorId: string,
): FloorGraph {
  return {
    nodes: graph.nodes.filter((node) => node.floorId === floorId),
    edges: graph.edges.filter((edge) => edge.floorId === floorId),
    pois: graph.pois.filter((poi) => poi.floorId === floorId),
    stopNodeIds: new Set(
      graph.connectorStops
        .filter((stop) => stop.floorId === floorId)
        .map((stop) => stop.nodeId),
    ),
  };
}

export function nodeMap(nodes: PathNode[]): Map<string, PathNode> {
  return new Map(nodes.map((node) => [node.id, node]));
}

export function moveNode(
  graph: DraftGraph,
  floor: Floor,
  nodeId: string,
  coordinate: PixelCoordinate,
): boolean {
  const node = graph.nodes.find((candidate) => candidate.id === nodeId);
  if (!node || node.floorId !== floor.id) {
    return false;
  }
  const [x, y] = clampPixel(
    coordinate,
    floor.mapRevision.imageWidth,
    floor.mapRevision.imageHeight,
  ).map((value) => Math.round(value * 10) / 10) as PixelCoordinate;
  if (x === node.x && y === node.y) {
    return false;
  }
  const deltaX = x - node.x;
  const deltaY = y - node.y;
  node.x = x;
  node.y = y;
  for (const poi of graph.pois) {
    if (poi.nodeId !== node.id) {
      continue;
    }
    [poi.x, poi.y] = clampPixel(
      [poi.x + deltaX, poi.y + deltaY],
      floor.mapRevision.imageWidth,
      floor.mapRevision.imageHeight,
    );
  }
  return true;
}

export function selectedObject(
  graph: DraftGraph,
  selection: MapSelection | null,
): PathNode | PathEdge | Poi | Connector | ConnectorStop | VerticalLink | null {
  if (!selection) {
    return null;
  }
  const property = {
    node: "nodes",
    edge: "edges",
    poi: "pois",
    stop: "connectorStops",
    connector: "connectors",
    link: "verticalLinks",
  }[selection.kind] as keyof DraftGraph;
  return (
    (
      graph[property] as Array<
        PathNode | PathEdge | Poi | Connector | ConnectorStop | VerticalLink
      >
    ).find(
      (item) => item.id === selection.id,
    ) ?? null
  );
}

export function graphDependencies(
  graph: DraftGraph,
  selection: MapSelection,
): GraphDependency[] {
  if (selection.kind !== "node") {
    return [];
  }
  return [
    ...graph.edges
      .filter(
        (edge) =>
          edge.fromNodeId === selection.id ||
          edge.toNodeId === selection.id,
      )
      .map((edge) => ({
        kind: "edge" as const,
        id: edge.id,
        label: edge.code,
        relation: "路径端点",
      })),
    ...graph.pois
      .filter((poi) => poi.nodeId === selection.id)
      .map((poi) => ({
        kind: "poi" as const,
        id: poi.id,
        label: poi.name,
        relation: "POI 绑定节点",
      })),
    ...graph.connectorStops
      .filter((stop) => stop.nodeId === selection.id)
      .map((stop) => ({
        kind: "stop" as const,
        id: stop.id,
        label: stop.code,
        relation: "设施停靠点",
      })),
  ];
}

export function removeBasicGraphObject(
  graph: DraftGraph,
  selection: MapSelection,
): boolean {
  if (!["node", "edge", "poi"].includes(selection.kind)) {
    return false;
  }
  if (graphDependencies(graph, selection).length) {
    return false;
  }
  const property = {
    node: "nodes",
    edge: "edges",
    poi: "pois",
  }[selection.kind as "node" | "edge" | "poi"] as
    | "nodes"
    | "edges"
    | "pois";
  const values = graph[property] as Array<{ id: string }>;
  const index = values.findIndex((value) => value.id === selection.id);
  if (index < 0) {
    return false;
  }
  values.splice(index, 1);
  return true;
}

export function rebindPoiToNode(
  graph: DraftGraph,
  poiId: string,
  nodeId: string,
): boolean {
  const poi = graph.pois.find((candidate) => candidate.id === poiId);
  const node = graph.nodes.find((candidate) => candidate.id === nodeId);
  if (!poi || !node || poi.floorId !== node.floorId) {
    return false;
  }
  poi.nodeId = node.id;
  return true;
}

export function rebindEdgeEndpoint(
  graph: DraftGraph,
  edgeId: string,
  endpoint: "from" | "to",
  nodeId: string,
): boolean {
  const edge = graph.edges.find((candidate) => candidate.id === edgeId);
  const node = graph.nodes.find((candidate) => candidate.id === nodeId);
  if (!edge || !node || edge.floorId !== node.floorId) {
    return false;
  }
  const fromNodeId = endpoint === "from" ? node.id : edge.fromNodeId;
  const toNodeId = endpoint === "to" ? node.id : edge.toNodeId;
  if (
    fromNodeId === toNodeId ||
    graph.edges.some(
      (candidate) =>
        candidate.id !== edge.id &&
        candidate.floorId === edge.floorId &&
        ((candidate.fromNodeId === fromNodeId &&
          candidate.toNodeId === toNodeId) ||
          (candidate.fromNodeId === toNodeId &&
            candidate.toNodeId === fromNodeId)),
    )
  ) {
    return false;
  }
  edge.fromNodeId = fromNodeId;
  edge.toNodeId = toNodeId;
  return true;
}

export function parsePoiKeywords(value: string): string[] {
  const seen = new Set<string>();
  return value
    .split(/[，,\n]/)
    .map((keyword) => keyword.trim())
    .filter((keyword) => {
      const normalized = keyword.normalize("NFKC").toLocaleLowerCase();
      if (!normalized || seen.has(normalized)) {
        return false;
      }
      seen.add(normalized);
      return true;
    });
}

export function nextCode(prefix: string, values: Array<{ code: string }>): string {
  const existing = new Set(values.map((value) => value.code));
  let sequence = values.length + 1;
  let code = `${prefix}-${sequence}`;
  while (existing.has(code)) {
    sequence += 1;
    code = `${prefix}-${sequence}`;
  }
  return code;
}
