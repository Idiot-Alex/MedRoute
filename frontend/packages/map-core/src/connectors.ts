import type {
  Connector,
  ConnectorStop,
  DraftGraph,
  Floor,
  SelectionKind,
  VerticalLink,
} from "./types";

export type ConnectorIssueSeverity = "error" | "warning";

export interface ConnectorIssue {
  code: string;
  severity: ConnectorIssueSeverity;
  blockingSave: boolean;
  elementKind: Extract<SelectionKind, "connector" | "stop" | "link">;
  elementId: string;
  floorId: string | null;
  message: string;
}

export function stopsForConnector(
  graph: DraftGraph,
  connectorId: string,
  floors: Floor[],
): ConnectorStop[] {
  const levelByFloor = new Map(
    floors.map((floor) => [floor.id, floor.levelNo]),
  );
  return graph.connectorStops
    .filter((stop) => stop.connectorId === connectorId)
    .sort(
      (left, right) =>
        (levelByFloor.get(left.floorId) ?? 0) -
        (levelByFloor.get(right.floorId) ?? 0),
    );
}

export function linksForConnector(
  graph: DraftGraph,
  connectorId: string,
): VerticalLink[] {
  return graph.verticalLinks.filter(
    (link) => link.connectorId === connectorId,
  );
}

export function connectorIssueCounts(
  issues: ConnectorIssue[],
  connectorId: string,
  graph: DraftGraph,
): { errors: number; warnings: number } {
  const stopIds = new Set(
    graph.connectorStops
      .filter((stop) => stop.connectorId === connectorId)
      .map((stop) => stop.id),
  );
  const linkIds = new Set(
    graph.verticalLinks
      .filter((link) => link.connectorId === connectorId)
      .map((link) => link.id),
  );
  const relevant = issues.filter(
    (issue) =>
      (issue.elementKind === "connector" &&
        issue.elementId === connectorId) ||
      (issue.elementKind === "stop" && stopIds.has(issue.elementId)) ||
      (issue.elementKind === "link" && linkIds.has(issue.elementId)),
  );
  return {
    errors: relevant.filter((issue) => issue.severity === "error").length,
    warnings: relevant.filter((issue) => issue.severity === "warning").length,
  };
}

export function validateConnectorRelations(
  graph: DraftGraph,
  floors: Floor[],
): ConnectorIssue[] {
  const issues: ConnectorIssue[] = [];
  const connectors = new Map(
    graph.connectors.map((connector) => [connector.id, connector]),
  );
  const floorById = new Map(floors.map((floor) => [floor.id, floor]));
  const nodeById = new Map(graph.nodes.map((node) => [node.id, node]));
  const stopById = new Map(
    graph.connectorStops.map((stop) => [stop.id, stop]),
  );
  const floorPairs = new Set<string>();

  for (const stop of graph.connectorStops) {
    const connector = connectors.get(stop.connectorId);
    const floor = floorById.get(stop.floorId);
    const node = nodeById.get(stop.nodeId);
    if (!connector) {
      issues.push(
        issue(
          "STOP_CONNECTOR_UNKNOWN",
          "stop",
          stop.id,
          stop.floorId,
          "停靠点引用了不存在的跨层设施。",
          true,
        ),
      );
    }
    if (!floor) {
      issues.push(
        issue(
          "STOP_FLOOR_UNKNOWN",
          "stop",
          stop.id,
          stop.floorId,
          "停靠点引用了不存在的楼层。",
          true,
        ),
      );
    }
    if (!node || node.floorId !== stop.floorId) {
      issues.push(
        issue(
          "STOP_NODE_INVALID",
          "stop",
          stop.id,
          stop.floorId,
          "停靠点必须绑定同一楼层的有效路径节点。",
          true,
        ),
      );
    }
    const pair = `${stop.connectorId}:${stop.floorId}`;
    if (floorPairs.has(pair)) {
      issues.push(
        issue(
          "DUPLICATE_CONNECTOR_FLOOR",
          "stop",
          stop.id,
          stop.floorId,
          `${connector?.name ?? "设施"}在同一楼层只能有一个停靠点。`,
          true,
        ),
      );
    }
    floorPairs.add(pair);
    const onFloorNetwork = graph.edges.some(
      (edge) =>
        edge.enabled &&
        edge.floorId === stop.floorId &&
        (edge.fromNodeId === stop.nodeId || edge.toNodeId === stop.nodeId),
    );
    if (node && !onFloorNetwork) {
      issues.push(
        issue(
          "STOP_NOT_ON_FLOOR_NETWORK",
          "stop",
          stop.id,
          stop.floorId,
          `${stop.code} 的节点没有连接本层可通行路径。`,
          false,
        ),
      );
    }
  }

  const linkKeys = new Set<string>();
  for (const link of graph.verticalLinks) {
    const connector = connectors.get(link.connectorId);
    const from = stopById.get(link.fromStopId);
    const to = stopById.get(link.toStopId);
    if (
      !connector ||
      !from ||
      !to ||
      from.connectorId !== link.connectorId ||
      to.connectorId !== link.connectorId
    ) {
      issues.push(
        issue(
          "LINK_REFERENCE_INVALID",
          "link",
          link.id,
          null,
          `${link.code} 的设施或停靠点引用无效。`,
          true,
        ),
      );
      continue;
    }
    if (from.id === to.id || from.floorId === to.floorId) {
      issues.push(
        issue(
          "LINK_FLOORS_INVALID",
          "link",
          link.id,
          from.floorId,
          `${link.code} 必须连接两个不同楼层的停靠点。`,
          true,
        ),
      );
    }
    if (link.timeSeconds <= 0 || link.distanceMeters < 0) {
      issues.push(
        issue(
          "LINK_COST_INVALID",
          "link",
          link.id,
          from.floorId,
          `${link.code} 的耗时必须大于零，距离不能小于零。`,
          true,
        ),
      );
    }
    const directKey = `${link.connectorId}:${link.fromStopId}:${link.toStopId}`;
    const reverseKey = `${link.connectorId}:${link.toStopId}:${link.fromStopId}`;
    if (
      linkKeys.has(directKey) ||
      (link.direction === "both" && linkKeys.has(reverseKey))
    ) {
      issues.push(
        issue(
          "DUPLICATE_VERTICAL_LINK",
          "link",
          link.id,
          from.floorId,
          `${link.code} 与已有跨层连接重复。`,
          true,
        ),
      );
    }
    linkKeys.add(directKey);
    if (link.direction === "both") {
      linkKeys.add(reverseKey);
    }
    if (
      connector.accessible &&
      connector.type === "elevator" &&
      link.enabled &&
      !link.accessible
    ) {
      issues.push(
        warning(
          "ACCESSIBLE_LINK_MISMATCH",
          "link",
          link.id,
          from.floorId,
          `${connector.name}标记为无障碍，但 ${link.code} 未标记为无障碍。`,
        ),
      );
    }
  }

  for (const connector of graph.connectors.filter(
    (candidate) => candidate.enabled,
  )) {
    validateEnabledConnector(
      connector,
      graph,
      floors,
      issues,
    );
  }
  return issues;
}

function validateEnabledConnector(
  connector: Connector,
  graph: DraftGraph,
  floors: Floor[],
  issues: ConnectorIssue[],
): void {
  const stops = stopsForConnector(graph, connector.id, floors);
  const links = linksForConnector(graph, connector.id).filter(
    (link) => link.enabled,
  );
  if (stops.length < 2) {
    issues.push(
      issue(
        "CONNECTOR_HAS_TOO_FEW_STOPS",
        "connector",
        connector.id,
        null,
        `${connector.name}至少需要两个楼层停靠点。`,
        false,
      ),
    );
  }
  const degree = new Map<string, number>();
  const adjacency = new Map<string, Set<string>>();
  for (const link of links) {
    degree.set(link.fromStopId, (degree.get(link.fromStopId) ?? 0) + 1);
    degree.set(link.toStopId, (degree.get(link.toStopId) ?? 0) + 1);
    connect(adjacency, link.fromStopId, link.toStopId);
    connect(adjacency, link.toStopId, link.fromStopId);
  }
  for (const stop of stops) {
    if ((degree.get(stop.id) ?? 0) === 0) {
      issues.push(
        issue(
          "CONNECTOR_STOP_NOT_LINKED",
          "stop",
          stop.id,
          stop.floorId,
          `${stop.code} 未配置任何启用的跨层连接。`,
          false,
        ),
      );
    }
  }
  if (stops.length < 2) {
    return;
  }
  const visited = reachable(stops[0]!.id, adjacency);
  for (const stop of stops.slice(1)) {
    if (!visited.has(stop.id)) {
      issues.push(
        issue(
          "CONNECTOR_STOP_DISCONNECTED",
          "stop",
          stop.id,
          stop.floorId,
          `${stop.code} 与${connector.name}的其他停靠楼层不连通。`,
          false,
        ),
      );
    }
  }
}

function connect(
  adjacency: Map<string, Set<string>>,
  from: string,
  to: string,
): void {
  const values = adjacency.get(from) ?? new Set<string>();
  values.add(to);
  adjacency.set(from, values);
}

function reachable(
  start: string,
  adjacency: Map<string, Set<string>>,
): Set<string> {
  const visited = new Set([start]);
  const queue = [start];
  while (queue.length) {
    const current = queue.shift()!;
    for (const next of adjacency.get(current) ?? []) {
      if (!visited.has(next)) {
        visited.add(next);
        queue.push(next);
      }
    }
  }
  return visited;
}

function issue(
  code: string,
  elementKind: ConnectorIssue["elementKind"],
  elementId: string,
  floorId: string | null,
  message: string,
  blockingSave: boolean,
): ConnectorIssue {
  return {
    code,
    severity: "error",
    blockingSave,
    elementKind,
    elementId,
    floorId,
    message,
  };
}

function warning(
  code: string,
  elementKind: ConnectorIssue["elementKind"],
  elementId: string,
  floorId: string | null,
  message: string,
): ConnectorIssue {
  return {
    code,
    severity: "warning",
    blockingSave: false,
    elementKind,
    elementId,
    floorId,
    message,
  };
}
