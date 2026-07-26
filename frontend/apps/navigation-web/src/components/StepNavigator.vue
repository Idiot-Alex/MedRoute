<script setup lang="ts">
import type { RouteStep } from "@medroute/map-core";
import {
  ArrowLeft,
  ArrowRight,
  Check,
  Map,
  Route,
  X,
} from "@lucide/vue";
import { computed } from "vue";

const props = defineProps<{
  step: RouteStep;
  index: number;
  total: number;
  floorLabel: string;
}>();

const emit = defineEmits<{
  previous: [];
  next: [];
  viewMap: [];
  close: [];
}>();

const lastStep = computed(() => props.index === props.total - 1);
const progress = computed(() =>
  Math.max(((props.index + 1) / props.total) * 100, 0),
);
</script>

<template>
  <section
    class="absolute inset-x-0 bottom-0 z-50 border-t border-[#cfd5dc] bg-white shadow-[0_-12px_30px_rgba(23,32,51,0.16)] lg:left-auto lg:w-[360px] lg:border-l"
    role="dialog"
    aria-label="逐条路线说明"
  >
    <div class="h-1 bg-[#e4e7eb]">
      <div
        class="h-full bg-[#087f8c]"
        :style="{ width: `${progress}%` }"
      ></div>
    </div>
    <header
      class="flex items-center justify-between border-b border-[#e4e7eb] px-3.5 py-2.5"
    >
      <div class="flex min-w-0 items-center gap-2">
        <Route class="shrink-0 text-[#087f8c]" :size="17" />
        <div class="min-w-0">
          <strong class="block truncate text-xs">固定路线说明</strong>
          <span class="block text-[10px] text-[#667085]">
            步骤 {{ index + 1 }} / {{ total }}
          </span>
        </div>
      </div>
      <button
        class="grid size-10 place-items-center rounded-md text-[#667085] hover:bg-[#f3f5f7] active:translate-y-px"
        type="button"
        title="关闭逐条说明"
        @click="emit('close')"
      >
        <X :size="19" />
        <span class="sr-only">关闭逐条说明</span>
      </button>
    </header>

    <div class="px-3.5 py-3">
      <span class="text-[10px] font-semibold text-[#087f8c]">
        {{ floorLabel || "跨层步骤" }}
      </span>
      <p class="mt-1 min-h-10 text-base font-semibold leading-6">
        {{ step.instruction }}
      </p>
      <button
        class="mt-2 flex h-10 items-center gap-1.5 text-xs font-semibold text-[#066d77]"
        type="button"
        @click="emit('viewMap')"
      >
        <Map :size="16" />
        查看此楼层地图
      </button>
    </div>

    <footer class="grid grid-cols-2 gap-2 border-t border-[#e4e7eb] p-3">
      <button
        class="flex h-11 items-center justify-center gap-1.5 rounded-md border border-[#cfd5dc] bg-white text-sm font-semibold text-[#344054] active:translate-y-px disabled:opacity-35"
        type="button"
        :disabled="index === 0"
        @click="emit('previous')"
      >
        <ArrowLeft :size="17" />
        上一步
      </button>
      <button
        class="flex h-11 items-center justify-center gap-1.5 rounded-md bg-[#087f8c] text-sm font-semibold text-white active:translate-y-px"
        type="button"
        @click="emit('next')"
      >
        <Check v-if="lastStep" :size="17" />
        <ArrowRight v-else :size="17" />
        {{ lastStep ? "完成" : "下一步" }}
      </button>
    </footer>
  </section>
</template>
