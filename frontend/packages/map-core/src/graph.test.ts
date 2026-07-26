import { describe, expect, it } from "vitest";
import {
  graphDependencies,
  moveNode,
  parsePoiKeywords,
  rebindEdgeEndpoint,
  rebindPoiToNode,
  removeBasicGraphObject,
} from "./graph";
import type { DraftGraph, Floor } from "./types";

const floor: Floor = {
  id: "floor-1",
  code: "1F",
  name: "一层",
  levelNo: 1,
  mapRevision: {
    id: "map-1",
    revisionNo: 1,
    imageUrl: "/map.jpg",
    imageWidth: 1000,
    imageHeight: 800,
  },
};

describe("moveNode", () => {
  it("moves POIs bound to the edited node by the same delta", () => {
    const graph: DraftGraph = {
      nodes: [
        {
          id: "node-1",
          code: "N-1",
          floorId: floor.id,
          x: 100,
          y: 200,
          type: "poi_access",
          enabled: true,
        },
      ],
      edges: [],
      pois: [
        {
          id: "poi-1",
          code: "P-1",
          name: "药房",
          category: "pharmacy",
          floorId: floor.id,
          nodeId: "node-1",
          x: 110,
          y: 185,
          accessScope: "public",
          accessible: true,
          enabled: true,
          keywords: [],
        },
      ],
      connectors: [],
      connectorStops: [],
      verticalLinks: [],
    };

    expect(moveNode(graph, floor, "node-1", [150, 240])).toBe(true);
    expect(graph.nodes[0]).toMatchObject({ x: 150, y: 240 });
    expect(graph.pois[0]).toMatchObject({ x: 160, y: 225 });
  });

  it("treats an unchanged coordinate as a no-op", () => {
    const graph = editingGraph();

    expect(moveNode(graph, floor, "node-1", [100, 100])).toBe(false);
    expect(graph.nodes[0]).toMatchObject({ x: 100, y: 100 });
  });
});

describe("graph editing integrity", () => {
  it("reports every graph object that references a node", () => {
    const graph = editingGraph();

    expect(
      graphDependencies(graph, { kind: "node", id: "node-1" }),
    ).toEqual([
      {
        kind: "edge",
        id: "edge-1",
        label: "E-1",
        relation: "路径端点",
      },
      {
        kind: "poi",
        id: "poi-1",
        label: "药房",
        relation: "POI 绑定节点",
      },
      {
        kind: "stop",
        id: "stop-1",
        label: "STOP-1",
        relation: "设施停靠点",
      },
    ]);
  });

  it("blocks referenced node deletion and removes independent objects", () => {
    const graph = editingGraph();

    expect(
      removeBasicGraphObject(graph, { kind: "node", id: "node-1" }),
    ).toBe(false);
    expect(
      removeBasicGraphObject(graph, { kind: "edge", id: "edge-1" }),
    ).toBe(true);
    expect(graph.edges).toHaveLength(1);
    expect(
      removeBasicGraphObject(graph, { kind: "poi", id: "poi-1" }),
    ).toBe(true);
    expect(graph.pois).toHaveLength(0);
  });

  it("only rebinds a POI to a node on the same floor", () => {
    const graph = editingGraph();

    expect(rebindPoiToNode(graph, "poi-1", "node-2")).toBe(true);
    expect(graph.pois[0]?.nodeId).toBe("node-2");
    expect(rebindPoiToNode(graph, "poi-1", "node-3")).toBe(false);
  });

  it("rejects equal or duplicate path endpoints", () => {
    const graph = editingGraph();

    expect(
      rebindEdgeEndpoint(graph, "edge-1", "to", "node-1"),
    ).toBe(false);
    expect(
      rebindEdgeEndpoint(graph, "edge-1", "to", "node-3"),
    ).toBe(false);
    expect(
      rebindEdgeEndpoint(graph, "edge-1", "from", "node-5"),
    ).toBe(true);
    expect(graph.edges[0]).toMatchObject({
      fromNodeId: "node-5",
      toNodeId: "node-2",
    });
  });

  it("parses Chinese commas, trims and removes case-insensitive duplicates", () => {
    expect(
      parsePoiKeywords(" 抽血，检验, JIANYAN\n jianyan ,, "),
    ).toEqual(["抽血", "检验", "JIANYAN"]);
  });
});

function editingGraph(): DraftGraph {
  return {
    nodes: [
      pathNode("node-1", "floor-1"),
      pathNode("node-2", "floor-1"),
      pathNode("node-3", "floor-2"),
      pathNode("node-4", "floor-1"),
      pathNode("node-5", "floor-1"),
    ],
    edges: [
      {
        id: "edge-1",
        code: "E-1",
        floorId: "floor-1",
        fromNodeId: "node-1",
        toNodeId: "node-2",
        timeSeconds: 10,
        distanceMeters: 8,
        direction: "both",
        type: "corridor",
        accessScope: "public",
        accessible: true,
        enabled: true,
      },
      {
        id: "edge-2",
        code: "E-2",
        floorId: "floor-1",
        fromNodeId: "node-4",
        toNodeId: "node-2",
        timeSeconds: 10,
        distanceMeters: 8,
        direction: "both",
        type: "corridor",
        accessScope: "public",
        accessible: true,
        enabled: true,
      },
    ],
    pois: [
      {
        id: "poi-1",
        code: "P-1",
        name: "药房",
        category: "pharmacy",
        floorId: "floor-1",
        nodeId: "node-1",
        x: 100,
        y: 100,
        accessScope: "public",
        accessible: true,
        enabled: true,
        keywords: [],
      },
    ],
    connectors: [],
    connectorStops: [
      {
        id: "stop-1",
        code: "STOP-1",
        connectorId: "connector-1",
        floorId: "floor-1",
        nodeId: "node-1",
      },
    ],
    verticalLinks: [],
  };
}

function pathNode(id: string, floorId: string) {
  return {
    id,
    code: id.toLocaleUpperCase(),
    floorId,
    x: 100,
    y: 100,
    type: "normal",
    enabled: true,
  };
}
