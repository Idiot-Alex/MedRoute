import type { DraftGraph } from "./types";

export interface MapImageDimensions {
  width: number;
  height: number;
}

export type MapReplacementRisk =
  | "unchanged"
  | "uniform-scale"
  | "nonuniform-scale";

export interface MapReplacementAssessment {
  risk: MapReplacementRisk;
  widthScale: number;
  heightScale: number;
  aspectRatioChange: number;
}

export interface RescaledAnnotationCount {
  nodes: number;
  pois: number;
}

function assertDimensions(dimensions: MapImageDimensions): void {
  if (
    !Number.isFinite(dimensions.width) ||
    !Number.isFinite(dimensions.height) ||
    dimensions.width <= 0 ||
    dimensions.height <= 0
  ) {
    throw new RangeError("Map image dimensions must be positive.");
  }
}

export function assessMapReplacement(
  current: MapImageDimensions,
  next: MapImageDimensions,
): MapReplacementAssessment {
  assertDimensions(current);
  assertDimensions(next);
  const widthScale = next.width / current.width;
  const heightScale = next.height / current.height;
  const aspectRatioChange = Math.abs(
    next.width / next.height / (current.width / current.height) - 1,
  );
  const sameDimensions =
    current.width === next.width && current.height === next.height;
  const relativeScaleDifference =
    Math.abs(widthScale - heightScale) /
    Math.max(widthScale, heightScale);

  return {
    risk: sameDimensions
      ? "unchanged"
      : relativeScaleDifference <= 0.01
        ? "uniform-scale"
        : "nonuniform-scale",
    widthScale,
    heightScale,
    aspectRatioChange,
  };
}

export function rescaleFloorCoordinates(
  graph: DraftGraph,
  floorId: string,
  current: MapImageDimensions,
  next: MapImageDimensions,
): RescaledAnnotationCount {
  const assessment = assessMapReplacement(current, next);
  let nodes = 0;
  let pois = 0;
  const scale = (value: number, factor: number): number =>
    Math.round(value * factor * 10_000) / 10_000;

  for (const node of graph.nodes) {
    if (node.floorId !== floorId) {
      continue;
    }
    node.x = scale(node.x, assessment.widthScale);
    node.y = scale(node.y, assessment.heightScale);
    nodes += 1;
  }
  for (const poi of graph.pois) {
    if (poi.floorId !== floorId) {
      continue;
    }
    poi.x = scale(poi.x, assessment.widthScale);
    poi.y = scale(poi.y, assessment.heightScale);
    pois += 1;
  }
  return { nodes, pois };
}
