import { describe, expect, it } from "vitest";
import { moveNode } from "./graph";
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
});
