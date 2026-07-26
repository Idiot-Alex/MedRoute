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
