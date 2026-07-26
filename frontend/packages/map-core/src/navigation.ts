import type { NavigationPoi } from "./types";

const categoryLabels: Record<string, string> = {
  clinic: "诊室",
  department: "科室",
  diagnostic: "医技",
  elevator: "电梯",
  entrance: "出入口",
  facility: "设施",
  imaging: "影像",
  laboratory: "检验",
  pharmacy: "药房",
  service: "服务",
  stairs: "楼梯",
  toilet: "卫生间",
  window: "窗口",
};

export function normalizeNavigationSearch(value: string): string {
  return value.normalize("NFKC").toLocaleLowerCase("zh-CN").trim();
}

export function navigationCategoryLabel(category: string): string {
  return (categoryLabels[category] ?? category) || "地点";
}

export function buildFixedPointNavigationUrl(
  baseUrl: string,
  buildingId: string,
  startPoiCode: string,
): string {
  let navigationUrl: URL;
  try {
    navigationUrl = new URL(baseUrl.trim());
  } catch {
    throw new Error("导航页面基础地址无效。");
  }
  if (!["http:", "https:"].includes(navigationUrl.protocol)) {
    throw new Error("导航页面必须使用 HTTP 或 HTTPS 地址。");
  }
  for (const parameter of [
    "building",
    "buildingId",
    "start",
    "startPoi",
    "startPoiCode",
    "end",
    "endPoi",
    "endPoiCode",
  ]) {
    navigationUrl.searchParams.delete(parameter);
  }
  navigationUrl.searchParams.set("building", buildingId);
  navigationUrl.searchParams.set("startPoi", startPoiCode);
  return navigationUrl.toString();
}

export interface NavigationBaseUrlOptions {
  currentOrigin: string;
  apiBase: string;
  configuredUrl?: string | null;
  development: boolean;
  apiExplicit: boolean;
}

export function resolveNavigationBaseUrl(
  options: NavigationBaseUrlOptions,
): string {
  if (options.configuredUrl?.trim()) {
    return options.configuredUrl.trim();
  }
  const navigationUrl = new URL("/", options.currentOrigin);
  if (options.development && navigationUrl.port === "5173") {
    navigationUrl.port = "5174";
  }
  const apiOrigin = new URL(options.apiBase, options.currentOrigin).origin;
  if (
    options.apiExplicit ||
    (!options.development && apiOrigin !== navigationUrl.origin)
  ) {
    navigationUrl.searchParams.set("api", options.apiBase);
  }
  return navigationUrl.toString();
}

export function navigationUrlUsesLoopback(value: string): boolean {
  try {
    const navigationUrl = new URL(value);
    if (isLoopbackHostname(navigationUrl.hostname)) {
      return true;
    }
    const apiParameter = navigationUrl.searchParams.get("api");
    return apiParameter
      ? isLoopbackHostname(new URL(apiParameter).hostname)
      : false;
  } catch {
    return false;
  }
}

export function findNavigationPoiByReference(
  pois: NavigationPoi[],
  reference: string,
): NavigationPoi | null {
  const normalized = normalizeNavigationSearch(reference);
  if (!normalized) {
    return null;
  }
  return (
    pois.find(
      (poi) =>
        normalizeNavigationSearch(poi.code) === normalized ||
        normalizeNavigationSearch(poi.id) === normalized,
    ) ?? null
  );
}

export function filterNavigationPois(
  pois: NavigationPoi[],
  query: string,
  floorId = "",
): NavigationPoi[] {
  const terms = normalizeNavigationSearch(query)
    .split(/\s+/)
    .filter(Boolean);
  const matches = pois.filter((poi) => {
    if (floorId && poi.floorId !== floorId) {
      return false;
    }
    if (!terms.length) {
      return true;
    }
    const corpus = normalizeNavigationSearch(
      [
        poi.name,
        poi.code,
        poi.floorCode,
        poi.category,
        navigationCategoryLabel(poi.category),
        ...poi.searchKeywords,
      ].join(" "),
    );
    return terms.every((term) => corpus.includes(term));
  });

  return matches.sort((left, right) => {
    const scoreDifference =
      poiSearchScore(left, terms) - poiSearchScore(right, terms);
    if (scoreDifference) {
      return scoreDifference;
    }
    const floorDifference = left.floorCode.localeCompare(
      right.floorCode,
      "zh-CN",
      { numeric: true },
    );
    return (
      floorDifference ||
      left.name.localeCompare(right.name, "zh-CN", { numeric: true })
    );
  });
}

function isLoopbackHostname(hostname: string): boolean {
  return ["127.0.0.1", "localhost", "::1", "[::1]"].includes(
    hostname.toLocaleLowerCase(),
  );
}

function poiSearchScore(poi: NavigationPoi, terms: string[]): number {
  if (!terms.length) {
    return 0;
  }
  const query = terms.join(" ");
  const code = normalizeNavigationSearch(poi.code);
  const name = normalizeNavigationSearch(poi.name);
  if (code === query) {
    return 0;
  }
  if (name === query) {
    return 1;
  }
  if (name.startsWith(query) || code.startsWith(query)) {
    return 2;
  }
  return 3;
}
