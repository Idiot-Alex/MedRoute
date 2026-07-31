import {
  findNavigationPoiByReference,
  type NavigationPoi,
} from "@medroute/map-core";

export type NavigationRouteMode = "normal" | "accessible";

export interface NavigationEndpointResolution {
  startPoiId: string;
  endPoiId: string;
  notices: string[];
}

export interface NavigationCalculationState {
  contextLoaded: boolean;
  startPoiId: string;
  endPoiId: string;
  routeMode: NavigationRouteMode;
  supportedRouteModes: string[];
}

export function resolveNavigationEndpoints(
  pois: NavigationPoi[],
  startReference: string,
  endReference: string,
): NavigationEndpointResolution {
  const entrance =
    pois.find((poi) => poi.category === "entrance") ?? pois[0];
  const requestedStart = findNavigationPoiByReference(
    pois,
    startReference,
  );
  const requestedEnd = findNavigationPoiByReference(pois, endReference);
  const notices: string[] = [];

  if (startReference.trim() && !requestedStart) {
    notices.push("链接中的起点已失效，已改为默认入口");
  }
  if (endReference.trim() && !requestedEnd) {
    notices.push("链接中的目的地已失效，请重新选择");
  }

  const resolvedStart = requestedStart ?? entrance;
  const resolvedEnd = endReference.trim()
    ? requestedEnd
    : pois.find((poi) => poi.id !== resolvedStart?.id);

  if (
    startReference.trim() &&
    endReference.trim() &&
    resolvedStart?.id === resolvedEnd?.id
  ) {
    notices.push("起点和目的地相同，请重新选择");
  }

  return {
    startPoiId: resolvedStart?.id ?? "",
    endPoiId: resolvedEnd?.id ?? "",
    notices,
  };
}

export function navigationCalculationBlocker(
  state: NavigationCalculationState,
): string {
  if (!state.contextLoaded) {
    return "";
  }
  if (!state.startPoiId || !state.endPoiId) {
    return "请选择有效的起点和目的地。";
  }
  if (state.startPoiId === state.endPoiId) {
    return "起点和终点不能相同，请重新选择。";
  }
  if (!state.supportedRouteModes.includes(state.routeMode)) {
    return state.routeMode === "accessible"
      ? "当前地图不支持无障碍路线，请改用常规路线或联系工作人员。"
      : "当前地图不支持所选路线模式，请重新选择。";
  }
  return "";
}

export function shouldAutomaticallyCalculateRoute(
  demoMode: boolean,
  endReference: string,
  calculationBlocker: string,
): boolean {
  return (
    (demoMode || Boolean(endReference.trim())) && !calculationBlocker
  );
}
