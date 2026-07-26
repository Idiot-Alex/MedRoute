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
