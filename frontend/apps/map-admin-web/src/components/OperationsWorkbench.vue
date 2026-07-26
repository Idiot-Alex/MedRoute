<script setup lang="ts">
import type {
  CreateOperationClosurePayload,
  OperationClosure,
  OperationClosureListResponse,
  OperationTarget,
  OperationTargetType,
  NavigationPoi,
} from "@medroute/map-core";
import {
  LocateFixed,
  QrCode,
  RefreshCw,
  RotateCcw,
  ShieldAlert,
} from "@lucide/vue";
import { computed, ref, watch } from "vue";

const props = defineProps<{
  operations: OperationClosureListResponse | null;
  pois: NavigationPoi[];
  busy: boolean;
}>();

const emit = defineEmits<{
  create: [payload: CreateOperationClosurePayload];
  revoke: [closureId: string];
  refresh: [];
  navigate: [targetType: OperationTargetType, targetId: string];
  qrCode: [poi: NavigationPoi];
}>();

const targetType = ref<OperationTargetType>("path_edge");
const targetId = ref("");
const reason = ref("");
const qrPoiId = ref("");

const activeTargetKeys = computed(
  () =>
    new Set(
      (props.operations?.items ?? []).map(
        (item) => `${item.targetType}:${item.targetId}`,
      ),
    ),
);

const availableTargets = computed(() =>
  (props.operations?.targets ?? []).filter(
    (target) =>
      target.targetType === targetType.value &&
      !activeTargetKeys.value.has(`${target.targetType}:${target.id}`),
  ),
);

watch(
  availableTargets,
  (targets) => {
    if (!targets.some((target) => target.id === targetId.value)) {
      targetId.value = targets[0]?.id ?? "";
    }
  },
  { immediate: true },
);

watch(
  () => props.pois,
  (pois) => {
    if (!pois.some((poi) => poi.id === qrPoiId.value)) {
      qrPoiId.value = pois[0]?.id ?? "";
    }
  },
  { immediate: true },
);

watch(
  () => props.operations?.items.map((item) => item.id).join(","),
  (nextIds, previousIds) => {
    if (
      previousIds !== undefined &&
      (nextIds?.split(",").filter(Boolean).length ?? 0) >
        previousIds.split(",").filter(Boolean).length
    ) {
      reason.value = "";
    }
  },
);

function submit(): void {
  const normalizedReason = reason.value.trim();
  if (!targetId.value || !normalizedReason) {
    return;
  }
  emit("create", {
    targetType: targetType.value,
    targetId: targetId.value,
    effectiveFrom: null,
    effectiveTo: null,
    reason: normalizedReason,
  });
}

function targetForClosure(
  closure: OperationClosure,
): OperationTarget | null {
  return (
    props.operations?.targets.find(
      (target) =>
        target.targetType === closure.targetType &&
        target.id === closure.targetId,
    ) ?? null
  );
}

function targetTypeLabel(type: OperationTargetType): string {
  return {
    path_edge: "楼层路径",
    vertical_connector: "整部设施",
    vertical_link: "跨层连接",
  }[type];
}

function targetOptionLabel(target: OperationTarget): string {
  const floor = target.floorCode ? `${target.floorCode} · ` : "";
  const code =
    target.name.toLocaleLowerCase() === target.code.toLocaleLowerCase()
      ? ""
      : `（${target.code}）`;
  return `${floor}${target.name}${code}`;
}

function formatTime(value: string): string {
  return new Intl.DateTimeFormat("zh-CN", {
    month: "numeric",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(value));
}
</script>

<template>
  <div>
    <div class="flex items-start justify-between gap-3">
      <div>
        <h2 class="text-sm font-semibold">临时运营封闭</h2>
        <p class="mt-1 text-[11px] text-[#667085]">
          当前发布版本 · {{ operations?.releaseCode ?? "未加载" }}
        </p>
      </div>
      <button
        class="grid size-8 shrink-0 place-items-center rounded-md border border-[#d7dce2] bg-white text-[#4b5563] hover:bg-[#f3f5f7] active:translate-y-px disabled:opacity-40"
        type="button"
        title="刷新运营状态"
        :disabled="busy"
        @click="emit('refresh')"
      >
        <RefreshCw :size="15" :class="{ 'animate-spin': busy }" />
        <span class="sr-only">刷新运营状态</span>
      </button>
    </div>

    <div
      class="mt-4 flex gap-2 border-y border-[#fde68a] bg-[#fffbeb] px-3 py-2.5 text-[11px] leading-4 text-[#92400e]"
    >
      <ShieldAlert class="mt-0.5 shrink-0" :size="15" />
      封闭立即影响新计算的路线，不修改地图草稿，也不需要重新发布。
    </div>

    <form class="mt-4 space-y-3" @submit.prevent="submit">
      <label class="block space-y-1.5">
        <span class="text-xs font-medium text-[#344054]">对象类型</span>
        <select
          v-model="targetType"
          class="h-9 w-full rounded-md border border-[#cfd5dc] bg-white px-2.5 text-xs outline-none focus:border-[#087f8c]"
          :disabled="busy || !operations"
        >
          <option value="path_edge">楼层路径</option>
          <option value="vertical_connector">整部电梯或楼梯</option>
          <option value="vertical_link">指定楼层间连接</option>
        </select>
      </label>
      <label class="block space-y-1.5">
        <span class="text-xs font-medium text-[#344054]">封闭对象</span>
        <select
          v-model="targetId"
          class="h-9 w-full rounded-md border border-[#cfd5dc] bg-white px-2.5 text-xs outline-none focus:border-[#087f8c]"
          :disabled="busy || !availableTargets.length"
        >
          <option
            v-for="target in availableTargets"
            :key="target.id"
            :value="target.id"
          >
            {{ targetOptionLabel(target) }}
          </option>
        </select>
        <span
          v-if="operations && !availableTargets.length"
          class="block text-[11px] text-[#b45309]"
        >
          此类型没有可继续封闭的对象。
        </span>
      </label>
      <label class="block space-y-1.5">
        <span class="text-xs font-medium text-[#344054]">封闭原因</span>
        <textarea
          v-model="reason"
          maxlength="500"
          rows="3"
          placeholder="例如：A 电梯年度检修"
          class="w-full resize-none rounded-md border border-[#cfd5dc] px-2.5 py-2 text-xs leading-5 outline-none focus:border-[#087f8c] focus:ring-2 focus:ring-[#b9dfe0]"
          :disabled="busy || !operations"
        ></textarea>
      </label>
      <button
        class="flex h-9 w-full items-center justify-center gap-1.5 rounded-md bg-[#b42318] text-xs font-semibold text-white hover:bg-[#912018] active:translate-y-px disabled:cursor-not-allowed disabled:opacity-40"
        type="submit"
        :disabled="busy || !targetId || !reason.trim()"
      >
        <ShieldAlert :size="15" />
        立即停用
      </button>
    </form>

    <section class="mt-6">
      <div class="flex items-center justify-between">
        <h3 class="text-xs font-semibold text-[#344054]">当前封闭</h3>
        <span class="text-[11px] text-[#667085]">
          {{ operations?.items.length ?? 0 }} 项
        </span>
      </div>

      <div
        v-if="!operations?.items.length"
        class="mt-2 border-y border-[#e4e7eb] py-6 text-center text-[11px] text-[#667085]"
      >
        当前没有临时封闭
      </div>

      <ul v-else class="mt-2 divide-y divide-[#e4e7eb] border-y border-[#e4e7eb]">
        <li
          v-for="closure in operations.items"
          :key="closure.id"
          class="py-3"
        >
          <div class="flex items-start justify-between gap-2">
            <button
              class="min-w-0 text-left hover:text-[#066d77]"
              type="button"
              :title="`在地图中定位${closure.targetName}`"
              @click="emit('navigate', closure.targetType, closure.targetId)"
            >
              <strong class="block truncate text-xs">
                {{ closure.targetName }}
              </strong>
              <span class="mt-0.5 flex items-center gap-1 text-[10px] text-[#667085]">
                <LocateFixed :size="11" />
                {{ targetForClosure(closure)?.floorCode
                  ? `${targetForClosure(closure)?.floorCode} · `
                  : ""
                }}{{ targetTypeLabel(closure.targetType) }} ·
                {{ closure.targetCode }}
              </span>
            </button>
            <button
              class="flex h-7 shrink-0 items-center gap-1 rounded-md border border-[#b9dfe0] px-2 text-[10px] font-semibold text-[#066d77] hover:bg-[#e3f3f3] active:translate-y-px disabled:opacity-40"
              type="button"
              :disabled="busy"
              @click="emit('revoke', closure.id)"
            >
              <RotateCcw :size="12" />
              恢复
            </button>
          </div>
          <p class="mt-2 text-[11px] leading-4 text-[#344054]">
            {{ closure.reason }}
          </p>
          <p class="mt-1 text-[10px] text-[#98a2b3]">
            {{ formatTime(closure.effectiveFrom) }} 起 ·
            {{
              closure.effectiveTo
                ? `${formatTime(closure.effectiveTo)} 自动恢复`
                : "持续至手动恢复"
            }}
          </p>
        </li>
      </ul>
    </section>

    <section class="mt-6 border-t border-[#e4e7eb] pt-5">
      <h3 class="text-xs font-semibold text-[#344054]">固定点导航二维码</h3>
      <p class="mt-1 text-[11px] leading-4 text-[#667085]">
        仅使用当前发布版本的稳定 POI 编码，适合入口、服务台和科室门口张贴。
      </p>
      <label class="mt-3 block space-y-1.5">
        <span class="text-xs font-medium text-[#344054]">固定起点</span>
        <select
          v-model="qrPoiId"
          class="h-9 w-full rounded-md border border-[#cfd5dc] bg-white px-2.5 text-xs outline-none focus:border-[#087f8c]"
          :disabled="busy || !pois.length"
        >
          <option v-for="poi in pois" :key="poi.id" :value="poi.id">
            {{ poi.floorCode }} · {{ poi.name }}（{{ poi.code }}）
          </option>
        </select>
      </label>
      <button
        class="mt-3 flex h-9 w-full items-center justify-center gap-1.5 rounded-md border border-[#b9dfe0] text-xs font-semibold text-[#066d77] hover:bg-[#e3f3f3] active:translate-y-px disabled:opacity-40"
        type="button"
        :disabled="busy || !qrPoiId"
        @click="
          emit(
            'qrCode',
            pois.find((poi) => poi.id === qrPoiId)!,
          )
        "
      >
        <QrCode :size="15" />
        生成二维码
      </button>
    </section>
  </div>
</template>
