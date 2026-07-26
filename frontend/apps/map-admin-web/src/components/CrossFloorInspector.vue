<script setup lang="ts">
import {
  linksForConnector,
  stopsForConnector,
  type Connector,
  type ConnectorStop,
  type DraftGraph,
  type Floor,
  type MapSelection,
  type VerticalLink,
} from "@medroute/map-core";
import {
  ArrowUpDown,
  Footprints,
  Link2,
  MapPin,
  MapPinPlus,
  Plus,
  Trash2,
} from "@lucide/vue";
import { computed } from "vue";

const props = defineProps<{
  graph: DraftGraph;
  floors: Floor[];
  activeFloorId: string;
  selection: MapSelection;
  editable: boolean;
}>();

const emit = defineEmits<{
  changed: [];
  select: [selection: MapSelection];
  switchFloor: [floorId: string];
  beginStop: [connectorId: string];
  createLink: [connectorId: string];
  rebindStop: [stopId: string, nodeId: string];
  deleteSelection: [];
}>();

const selectedStop = computed<ConnectorStop | null>(() =>
  props.selection.kind === "stop"
    ? props.graph.connectorStops.find(
        (stop) => stop.id === props.selection.id,
      ) ?? null
    : null,
);

const selectedLink = computed<VerticalLink | null>(() =>
  props.selection.kind === "link"
    ? props.graph.verticalLinks.find(
        (link) => link.id === props.selection.id,
      ) ?? null
    : null,
);

const connector = computed<Connector | null>(() => {
  const connectorId =
    props.selection.kind === "connector"
      ? props.selection.id
      : selectedStop.value?.connectorId ?? selectedLink.value?.connectorId;
  return (
    props.graph.connectors.find((item) => item.id === connectorId) ?? null
  );
});

const connectorStops = computed(() =>
  connector.value
    ? stopsForConnector(props.graph, connector.value.id, props.floors)
    : [],
);

const connectorLinks = computed(() =>
  connector.value
    ? linksForConnector(props.graph, connector.value.id)
    : [],
);

const stopNodes = computed(() =>
  selectedStop.value
    ? props.graph.nodes.filter(
        (node) => node.floorId === selectedStop.value?.floorId,
      )
    : [],
);

const stopLinks = computed(() =>
  selectedStop.value
    ? props.graph.verticalLinks.filter(
        (link) =>
          link.fromStopId === selectedStop.value?.id ||
          link.toStopId === selectedStop.value?.id,
      )
    : [],
);

function floorCode(floorId: string): string {
  return props.floors.find((floor) => floor.id === floorId)?.code ?? "?";
}

function stopById(stopId: string): ConnectorStop | undefined {
  return props.graph.connectorStops.find((stop) => stop.id === stopId);
}

function stopLabel(stopId: string): string {
  const stop = stopById(stopId);
  return stop ? `${floorCode(stop.floorId)} · ${stop.code}` : "停靠点缺失";
}

function linkLabel(link: VerticalLink): string {
  const from = stopById(link.fromStopId);
  const to = stopById(link.toStopId);
  return `${from ? floorCode(from.floorId) : "?"} ${
    link.direction === "both" ? "↔" : "→"
  } ${to ? floorCode(to.floorId) : "?"}`;
}

function chooseStop(stop: ConnectorStop): void {
  emit("switchFloor", stop.floorId);
  emit("select", { kind: "stop", id: stop.id });
}

function chooseLink(link: VerticalLink): void {
  emit("select", { kind: "link", id: link.id });
}

function handleNodeChange(event: Event): void {
  const select = event.target as HTMLSelectElement;
  const nodeId = select.value;
  if (selectedStop.value && nodeId) {
    emit("rebindStop", selectedStop.value.id, nodeId);
    select.value = selectedStop.value.nodeId;
  }
}
</script>

<template>
  <div v-if="connector">
    <form
      v-if="selection.kind === 'connector'"
      class="space-y-4"
      @change="emit('changed')"
      @submit.prevent
    >
      <div class="flex items-center gap-2 border-b border-[#e4e7eb] pb-3">
        <span
          class="grid size-8 place-items-center rounded-md bg-[#e3f3f3] text-[#066d77]"
        >
          <ArrowUpDown v-if="connector.type === 'elevator'" :size="17" />
          <Footprints v-else :size="17" />
        </span>
        <div class="min-w-0">
          <strong class="block truncate text-sm">{{ connector.name }}</strong>
          <span class="text-[11px] text-[#667085]">{{ connector.code }}</span>
        </div>
      </div>

      <label class="block space-y-1.5">
        <span class="text-xs font-medium text-[#344054]">设施名称</span>
        <input
          v-model="connector.name"
          maxlength="100"
          class="h-9 w-full rounded-md border border-[#cfd5dc] px-2.5 text-xs outline-none focus:border-[#087f8c] focus:ring-2 focus:ring-[#b9dfe0]"
          :disabled="!editable"
        />
      </label>
      <label class="block space-y-1.5">
        <span class="text-xs font-medium text-[#344054]">设施编码</span>
        <input
          v-model="connector.code"
          maxlength="80"
          class="h-9 w-full rounded-md border border-[#cfd5dc] px-2.5 text-xs outline-none focus:border-[#087f8c]"
          :disabled="!editable"
        />
      </label>
      <div class="grid grid-cols-2 gap-2">
        <label class="block space-y-1.5">
          <span class="text-xs font-medium text-[#344054]">设施类型</span>
          <select
            v-model="connector.type"
            class="h-9 w-full rounded-md border border-[#cfd5dc] bg-white px-2 text-xs"
            :disabled="!editable"
          >
            <option value="elevator">电梯</option>
            <option value="stairs">楼梯</option>
          </select>
        </label>
        <label class="block space-y-1.5">
          <span class="text-xs font-medium text-[#344054]">使用范围</span>
          <select
            v-model="connector.accessScope"
            class="h-9 w-full rounded-md border border-[#cfd5dc] bg-white px-2 text-xs"
            :disabled="!editable"
          >
            <option value="public">公共</option>
            <option value="staff">员工</option>
          </select>
        </label>
      </div>
      <label class="flex items-center gap-2 text-xs text-[#344054]">
        <input
          v-model="connector.accessible"
          type="checkbox"
          :disabled="!editable"
          class="size-4 accent-[#087f8c]"
        />
        可用于无障碍路线
      </label>
      <label class="flex items-center gap-2 text-xs text-[#344054]">
        <input
          v-model="connector.enabled"
          type="checkbox"
          :disabled="!editable"
          class="size-4 accent-[#087f8c]"
        />
        设施启用
      </label>
    </form>

    <form
      v-else-if="selectedStop"
      class="space-y-4"
      @change="emit('changed')"
      @submit.prevent
    >
      <div class="border-b border-[#e4e7eb] pb-3">
        <strong class="flex items-center gap-2 text-sm">
          <MapPin :size="16" class="text-[#087f8c]" />
          {{ floorCode(selectedStop.floorId) }} 停靠点
        </strong>
        <span class="mt-1 block text-[11px] text-[#667085]">
          {{ connector.name }}
        </span>
      </div>
      <label class="block space-y-1.5">
        <span class="text-xs font-medium text-[#344054]">停靠点编码</span>
        <input
          v-model="selectedStop.code"
          maxlength="80"
          class="h-9 w-full rounded-md border border-[#cfd5dc] px-2.5 text-xs outline-none focus:border-[#087f8c]"
          :disabled="!editable"
        />
      </label>
      <label class="block space-y-1.5">
        <span class="text-xs font-medium text-[#344054]">绑定路径节点</span>
        <select
          :value="selectedStop.nodeId"
          class="h-9 w-full rounded-md border border-[#cfd5dc] bg-white px-2 text-xs"
          :disabled="!editable"
          @change.stop="handleNodeChange"
        >
          <option
            v-for="node in stopNodes"
            :key="node.id"
            :value="node.id"
          >
            {{ node.code }}
          </option>
        </select>
      </label>
      <div>
        <div class="mb-2 flex items-center justify-between">
          <span class="text-xs font-medium text-[#344054]">相关跨层连接</span>
          <button
            class="grid size-7 place-items-center rounded-md border border-[#d7dce2] text-[#4b5563] active:translate-y-px disabled:opacity-40"
            type="button"
            title="新建跨层连接"
            :disabled="!editable"
            @click="emit('createLink', connector.id)"
          >
            <Plus :size="14" />
            <span class="sr-only">新建跨层连接</span>
          </button>
        </div>
        <div class="divide-y divide-[#e4e7eb] border-y border-[#e4e7eb]">
          <button
            v-for="link in stopLinks"
            :key="link.id"
            type="button"
            class="flex w-full items-center justify-between py-2 text-left text-xs hover:bg-[#f8fafb]"
            @click="chooseLink(link)"
          >
            <span>{{ linkLabel(link) }}</span>
            <Link2 :size="14" class="text-[#98a2b3]" />
          </button>
          <p
            v-if="!stopLinks.length"
            class="py-2 text-[11px] text-[#667085]"
          >
            尚未配置连接。
          </p>
        </div>
      </div>
    </form>

    <form
      v-else-if="selectedLink"
      class="space-y-4"
      @change="emit('changed')"
      @submit.prevent
    >
      <div class="border-b border-[#e4e7eb] pb-3">
        <strong class="flex items-center gap-2 text-sm">
          <Link2 :size="16" class="text-[#087f8c]" />
          {{ linkLabel(selectedLink) }}
        </strong>
        <span class="mt-1 block text-[11px] text-[#667085]">
          {{ connector.name }}
        </span>
      </div>
      <label class="block space-y-1.5">
        <span class="text-xs font-medium text-[#344054]">连接编码</span>
        <input
          v-model="selectedLink.code"
          maxlength="80"
          class="h-9 w-full rounded-md border border-[#cfd5dc] px-2.5 text-xs outline-none focus:border-[#087f8c]"
          :disabled="!editable"
        />
      </label>
      <div class="grid grid-cols-2 gap-2">
        <label class="block space-y-1.5">
          <span class="text-xs font-medium text-[#344054]">起始停靠点</span>
          <select
            v-model="selectedLink.fromStopId"
            class="h-9 w-full rounded-md border border-[#cfd5dc] bg-white px-1.5 text-[11px]"
            :disabled="!editable"
          >
            <option
              v-for="stop in connectorStops"
              :key="stop.id"
              :value="stop.id"
            >
              {{ floorCode(stop.floorId) }}
            </option>
          </select>
        </label>
        <label class="block space-y-1.5">
          <span class="text-xs font-medium text-[#344054]">目标停靠点</span>
          <select
            v-model="selectedLink.toStopId"
            class="h-9 w-full rounded-md border border-[#cfd5dc] bg-white px-1.5 text-[11px]"
            :disabled="!editable"
          >
            <option
              v-for="stop in connectorStops"
              :key="stop.id"
              :value="stop.id"
            >
              {{ floorCode(stop.floorId) }}
            </option>
          </select>
        </label>
      </div>
      <div class="grid grid-cols-2 gap-2">
        <label class="block space-y-1.5">
          <span class="text-xs font-medium text-[#344054]">耗时（秒）</span>
          <input
            v-model.number="selectedLink.timeSeconds"
            type="number"
            min="1"
            class="h-9 w-full rounded-md border border-[#cfd5dc] px-2.5 text-xs"
            :disabled="!editable"
          />
        </label>
        <label class="block space-y-1.5">
          <span class="text-xs font-medium text-[#344054]">距离（米）</span>
          <input
            v-model.number="selectedLink.distanceMeters"
            type="number"
            min="0"
            step="0.1"
            class="h-9 w-full rounded-md border border-[#cfd5dc] px-2.5 text-xs"
            :disabled="!editable"
          />
        </label>
      </div>
      <div class="grid grid-cols-2 gap-2">
        <label class="block space-y-1.5">
          <span class="text-xs font-medium text-[#344054]">方向</span>
          <select
            v-model="selectedLink.direction"
            class="h-9 w-full rounded-md border border-[#cfd5dc] bg-white px-2 text-xs"
            :disabled="!editable"
          >
            <option value="both">双向</option>
            <option value="forward">仅起点到终点</option>
          </select>
        </label>
        <label class="block space-y-1.5">
          <span class="text-xs font-medium text-[#344054]">使用范围</span>
          <select
            v-model="selectedLink.accessScope"
            class="h-9 w-full rounded-md border border-[#cfd5dc] bg-white px-2 text-xs"
            :disabled="!editable"
          >
            <option value="public">公共</option>
            <option value="staff">员工</option>
          </select>
        </label>
      </div>
      <label class="flex items-center gap-2 text-xs text-[#344054]">
        <input
          v-model="selectedLink.accessible"
          type="checkbox"
          :disabled="!editable"
          class="size-4 accent-[#087f8c]"
        />
        连接可用于无障碍路线
      </label>
      <label class="flex items-center gap-2 text-xs text-[#344054]">
        <input
          v-model="selectedLink.enabled"
          type="checkbox"
          :disabled="!editable"
          class="size-4 accent-[#087f8c]"
        />
        连接启用
      </label>
      <p class="rounded-md bg-[#f3f5f7] p-2.5 text-[11px] leading-4 text-[#667085]">
        {{ stopLabel(selectedLink.fromStopId) }} 到
        {{ stopLabel(selectedLink.toStopId) }}。不同电梯可以维护完全不同的连接组合。
      </p>
    </form>

    <template v-if="selection.kind === 'connector'">
      <div class="mt-5">
        <div class="mb-2 flex items-center justify-between">
          <h3 class="text-xs font-semibold text-[#344054]">停靠楼层</h3>
          <button
            class="flex h-7 items-center gap-1 rounded-md border border-[#d7dce2] px-2 text-[10px] font-medium text-[#344054] active:translate-y-px disabled:opacity-40"
            type="button"
            :disabled="!editable"
            @click="emit('beginStop', connector.id)"
          >
            <MapPinPlus :size="13" />
            标注当前层
          </button>
        </div>
        <div class="divide-y divide-[#e4e7eb] border-y border-[#e4e7eb]">
          <button
            v-for="stop in connectorStops"
            :key="stop.id"
            type="button"
            class="flex w-full items-center justify-between py-2 text-left text-xs hover:bg-[#f8fafb]"
            @click="chooseStop(stop)"
          >
            <span>
              <strong>{{ floorCode(stop.floorId) }}</strong>
              <span class="ml-1 text-[10px] text-[#667085]">{{ stop.code }}</span>
            </span>
            <MapPin :size="14" class="text-[#087f8c]" />
          </button>
          <p
            v-if="!connectorStops.length"
            class="py-2 text-[11px] text-[#667085]"
          >
            尚未标注任何楼层。
          </p>
        </div>
      </div>

      <div class="mt-5">
        <div class="mb-2 flex items-center justify-between">
          <h3 class="text-xs font-semibold text-[#344054]">明确跨层连接</h3>
          <button
            class="flex h-7 items-center gap-1 rounded-md border border-[#d7dce2] px-2 text-[10px] font-medium text-[#344054] active:translate-y-px disabled:opacity-40"
            type="button"
            :disabled="!editable"
            @click="emit('createLink', connector.id)"
          >
            <Plus :size="13" />
            新增连接
          </button>
        </div>
        <div class="divide-y divide-[#e4e7eb] border-y border-[#e4e7eb]">
          <button
            v-for="link in connectorLinks"
            :key="link.id"
            type="button"
            class="flex w-full items-center justify-between py-2 text-left text-xs hover:bg-[#f8fafb]"
            @click="chooseLink(link)"
          >
            <span>{{ linkLabel(link) }}</span>
            <span class="text-[10px] text-[#667085]">
              {{ link.timeSeconds }} 秒
            </span>
          </button>
          <p
            v-if="!connectorLinks.length"
            class="py-2 text-[11px] text-[#667085]"
          >
            尚未配置任何连接。
          </p>
        </div>
      </div>
    </template>

    <button
      class="mt-6 flex h-9 w-full items-center justify-center gap-1.5 rounded-md border border-[#fecaca] bg-white text-xs font-semibold text-[#b91c1c] hover:bg-[#fef2f2] active:translate-y-px disabled:opacity-40"
      type="button"
      :disabled="!editable"
      @click="emit('deleteSelection')"
    >
      <Trash2 :size="15" />
      {{
        selection.kind === "connector"
          ? "删除设施及其配置"
          : selection.kind === "stop"
            ? "删除停靠点"
            : "删除跨层连接"
      }}
    </button>
  </div>
</template>
