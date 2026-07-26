import type {
  AdminValidation,
  AdminWorkspace,
  Connector,
  ConnectorStop,
  DraftGraph,
  Floor,
  NavigationContext,
  NavigationPoi,
  NavigationRoute,
  OperationClosureListResponse,
  OperationTarget,
  PathEdge,
  PathNode,
  Poi,
  ReleaseListItem,
  ReleaseListResponse,
  RouteRegressionCase,
  RouteRegressionResult,
  RoutePoint,
  RouteSegment,
  VerticalLink,
} from "@medroute/map-core";
import { validateConnectorRelations } from "@medroute/map-core";

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
  poi(
    "1201",
    "P-ENTRANCE",
    "门诊主入口",
    "entrance",
    0,
    "1001",
    ["入口", "大门", "menzhenrukou", "mzrk"],
  ),
  poi(
    "1202",
    "P-PHARMACY",
    "门诊药房",
    "pharmacy",
    0,
    "1003",
    ["取药", "yaofang", "yf"],
  ),
  poi(
    "2201",
    "P-LAB",
    "检验科",
    "laboratory",
    1,
    "2002",
    ["检验", "抽血", "jianyan", "jy"],
  ),
  poi(
    "3201",
    "P-ULTRASOUND",
    "超声诊断科",
    "department",
    2,
    "3002",
    ["超声", "B超", "chaosheng", "cs"],
  ),
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

const releaseItems: ReleaseListItem[] = [
  {
    id: id("5001"),
    code: "PILOT-OPENLAYERS",
    status: "draft",
    active: false,
    contentRevision: 0,
    basedOnReleaseId: id("5000"),
    description: "调整跨层设施并复核关键路线。",
    createdBy: "fixture",
    createdAt: "2026-07-26T08:30:00Z",
    publishedBy: null,
    publishedAt: null,
    validationPassed: null,
    validatedRevision: null,
  },
  {
    id: id("5000"),
    code: "REL-DEMO-OPENLAYERS",
    status: "published",
    active: true,
    contentRevision: 3,
    basedOnReleaseId: id("4999"),
    description: "当前移动导航使用版本。",
    createdBy: "fixture",
    createdAt: "2026-07-25T08:00:00Z",
    publishedBy: "fixture",
    publishedAt: "2026-07-26T08:00:00Z",
    validationPassed: true,
    validatedRevision: 3,
  },
  {
    id: id("4999"),
    code: "REL-DEMO-PREVIOUS",
    status: "published",
    active: false,
    contentRevision: 2,
    basedOnReleaseId: null,
    description: "上一版现场地图。",
    createdBy: "fixture",
    createdAt: "2026-07-20T08:00:00Z",
    publishedBy: "fixture",
    publishedAt: "2026-07-21T08:00:00Z",
    validationPassed: true,
    validatedRevision: 2,
  },
];

const regressionCases: RouteRegressionCase[] = [
  {
    id: id("6001"),
    code: "ENTRANCE-TO-PHARMACY",
    name: "入口到门诊药房",
    startPoiCode: "P-ENTRANCE",
    endPoiCode: "P-PHARMACY",
    routeMode: "normal",
    critical: true,
    enabled: true,
    maxDistanceMeters: 60,
    maxEstimatedSeconds: 120,
    createdBy: "fixture",
    createdAt: "2026-07-26T08:00:00Z",
    updatedBy: "fixture",
    updatedAt: "2026-07-26T08:00:00Z",
  },
  {
    id: id("6002"),
    code: "ENTRANCE-TO-LAB-ACCESSIBLE",
    name: "入口到检验科无障碍路线",
    startPoiCode: "P-ENTRANCE",
    endPoiCode: "P-LAB",
    routeMode: "accessible",
    critical: true,
    enabled: true,
    maxDistanceMeters: 120,
    maxEstimatedSeconds: 180,
    createdBy: "fixture",
    createdAt: "2026-07-26T08:00:00Z",
    updatedBy: "fixture",
    updatedAt: "2026-07-26T08:00:00Z",
  },
];

export function demoReleaseList(): ReleaseListResponse {
  return structuredClone({
    items: releaseItems,
    nextPageToken: null,
  });
}

export function demoRouteRegressionCases(): RouteRegressionCase[] {
  return structuredClone(regressionCases);
}

export function demoOperationClosures(
  releaseId = id("5000"),
  releaseCode = "REL-DEMO-OPENLAYERS",
  source?: Pick<AdminWorkspace, "floors" | "graph">,
): OperationClosureListResponse {
  const operationGraph = source?.graph ?? graph;
  const operationFloors = source?.floors ?? floors;
  const targets: OperationTarget[] = [
    ...operationGraph.edges
      .filter((item) => item.enabled)
      .map((item) => ({
        targetType: "path_edge" as const,
        id: item.id,
        code: item.code,
        name: item.code,
        floorCode:
          operationFloors.find((floor) => floor.id === item.floorId)?.code ??
          null,
      })),
    ...operationGraph.connectors
      .filter((item) => item.enabled)
      .map((item) => ({
        targetType: "vertical_connector" as const,
        id: item.id,
        code: item.code,
        name: item.name,
        floorCode: null,
      })),
    ...operationGraph.verticalLinks
      .filter((item) => item.enabled)
      .map((item) => {
        const fromStop = operationGraph.connectorStops.find(
          (stopItem) => stopItem.id === item.fromStopId,
        );
        const toStop = operationGraph.connectorStops.find(
          (stopItem) => stopItem.id === item.toStopId,
        );
        const fromFloor = operationFloors.find(
          (floor) => floor.id === fromStop?.floorId,
        );
        const toFloor = operationFloors.find(
          (floor) => floor.id === toStop?.floorId,
        );
        const linkConnector = operationGraph.connectors.find(
          (connectorItem) => connectorItem.id === item.connectorId,
        );
        const floorCode = `${fromFloor?.code ?? "?"} ↔ ${toFloor?.code ?? "?"}`;
        return {
          targetType: "vertical_link" as const,
          id: item.id,
          code: item.code,
          name: `${linkConnector?.name ?? item.code} ${floorCode}`,
          floorCode,
        };
      }),
  ];
  return structuredClone({
    buildingId: building.id,
    releaseId,
    releaseCode,
    targets,
    items: [],
  });
}

export function demoAdminWorkspace(releaseId = id("5001")): AdminWorkspace {
  const summary =
    releaseItems.find((item) => item.id === releaseId) ?? releaseItems[0]!;
  return structuredClone({
    building,
    release: {
      id: summary.id,
      code: summary.code,
      status: summary.status,
      contentRevision: summary.contentRevision,
      basedOnReleaseId: summary.basedOnReleaseId,
      description: summary.description,
      createdBy: summary.createdBy,
      createdAt: summary.createdAt,
      publishedBy: summary.publishedBy,
      publishedAt: summary.publishedAt,
    },
    floors,
    graph,
  });
}

export function demoValidation(
  workspace: AdminWorkspace,
  cases: RouteRegressionCase[],
): AdminValidation {
  const errors = validateConnectorRelations(
    workspace.graph,
    workspace.floors,
  )
    .filter((item) => item.severity === "error")
    .map((item) => ({
      code: item.code,
      elementType: {
        connector: "vertical_connector",
        stop: "connector_stop",
        link: "vertical_link",
      }[item.elementKind],
      elementId: item.elementId,
      message: item.message,
    }));
  for (const node of workspace.graph.nodes.filter((item) => item.enabled)) {
    const connected = workspace.graph.edges.some(
      (edge) =>
        edge.enabled &&
        (edge.fromNodeId === node.id || edge.toNodeId === node.id),
    );
    if (!connected) {
      errors.push({
        code: "ISOLATED_NODE",
        elementType: "path_node",
        elementId: node.id,
        message: `${node.code} 没有连接任何启用路径。`,
      });
    }
  }

  const enabledCases = cases.filter((item) => item.enabled);
  const routeRegressions = enabledCases.map((item) =>
    demoRegressionResult(workspace, item),
  );
  const warnings = [];
  if (!enabledCases.length) {
    warnings.push({
      code: "NO_ROUTE_REGRESSION_CASE",
      elementType: "release",
      elementId: workspace.release.id,
      message: "当前楼栋尚未配置启用的关键路线回归用例。",
    });
  }
  for (const result of routeRegressions.filter((item) => !item.passed)) {
    const target = result.critical ? errors : warnings;
    target.push({
      code: `ROUTE_REGRESSION_${result.resultCode}`,
      elementType: "route_regression_case",
      elementId: result.caseId,
      message: `${result.caseName}：${result.message}`,
    });
  }
  return {
    releaseId: workspace.release.id,
    contentRevision: workspace.release.contentRevision,
    passed: errors.length === 0,
    errors,
    warnings,
    routeRegressions,
  };
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

function demoRegressionResult(
  workspace: AdminWorkspace,
  regressionCase: RouteRegressionCase,
): RouteRegressionResult {
  const start = workspace.graph.pois.find(
    (item) => item.enabled && item.code === regressionCase.startPoiCode,
  );
  const end = workspace.graph.pois.find(
    (item) => item.enabled && item.code === regressionCase.endPoiCode,
  );
  if (!start || !end) {
    return {
      caseId: regressionCase.id,
      caseCode: regressionCase.code,
      caseName: regressionCase.name,
      routeMode: regressionCase.routeMode,
      critical: regressionCase.critical,
      startPoiCode: regressionCase.startPoiCode,
      startPoiName: start?.name ?? null,
      endPoiCode: regressionCase.endPoiCode,
      endPoiName: end?.name ?? null,
      passed: false,
      resultCode: "POI_NOT_FOUND",
      distanceMeters: null,
      estimatedSeconds: null,
      connectorCodes: [],
      message: "当前草稿中找不到回归路线使用的已启用 POI。",
    };
  }
  const crossFloor = start.floorId !== end.floorId;
  const distanceMeters = crossFloor ? 72 : 36;
  const estimatedSeconds = crossFloor ? 105 : 40;
  const exceeded =
    (regressionCase.maxDistanceMeters != null &&
      distanceMeters > regressionCase.maxDistanceMeters) ||
    (regressionCase.maxEstimatedSeconds != null &&
      estimatedSeconds > regressionCase.maxEstimatedSeconds);
  return {
    caseId: regressionCase.id,
    caseCode: regressionCase.code,
    caseName: regressionCase.name,
    routeMode: regressionCase.routeMode,
    critical: regressionCase.critical,
    startPoiCode: regressionCase.startPoiCode,
    startPoiName: start.name,
    endPoiCode: regressionCase.endPoiCode,
    endPoiName: end.name,
    passed: !exceeded,
    resultCode: exceeded ? "LIMIT_EXCEEDED" : "PASSED",
    distanceMeters,
    estimatedSeconds,
    connectorCodes: crossFloor ? ["ELEV-B"] : [],
    message: exceeded
      ? "演示路线超过配置的距离或耗时上限。"
      : "路线可达并符合配置限制。",
  };
}

export function demoNavigationPois(
  source?: Pick<AdminWorkspace, "floors" | "graph">,
): NavigationPoi[] {
  const navigationFloors = source?.floors ?? floors;
  const navigationPois = source?.graph.pois ?? pois;
  return navigationPois
    .filter((item) => item.enabled && item.accessScope === "public")
    .map((item) => ({
      id: item.id,
      code: item.code,
      name: item.name,
      category: item.category,
      floorId: item.floorId,
      floorCode:
        navigationFloors.find((floor) => floor.id === item.floorId)?.code ??
        "?",
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
  keywords: string[],
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
    keywords: [name, ...keywords],
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
