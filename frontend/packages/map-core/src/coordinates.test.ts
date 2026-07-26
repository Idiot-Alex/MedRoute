import { describe, expect, it } from "vitest";
import { clampPixel, mapToPixel, pixelToMap } from "./coordinates";

describe("floor image coordinate conversion", () => {
  it("round-trips top-left image coordinates", () => {
    const imagePoint: [number, number] = [235.4, 385.8];
    expect(mapToPixel(pixelToMap(imagePoint, 800), 800)).toEqual(imagePoint);
  });

  it("maps the image corners to the OpenLayers extent", () => {
    expect(pixelToMap([0, 0], 800)).toEqual([0, 800]);
    expect(pixelToMap([1000, 800], 800)).toEqual([1000, 0]);
  });

  it("clamps edits to the source image", () => {
    expect(clampPixel([-20, 920], 1000, 800)).toEqual([0, 800]);
  });
});
