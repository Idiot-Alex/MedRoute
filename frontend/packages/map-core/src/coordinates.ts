import type { Floor } from "./types";

export type PixelCoordinate = [x: number, y: number];
export type MapCoordinate = [x: number, y: number];

export function pixelToMap(
  coordinate: PixelCoordinate,
  imageHeight: number,
): MapCoordinate {
  return [coordinate[0], imageHeight - coordinate[1]];
}

export function mapToPixel(
  coordinate: MapCoordinate,
  imageHeight: number,
): PixelCoordinate {
  return [coordinate[0], imageHeight - coordinate[1]];
}

export function clampPixel(
  coordinate: PixelCoordinate,
  imageWidth: number,
  imageHeight: number,
): PixelCoordinate {
  return [
    Math.min(Math.max(coordinate[0], 0), imageWidth),
    Math.min(Math.max(coordinate[1], 0), imageHeight),
  ];
}

export function floorExtent(floor: Floor): [number, number, number, number] {
  return [
    0,
    0,
    floor.mapRevision.imageWidth,
    floor.mapRevision.imageHeight,
  ];
}
