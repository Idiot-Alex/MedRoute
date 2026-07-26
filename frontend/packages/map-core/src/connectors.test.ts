import { describe, expect, it } from "vitest";
import { validateConnectorRelations } from "./connectors";
import type { DraftGraph, Floor } from "./types";

const floors: Floor[] = [1, 2, 3].map((level) => ({
  id: `floor-${level}`,
  code: `${level}F`,
  name: `${level}F`,
  levelNo: level,
  mapRevision: {
    id: `map-${level}`,
    revisionNo: 1,
    imageUrl: `/map-${level}.jpg`,
    imageWidth: 1000,
    imageHeight: 800,
  },
}));

function graphFixture(): DraftGraph {
  return {
    nodes: floors.map((floor, index) => ({
      id: `node-${index + 1}`,
      code: `N-${floor.code}`,
      floorId: floor.id,
      x: 100,
      y: 100,
      type: "connector_stop",
      enabled: true,
    })),
    edges: floors.map((floor, index) => ({
      id: `edge-${index + 1}`,
      code: `E-${floor.code}`,
      floorId: floor.id,
      fromNodeId: `node-${index + 1}`,
      toNodeId: `hall-${index + 1}`,
      timeSeconds: 10,
      distanceMeters: 5,
      direction: "both",
      type: "corridor",
      accessScope: "public",
      accessible: true,
      enabled: true,
    })),
    pois: [],
    connectors: [
      {
        id: "elevator-a",
        code: "ELEV-A",
        name: "A 电梯",
        type: "elevator",
        accessScope: "public",
        accessible: true,
        enabled: true,
      },
    ],
    connectorStops: [],
    verticalLinks: [],
  };
}

describe("validateConnectorRelations", () => {
  it("allows an elevator to connect non-adjacent served floors", () => {
    const graph = graphFixture();
    graph.connectorStops.push(
      {
        id: "stop-1",
        code: "STOP-A-1F",
        connectorId: "elevator-a",
        floorId: "floor-1",
        nodeId: "node-1",
      },
      {
        id: "stop-3",
        code: "STOP-A-3F",
        connectorId: "elevator-a",
        floorId: "floor-3",
        nodeId: "node-3",
      },
    );
    graph.verticalLinks.push({
      id: "link-1-3",
      code: "VERT-A-1-3",
      connectorId: "elevator-a",
      fromStopId: "stop-1",
      toStopId: "stop-3",
      timeSeconds: 30,
      distanceMeters: 6,
      direction: "both",
      accessScope: "public",
      accessible: true,
      enabled: true,
    });

    expect(validateConnectorRelations(graph, floors)).toEqual([]);
  });

  it("reports incomplete stops as publish errors without blocking draft save", () => {
    const graph = graphFixture();
    graph.connectorStops.push({
      id: "stop-1",
      code: "STOP-A-1F",
      connectorId: "elevator-a",
      floorId: "floor-1",
      nodeId: "node-1",
    });

    const issues = validateConnectorRelations(graph, floors);
    expect(issues.map((issue) => issue.code)).toEqual(
      expect.arrayContaining([
        "CONNECTOR_HAS_TOO_FEW_STOPS",
        "CONNECTOR_STOP_NOT_LINKED",
      ]),
    );
    expect(issues.some((issue) => issue.blockingSave)).toBe(false);
  });

  it("blocks duplicate stops for the same connector and floor", () => {
    const graph = graphFixture();
    graph.connectorStops.push(
      {
        id: "stop-1",
        code: "STOP-A-1F",
        connectorId: "elevator-a",
        floorId: "floor-1",
        nodeId: "node-1",
      },
      {
        id: "stop-1-copy",
        code: "STOP-A-1F-COPY",
        connectorId: "elevator-a",
        floorId: "floor-1",
        nodeId: "node-1",
      },
    );

    expect(
      validateConnectorRelations(graph, floors).some(
        (issue) =>
          issue.code === "DUPLICATE_CONNECTOR_FLOOR" && issue.blockingSave,
      ),
    ).toBe(true);
  });

  it("blocks a reverse one-way link already covered by a two-way link", () => {
    const graph = graphFixture();
    graph.connectorStops.push(
      {
        id: "stop-1",
        code: "STOP-A-1F",
        connectorId: "elevator-a",
        floorId: "floor-1",
        nodeId: "node-1",
      },
      {
        id: "stop-2",
        code: "STOP-A-2F",
        connectorId: "elevator-a",
        floorId: "floor-2",
        nodeId: "node-2",
      },
    );
    graph.verticalLinks.push(
      {
        id: "link-both",
        code: "VERT-A-BOTH",
        connectorId: "elevator-a",
        fromStopId: "stop-1",
        toStopId: "stop-2",
        timeSeconds: 30,
        distanceMeters: 6,
        direction: "both",
        accessScope: "public",
        accessible: true,
        enabled: true,
      },
      {
        id: "link-reverse",
        code: "VERT-A-REVERSE",
        connectorId: "elevator-a",
        fromStopId: "stop-2",
        toStopId: "stop-1",
        timeSeconds: 30,
        distanceMeters: 6,
        direction: "forward",
        accessScope: "public",
        accessible: true,
        enabled: true,
      },
    );

    expect(
      validateConnectorRelations(graph, floors).some(
        (issue) =>
          issue.code === "DUPLICATE_VERTICAL_LINK" &&
          issue.elementId === "link-reverse" &&
          issue.blockingSave,
      ),
    ).toBe(true);
  });
});
