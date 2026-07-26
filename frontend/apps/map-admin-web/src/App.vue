<script setup lang="ts">
import {
  ApiError,
  MedRouteApiClient,
  resolveMapImageUrl,
} from "@medroute/api-client";
import { demoAdminWorkspace } from "@medroute/api-client/demo";
import {
  DEFAULT_BUILDING_ID,
  graphForFloor,
  moveNode,
  nextCode,
  selectedObject,
  type AdminWorkspace,
  type EditorTool,
  type MapSelection,
  type PathEdge,
  type PathNode,
  type PixelCoordinate,
  type Poi,
} from "@medroute/map-core";
import {
  CirclePlus,
  Cloud,
  GitBranch,
  LocateFixed,
  MapPin,
  MousePointer2,
  RefreshCw,
  Save,
  Server,
  TriangleAlert,
} from "@lucide/vue";
import { computed, onMounted, ref } from "vue";
import FloorMapEditor from "./components/FloorMapEditor.vue";

const params = new URLSearchParams(window.location.search);
const apiBase = (params.get("api") ?? "http://127.0.0.1:8080").replace(
  /\/$/,
  "",
);
const buildingId = params.get("building") ?? DEFAULT_BUILDING_ID;
const assetBase = params.get("assets") ?? window.location.origin;
const client = new MedRouteApiClient({ apiBase });

const workspace = ref<AdminWorkspace | null>(null);
const etag = ref("");
const activeFloorId = ref("");
const tool = ref<EditorTool>("select");
const selection = ref<MapSelection | null>(null);
const edgeStartId = ref<string | null>(null);
const dirty = ref(false);
const busy = ref(false);
const error = ref("");
const toast = ref("");
const renderRevision = ref(0);
const demoMode = ref(params.get("demo") === "1");

const tools = [
  { id: "select" as const, label: "选择", icon: MousePointer2 },
  { id: "node" as const, label: "节点", icon: CirclePlus },
  { id: "edge" as const, label: "路径", icon: GitBranch },
  { id: "poi" as const, label: "POI", icon: MapPin },
];

const activeFloor = computed(
  () =>
    workspace.value?.floors.find(
      (floor) => floor.id === activeFloorId.value,
    ) ?? null,
);

const currentGraph = computed(() =>
  workspace.value && activeFloor.value
    ? graphForFloor(workspace.value.graph, activeFloor.value.id)
    : null,
);

const editable = computed(
  () => workspace.value?.release.status === "draft" && !busy.value,
);

const selectedNode = computed<PathNode | null>(() => {
  if (!workspace.value || selection.value?.kind !== "node") {
    return null;
  }
  return (
    (selectedObject(workspace.value.graph, selection.value) as PathNode) ??
    null
  );
});

const selectedEdge = computed<PathEdge | null>(() => {
  if (!workspace.value || selection.value?.kind !== "edge") {
    return null;
  }
  return (
    (selectedObject(workspace.value.graph, selection.value) as PathEdge) ??
    null
  );
});

const selectedPoi = computed<Poi | null>(() => {
  if (!workspace.value || selection.value?.kind !== "poi") {
    return null;
  }
  return (selectedObject(workspace.value.graph, selection.value) as Poi) ?? null;
});

const imageUrl = computed(() =>
  activeFloor.value
    ? resolveMapImageUrl(
        activeFloor.value.mapRevision.imageUrl,
        apiBase,
        assetBase,
      )
    : "",
);

async function load(): Promise<void> {
  busy.value = true;
  error.value = "";
  selection.value = null;
  edgeStartId.value = null;
  try {
    if (demoMode.value) {
      workspace.value = demoAdminWorkspace();
      etag.value = "0";
    } else {
      const result = await client.loadPreferredWorkspace(buildingId);
      workspace.value = result.workspace;
      etag.value = result.etag;
    }
    activeFloorId.value = workspace.value.floors[0]?.id ?? "";
    dirty.value = false;
    renderRevision.value += 1;
  } catch (caught) {
    error.value =
      caught instanceof ApiError ? caught.message : "地图工作区加载失败。";
  } finally {
    busy.value = false;
  }
}

function useDemo(): void {
  demoMode.value = true;
  void load();
}

function changeFloor(floorId: string): void {
  activeFloorId.value = floorId;
  selection.value = null;
  edgeStartId.value = null;
  renderRevision.value += 1;
}

function chooseTool(nextTool: EditorTool): void {
  tool.value = nextTool;
  edgeStartId.value = null;
  if (nextTool !== "select") {
    selection.value = null;
  }
}

function changed(message?: string): void {
  dirty.value = true;
  renderRevision.value += 1;
  if (message) {
    showToast(message);
  }
}

function select(nextSelection: MapSelection | null): void {
  selection.value = nextSelection;
  renderRevision.value += 1;
}

function addNode(coordinate: PixelCoordinate): void {
  if (!workspace.value || !activeFloor.value || !editable.value) {
    return;
  }
  const node: PathNode = {
    id: crypto.randomUUID(),
    code: nextCode(
      `N-${activeFloor.value.code}`,
      workspace.value.graph.nodes,
    ),
    floorId: activeFloor.value.id,
    x: Math.round(coordinate[0]),
    y: Math.round(coordinate[1]),
    type: "normal",
    enabled: true,
  };
  workspace.value.graph.nodes.push(node);
  selection.value = { kind: "node", id: node.id };
  changed("节点已创建");
}

function moveSelectedNode(
  nodeId: string,
  coordinate: PixelCoordinate,
): void {
  if (!workspace.value || !activeFloor.value || !editable.value) {
    return;
  }
  if (moveNode(workspace.value.graph, activeFloor.value, nodeId, coordinate)) {
    changed();
  }
}

function chooseEdgeNode(nodeId: string): void {
  if (!workspace.value || !activeFloor.value || !editable.value) {
    return;
  }
  if (!edgeStartId.value) {
    edgeStartId.value = nodeId;
    renderRevision.value += 1;
    showToast("已选择起点，请点击第二个节点");
    return;
  }
  if (edgeStartId.value === nodeId) {
    edgeStartId.value = null;
    renderRevision.value += 1;
    return;
  }
  const duplicate = workspace.value.graph.edges.some(
    (edge) =>
      edge.floorId === activeFloor.value?.id &&
      ((edge.fromNodeId === edgeStartId.value && edge.toNodeId === nodeId) ||
        (edge.fromNodeId === nodeId && edge.toNodeId === edgeStartId.value)),
  );
  if (duplicate) {
    showToast("这两个节点之间已经有路径");
    edgeStartId.value = null;
    renderRevision.value += 1;
    return;
  }
  const edge: PathEdge = {
    id: crypto.randomUUID(),
    code: nextCode(
      `EDGE-${activeFloor.value.code}`,
      workspace.value.graph.edges,
    ),
    floorId: activeFloor.value.id,
    fromNodeId: edgeStartId.value,
    toNodeId: nodeId,
    timeSeconds: 10,
    distanceMeters: 0,
    direction: "both",
    type: "corridor",
    accessScope: "public",
    accessible: true,
    enabled: true,
  };
  workspace.value.graph.edges.push(edge);
  edgeStartId.value = null;
  selection.value = { kind: "edge", id: edge.id };
  tool.value = "select";
  changed("路径已创建，请填写距离和耗时");
}

function addPoi(nodeId: string): void {
  if (!workspace.value || !activeFloor.value || !editable.value) {
    return;
  }
  const node = workspace.value.graph.nodes.find(
    (candidate) => candidate.id === nodeId,
  );
  if (!node) {
    return;
  }
  const poi: Poi = {
    id: crypto.randomUUID(),
    code: nextCode(
      `P-${activeFloor.value.code}`,
      workspace.value.graph.pois,
    ),
    name: "新 POI",
    category: "department",
    floorId: activeFloor.value.id,
    nodeId,
    x: node.x,
    y: node.y,
    accessScope: "public",
    accessible: true,
    enabled: true,
    keywords: [],
  };
  workspace.value.graph.pois.push(poi);
  selection.value = { kind: "poi", id: poi.id };
  tool.value = "select";
  changed("POI 已绑定到节点");
}

function updateSelected(): void {
  changed();
}

async function save(): Promise<void> {
  if (!workspace.value || !dirty.value || !editable.value) {
    return;
  }
  busy.value = true;
  try {
    if (demoMode.value) {
      dirty.value = false;
      showToast("演示模式已在浏览器内保存，不写入后端");
      return;
    }
    const result = await client.saveWorkspace(
      workspace.value.release.id,
      etag.value,
      workspace.value.graph,
    );
    workspace.value = result.workspace;
    etag.value = result.etag;
    dirty.value = false;
    renderRevision.value += 1;
    showToast(`草稿修订 ${workspace.value.release.contentRevision} 已保存`);
  } catch (caught) {
    showToast(
      caught instanceof ApiError ? caught.message : "草稿保存失败。",
    );
  } finally {
    busy.value = false;
  }
}

function showToast(message: string): void {
  toast.value = message;
  window.setTimeout(() => {
    if (toast.value === message) {
      toast.value = "";
    }
  }, 2600);
}

onMounted(load);
</script>

<template>
  <div
    class="grid h-[100dvh] grid-rows-[56px_minmax(0,1fr)] bg-[#eef1f4] text-[#172033]"
  >
    <header
      class="flex items-center justify-between border-b border-[#d7dce2] bg-white px-4"
    >
      <div class="flex min-w-0 items-center gap-3">
        <span
          class="grid size-8 shrink-0 place-items-center rounded-md bg-[#087f8c] text-sm font-bold text-white"
          >M</span
        >
        <div class="min-w-0">
          <h1 class="truncate text-sm font-semibold">MedRoute 地图维护</h1>
          <p class="truncate text-xs text-[#667085]">
            {{ workspace?.building.name ?? "正在连接地图服务" }}
          </p>
        </div>
      </div>
      <div class="flex items-center gap-2">
        <span
          class="flex items-center gap-1.5 rounded-md border border-[#d7dce2] px-2 py-1 text-xs text-[#4b5563]"
        >
          <Cloud v-if="demoMode" :size="14" />
          <Server v-else :size="14" />
          {{ demoMode ? "演示数据" : apiBase }}
        </span>
        <button
          class="grid size-8 place-items-center rounded-md border border-[#d7dce2] bg-white text-[#4b5563] hover:bg-[#f3f5f7] active:translate-y-px disabled:opacity-50"
          type="button"
          title="重新加载"
          :disabled="busy"
          @click="load"
        >
          <RefreshCw :size="16" :class="{ 'animate-spin': busy }" />
          <span class="sr-only">重新加载</span>
        </button>
        <button
          class="flex h-8 items-center gap-1.5 whitespace-nowrap rounded-md bg-[#087f8c] px-3 text-xs font-semibold text-white hover:bg-[#066d77] active:translate-y-px disabled:cursor-not-allowed disabled:opacity-40"
          type="button"
          :disabled="!dirty || !editable"
          @click="save"
        >
          <Save :size="15" />
          保存草稿
        </button>
      </div>
    </header>

    <main
      v-if="workspace && activeFloor"
      class="grid min-h-0 grid-cols-[224px_minmax(0,1fr)_288px]"
    >
      <aside
        class="min-h-0 overflow-y-auto border-r border-[#d7dce2] bg-white"
        aria-label="地图编辑控制"
      >
        <section class="border-b border-[#e4e7eb] p-4">
          <p class="mb-2 text-xs font-semibold text-[#344054]">工作版本</p>
          <div class="rounded-md border border-[#d7dce2] bg-[#f8fafb] p-3">
            <div class="flex items-center justify-between gap-2">
              <strong class="truncate text-xs">{{ workspace.release.code }}</strong>
              <span
                class="rounded-sm bg-[#dff3f2] px-1.5 py-0.5 text-[11px] font-semibold text-[#066d77]"
                >草稿</span
              >
            </div>
            <p class="mt-1 text-[11px] leading-4 text-[#667085]">
              修订 {{ workspace.release.contentRevision }}
              <span v-if="dirty" class="font-semibold text-[#b45309]"> · 未保存</span>
            </p>
          </div>
        </section>

        <section class="border-b border-[#e4e7eb] p-4">
          <p class="mb-2 text-xs font-semibold text-[#344054]">楼层</p>
          <div class="grid grid-cols-3 gap-1">
            <button
              v-for="floor in workspace.floors"
              :key="floor.id"
              type="button"
              class="h-8 rounded-md border text-xs font-semibold active:translate-y-px"
              :class="
                floor.id === activeFloorId
                  ? 'border-[#087f8c] bg-[#e3f3f3] text-[#066d77]'
                  : 'border-[#d7dce2] bg-white text-[#4b5563] hover:bg-[#f3f5f7]'
              "
              @click="changeFloor(floor.id)"
            >
              {{ floor.code }}
            </button>
          </div>
        </section>

        <section class="border-b border-[#e4e7eb] p-4">
          <p class="mb-2 text-xs font-semibold text-[#344054]">标注工具</p>
          <div class="grid grid-cols-2 gap-1.5">
            <button
              v-for="item in tools"
              :key="item.id"
              type="button"
              class="flex h-9 items-center gap-2 rounded-md border px-2.5 text-xs font-medium active:translate-y-px disabled:opacity-40"
              :class="
                tool === item.id
                  ? 'border-[#087f8c] bg-[#087f8c] text-white'
                  : 'border-[#d7dce2] bg-white text-[#344054] hover:bg-[#f3f5f7]'
              "
              :disabled="item.id !== 'select' && !editable"
              :title="item.label"
              @click="chooseTool(item.id)"
            >
              <component :is="item.icon" :size="15" />
              {{ item.label }}
            </button>
          </div>
          <p class="mt-2 text-[11px] leading-4 text-[#667085]">
            <template v-if="tool === 'node'">点击地图空白处创建节点。</template>
            <template v-else-if="tool === 'edge'">
              {{ edgeStartId ? "点击第二个节点完成路径。" : "依次点击两个节点创建路径。" }}
            </template>
            <template v-else-if="tool === 'poi'">点击节点并将 POI 绑定到它。</template>
            <template v-else>点击元素查看属性；直接拖动节点调整位置。</template>
          </p>
        </section>

        <section class="p-4">
          <p class="mb-2 text-xs font-semibold text-[#344054]">本层数据</p>
          <dl class="grid grid-cols-2 gap-x-3 gap-y-2 text-xs">
            <div>
              <dt class="text-[#667085]">节点</dt>
              <dd class="mt-0.5 font-semibold">{{ currentGraph?.nodes.length }}</dd>
            </div>
            <div>
              <dt class="text-[#667085]">路径</dt>
              <dd class="mt-0.5 font-semibold">{{ currentGraph?.edges.length }}</dd>
            </div>
            <div>
              <dt class="text-[#667085]">POI</dt>
              <dd class="mt-0.5 font-semibold">{{ currentGraph?.pois.length }}</dd>
            </div>
            <div>
              <dt class="text-[#667085]">停靠节点</dt>
              <dd class="mt-0.5 font-semibold">{{ currentGraph?.stopNodeIds.size }}</dd>
            </div>
          </dl>
        </section>
      </aside>

      <section class="grid min-w-0 grid-rows-[48px_minmax(0,1fr)]">
        <div
          class="flex items-center justify-between border-b border-[#d7dce2] bg-[#f8fafb] px-4"
        >
          <div>
            <h2 class="text-sm font-semibold">
              {{ activeFloor.code }} · {{ activeFloor.name }}
            </h2>
            <p class="text-[11px] text-[#667085]">
              {{ activeFloor.mapRevision.imageWidth }} ×
              {{ activeFloor.mapRevision.imageHeight }} px
            </p>
          </div>
          <div class="flex items-center gap-1.5 text-[11px] text-[#667085]">
            <LocateFixed :size="14" />
            左上角像素坐标
          </div>
        </div>
        <div class="relative min-h-0 overflow-hidden">
          <FloorMapEditor
            :floor="activeFloor"
            :graph="workspace.graph"
            :image-url="imageUrl"
            :selection="selection"
            :tool="tool"
            :edge-start-id="edgeStartId"
            :editable="editable"
            :revision="renderRevision"
            @select="select"
            @add-node="addNode"
            @add-poi="addPoi"
            @choose-edge-node="chooseEdgeNode"
            @move-node="moveSelectedNode"
          />
          <span
            v-if="demoMode"
            class="pointer-events-none absolute bottom-3 left-3 rounded-md border border-[#d7dce2] bg-white/95 px-2 py-1 text-[11px] text-[#667085]"
            >测试标注，未经现场核验</span
          >
        </div>
      </section>

      <aside
        class="min-h-0 overflow-y-auto border-l border-[#d7dce2] bg-white p-4"
        aria-label="元素属性"
      >
        <div class="mb-4 flex items-center justify-between">
          <h2 class="text-sm font-semibold">属性</h2>
          <span class="text-[11px] text-[#667085]">
            {{ selection ? selection.kind.toUpperCase() : "未选择" }}
          </span>
        </div>

        <form
          v-if="selectedNode"
          class="space-y-4"
          @change="updateSelected"
          @submit.prevent
        >
          <label class="block space-y-1.5">
            <span class="text-xs font-medium text-[#344054]">节点编号</span>
            <input
              v-model="selectedNode.code"
              class="h-9 w-full rounded-md border border-[#cfd5dc] px-2.5 text-xs outline-none focus:border-[#087f8c] focus:ring-2 focus:ring-[#b9dfe0]"
              :disabled="!editable"
            />
          </label>
          <label class="block space-y-1.5">
            <span class="text-xs font-medium text-[#344054]">节点类型</span>
            <select
              v-model="selectedNode.type"
              class="h-9 w-full rounded-md border border-[#cfd5dc] bg-white px-2.5 text-xs outline-none focus:border-[#087f8c]"
              :disabled="!editable"
            >
              <option value="normal">普通节点</option>
              <option value="decision">路口节点</option>
              <option value="poi_access">POI 到达节点</option>
              <option value="connector_stop">跨层停靠节点</option>
            </select>
          </label>
          <div class="grid grid-cols-2 gap-2">
            <label class="block space-y-1.5">
              <span class="text-xs font-medium text-[#344054]">X 坐标</span>
              <input
                :value="selectedNode.x"
                class="h-9 w-full rounded-md border border-[#d7dce2] bg-[#f3f5f7] px-2.5 text-xs"
                readonly
              />
            </label>
            <label class="block space-y-1.5">
              <span class="text-xs font-medium text-[#344054]">Y 坐标</span>
              <input
                :value="selectedNode.y"
                class="h-9 w-full rounded-md border border-[#d7dce2] bg-[#f3f5f7] px-2.5 text-xs"
                readonly
              />
            </label>
          </div>
          <label class="flex items-center gap-2 text-xs text-[#344054]">
            <input
              v-model="selectedNode.enabled"
              type="checkbox"
              :disabled="!editable"
              class="size-4 accent-[#087f8c]"
            />
            节点启用
          </label>
        </form>

        <form
          v-else-if="selectedEdge"
          class="space-y-4"
          @change="updateSelected"
          @submit.prevent
        >
          <label class="block space-y-1.5">
            <span class="text-xs font-medium text-[#344054]">路径编号</span>
            <input
              v-model="selectedEdge.code"
              class="h-9 w-full rounded-md border border-[#cfd5dc] px-2.5 text-xs outline-none focus:border-[#087f8c]"
              :disabled="!editable"
            />
          </label>
          <div class="grid grid-cols-2 gap-2">
            <label class="block space-y-1.5">
              <span class="text-xs font-medium text-[#344054]">距离（米）</span>
              <input
                v-model.number="selectedEdge.distanceMeters"
                type="number"
                min="0"
                step="0.1"
                class="h-9 w-full rounded-md border border-[#cfd5dc] px-2.5 text-xs outline-none focus:border-[#087f8c]"
                :disabled="!editable"
              />
            </label>
            <label class="block space-y-1.5">
              <span class="text-xs font-medium text-[#344054]">耗时（秒）</span>
              <input
                v-model.number="selectedEdge.timeSeconds"
                type="number"
                min="1"
                class="h-9 w-full rounded-md border border-[#cfd5dc] px-2.5 text-xs outline-none focus:border-[#087f8c]"
                :disabled="!editable"
              />
            </label>
          </div>
          <label class="block space-y-1.5">
            <span class="text-xs font-medium text-[#344054]">通行方向</span>
            <select
              v-model="selectedEdge.direction"
              class="h-9 w-full rounded-md border border-[#cfd5dc] bg-white px-2.5 text-xs"
              :disabled="!editable"
            >
              <option value="both">双向</option>
              <option value="forward">仅起点到终点</option>
              <option value="reverse">仅终点到起点</option>
            </select>
          </label>
          <label class="flex items-center gap-2 text-xs text-[#344054]">
            <input
              v-model="selectedEdge.accessible"
              type="checkbox"
              :disabled="!editable"
              class="size-4 accent-[#087f8c]"
            />
            无障碍可通行
          </label>
        </form>

        <form
          v-else-if="selectedPoi"
          class="space-y-4"
          @change="updateSelected"
          @submit.prevent
        >
          <label class="block space-y-1.5">
            <span class="text-xs font-medium text-[#344054]">POI 名称</span>
            <input
              v-model="selectedPoi.name"
              class="h-9 w-full rounded-md border border-[#cfd5dc] px-2.5 text-xs outline-none focus:border-[#087f8c]"
              :disabled="!editable"
            />
          </label>
          <label class="block space-y-1.5">
            <span class="text-xs font-medium text-[#344054]">分类</span>
            <select
              v-model="selectedPoi.category"
              class="h-9 w-full rounded-md border border-[#cfd5dc] bg-white px-2.5 text-xs"
              :disabled="!editable"
            >
              <option value="department">科室</option>
              <option value="entrance">入口</option>
              <option value="pharmacy">药房</option>
              <option value="laboratory">检验检查</option>
              <option value="service">服务设施</option>
            </select>
          </label>
          <p class="rounded-md bg-[#f3f5f7] p-2.5 text-[11px] leading-4 text-[#667085]">
            已绑定节点 {{ selectedPoi.nodeId }}
          </p>
        </form>

        <div
          v-else-if="selection?.kind === 'stop'"
          class="rounded-md border border-[#d7dce2] bg-[#f8fafb] p-3"
        >
          <strong class="text-xs">跨层设施停靠点</strong>
          <p class="mt-1 text-[11px] leading-4 text-[#667085]">
            当前阶段支持查看停靠点；新增设施和跨层连接继续使用原维护后台。
          </p>
        </div>

        <div
          v-else
          class="grid min-h-40 place-items-center border-y border-[#e4e7eb] py-8 text-center"
        >
          <div>
            <MousePointer2 class="mx-auto text-[#98a2b3]" :size="22" />
            <strong class="mt-3 block text-xs">未选择地图元素</strong>
            <p class="mt-1 text-[11px] leading-4 text-[#667085]">
              点击节点、路径或 POI 查看并修改属性。
            </p>
          </div>
        </div>
      </aside>
    </main>

    <main
      v-else
      class="grid min-h-0 place-items-center bg-[#eef1f4] p-8"
    >
      <section
        class="w-full max-w-md rounded-md border border-[#d7dce2] bg-white p-6"
      >
        <TriangleAlert class="text-[#b45309]" :size="24" />
        <h2 class="mt-4 text-base font-semibold">
          {{ busy ? "正在加载地图工作区" : "无法加载地图工作区" }}
        </h2>
        <p class="mt-2 text-sm leading-6 text-[#667085]">
          {{
            busy
              ? `正在连接 ${apiBase}`
              : error || "请确认后端服务和数据库已经启动。"
          }}
        </p>
        <div v-if="!busy" class="mt-5 flex gap-2">
          <button
            class="h-9 whitespace-nowrap rounded-md bg-[#087f8c] px-3 text-xs font-semibold text-white active:translate-y-px"
            type="button"
            @click="load"
          >
            重试连接
          </button>
          <button
            class="h-9 whitespace-nowrap rounded-md border border-[#cfd5dc] bg-white px-3 text-xs font-semibold text-[#344054] active:translate-y-px"
            type="button"
            @click="useDemo"
          >
            使用演示数据
          </button>
        </div>
      </section>
    </main>

    <div
      v-if="toast"
      class="fixed bottom-4 left-1/2 z-50 -translate-x-1/2 rounded-md bg-[#172033] px-3 py-2 text-xs font-medium text-white shadow-lg"
      role="status"
    >
      {{ toast }}
    </div>
  </div>
</template>
