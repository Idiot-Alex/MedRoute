import { describe, expect, it } from "vitest";
import type { NavigationPoi } from "@medroute/map-core";
import {
  navigationCalculationBlocker,
  resolveNavigationEndpoints,
  shouldAutomaticallyCalculateRoute,
} from "./navigation-safety";

const pois: NavigationPoi[] = [
  poi("entrance", "ENTRANCE", "entrance"),
  poi("clinic", "CLINIC", "clinic"),
  poi("pharmacy", "PHARMACY", "pharmacy"),
];

describe("resolveNavigationEndpoints", () => {
  it("keeps an invalid explicit destination empty instead of using a default", () => {
    const result = resolveNavigationEndpoints(
      pois,
      "ENTRANCE",
      "REMOVED-POI",
    );

    expect(result.startPoiId).toBe("entrance");
    expect(result.endPoiId).toBe("");
    expect(result.notices).toContain(
      "链接中的目的地已失效，请重新选择",
    );
  });

  it("keeps identical explicit endpoints unchanged", () => {
    const result = resolveNavigationEndpoints(
      pois,
      "CLINIC",
      "CLINIC",
    );

    expect(result.startPoiId).toBe("clinic");
    expect(result.endPoiId).toBe("clinic");
    expect(result.notices).toContain("起点和目的地相同，请重新选择");
  });

  it("selects a safe default destination only when no destination was requested", () => {
    const result = resolveNavigationEndpoints(pois, "ENTRANCE", "");

    expect(result.startPoiId).toBe("entrance");
    expect(result.endPoiId).toBe("clinic");
  });
});

describe("navigationCalculationBlocker", () => {
  it("blocks accessible routing when the published map does not support it", () => {
    const blocker = navigationCalculationBlocker({
      contextLoaded: true,
      startPoiId: "entrance",
      endPoiId: "clinic",
      routeMode: "accessible",
      supportedRouteModes: ["normal"],
    });

    expect(blocker).toBe(
      "当前地图不支持无障碍路线，请改用常规路线或联系工作人员。",
    );
    expect(
      shouldAutomaticallyCalculateRoute(false, "CLINIC", blocker),
    ).toBe(false);
  });

  it("blocks missing and identical endpoints", () => {
    expect(
      navigationCalculationBlocker({
        contextLoaded: true,
        startPoiId: "entrance",
        endPoiId: "",
        routeMode: "normal",
        supportedRouteModes: ["normal"],
      }),
    ).toBe("请选择有效的起点和目的地。");

    expect(
      navigationCalculationBlocker({
        contextLoaded: true,
        startPoiId: "clinic",
        endPoiId: "clinic",
        routeMode: "normal",
        supportedRouteModes: ["normal"],
      }),
    ).toBe("起点和终点不能相同，请重新选择。");
  });
});

describe("shouldAutomaticallyCalculateRoute", () => {
  it("requires an explicit destination or demo mode and no blocker", () => {
    expect(
      shouldAutomaticallyCalculateRoute(false, "CLINIC", ""),
    ).toBe(true);
    expect(shouldAutomaticallyCalculateRoute(false, "", "")).toBe(false);
    expect(shouldAutomaticallyCalculateRoute(true, "", "")).toBe(true);
    expect(
      shouldAutomaticallyCalculateRoute(
        false,
        "REMOVED-POI",
        "请选择有效的起点和目的地。",
      ),
    ).toBe(false);
  });
});

function poi(
  id: string,
  code: string,
  category: string,
): NavigationPoi {
  return {
    id,
    code,
    name: code,
    category,
    floorId: "floor-1",
    floorCode: "1F",
    x: 0,
    y: 0,
    accessible: true,
    searchKeywords: [],
  };
}
