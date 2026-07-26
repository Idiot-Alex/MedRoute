<script setup lang="ts">
import type {
  DraftGraph,
  Floor,
  Poi,
  RouteMode,
  RouteRegressionCase,
  RouteRegressionCasePayload,
} from "@medroute/map-core";
import { Route } from "@lucide/vue";
import { computed, reactive, ref } from "vue";

const props = defineProps<{
  graph: DraftGraph;
  floors: Floor[];
}>();

const emit = defineEmits<{
  save: [caseId: string | null, payload: RouteRegressionCasePayload];
}>();

const dialog = ref<HTMLDialogElement | null>(null);
const editingId = ref<string | null>(null);
const formError = ref("");
const maxDistance = ref<string | number>("");
const maxSeconds = ref<string | number>("");
const draft = reactive({
  code: "",
  name: "",
  startPoiCode: "",
  endPoiCode: "",
  routeMode: "normal" as RouteMode,
  critical: true,
  enabled: true,
});

const publicPois = computed(() =>
  props.graph.pois.filter(
    (poi) => poi.enabled && poi.accessScope === "public",
  ),
);

function floorCode(floorId: string): string {
  return props.floors.find((floor) => floor.id === floorId)?.code ?? "?";
}

function poiLabel(poi: Poi): string {
  return `${floorCode(poi.floorId)} · ${poi.name}（${poi.code}）`;
}

function open(regressionCase: RouteRegressionCase | null = null): void {
  const entrance =
    publicPois.value.find((poi) => poi.category === "entrance") ??
    publicPois.value[0];
  const destination = publicPois.value.find(
    (poi) => poi.code !== entrance?.code,
  );
  editingId.value = regressionCase?.id ?? null;
  draft.code = regressionCase?.code ?? "";
  draft.name = regressionCase?.name ?? "";
  draft.startPoiCode = regressionCase?.startPoiCode ?? entrance?.code ?? "";
  draft.endPoiCode = regressionCase?.endPoiCode ?? destination?.code ?? "";
  draft.routeMode = regressionCase?.routeMode ?? "normal";
  draft.critical = regressionCase?.critical ?? true;
  draft.enabled = regressionCase?.enabled ?? true;
  maxDistance.value =
    regressionCase?.maxDistanceMeters != null
      ? String(regressionCase.maxDistanceMeters)
      : "";
  maxSeconds.value =
    regressionCase?.maxEstimatedSeconds != null
      ? String(regressionCase.maxEstimatedSeconds)
      : "";
  formError.value = "";
  dialog.value?.showModal();
}

function close(): void {
  dialog.value?.close();
}

function optionalNumber(value: string | number): number | null {
  if (!String(value).trim()) {
    return null;
  }
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

function submit(): void {
  formError.value = "";
  if (!draft.code.trim() || !draft.name.trim()) {
    formError.value = "路线名称和编码不能为空。";
    return;
  }
  if (!draft.startPoiCode || !draft.endPoiCode) {
    formError.value = "请选择起点和目的地。";
    return;
  }
  if (draft.startPoiCode === draft.endPoiCode) {
    formError.value = "起点和目的地不能相同。";
    return;
  }
  const maxDistanceMeters = optionalNumber(maxDistance.value);
  const maxEstimatedSeconds = optionalNumber(maxSeconds.value);
  if (
    (maxDistanceMeters != null && maxDistanceMeters <= 0) ||
    (maxEstimatedSeconds != null && maxEstimatedSeconds < 1)
  ) {
    formError.value = "距离和耗时上限必须大于零。";
    return;
  }
  emit("save", editingId.value, {
    code: draft.code.trim().toUpperCase(),
    name: draft.name.trim(),
    startPoiCode: draft.startPoiCode,
    endPoiCode: draft.endPoiCode,
    routeMode: draft.routeMode,
    critical: draft.critical,
    enabled: draft.enabled,
    maxDistanceMeters,
    maxEstimatedSeconds:
      maxEstimatedSeconds == null ? null : Math.round(maxEstimatedSeconds),
  });
}

defineExpose({ open, close });
</script>

<template>
  <dialog
    ref="dialog"
    class="m-auto w-[520px] rounded-md border border-[#cfd5dc] bg-white p-0 text-[#172033] shadow-xl"
  >
    <form class="p-5" @submit.prevent="submit">
      <div class="flex items-start justify-between gap-3">
        <div>
          <h2 class="text-base font-semibold">
            {{ editingId ? "编辑关键路线" : "新增关键路线" }}
          </h2>
          <p class="mt-1 text-xs text-[#667085]">稳定 POI 编码回归检查</p>
        </div>
        <Route class="text-[#087f8c]" :size="22" />
      </div>

      <div class="mt-5 grid grid-cols-2 gap-3">
        <label class="block space-y-1.5">
          <span class="text-xs font-medium text-[#344054]">路线名称</span>
          <input
            v-model="draft.name"
            required
            autofocus
            maxlength="120"
            class="h-9 w-full rounded-md border border-[#cfd5dc] px-2.5 text-xs outline-none focus:border-[#087f8c] focus:ring-2 focus:ring-[#b9dfe0]"
          />
        </label>
        <label class="block space-y-1.5">
          <span class="text-xs font-medium text-[#344054]">路线编码</span>
          <input
            v-model="draft.code"
            required
            maxlength="80"
            class="h-9 w-full rounded-md border border-[#cfd5dc] px-2.5 text-xs uppercase outline-none focus:border-[#087f8c]"
          />
        </label>
        <label class="block space-y-1.5">
          <span class="text-xs font-medium text-[#344054]">起点 POI</span>
          <select
            v-model="draft.startPoiCode"
            required
            class="h-9 w-full rounded-md border border-[#cfd5dc] bg-white px-2 text-xs"
          >
            <option
              v-for="poi in publicPois"
              :key="poi.id"
              :value="poi.code"
            >
              {{ poiLabel(poi) }}
            </option>
          </select>
        </label>
        <label class="block space-y-1.5">
          <span class="text-xs font-medium text-[#344054]">目的地 POI</span>
          <select
            v-model="draft.endPoiCode"
            required
            class="h-9 w-full rounded-md border border-[#cfd5dc] bg-white px-2 text-xs"
          >
            <option
              v-for="poi in publicPois"
              :key="poi.id"
              :value="poi.code"
            >
              {{ poiLabel(poi) }}
            </option>
          </select>
        </label>
        <label class="block space-y-1.5">
          <span class="text-xs font-medium text-[#344054]">路线模式</span>
          <select
            v-model="draft.routeMode"
            class="h-9 w-full rounded-md border border-[#cfd5dc] bg-white px-2 text-xs"
          >
            <option value="normal">常规路线</option>
            <option value="accessible">无障碍路线</option>
          </select>
        </label>
        <div></div>
        <label class="block space-y-1.5">
          <span class="text-xs font-medium text-[#344054]">
            最大距离（米）
          </span>
          <input
            v-model="maxDistance"
            type="number"
            min="0.01"
            step="0.01"
            placeholder="不限制"
            class="h-9 w-full rounded-md border border-[#cfd5dc] px-2.5 text-xs outline-none focus:border-[#087f8c]"
          />
        </label>
        <label class="block space-y-1.5">
          <span class="text-xs font-medium text-[#344054]">
            最大耗时（秒）
          </span>
          <input
            v-model="maxSeconds"
            type="number"
            min="1"
            step="1"
            placeholder="不限制"
            class="h-9 w-full rounded-md border border-[#cfd5dc] px-2.5 text-xs outline-none focus:border-[#087f8c]"
          />
        </label>
        <label class="flex items-center gap-2 text-xs text-[#344054]">
          <input
            v-model="draft.critical"
            type="checkbox"
            class="size-4 accent-[#087f8c]"
          />
          失败时阻止发布
        </label>
        <label class="flex items-center gap-2 text-xs text-[#344054]">
          <input
            v-model="draft.enabled"
            type="checkbox"
            class="size-4 accent-[#087f8c]"
          />
          启用此路线
        </label>
      </div>

      <p
        v-if="formError"
        class="mt-4 border-y border-[#fecaca] bg-[#fef2f2] px-2.5 py-2 text-xs text-[#b91c1c]"
        role="alert"
      >
        {{ formError }}
      </p>

      <div class="mt-6 flex justify-end gap-2">
        <button
          class="h-9 rounded-md border border-[#cfd5dc] bg-white px-3 text-xs font-semibold text-[#344054]"
          type="button"
          @click="close"
        >
          取消
        </button>
        <button
          class="h-9 rounded-md bg-[#087f8c] px-3 text-xs font-semibold text-white"
          type="submit"
        >
          保存路线
        </button>
      </div>
    </form>
  </dialog>
</template>
