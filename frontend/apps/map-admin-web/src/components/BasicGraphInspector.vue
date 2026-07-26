<script setup lang="ts">
import type {
  DraftGraph,
  GraphDependency,
  MapSelection,
  PathEdge,
  PathNode,
  Poi,
} from "@medroute/map-core";
import {
  ArrowUpRight,
  QrCode,
  Trash2,
} from "@lucide/vue";
import { computed } from "vue";

const props = defineProps<{
  graph: DraftGraph;
  selection: MapSelection;
  editable: boolean;
  dependencies: GraphDependency[];
  qrCodeAllowed: boolean;
}>();

const emit = defineEmits<{
  changed: [];
  moveNode: [axis: "x" | "y", value: number];
  movePoi: [axis: "x" | "y", value: number];
  rebindEdge: [endpoint: "from" | "to", nodeId: string];
  rebindPoi: [nodeId: string];
  updateKeywords: [value: string];
  deleteSelection: [];
  navigateDependency: [kind: GraphDependency["kind"], id: string];
  openQrCode: [];
}>();

const selectedNode = computed<PathNode | null>(() =>
  props.selection.kind === "node"
    ? props.graph.nodes.find((item) => item.id === props.selection.id) ?? null
    : null,
);

const selectedEdge = computed<PathEdge | null>(() =>
  props.selection.kind === "edge"
    ? props.graph.edges.find((item) => item.id === props.selection.id) ?? null
    : null,
);

const selectedPoi = computed<Poi | null>(() =>
  props.selection.kind === "poi"
    ? props.graph.pois.find((item) => item.id === props.selection.id) ?? null
    : null,
);

const floorNodes = computed(() => {
  const floorId =
    selectedNode.value?.floorId ??
    selectedEdge.value?.floorId ??
    selectedPoi.value?.floorId;
  return props.graph.nodes
    .filter((node) => node.floorId === floorId)
    .sort((left, right) => left.code.localeCompare(right.code));
});

function numberValue(event: Event): number | null {
  const value = Number((event.target as HTMLInputElement).value);
  return Number.isFinite(value) ? value : null;
}

function moveNode(axis: "x" | "y", event: Event): void {
  const value = numberValue(event);
  if (value !== null) {
    emit("moveNode", axis, value);
  }
}

function movePoi(axis: "x" | "y", event: Event): void {
  const value = numberValue(event);
  if (value !== null) {
    emit("movePoi", axis, value);
  }
}

function rebindEdge(endpoint: "from" | "to", event: Event): void {
  const select = event.target as HTMLSelectElement;
  emit("rebindEdge", endpoint, select.value);
  select.value =
    endpoint === "from"
      ? selectedEdge.value?.fromNodeId ?? ""
      : selectedEdge.value?.toNodeId ?? "";
}

function rebindPoi(event: Event): void {
  const select = event.target as HTMLSelectElement;
  emit("rebindPoi", select.value);
  select.value = selectedPoi.value?.nodeId ?? "";
}

function updateKeywords(event: Event): void {
  emit("updateKeywords", (event.target as HTMLTextAreaElement).value);
}
</script>

<template>
  <form
    v-if="selectedNode"
    class="space-y-4"
    @change="emit('changed')"
    @submit.prevent
  >
    <label class="block space-y-1.5">
      <span class="text-xs font-medium text-[#344054]">节点编号</span>
      <input
        v-model.trim="selectedNode.code"
        required
        maxlength="80"
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
          type="number"
          min="0"
          step="0.1"
          class="h-9 w-full rounded-md border border-[#cfd5dc] px-2.5 text-xs outline-none focus:border-[#087f8c]"
          :disabled="!editable"
          @change.stop="moveNode('x', $event)"
        />
      </label>
      <label class="block space-y-1.5">
        <span class="text-xs font-medium text-[#344054]">Y 坐标</span>
        <input
          :value="selectedNode.y"
          type="number"
          min="0"
          step="0.1"
          class="h-9 w-full rounded-md border border-[#cfd5dc] px-2.5 text-xs outline-none focus:border-[#087f8c]"
          :disabled="!editable"
          @change.stop="moveNode('y', $event)"
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

    <div
      v-if="dependencies.length"
      class="border-y border-[#fde68a] bg-[#fffbeb] px-3 py-2.5"
    >
      <strong class="text-[11px] text-[#92400e]">
        当前节点有 {{ dependencies.length }} 个引用
      </strong>
      <ul class="mt-2 divide-y divide-[#fde68a]">
        <li
          v-for="dependency in dependencies"
          :key="`${dependency.kind}:${dependency.id}`"
          class="flex items-center justify-between gap-2 py-1.5"
        >
          <span class="min-w-0 truncate text-[10px] text-[#667085]">
            {{ dependency.relation }} · {{ dependency.label }}
          </span>
          <button
            class="grid size-7 shrink-0 place-items-center rounded-md text-[#92400e] hover:bg-[#fef3c7]"
            type="button"
            title="定位引用对象"
            @click="emit('navigateDependency', dependency.kind, dependency.id)"
          >
            <ArrowUpRight :size="13" />
            <span class="sr-only">定位引用对象</span>
          </button>
        </li>
      </ul>
    </div>

    <button
      class="flex h-9 w-full items-center justify-center gap-1.5 rounded-md border border-[#fca5a5] text-xs font-semibold text-[#b42318] hover:bg-[#fef2f2] disabled:cursor-not-allowed disabled:opacity-50"
      type="button"
      :disabled="!editable"
      @click="emit('deleteSelection')"
    >
      <Trash2 :size="14" />
      删除节点
    </button>
  </form>

  <form
    v-else-if="selectedEdge"
    class="space-y-4"
    @change="emit('changed')"
    @submit.prevent
  >
    <label class="block space-y-1.5">
      <span class="text-xs font-medium text-[#344054]">路径编号</span>
      <input
        v-model.trim="selectedEdge.code"
        required
        maxlength="80"
        class="h-9 w-full rounded-md border border-[#cfd5dc] px-2.5 text-xs outline-none focus:border-[#087f8c] focus:ring-2 focus:ring-[#b9dfe0]"
        :disabled="!editable"
      />
    </label>
    <div class="grid grid-cols-2 gap-2">
      <label class="block space-y-1.5">
        <span class="text-xs font-medium text-[#344054]">起点</span>
        <select
          :value="selectedEdge.fromNodeId"
          class="h-9 w-full rounded-md border border-[#cfd5dc] bg-white px-2 text-xs"
          :disabled="!editable"
          @change.stop="rebindEdge('from', $event)"
        >
          <option v-for="node in floorNodes" :key="node.id" :value="node.id">
            {{ node.code }}
          </option>
        </select>
      </label>
      <label class="block space-y-1.5">
        <span class="text-xs font-medium text-[#344054]">终点</span>
        <select
          :value="selectedEdge.toNodeId"
          class="h-9 w-full rounded-md border border-[#cfd5dc] bg-white px-2 text-xs"
          :disabled="!editable"
          @change.stop="rebindEdge('to', $event)"
        >
          <option v-for="node in floorNodes" :key="node.id" :value="node.id">
            {{ node.code }}
          </option>
        </select>
      </label>
    </div>
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
          step="1"
          class="h-9 w-full rounded-md border border-[#cfd5dc] px-2.5 text-xs outline-none focus:border-[#087f8c]"
          :disabled="!editable"
        />
      </label>
    </div>
    <div class="grid grid-cols-2 gap-2">
      <label class="block space-y-1.5">
        <span class="text-xs font-medium text-[#344054]">路径类型</span>
        <select
          v-model="selectedEdge.type"
          class="h-9 w-full rounded-md border border-[#cfd5dc] bg-white px-2 text-xs"
          :disabled="!editable"
        >
          <option value="walk">步行区</option>
          <option value="corridor">走廊</option>
          <option value="door">门</option>
          <option value="ramp">坡道</option>
          <option value="virtual">虚拟连接</option>
        </select>
      </label>
      <label class="block space-y-1.5">
        <span class="text-xs font-medium text-[#344054]">通行方向</span>
        <select
          v-model="selectedEdge.direction"
          class="h-9 w-full rounded-md border border-[#cfd5dc] bg-white px-2 text-xs"
          :disabled="!editable"
        >
          <option value="both">双向</option>
          <option value="forward">仅起点到终点</option>
        </select>
      </label>
    </div>
    <label class="block space-y-1.5">
      <span class="text-xs font-medium text-[#344054]">使用范围</span>
      <select
        v-model="selectedEdge.accessScope"
        class="h-9 w-full rounded-md border border-[#cfd5dc] bg-white px-2.5 text-xs"
        :disabled="!editable"
      >
        <option value="public">公共</option>
        <option value="staff">员工</option>
      </select>
    </label>
    <div class="grid grid-cols-2 gap-2">
      <label class="flex items-center gap-2 text-xs text-[#344054]">
        <input
          v-model="selectedEdge.accessible"
          type="checkbox"
          :disabled="!editable"
          class="size-4 accent-[#087f8c]"
        />
        无障碍
      </label>
      <label class="flex items-center gap-2 text-xs text-[#344054]">
        <input
          v-model="selectedEdge.enabled"
          type="checkbox"
          :disabled="!editable"
          class="size-4 accent-[#087f8c]"
        />
        路径启用
      </label>
    </div>
    <button
      class="flex h-9 w-full items-center justify-center gap-1.5 rounded-md border border-[#fca5a5] text-xs font-semibold text-[#b42318] hover:bg-[#fef2f2] disabled:cursor-not-allowed disabled:opacity-50"
      type="button"
      :disabled="!editable"
      @click="emit('deleteSelection')"
    >
      <Trash2 :size="14" />
      删除路径
    </button>
  </form>

  <form
    v-else-if="selectedPoi"
    class="space-y-4"
    @change="emit('changed')"
    @submit.prevent
  >
    <label class="block space-y-1.5">
      <span class="text-xs font-medium text-[#344054]">POI 名称</span>
      <input
        v-model.trim="selectedPoi.name"
        required
        maxlength="160"
        class="h-9 w-full rounded-md border border-[#cfd5dc] px-2.5 text-xs outline-none focus:border-[#087f8c] focus:ring-2 focus:ring-[#b9dfe0]"
        :disabled="!editable"
      />
    </label>
    <div class="grid grid-cols-2 gap-2">
      <label class="block space-y-1.5">
        <span class="text-xs font-medium text-[#344054]">POI 编号</span>
        <input
          v-model.trim="selectedPoi.code"
          required
          maxlength="80"
          class="h-9 w-full rounded-md border border-[#cfd5dc] px-2.5 text-xs outline-none focus:border-[#087f8c]"
          :disabled="!editable"
        />
      </label>
      <label class="block space-y-1.5">
        <span class="text-xs font-medium text-[#344054]">分类</span>
        <input
          v-model.trim="selectedPoi.category"
          list="medroute-poi-categories"
          required
          maxlength="80"
          class="h-9 w-full rounded-md border border-[#cfd5dc] px-2.5 text-xs outline-none focus:border-[#087f8c]"
          :disabled="!editable"
        />
        <datalist id="medroute-poi-categories">
          <option value="department">科室</option>
          <option value="entrance">入口</option>
          <option value="pharmacy">药房</option>
          <option value="laboratory">检验检查</option>
          <option value="service">服务设施</option>
        </datalist>
      </label>
    </div>
    <label class="block space-y-1.5">
      <span class="text-xs font-medium text-[#344054]">绑定路径节点</span>
      <select
        :value="selectedPoi.nodeId"
        class="h-9 w-full rounded-md border border-[#cfd5dc] bg-white px-2.5 text-xs"
        :disabled="!editable"
        @change.stop="rebindPoi"
      >
        <option v-for="node in floorNodes" :key="node.id" :value="node.id">
          {{ node.code }}
        </option>
      </select>
    </label>
    <div class="grid grid-cols-2 gap-2">
      <label class="block space-y-1.5">
        <span class="text-xs font-medium text-[#344054]">标注 X</span>
        <input
          :value="selectedPoi.x"
          type="number"
          min="0"
          step="0.1"
          class="h-9 w-full rounded-md border border-[#cfd5dc] px-2.5 text-xs outline-none focus:border-[#087f8c]"
          :disabled="!editable"
          @change.stop="movePoi('x', $event)"
        />
      </label>
      <label class="block space-y-1.5">
        <span class="text-xs font-medium text-[#344054]">标注 Y</span>
        <input
          :value="selectedPoi.y"
          type="number"
          min="0"
          step="0.1"
          class="h-9 w-full rounded-md border border-[#cfd5dc] px-2.5 text-xs outline-none focus:border-[#087f8c]"
          :disabled="!editable"
          @change.stop="movePoi('y', $event)"
        />
      </label>
    </div>
    <label class="block space-y-1.5">
      <span class="text-xs font-medium text-[#344054]">搜索词</span>
      <textarea
        :value="selectedPoi.keywords.join('，')"
        rows="2"
        maxlength="500"
        placeholder="例如：抽血，化验，检验科"
        class="min-h-16 w-full resize-y rounded-md border border-[#cfd5dc] px-2.5 py-2 text-xs leading-5 outline-none focus:border-[#087f8c]"
        :disabled="!editable"
        @change.stop="updateKeywords"
      ></textarea>
      <span class="block text-[10px] text-[#667085]">
        使用逗号或换行分隔；移动端搜索名称、编号和搜索词。
      </span>
    </label>
    <label class="block space-y-1.5">
      <span class="text-xs font-medium text-[#344054]">使用范围</span>
      <select
        v-model="selectedPoi.accessScope"
        class="h-9 w-full rounded-md border border-[#cfd5dc] bg-white px-2.5 text-xs"
        :disabled="!editable"
      >
        <option value="public">公共</option>
        <option value="staff">员工</option>
      </select>
    </label>
    <div class="grid grid-cols-2 gap-2">
      <label class="flex items-center gap-2 text-xs text-[#344054]">
        <input
          v-model="selectedPoi.accessible"
          type="checkbox"
          :disabled="!editable"
          class="size-4 accent-[#087f8c]"
        />
        无障碍可达
      </label>
      <label class="flex items-center gap-2 text-xs text-[#344054]">
        <input
          v-model="selectedPoi.enabled"
          type="checkbox"
          :disabled="!editable"
          class="size-4 accent-[#087f8c]"
        />
        POI 启用
      </label>
    </div>
    <div class="grid grid-cols-2 gap-2 border-t border-[#e4e7eb] pt-4">
      <button
        class="flex h-9 items-center justify-center gap-1.5 rounded-md border border-[#b9dfe0] text-xs font-semibold text-[#066d77] hover:bg-[#e3f3f3] disabled:cursor-not-allowed disabled:opacity-50"
        type="button"
        :disabled="!qrCodeAllowed"
        title="生成以此位置为导航起点的二维码"
        @click="emit('openQrCode')"
      >
        <QrCode :size="14" />
        固定点二维码
      </button>
      <button
        class="flex h-9 items-center justify-center gap-1.5 rounded-md border border-[#fca5a5] text-xs font-semibold text-[#b42318] hover:bg-[#fef2f2] disabled:cursor-not-allowed disabled:opacity-50"
        type="button"
        :disabled="!editable"
        @click="emit('deleteSelection')"
      >
        <Trash2 :size="14" />
        删除 POI
      </button>
    </div>
  </form>
</template>
