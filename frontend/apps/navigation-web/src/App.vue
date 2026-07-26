<script setup lang="ts">
import { ApiError, MedRouteApiClient } from "@medroute/api-client";
import {
  demoNavigationContext,
  demoNavigationPois,
  demoRoute,
} from "@medroute/api-client/demo";
import {
  DEFAULT_BUILDING_ID,
  findNavigationPoiByReference,
  type NavigationContext,
  type NavigationPoi,
  type NavigationRoute,
  type RouteStep,
} from "@medroute/map-core";
import {
  Accessibility,
  AlertCircle,
  ArrowDownUp,
  Building2,
  ChevronDown,
  ChevronRight,
  Clock3,
  Footprints,
  LocateFixed,
  ListStart,
  MapPinned,
  Navigation,
  RefreshCw,
  Route,
} from "@lucide/vue";
import { computed, onMounted, ref } from "vue";
import PoiSearchDialog from "./components/PoiSearchDialog.vue";
import RouteFloorMap from "./components/RouteFloorMap.vue";
import StepNavigator from "./components/StepNavigator.vue";

const params = new URLSearchParams(window.location.search);
const apiBase = (params.get("api") ?? "http://127.0.0.1:8080").replace(
  /\/$/,
  "",
);
const assetBase = params.get("assets") ?? window.location.origin;
const buildingId =
  params.get("building") ??
  params.get("buildingId") ??
  DEFAULT_BUILDING_ID;
const requestedStartReference =
  params.get("startPoi") ??
  params.get("startPoiCode") ??
  params.get("start") ??
  "";
const requestedEndReference =
  params.get("endPoi") ??
  params.get("endPoiCode") ??
  params.get("end") ??
  "";
const requestedRouteMode =
  params.get("routeMode") ?? params.get("mode") ?? "normal";
const client = new MedRouteApiClient({ apiBase });

const context = ref<NavigationContext | null>(null);
const pois = ref<NavigationPoi[]>([]);
const route = ref<NavigationRoute | null>(null);
const startPoiId = ref("");
const endPoiId = ref("");
const routeMode = ref(
  ["normal", "accessible"].includes(requestedRouteMode)
    ? requestedRouteMode
    : "normal",
);
const activeFloorId = ref("");
const loading = ref(false);
const routeLoading = ref(false);
const error = ref("");
const routeError = ref("");
const deepLinkNotice = ref("");
const demoMode = ref(params.get("demo") === "1");
const stepNavigatorOpen = ref(false);
const currentStepIndex = ref(0);
const poiSearchDialog = ref<
  InstanceType<typeof PoiSearchDialog> | null
>(null);

const activeFloor = computed(
  () =>
    context.value?.floors.find(
      (floor) => floor.id === activeFloorId.value,
    ) ?? null,
);

const activeSegment = computed(
  () =>
    route.value?.segments.find(
      (segment) => segment.floorId === activeFloorId.value,
    ) ?? null,
);

const startPoi = computed(
  () => pois.value.find((poi) => poi.id === startPoiId.value) ?? null,
);

const endPoi = computed(
  () => pois.value.find((poi) => poi.id === endPoiId.value) ?? null,
);

const routeFloorIds = computed(
  () => new Set(route.value?.segments.map((segment) => segment.floorId) ?? []),
);

const routeTime = computed(() => {
  const seconds = route.value?.summary.estimatedSeconds ?? 0;
  return Math.max(Math.ceil(seconds / 60), 1);
});

const currentStep = computed<RouteStep | null>(
  () => route.value?.steps[currentStepIndex.value] ?? null,
);

const currentStepFloorLabel = computed(() =>
  floorName(currentStep.value?.floorId ?? null),
);

async function load(): Promise<void> {
  loading.value = true;
  error.value = "";
  route.value = null;
  stepNavigatorOpen.value = false;
  deepLinkNotice.value = "";
  try {
    if (demoMode.value) {
      context.value = demoNavigationContext();
      pois.value = demoNavigationPois();
    } else {
      const [nextContext, poiResponse] = await Promise.all([
        client.navigationContext(buildingId),
        client.navigationPois(buildingId),
      ]);
      context.value = nextContext;
      if (poiResponse.releaseId !== nextContext.release.id) {
        throw new ApiError(
          "医院地图刚刚更新，楼层与地点数据版本不一致，请重新加载。",
          409,
          "NAVIGATION_RELEASE_MISMATCH",
        );
      }
      pois.value = poiResponse.items;
    }
    if (!context.value.floors.length || pois.value.length < 2) {
      throw new ApiError(
        "当前发布版本缺少可导航的楼层或地点。",
        422,
        "NAVIGATION_DATA_INCOMPLETE",
      );
    }
    const entrance =
      pois.value.find((poi) => poi.category === "entrance") ?? pois.value[0];
    const destination =
      pois.value.find((poi) => poi.id !== entrance?.id) ?? pois.value[1];
    const requestedStart = findNavigationPoiByReference(
      pois.value,
      requestedStartReference,
    );
    const requestedEnd = findNavigationPoiByReference(
      pois.value,
      requestedEndReference,
    );
    const notices: string[] = [];
    if (requestedStartReference && !requestedStart) {
      notices.push("链接中的起点已失效，已改为默认入口");
    }
    if (requestedEndReference && !requestedEnd) {
      notices.push("链接中的目的地已失效，请重新选择");
    }
    if (
      requestedRouteMode &&
      !["normal", "accessible"].includes(requestedRouteMode)
    ) {
      notices.push("链接中的路线偏好不可用，已改为常规路线");
    }
    const resolvedStart = requestedStart ?? entrance ?? pois.value[0]!;
    let resolvedEnd = requestedEnd ?? destination ?? pois.value[1]!;
    if (resolvedEnd.id === resolvedStart.id) {
      resolvedEnd =
        pois.value.find((poi) => poi.id !== resolvedStart.id) ??
        resolvedEnd;
      if (requestedEnd) {
        notices.push("起点和目的地相同，已保留另一个可选地点");
      }
    }
    startPoiId.value = resolvedStart.id;
    endPoiId.value = resolvedEnd.id;
    if (
      !context.value.supportedRouteModes.includes(routeMode.value)
    ) {
      routeMode.value = "normal";
      notices.push("链接中的路线偏好不可用，已改为常规路线");
    }
    deepLinkNotice.value = notices.join("；");
    activeFloorId.value =
      resolvedStart.floorId ?? context.value.floors[0]?.id ?? "";
    if (demoMode.value || requestedEnd) {
      await calculate();
    } else if (
      params.has("start") ||
      params.has("end") ||
      params.has("startPoiCode") ||
      params.has("endPoiCode") ||
      params.has("buildingId") ||
      params.has("mode")
    ) {
      syncNavigationUrl();
    }
  } catch (caught) {
    error.value =
      caught instanceof ApiError ? caught.message : "导航地图加载失败。";
  } finally {
    loading.value = false;
  }
}

function useDemo(): void {
  demoMode.value = true;
  const url = new URL(window.location.href);
  url.searchParams.set("demo", "1");
  window.history.replaceState(null, "", url);
  void load();
}

async function calculate(): Promise<void> {
  if (
    !context.value ||
    !startPoiId.value ||
    !endPoiId.value ||
    startPoiId.value === endPoiId.value
  ) {
    routeError.value =
      startPoiId.value === endPoiId.value ? "起点和终点不能相同。" : "";
    return;
  }
  routeLoading.value = true;
  routeError.value = "";
  try {
    route.value = demoMode.value
      ? demoRoute(startPoiId.value, endPoiId.value, routeMode.value)
      : await client.calculateRoute({
          buildingId,
          expectedReleaseId: context.value.release.id,
          startPoiId: startPoiId.value,
          endPoiId: endPoiId.value,
          routeMode: routeMode.value,
        });
    currentStepIndex.value = 0;
    stepNavigatorOpen.value = false;
    activeFloorId.value =
      route.value.segments[0]?.floorId ??
      startPoi.value?.floorId ??
      context.value.floors[0]?.id ??
      "";
    syncNavigationUrl();
  } catch (caught) {
    routeError.value =
      caught instanceof ApiError ? caught.message : "暂时无法计算这条路线。";
  } finally {
    routeLoading.value = false;
  }
}

function swapEndpoints(): void {
  [startPoiId.value, endPoiId.value] = [
    endPoiId.value,
    startPoiId.value,
  ];
  route.value = null;
  routeError.value = "";
  stepNavigatorOpen.value = false;
  activeFloorId.value =
    startPoi.value?.floorId ?? activeFloorId.value;
  syncNavigationUrl();
}

function syncNavigationUrl(): void {
  const url = new URL(window.location.href);
  for (const key of [
    "buildingId",
    "start",
    "end",
    "startPoiCode",
    "endPoiCode",
    "mode",
  ]) {
    url.searchParams.delete(key);
  }
  url.searchParams.set("building", buildingId);
  if (startPoi.value) {
    url.searchParams.set("startPoi", startPoi.value.code);
  }
  if (endPoi.value) {
    url.searchParams.set("endPoi", endPoi.value.code);
  }
  url.searchParams.set("routeMode", routeMode.value);
  window.history.replaceState(null, "", url);
}

function openPoiSearch(endpoint: "start" | "end"): void {
  poiSearchDialog.value?.open(endpoint);
}

function selectEndpoint(
  endpoint: "start" | "end",
  poiId: string,
): void {
  if (endpoint === "start") {
    startPoiId.value = poiId;
  } else {
    endPoiId.value = poiId;
  }
  route.value = null;
  routeError.value =
    startPoiId.value === endPoiId.value
      ? "起点和终点不能相同。"
      : "";
  deepLinkNotice.value = "";
  stepNavigatorOpen.value = false;
  const selected = pois.value.find((poi) => poi.id === poiId);
  if (selected) {
    activeFloorId.value = selected.floorId;
  }
  syncNavigationUrl();
}

function chooseRouteMode(mode: "normal" | "accessible"): void {
  if (routeMode.value === mode) {
    return;
  }
  routeMode.value = mode;
  route.value = null;
  routeError.value = "";
  stepNavigatorOpen.value = false;
  syncNavigationUrl();
}

function setCurrentStep(index: number): void {
  if (!route.value?.steps.length) {
    return;
  }
  currentStepIndex.value = Math.min(
    Math.max(index, 0),
    route.value.steps.length - 1,
  );
  const floorId = route.value.steps[currentStepIndex.value]?.floorId;
  if (floorId) {
    activeFloorId.value = floorId;
  }
}

function openStepNavigator(index = 0): void {
  if (!route.value?.steps.length) {
    return;
  }
  setCurrentStep(index);
  stepNavigatorOpen.value = true;
}

function moveStep(direction: number): void {
  if (!route.value) {
    return;
  }
  const nextIndex = currentStepIndex.value + direction;
  if (nextIndex >= route.value.steps.length) {
    stepNavigatorOpen.value = false;
    return;
  }
  setCurrentStep(nextIndex);
}

function viewCurrentStepMap(): void {
  setCurrentStep(currentStepIndex.value);
  stepNavigatorOpen.value = false;
}

function floorName(floorId: string | null): string {
  return (
    context.value?.floors.find((floor) => floor.id === floorId)?.code ?? ""
  );
}

onMounted(load);
</script>

<template>
  <div
    class="relative mx-auto grid h-[100dvh] w-full max-w-[1120px] grid-rows-[56px_minmax(0,1fr)] overflow-hidden bg-white text-[#172033] lg:my-4 lg:h-[calc(100dvh-2rem)] lg:border lg:border-[#cad2d8] lg:shadow-[0_12px_30px_rgba(23,32,51,0.12)]"
  >
    <header
      class="flex items-center justify-between border-b border-[#d7dce2] bg-white px-3.5 sm:px-5"
    >
      <div class="flex min-w-0 items-center gap-2.5">
        <span
          class="grid size-8 shrink-0 place-items-center rounded-md bg-[#087f8c] text-sm font-bold text-white"
          >M</span
        >
        <div class="min-w-0">
          <h1 class="truncate text-sm font-semibold">院内导航</h1>
          <p class="truncate text-[11px] text-[#667085]">
            {{ context?.building.name ?? "正在读取医院地图" }}
          </p>
        </div>
      </div>
      <button
        class="grid size-9 place-items-center rounded-md border border-[#d7dce2] bg-white text-[#4b5563] active:translate-y-px disabled:opacity-40"
        type="button"
        title="重新加载"
        :disabled="loading"
        @click="load"
      >
        <RefreshCw :size="17" :class="{ 'animate-spin': loading }" />
        <span class="sr-only">重新加载</span>
      </button>
    </header>

    <main
      v-if="context && activeFloor"
      class="grid min-h-0 grid-rows-[48px_minmax(250px,50dvh)_minmax(0,1fr)] lg:grid-cols-[minmax(0,1fr)_360px] lg:grid-rows-[48px_minmax(0,1fr)]"
    >
      <div
        class="flex min-w-0 items-center justify-between border-b border-[#d7dce2] bg-[#f8fafb] px-3 lg:col-span-2"
      >
        <div
          class="flex min-w-0 items-center gap-1 overflow-x-auto"
          role="tablist"
          aria-label="楼层"
        >
          <button
            v-for="floor in context.floors"
            :key="floor.id"
            type="button"
            role="tab"
            class="relative h-8 min-w-11 shrink-0 rounded-md px-2 text-xs font-semibold active:translate-y-px"
            :class="
              floor.id === activeFloorId
                ? 'bg-[#087f8c] text-white'
                : 'text-[#4b5563] hover:bg-[#e8ecef]'
            "
            :aria-selected="floor.id === activeFloorId"
            @click="activeFloorId = floor.id"
          >
            {{ floor.code }}
            <span
              v-if="routeFloorIds.has(floor.id) && floor.id !== activeFloorId"
              class="absolute right-1 top-1 size-1.5 rounded-full bg-[#dc2626]"
              aria-label="路线经过"
            ></span>
          </button>
        </div>
        <div
          v-if="route"
          class="ml-3 flex shrink-0 items-center gap-3 text-xs font-semibold"
        >
          <span class="flex items-center gap-1">
            <MapPinned :size="14" class="text-[#087f8c]" />
            {{ Math.round(route.summary.distanceMeters) }} 米
          </span>
          <span class="flex items-center gap-1">
            <Clock3 :size="14" class="text-[#087f8c]" />
            约 {{ routeTime }} 分钟
          </span>
        </div>
      </div>

      <section
        class="relative min-h-0 overflow-hidden border-b border-[#d7dce2] lg:border-b-0 lg:border-r"
      >
        <RouteFloorMap
          :floor="activeFloor"
          :segment="activeSegment"
          :start-poi="startPoi"
          :end-poi="endPoi"
          :api-base="apiBase"
          :asset-base="assetBase"
        />
        <div
          class="pointer-events-none absolute right-3 top-3 rounded-md border border-[#d7dce2] bg-white/95 px-2.5 py-1.5 shadow-sm"
        >
          <strong class="block text-xs">{{ activeFloor.code }} · {{ activeFloor.name }}</strong>
          <span class="mt-0.5 block text-[11px] text-[#667085]">
            {{
              activeSegment
                ? `本层 ${Math.round(activeSegment.distanceMeters)} 米`
                : route
                  ? "路线不经过本层"
                  : "尚未规划路线"
            }}
          </span>
        </div>
        <div
          v-if="routeLoading"
          class="absolute inset-0 grid place-items-center bg-white/75"
          aria-live="polite"
        >
          <div class="w-40 space-y-2">
            <div class="h-2 animate-pulse rounded-sm bg-[#b9dfe0]"></div>
            <div class="h-2 w-3/4 animate-pulse rounded-sm bg-[#d7dce2]"></div>
            <p class="pt-1 text-center text-xs font-medium text-[#344054]">
              正在计算路线
            </p>
          </div>
        </div>
        <span
          v-if="demoMode"
          class="pointer-events-none absolute bottom-3 right-3 rounded-md border border-[#d7dce2] bg-white/95 px-2 py-1 text-[10px] text-[#667085]"
          >演示数据 · 未经现场核验</span
        >
      </section>

      <aside
        class="min-h-0 overflow-y-auto bg-white lg:border-l-0"
        aria-label="路线设置和步骤"
      >
        <form
          class="border-b border-[#e4e7eb] p-3.5 sm:p-4"
          @submit.prevent="calculate"
        >
          <div class="grid grid-cols-[minmax(0,1fr)_36px] gap-2">
            <div class="space-y-2">
              <div class="grid grid-cols-[38px_minmax(0,1fr)] items-center">
                <span class="text-xs font-semibold text-[#15803d]">起点</span>
                <button
                  class="flex h-11 min-w-0 items-center justify-between gap-2 rounded-md border border-[#cfd5dc] bg-white px-3 text-left outline-none focus:border-[#087f8c] focus:ring-2 focus:ring-[#b9dfe0]"
                  type="button"
                  aria-label="选择起点"
                  aria-haspopup="dialog"
                  @click="openPoiSearch('start')"
                >
                  <span class="truncate text-sm">
                    {{
                      startPoi
                        ? `${startPoi.floorCode} · ${startPoi.name}`
                        : "选择起点"
                    }}
                  </span>
                  <ChevronDown class="shrink-0 text-[#667085]" :size="16" />
                </button>
              </div>
              <div class="grid grid-cols-[38px_minmax(0,1fr)] items-center">
                <span class="text-xs font-semibold text-[#b91c1c]">终点</span>
                <button
                  class="flex h-11 min-w-0 items-center justify-between gap-2 rounded-md border border-[#cfd5dc] bg-white px-3 text-left outline-none focus:border-[#087f8c] focus:ring-2 focus:ring-[#b9dfe0]"
                  type="button"
                  aria-label="选择目的地"
                  aria-haspopup="dialog"
                  @click="openPoiSearch('end')"
                >
                  <span class="truncate text-sm">
                    {{
                      endPoi
                        ? `${endPoi.floorCode} · ${endPoi.name}`
                        : "选择目的地"
                    }}
                  </span>
                  <ChevronDown class="shrink-0 text-[#667085]" :size="16" />
                </button>
              </div>
            </div>
            <button
              class="mt-7 grid size-9 place-items-center rounded-md border border-[#cfd5dc] bg-white text-[#4b5563] active:translate-y-px"
              type="button"
              title="交换起点和终点"
              @click="swapEndpoints"
            >
              <ArrowDownUp :size="17" />
              <span class="sr-only">交换起点和终点</span>
            </button>
          </div>

          <div class="mt-3 flex items-center justify-between gap-3">
            <div
              class="grid grid-cols-2 rounded-md border border-[#cfd5dc] p-0.5"
              role="group"
              aria-label="路线模式"
            >
              <button
                type="button"
                class="flex h-8 items-center gap-1.5 whitespace-nowrap rounded-sm px-2.5 text-xs font-medium"
                :aria-pressed="routeMode === 'normal'"
                :class="
                  routeMode === 'normal'
                    ? 'bg-[#e3f3f3] text-[#066d77]'
                    : 'text-[#667085]'
                "
                @click="chooseRouteMode('normal')"
              >
                <Footprints :size="14" />
                常规
              </button>
              <button
                type="button"
                class="flex h-8 items-center gap-1.5 whitespace-nowrap rounded-sm px-2.5 text-xs font-medium"
                :aria-pressed="routeMode === 'accessible'"
                :class="
                  routeMode === 'accessible'
                    ? 'bg-[#e3f3f3] text-[#066d77]'
                    : 'text-[#667085]'
                "
                @click="chooseRouteMode('accessible')"
              >
                <Accessibility :size="14" />
                无障碍
              </button>
            </div>
            <button
              class="flex h-9 items-center gap-1.5 whitespace-nowrap rounded-md bg-[#087f8c] px-3.5 text-xs font-semibold text-white active:translate-y-px disabled:opacity-40"
              type="submit"
              :disabled="routeLoading"
            >
              <Navigation :size="15" />
              开始导航
            </button>
          </div>
          <p
            v-if="routeError"
            class="mt-2 flex items-start gap-1.5 text-xs leading-5 text-[#b91c1c]"
            role="alert"
          >
            <AlertCircle :size="14" class="mt-0.5 shrink-0" />
            {{ routeError }}
          </p>
          <p
            v-if="deepLinkNotice"
            class="mt-2 flex items-start gap-1.5 border-y border-[#fed7aa] bg-[#fff7ed] px-2.5 py-2 text-[11px] leading-4 text-[#9a3412]"
            role="status"
          >
            <AlertCircle :size="14" class="mt-0.5 shrink-0" />
            {{ deepLinkNotice }}
          </p>
        </form>

        <section v-if="route" class="p-3.5 sm:p-4">
          <div class="mb-3 flex items-center justify-between">
            <h2 class="flex items-center gap-2 text-sm font-semibold">
              <Route :size="17" class="text-[#087f8c]" />
              路线步骤
            </h2>
            <button
              class="flex h-8 items-center gap-1.5 rounded-md border border-[#cfd5dc] bg-white px-2.5 text-[11px] font-semibold text-[#344054] active:translate-y-px"
              type="button"
              @click="openStepNavigator()"
            >
              <ListStart :size="15" />
              逐步查看
            </button>
          </div>
          <ol class="divide-y divide-[#e4e7eb] border-y border-[#e4e7eb]">
            <li
              v-for="(step, index) in route.steps"
              :key="step.sequence"
            >
              <button
                class="grid w-full grid-cols-[28px_minmax(0,1fr)_16px] items-start gap-2 py-3 text-left active:bg-[#f3f5f7]"
                type="button"
                @click="openStepNavigator(index)"
              >
                <span
                  class="grid size-7 place-items-center rounded-md bg-[#e3f3f3] text-xs font-bold text-[#066d77]"
                >
                  {{ index + 1 }}
                </span>
                <span class="min-w-0">
                  <span class="block text-sm leading-5">
                    {{ step.instruction }}
                  </span>
                  <span class="mt-0.5 block text-[11px] text-[#667085]">
                    {{ floorName(step.floorId) }}
                  </span>
                </span>
                <ChevronRight :size="16" class="mt-1 text-[#98a2b3]" />
              </button>
            </li>
          </ol>
          <p
            class="mt-3 flex items-start gap-2 rounded-md bg-[#fff7ed] p-2.5 text-[11px] leading-4 text-[#9a3412]"
          >
            <LocateFixed :size="15" class="mt-0.5 shrink-0" />
            院内环境可能变化，请同时留意现场指示牌和工作人员引导。
          </p>
        </section>

        <section
          v-else
          class="grid min-h-40 place-items-center p-6 text-center"
        >
          <div>
            <MapPinned class="mx-auto text-[#98a2b3]" :size="24" />
            <strong class="mt-3 block text-sm">设置起点和目的地</strong>
            <p class="mt-1 text-xs leading-5 text-[#667085]">
              路线会按楼层拆分，并明确显示电梯或楼梯换层。
            </p>
          </div>
        </section>
      </aside>
    </main>

    <main
      v-else
      class="grid min-h-0 place-items-center bg-[#edf1f3] p-5"
    >
      <section
        class="w-full max-w-sm rounded-md border border-[#d7dce2] bg-white p-5"
      >
        <Building2 class="text-[#087f8c]" :size="26" />
        <h2 class="mt-4 text-base font-semibold">
          {{ loading ? "正在加载医院地图" : "无法加载医院地图" }}
        </h2>
        <p class="mt-2 text-sm leading-6 text-[#667085]">
          {{
            loading
              ? `正在连接 ${apiBase}`
              : error || "请确认后端服务和数据库已经启动。"
          }}
        </p>
        <div v-if="!loading" class="mt-5 flex gap-2">
          <button
            class="h-10 whitespace-nowrap rounded-md bg-[#087f8c] px-3.5 text-xs font-semibold text-white active:translate-y-px"
            type="button"
            @click="load"
          >
            重试连接
          </button>
          <button
            class="h-10 whitespace-nowrap rounded-md border border-[#cfd5dc] bg-white px-3.5 text-xs font-semibold text-[#344054] active:translate-y-px"
            type="button"
            @click="useDemo"
          >
            查看演示地图
          </button>
        </div>
      </section>
    </main>

    <PoiSearchDialog
      v-if="context"
      ref="poiSearchDialog"
      :pois="pois"
      :floors="context.floors"
      :start-poi-id="startPoiId"
      :end-poi-id="endPoiId"
      @select="selectEndpoint"
    />

    <StepNavigator
      v-if="stepNavigatorOpen && currentStep && route"
      :step="currentStep"
      :index="currentStepIndex"
      :total="route.steps.length"
      :floor-label="currentStepFloorLabel"
      @previous="moveStep(-1)"
      @next="moveStep(1)"
      @view-map="viewCurrentStepMap"
      @close="stepNavigatorOpen = false"
    />
  </div>
</template>
