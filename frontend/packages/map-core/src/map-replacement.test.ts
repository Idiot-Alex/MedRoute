import { describe, expect, it } from "vitest";
import {
  assessMapReplacement,
  rescaleFloorCoordinates,
} from "./map-replacement";
import type { DraftGraph } from "./types";

describe("map replacement", () => {
  it("keeps coordinates unchanged when image dimensions match", () => {
    expect(
      assessMapReplacement(
        { width: 1000, height: 800 },
        { width: 1000, height: 800 },
      ),
    ).toEqual({
      risk: "unchanged",
      widthScale: 1,
      heightScale: 1,
      aspectRatioChange: 0,
    });
  });

  it("recognizes proportional image scaling", () => {
    expect(
      assessMapReplacement(
        { width: 1000, height: 800 },
        { width: 1500, height: 1200 },
      ).risk,
    ).toBe("uniform-scale");
  });

  it("warns when width and height use different scale factors", () => {
    const assessment = assessMapReplacement(
      { width: 1000, height: 800 },
      { width: 1200, height: 600 },
    );
    expect(assessment.risk).toBe("nonuniform-scale");
    expect(assessment.widthScale).toBe(1.2);
    expect(assessment.heightScale).toBe(0.75);
    expect(assessment.aspectRatioChange).toBeCloseTo(0.6);
  });

  it("rescales only nodes and POIs on the replaced floor", () => {
    const graph = fixtureGraph();
    expect(
      rescaleFloorCoordinates(
        graph,
        "floor-1",
        { width: 1000, height: 800 },
        { width: 1500, height: 400 },
      ),
    ).toEqual({ nodes: 1, pois: 1 });
    expect(graph.nodes[0]).toMatchObject({ x: 150, y: 100 });
    expect(graph.pois[0]).toMatchObject({ x: 150, y: 100 });
    expect(graph.nodes[1]).toMatchObject({ x: 300, y: 400 });
  });
});

function fixtureGraph(): DraftGraph {
  return {
    nodes: [
      {
        id: "node-1",
        code: "N-1",
        floorId: "floor-1",
        x: 100,
        y: 200,
        type: "corridor",
        enabled: true,
      },
      {
        id: "node-2",
        code: "N-2",
        floorId: "floor-2",
        x: 300,
        y: 400,
        type: "corridor",
        enabled: true,
      },
    ],
    edges: [],
    pois: [
      {
        id: "poi-1",
        code: "P-1",
        name: "门诊",
        category: "clinic",
        floorId: "floor-1",
        nodeId: "node-1",
        x: 100,
        y: 200,
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
}
