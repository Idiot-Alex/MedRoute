<script setup lang="ts">
import {
  connectorIssueCounts,
  stopsForConnector,
  type ConnectorIssue,
  type DraftGraph,
  type Floor,
} from "@medroute/map-core";
import {
  ArrowUpDown,
  CircleAlert,
  Footprints,
  Link2,
  MapPinPlus,
  Plus,
} from "@lucide/vue";

const props = defineProps<{
  graph: DraftGraph;
  floors: Floor[];
  activeFloorId: string;
  selectedConnectorId: string | null;
  issues: ConnectorIssue[];
  editable: boolean;
}>();

const emit = defineEmits<{
  createConnector: [];
  selectConnector: [connectorId: string];
  beginStop: [connectorId: string];
  createLink: [connectorId: string];
}>();

function floorCodes(connectorId: string): string {
  const codes = stopsForConnector(
    props.graph,
    connectorId,
    props.floors,
  ).map(
    (stop) =>
      props.floors.find((floor) => floor.id === stop.floorId)?.code ?? "?",
  );
  return codes.length ? codes.join("、") : "尚未标注";
}

function issueCount(connectorId: string): number {
  return connectorIssueCounts(
    props.issues,
    connectorId,
    props.graph,
  ).errors;
}

function hasCurrentFloorStop(connectorId: string): boolean {
  return props.graph.connectorStops.some(
    (stop) =>
      stop.connectorId === connectorId &&
      stop.floorId === props.activeFloorId,
  );
}
</script>

<template>
  <section class="border-b border-[#e4e7eb] p-4">
    <div class="mb-2 flex items-center justify-between gap-2">
      <p class="text-xs font-semibold text-[#344054]">跨层设施</p>
      <button
        class="grid size-7 place-items-center rounded-md border border-[#d7dce2] bg-white text-[#4b5563] hover:bg-[#f3f5f7] active:translate-y-px disabled:opacity-40"
        type="button"
        title="新建电梯或楼梯"
        :disabled="!editable"
        @click="emit('createConnector')"
      >
        <Plus :size="15" />
        <span class="sr-only">新建电梯或楼梯</span>
      </button>
    </div>

    <div v-if="graph.connectors.length" class="space-y-1.5">
      <button
        v-for="connector in graph.connectors"
        :key="connector.id"
        type="button"
        class="grid w-full grid-cols-[28px_minmax(0,1fr)_auto] items-center gap-2 rounded-md border p-2 text-left active:translate-y-px"
        :class="
          connector.id === selectedConnectorId
            ? 'border-[#087f8c] bg-[#edf8f7]'
            : 'border-[#d7dce2] bg-white hover:bg-[#f8fafb]'
        "
        @click="emit('selectConnector', connector.id)"
      >
        <span
          class="grid size-7 place-items-center rounded-md bg-[#e3f3f3] text-[#066d77]"
        >
          <ArrowUpDown v-if="connector.type === 'elevator'" :size="15" />
          <Footprints v-else :size="15" />
        </span>
        <span class="min-w-0">
          <strong class="block truncate text-xs">{{ connector.name }}</strong>
          <span class="mt-0.5 block truncate text-[10px] text-[#667085]">
            {{ floorCodes(connector.id) }}
          </span>
        </span>
        <span
          v-if="issueCount(connector.id)"
          class="flex items-center gap-0.5 text-[10px] font-semibold text-[#b91c1c]"
        >
          <CircleAlert :size="12" />
          {{ issueCount(connector.id) }}
        </span>
      </button>
    </div>

    <p
      v-else
      class="border-y border-[#e4e7eb] py-3 text-[11px] leading-4 text-[#667085]"
    >
      尚未配置电梯或楼梯。
    </p>

    <div v-if="selectedConnectorId" class="mt-2 grid grid-cols-2 gap-1.5">
      <button
        class="flex h-8 items-center justify-center gap-1.5 whitespace-nowrap rounded-md border border-[#cfd5dc] bg-white px-2 text-[11px] font-medium text-[#344054] hover:bg-[#f3f5f7] active:translate-y-px disabled:opacity-40"
        type="button"
        :disabled="
          !editable || hasCurrentFloorStop(selectedConnectorId)
        "
        :title="
          hasCurrentFloorStop(selectedConnectorId)
            ? '该设施在本层已有停靠点'
            : '在当前楼层绑定停靠节点'
        "
        @click="emit('beginStop', selectedConnectorId)"
      >
        <MapPinPlus :size="14" />
        {{
          hasCurrentFloorStop(selectedConnectorId)
            ? "本层已标注"
            : "标注本层"
        }}
      </button>
      <button
        class="flex h-8 items-center justify-center gap-1.5 whitespace-nowrap rounded-md border border-[#cfd5dc] bg-white px-2 text-[11px] font-medium text-[#344054] hover:bg-[#f3f5f7] active:translate-y-px disabled:opacity-40"
        type="button"
        :disabled="!editable"
        title="新建明确的楼层间连接"
        @click="emit('createLink', selectedConnectorId)"
      >
        <Link2 :size="14" />
        配置连接
      </button>
    </div>
  </section>
</template>
