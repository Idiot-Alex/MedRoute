import { describe, expect, it } from "vitest";
import {
  buildFixedPointNavigationUrl,
  filterNavigationPois,
  findNavigationPoiByReference,
  navigationCategoryLabel,
  navigationUrlUsesLoopback,
  normalizeNavigationSearch,
} from "./navigation";
import type { NavigationPoi } from "./types";

const pois: NavigationPoi[] = [
  poi(
    "id-entrance",
    "P-ENTRANCE",
    "门诊入口",
    "entrance",
    "floor-1",
    "1F",
    ["大门", "menzhenrukou", "mzrk"],
  ),
  poi(
    "id-pharmacy",
    "P-PHARMACY",
    "门诊药房",
    "pharmacy",
    "floor-1",
    "1F",
    ["取药", "yaofang", "yf"],
  ),
  poi(
    "id-lab",
    "P-LAB",
    "检验科",
    "laboratory",
    "floor-2",
    "2F",
    ["抽血", "jianyan", "jy"],
  ),
];

describe("navigation POI helpers", () => {
  it("normalizes full-width and mixed-case search text", () => {
    expect(normalizeNavigationSearch("  Ｐ－ＰＨＡＲＭＡＣＹ  ")).toBe(
      "p-pharmacy",
    );
  });

  it("resolves stable codes case-insensitively and supports legacy IDs", () => {
    expect(
      findNavigationPoiByReference(pois, "p-pharmacy")?.id,
    ).toBe("id-pharmacy");
    expect(
      findNavigationPoiByReference(pois, "id-entrance")?.code,
    ).toBe("P-ENTRANCE");
    expect(findNavigationPoiByReference(pois, "missing")).toBeNull();
  });

  it("matches names, maintained aliases, pinyin and category labels", () => {
    expect(filterNavigationPois(pois, "取药")).toHaveLength(1);
    expect(filterNavigationPois(pois, "YAOFANG")[0]?.code).toBe(
      "P-PHARMACY",
    );
    expect(filterNavigationPois(pois, "检验 抽血")[0]?.code).toBe(
      "P-LAB",
    );
    expect(filterNavigationPois(pois, "出入口")[0]?.code).toBe(
      "P-ENTRANCE",
    );
  });

  it("filters by floor and ranks an exact code first", () => {
    expect(
      filterNavigationPois(pois, "", "floor-1").map((item) => item.code),
    ).toEqual(["P-ENTRANCE", "P-PHARMACY"]);
    expect(
      filterNavigationPois(
        [
          ...pois,
          poi(
            "id-similar",
            "P-PHARMACY-WINDOW",
            "药房窗口",
            "window",
            "floor-2",
            "2F",
            [],
          ),
        ],
        "P-PHARMACY",
      )[0]?.id,
    ).toBe("id-pharmacy");
  });

  it("uses a readable fallback for unknown categories", () => {
    expect(navigationCategoryLabel("department")).toBe("科室");
    expect(navigationCategoryLabel("custom")).toBe("custom");
    expect(navigationCategoryLabel("")).toBe("地点");
  });

  it("builds fixed-point URLs with stable parameters", () => {
    const result = new URL(
      buildFixedPointNavigationUrl(
        "https://nav.example.com/?api=https://api.example.com&buildingId=old&start=id&endPoi=P-LAB",
        "building-1",
        "P-ENTRANCE",
      ),
    );

    expect(result.searchParams.get("api")).toBe(
      "https://api.example.com",
    );
    expect(result.searchParams.get("building")).toBe("building-1");
    expect(result.searchParams.get("startPoi")).toBe("P-ENTRANCE");
    expect(result.searchParams.has("buildingId")).toBe(false);
    expect(result.searchParams.has("start")).toBe(false);
    expect(result.searchParams.has("endPoi")).toBe(false);
  });

  it("rejects invalid QR destinations and detects local-only links", () => {
    expect(() =>
      buildFixedPointNavigationUrl(
        "file:///tmp/navigation.html",
        "building-1",
        "P-ENTRANCE",
      ),
    ).toThrow("HTTP");
    expect(
      navigationUrlUsesLoopback(
        "https://nav.example.com/?api=http://127.0.0.1:8080",
      ),
    ).toBe(true);
    expect(
      navigationUrlUsesLoopback("https://nav.example.com/"),
    ).toBe(false);
  });
});

function poi(
  id: string,
  code: string,
  name: string,
  category: string,
  floorId: string,
  floorCode: string,
  searchKeywords: string[],
): NavigationPoi {
  return {
    id,
    code,
    name,
    category,
    floorId,
    floorCode,
    x: 0,
    y: 0,
    accessible: true,
    searchKeywords,
  };
}
