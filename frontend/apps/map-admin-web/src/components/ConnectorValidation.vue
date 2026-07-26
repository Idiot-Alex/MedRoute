<script setup lang="ts">
import type { ConnectorIssue, Floor } from "@medroute/map-core";
import {
  CheckCircle2,
  CircleAlert,
  TriangleAlert,
} from "@lucide/vue";

defineProps<{
  issues: ConnectorIssue[];
  floors: Floor[];
}>();

const emit = defineEmits<{
  navigate: [issue: ConnectorIssue];
}>();

function floorCode(floors: Floor[], floorId: string | null): string {
  return floors.find((floor) => floor.id === floorId)?.code ?? "";
}
</script>

<template>
  <div v-if="issues.length">
    <div
      class="mb-3 rounded-md border border-[#fed7aa] bg-[#fff7ed] p-3 text-[11px] leading-4 text-[#9a3412]"
    >
      未完成关系允许保存草稿；标记“阻止保存”的结构问题必须先处理。
    </div>
    <div class="divide-y divide-[#e4e7eb] border-y border-[#e4e7eb]">
      <button
        v-for="issue in issues"
        :key="`${issue.code}:${issue.elementId}`"
        type="button"
        class="grid w-full grid-cols-[18px_minmax(0,1fr)] gap-2 py-3 text-left hover:bg-[#f8fafb] active:translate-y-px"
        @click="emit('navigate', issue)"
      >
        <CircleAlert
          v-if="issue.severity === 'error'"
          :size="16"
          class="mt-0.5 text-[#b91c1c]"
        />
        <TriangleAlert
          v-else
          :size="16"
          class="mt-0.5 text-[#b45309]"
        />
        <span class="min-w-0">
          <span class="block text-xs leading-5 text-[#344054]">
            {{ issue.message }}
          </span>
          <span class="mt-0.5 block text-[10px] font-medium text-[#667085]">
            {{ floorCode(floors, issue.floorId) }}
            {{
              issue.blockingSave
                ? "阻止保存"
                : issue.severity === "error"
                  ? "发布前修复"
                  : "建议检查"
            }}
          </span>
        </span>
      </button>
    </div>
  </div>

  <div
    v-else
    class="grid min-h-44 place-items-center border-y border-[#e4e7eb] text-center"
  >
    <div>
      <CheckCircle2 class="mx-auto text-[#15803d]" :size="24" />
      <strong class="mt-3 block text-xs">跨层关系完整</strong>
      <p class="mt-1 text-[11px] leading-4 text-[#667085]">
        所有设施、停靠点和明确连接通过本地检查。
      </p>
    </div>
  </div>
</template>
