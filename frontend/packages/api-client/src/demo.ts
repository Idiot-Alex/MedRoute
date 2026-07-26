import type {
  AdminWorkspace,
  Connector,
  ConnectorStop,
  DraftGraph,
  Floor,
  NavigationContext,
  NavigationPoi,
  NavigationRoute,
  PathEdge,
  PathNode,
  Poi,
  RoutePoint,
  RouteSegment,
  VerticalLink,
} from "@medroute/map-core";

const building = {
  id: "00000000-0000-0000-0000-000000000100",
  code: "OUTPATIENT",
  name: "东方市人民医院门急诊楼",
};

const floors: Floor[] = [
  {
    id: "00000000-0000-0000-0000-000000001001",
    code: "1F",
    name: "门急诊大厅",
    levelNo: 1,
    mapRevision: {
      id: "00000000-0000-0000-0000-000000001101",
      revisionNo: 1,
      imageUrl: "/hospital-map-demo/assets/hndfsrmyy/outpatient-1f.jpg",
      imageWidth: 1000,
      imageHeight: 800,
    },
  },
  {
    id: "00000000-0000-0000-0000-000000001002",
    code: "2F",
    name: "门诊二层",
    levelNo: 2,
    mapRevision: {
      id: "00000000-0000-0000-0000-000000001102",
      revisionNo: 1,
      imageUrl: "/hospital-map-demo/assets/hndfsrmyy/outpatient-2f.jpg",
      imageWidth: 1000,
      imageHeight: 800,
    },
  },
  {
    id: "00000000-0000-0000-0000-000000001003",
    code: "3F",
    name: "门诊三层",
    levelNo: 3,
    mapRevision: {
      id: "00000000-0000-0000-0000-000000001103",
      revisionNo: 1,
      imageUrl: "/hospital-map-demo/assets/hndfsrmyy/outpatient-3f.jpg",
      imageWidth: 1000,
      imageHeight: 800,
    },
  },
];

const nodes: PathNode[] = [
  node("1001", "N-1F-ENTRANCE", 0, 500, 775, "poi_access"),
  node("1002", "N-1F-HUB", 0, 500, 435, "decision"),
  node("1003", "N-1F-PHARMACY", 0, 850, 640, "poi_access"),
  node("1004", "N-1F-ELEV-B", 0, 755, 365, "connector_stop"),
  node("2001", "N-2F-HUB", 1, 500, 435, "decision"),
  node("2002", "N-2F-LAB", 1, 365, 185, "poi_access"),
  node("2003", "N-2F-ELEV-B", 1, 755, 385, "connector_stop"),
  node("3001", "N-3F-HUB", 2, 500, 435, "decision"),
  node("3002", "N-3F-ULTRASOUND", 2, 300, 560, "poi_access"),
  node("3003", "N-3F-ELEV-B", 2, 755, 385, "connector_stop"),
];

const edges: PathEdge[] = [
  edge("1101", "EDGE-1F-1", 0, "1001", "1002"),
  edge("1102", "EDGE-1F-2", 0, "1002", "1003"),
  edge("1103", "EDGE-1F-3", 0, "1002", "1004"),
  edge("2101", "EDGE-2F-1", 1, "2003", "2001"),
  edge("2102", "EDGE-2F-2", 1, "2001", "2002"),
  edge("3101", "EDGE-3F-1", 2, "3003", "3001"),
  edge("3102", "EDGE-3F-2", 2, "3001", "3002"),
];

const pois: Poi[] = [
  poi("1201", "P-ENTRANCE", "门诊主入口", "entrance", 0, "1001"),
  poi("1202", "P-PHARMACY", "门诊药房", "pharmacy", 0, "1003"),
  poi("2201", "P-LAB", "检验科", "laboratory", 1, "2002"),
  poi("3201", "P-ULTRASOUND", "超声诊断科", "department", 2, "3002"),
];

const connector: Connector = {
  id: id("4001"),
  code: "ELEV-B",
  name: "B 电梯",
  type: "elevator",
  accessScope: "public",
  accessible: true,
  enabled: true,
};

const connectorStops: ConnectorStop[] = [
  stop("4101", 0, "1004"),
  stop("4102", 1, "2003"),
  stop("4103", 2, "3003"),
];

const verticalLinks: VerticalLink[] = [
  verticalLink("4201", "4101", "4102"),
  verticalLink("4202", "4102", "4103"),
];

const graph: DraftGraph = {
  nodes,
  edges,
  pois,
  connectors: [connector],
  connectorStops,
  verticalLinks,
};

export function demoAdminWorkspace(): AdminWorkspace {
  return structuredClone({
    building,
    release: {
      id: id("5001"),
      code: "PILOT-OPENLAYERS",
      status: "draft",
      contentRevision: 0,
      basedOnReleaseId: null,
      description: "OpenLayers 前端验证数据，不用于现场导诊。",
      createdBy: "fixture",
      createdAt: "2026-07-26T08:00:00Z",
      publishedBy: null,
      publishedAt: null,
    },
    floors,
    graph,
  });
}

export function demoNavigationContext(): NavigationContext {
  return structuredClone({
    building,
    release: {
      id: id("5000"),
      code: "REL-DEMO-OPENLAYERS",
      publishedAt: "2026-07-26T08:00:00Z",
    },
    floors,
    supportedRouteModes: ["normal", "accessible"],
  });
}

export function demoNavigationPois(): NavigationPoi[] {
  return pois.map((item) => ({
    id: item.id,
    code: item.code,
    name: item.name,
    category: item.category,
    floorId: item.floorId,
    floorCode: floors.find((floor) => floor.id === item.floorId)?.code ?? "?",
    x: item.x,
    y: item.y,
    accessible: item.accessible,
    searchKeywords: item.keywords,
  }));
}

export function demoRoute(
  startPoiId: string,
  endPoiId: string,
  routeMode: string,
): NavigationRoute {
  const start = pois.find((item) => item.id === startPoiId) ?? pois[0]!;
  const end = pois.find((item) => item.id === endPoiId) ?? pois.at(-1)!;
  const startFloor = floors.find((floor) => floor.id === start.floorId)!;
  const endFloor = floors.find((floor) => floor.id === end.floorId)!;
  const segments: RouteSegment[] = [];
  const transitions = [];
  const startNode = nodes.find((item) => item.id === start.nodeId)!;
  const endNode = nodes.find((item) => item.id === end.nodeId)!;

  if (start.floorId === end.floorId) {
    segments.push(
      segment(0, startFloor, compactPoints([
        point(startNode),
        point(hubNode(startFloor)),
        point(endNode),
      ])),
    );
  } else {
    const startStop = stopNode(startFloor);
    const endStop = stopNode(endFloor);
    segments.push(
      segment(0, startFloor, compactPoints([
        point(startNode),
        point(hubNode(startFloor)),
        point(startStop),
      ])),
    );
    segments.push(
      segment(1, endFloor, compactPoints([
        point(endStop),
        point(hubNode(endFloor)),
        point(endNode),
      ])),
    );
    transitions.push({
      sequence: 0,
      afterSegmentSequence: 0,
      type: "elevator",
      connectorId: connector.id,
      connectorCode: connector.code,
      connectorName: connector.name,
      fromFloorId: startFloor.id,
      fromFloorCode: startFloor.code,
      toFloorId: endFloor.id,
      toFloorCode: endFloor.code,
      estimatedSeconds: 35,
      instruction: `乘坐 ${connector.name} 前往 ${endFloor.code}`,
    });
  }
  const totalDistance = segments.reduce(
    (sum, item) => sum + item.distanceMeters,
    0,
  );
  const totalSeconds =
    segments.reduce((sum, item) => sum + item.estimatedSeconds, 0) +
    (transitions.length ? 35 : 0);
  return {
    releaseId: id("5000"),
    buildingId: building.id,
    routeMode,
    calculatedAt: new Date().toISOString(),
    startPoi: routePoi(start),
    endPoi: routePoi(end),
    summary: {
      distanceMeters: totalDistance,
      estimatedSeconds: totalSeconds,
    },
    segments,
    transitions,
    steps: [
      {
        sequence: 0,
        type: "start",
        floorId: startFloor.id,
        instruction: `从${start.name}出发`,
      },
      ...(transitions.length
        ? [
            {
              sequence: 1,
              type: "transition",
              floorId: startFloor.id,
              instruction: transitions[0]!.instruction,
            },
          ]
        : []),
      {
        sequence: transitions.length ? 2 : 1,
        type: "arrive",
        floorId: endFloor.id,
        instruction: `到达${end.name}`,
      },
    ],
    warnings: [],
  };
}

function id(suffix: string): string {
  return `00000000-0000-0000-0000-${suffix.padStart(12, "0")}`;
}

function node(
  suffix: string,
  code: string,
  floorIndex: number,
  x: number,
  y: number,
  type: string,
): PathNode {
  return {
    id: id(suffix),
    code,
    floorId: floors[floorIndex]!.id,
    x,
    y,
    type,
    enabled: true,
  };
}

function edge(
  suffix: string,
  code: string,
  floorIndex: number,
  fromSuffix: string,
  toSuffix: string,
): PathEdge {
  return {
    id: id(suffix),
    code,
    floorId: floors[floorIndex]!.id,
    fromNodeId: id(fromSuffix),
    toNodeId: id(toSuffix),
    timeSeconds: 20,
    distanceMeters: 18,
    direction: "both",
    type: "corridor",
    accessScope: "public",
    accessible: true,
    enabled: true,
  };
}

function poi(
  suffix: string,
  code: string,
  name: string,
  category: string,
  floorIndex: number,
  nodeSuffix: string,
): Poi {
  const boundNode = nodes.find((item) => item.id === id(nodeSuffix))!;
  return {
    id: id(suffix),
    code,
    name,
    category,
    floorId: floors[floorIndex]!.id,
    nodeId: boundNode.id,
    x: boundNode.x,
    y: boundNode.y,
    accessScope: "public",
    accessible: true,
    enabled: true,
    keywords: [name],
  };
}

function stop(
  suffix: string,
  floorIndex: number,
  nodeSuffix: string,
): ConnectorStop {
  return {
    id: id(suffix),
    code: `STOP-ELEV-B-${floors[floorIndex]!.code}`,
    connectorId: connector.id,
    floorId: floors[floorIndex]!.id,
    nodeId: id(nodeSuffix),
  };
}

function verticalLink(
  suffix: string,
  fromStopSuffix: string,
  toStopSuffix: string,
): VerticalLink {
  return {
    id: id(suffix),
    code: `LINK-ELEV-B-${suffix}`,
    connectorId: connector.id,
    fromStopId: id(fromStopSuffix),
    toStopId: id(toStopSuffix),
    timeSeconds: 35,
    distanceMeters: 4,
    direction: "both",
    accessScope: "public",
    accessible: true,
    enabled: true,
  };
}

function hubNode(floor: Floor): PathNode {
  return nodes.find(
    (item) => item.floorId === floor.id && item.type === "decision",
  )!;
}

function stopNode(floor: Floor): PathNode {
  return nodes.find(
    (item) => item.floorId === floor.id && item.type === "connector_stop",
  )!;
}

function point(nodeValue: PathNode): RoutePoint {
  return {
    nodeId: nodeValue.id,
    x: nodeValue.x,
    y: nodeValue.y,
  };
}

function compactPoints(values: RoutePoint[]): RoutePoint[] {
  return values.filter(
    (value, index) => index === 0 || value.nodeId !== values[index - 1]?.nodeId,
  );
}

function segment(
  sequence: number,
  floor: Floor,
  points: RoutePoint[],
): RouteSegment {
  const pixels = points.slice(1).reduce((total, current, index) => {
    const previous = points[index]!;
    return total + Math.hypot(current.x - previous.x, current.y - previous.y);
  }, 0);
  const distanceMeters = Math.round(pixels * 0.08);
  return {
    sequence,
    floorId: floor.id,
    floorCode: floor.code,
    mapRevisionId: floor.mapRevision.id,
    distanceMeters,
    estimatedSeconds: Math.max(Math.round(distanceMeters / 1.1), 5),
    points,
  };
}

function routePoi(item: Poi) {
  return {
    id: item.id,
    code: item.code,
    name: item.name,
    floorId: item.floorId,
    floorCode: floors.find((floor) => floor.id === item.floorId)!.code,
  };
}
