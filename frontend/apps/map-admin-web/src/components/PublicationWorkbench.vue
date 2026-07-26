<script setup lang="ts">
import {
  canPublishRelease,
  canRollbackRelease,
  validationMatchesRelease,
  type AdminRelease,
  type AdminValidation,
  type ReleaseListItem,
  type RouteRegressionCase,
  type ValidationIssue,
} from "@medroute/map-core";
import {
  CheckCircle2,
  CircleAlert,
  CirclePlus,
  Edit3,
  GitPullRequestArrow,
  RotateCcw,
  Route,
  ShieldCheck,
  Trash2,
  TriangleAlert,
} from "@lucide/vue";
import { computed } from "vue";

const props = defineProps<{
  release: AdminRelease;
  releases: ReleaseListItem[];
  validation: AdminValidation | null;
  regressionCases: RouteRegressionCase[];
  dirty: boolean;
  busy: boolean;
}>();

const emit = defineEmits<{
  validate: [];
  publish: [];
  rollback: [];
  createCase: [];
  editCase: [caseId: string];
  deleteCase: [caseId: string];
  navigateIssue: [issue: ValidationIssue];
}>();

const validationCurrent = computed(() =>
  validationMatchesRelease(props.validation, props.release),
);

const publishEnabled = computed(() =>
  canPublishRelease(
    props.release,
    props.validation,
    props.dirty,
    props.busy,
  ),
);

const rollbackEnabled = computed(() =>
  canRollbackRelease(props.release.id, props.releases, props.busy),
);

const enabledCaseCount = computed(
  () => props.regressionCases.filter((item) => item.enabled).length,
);

const activeSummary = computed(
  () =>
    props.releases.find((item) => item.id === props.release.id) ?? null,
);

const issues = computed(() => [
  ...(props.validation?.errors ?? []).map((issue) => ({
    issue,
    severity: "error" as const,
  })),
  ...(props.validation?.warnings ?? []).map((issue) => ({
    issue,
    severity: "warning" as const,
  })),
]);

function routeLabel(item: RouteRegressionCase): string {
  return `${item.startPoiCode} → ${item.endPoiCode}`;
}

function formatMetrics(
  distanceMeters: number | null,
  estimatedSeconds: number | null,
): string {
  if (distanceMeters == null || estimatedSeconds == null) {
    return "未计算出路线";
  }
  return `${distanceMeters} 米 · ${estimatedSeconds} 秒`;
}
</script>

<template>
  <div>
    <section class="border-b border-[#e4e7eb] pb-4">
      <div class="flex items-start gap-2.5">
        <span
          class="grid size-8 shrink-0 place-items-center rounded-md"
          :class="
            validationCurrent && validation?.passed
              ? 'bg-[#dcfce7] text-[#15803d]'
              : validationCurrent
                ? 'bg-[#fee2e2] text-[#b91c1c]'
                : 'bg-[#e5e7eb] text-[#667085]'
          "
        >
          <ShieldCheck :size="17" />
        </span>
        <div class="min-w-0">
          <strong class="block text-sm">
            {{
              validationCurrent
                ? validation?.passed
                  ? "发布校验通过"
                  : "发布校验未通过"
                : validation
                  ? "校验结果已失效"
                  : "尚未执行发布校验"
            }}
          </strong>
          <p class="mt-0.5 text-[11px] leading-4 text-[#667085]">
            <template v-if="validationCurrent">
              修订 {{ validation?.contentRevision }} ·
              {{ validation?.errors.length }} 个错误 ·
              {{ validation?.warnings.length }} 个提醒
            </template>
            <template v-else>
              当前修订 {{ release.contentRevision }}
              <span v-if="dirty"> · 有未保存修改</span>
            </template>
          </p>
        </div>
      </div>

      <div v-if="release.status === 'draft'" class="mt-4 grid grid-cols-2 gap-2">
        <button
          class="flex h-9 items-center justify-center gap-1.5 rounded-md border border-[#cfd5dc] bg-white text-xs font-semibold text-[#344054] hover:bg-[#f3f5f7] active:translate-y-px disabled:opacity-40"
          type="button"
          :disabled="busy"
          @click="emit('validate')"
        >
          <GitPullRequestArrow :size="15" />
          保存并校验
        </button>
        <button
          class="flex h-9 items-center justify-center gap-1.5 rounded-md bg-[#087f8c] text-xs font-semibold text-white hover:bg-[#066d77] active:translate-y-px disabled:cursor-not-allowed disabled:opacity-40"
          type="button"
          :disabled="!publishEnabled"
          @click="emit('publish')"
        >
          <CheckCircle2 :size="15" />
          发布版本
        </button>
      </div>

      <button
        v-else-if="!activeSummary?.active"
        class="mt-4 flex h-9 w-full items-center justify-center gap-1.5 rounded-md border border-[#fecaca] bg-white text-xs font-semibold text-[#b91c1c] hover:bg-[#fef2f2] active:translate-y-px disabled:opacity-40"
        type="button"
        :disabled="!rollbackEnabled"
        @click="emit('rollback')"
      >
        <RotateCcw :size="15" />
        回滚启用此版本
      </button>

      <div
        v-else
        class="mt-4 flex items-center gap-2 border-y border-[#bbf7d0] bg-[#f0fdf4] px-2.5 py-2 text-[11px] font-medium text-[#166534]"
      >
        <CheckCircle2 :size="14" />
        当前移动导航正在使用此版本
      </div>
    </section>

    <section class="border-b border-[#e4e7eb] py-4">
      <div class="mb-2 flex items-center justify-between gap-2">
        <div>
          <h2 class="text-xs font-semibold text-[#344054]">关键路线</h2>
          <span class="text-[10px] text-[#667085]">
            {{ enabledCaseCount }} 条启用
          </span>
        </div>
        <button
          class="grid size-7 place-items-center rounded-md border border-[#d7dce2] bg-white text-[#4b5563] hover:bg-[#f3f5f7] active:translate-y-px disabled:opacity-40"
          type="button"
          title="新增关键路线"
          :disabled="busy"
          @click="emit('createCase')"
        >
          <CirclePlus :size="15" />
          <span class="sr-only">新增关键路线</span>
        </button>
      </div>

      <div class="divide-y divide-[#e4e7eb] border-y border-[#e4e7eb]">
        <article
          v-for="item in regressionCases"
          :key="item.id"
          class="py-2.5"
        >
          <div class="flex items-start justify-between gap-2">
            <div class="min-w-0">
              <strong class="block truncate text-xs">{{ item.name }}</strong>
              <span class="mt-0.5 block truncate text-[10px] text-[#667085]">
                {{ routeLabel(item) }}
              </span>
            </div>
            <span
              class="shrink-0 rounded-sm px-1 py-0.5 text-[9px] font-semibold"
              :class="
                item.enabled
                  ? item.critical
                    ? 'bg-[#fee2e2] text-[#b91c1c]'
                    : 'bg-[#fff7ed] text-[#9a3412]'
                  : 'bg-[#e5e7eb] text-[#667085]'
              "
            >
              {{ item.enabled ? (item.critical ? "关键" : "提醒") : "停用" }}
            </span>
          </div>
          <div class="mt-2 flex items-center justify-between">
            <span class="text-[10px] text-[#667085]">
              {{ item.routeMode === "accessible" ? "无障碍" : "常规" }}
              <template v-if="item.maxDistanceMeters">
                · ≤ {{ item.maxDistanceMeters }} 米
              </template>
            </span>
            <div class="flex gap-1">
              <button
                class="grid size-6 place-items-center rounded-md text-[#667085] hover:bg-[#f3f5f7] active:translate-y-px disabled:opacity-40"
                type="button"
                title="编辑关键路线"
                :disabled="busy"
                @click="emit('editCase', item.id)"
              >
                <Edit3 :size="13" />
                <span class="sr-only">编辑 {{ item.name }}</span>
              </button>
              <button
                class="grid size-6 place-items-center rounded-md text-[#b91c1c] hover:bg-[#fef2f2] active:translate-y-px disabled:opacity-40"
                type="button"
                title="删除关键路线"
                :disabled="busy"
                @click="emit('deleteCase', item.id)"
              >
                <Trash2 :size="13" />
                <span class="sr-only">删除 {{ item.name }}</span>
              </button>
            </div>
          </div>
        </article>
        <p
          v-if="!regressionCases.length"
          class="py-3 text-[11px] text-[#667085]"
        >
          尚未配置关键路线。
        </p>
      </div>
    </section>

    <section
      v-if="validationCurrent && validation?.routeRegressions.length"
      class="border-b border-[#e4e7eb] py-4"
    >
      <div class="mb-2 flex items-center justify-between gap-2">
        <h2 class="text-xs font-semibold text-[#344054]">回归结果</h2>
        <span class="text-[10px] text-[#667085]">
          {{
            validation.routeRegressions.filter((item) => item.passed).length
          }}
          / {{ validation.routeRegressions.length }} 通过
        </span>
      </div>
      <div class="divide-y divide-[#e4e7eb] border-y border-[#e4e7eb]">
        <div
          v-for="result in validation.routeRegressions"
          :key="result.caseId"
          class="grid grid-cols-[18px_minmax(0,1fr)] gap-2 py-2.5"
        >
          <CheckCircle2
            v-if="result.passed"
            :size="15"
            class="mt-0.5 text-[#15803d]"
          />
          <CircleAlert
            v-else
            :size="15"
            class="mt-0.5 text-[#b91c1c]"
          />
          <div class="min-w-0">
            <strong class="block truncate text-xs">
              {{ result.caseName }}
            </strong>
            <span class="mt-0.5 block text-[10px] text-[#667085]">
              {{ formatMetrics(result.distanceMeters, result.estimatedSeconds) }}
            </span>
            <span
              v-if="!result.passed"
              class="mt-1 block text-[10px] leading-4 text-[#b91c1c]"
            >
              {{ result.message }}
            </span>
          </div>
        </div>
      </div>
    </section>

    <section v-if="validationCurrent && issues.length" class="py-4">
      <h2 class="mb-2 text-xs font-semibold text-[#344054]">校验问题</h2>
      <div class="divide-y divide-[#e4e7eb] border-y border-[#e4e7eb]">
        <button
          v-for="{ issue, severity } in issues"
          :key="`${severity}:${issue.code}:${issue.elementId}`"
          class="grid w-full grid-cols-[18px_minmax(0,1fr)] gap-2 py-2.5 text-left hover:bg-[#f8fafb] active:translate-y-px"
          type="button"
          @click="emit('navigateIssue', issue)"
        >
          <CircleAlert
            v-if="severity === 'error'"
            :size="15"
            class="mt-0.5 text-[#b91c1c]"
          />
          <TriangleAlert
            v-else
            :size="15"
            class="mt-0.5 text-[#b45309]"
          />
          <span>
            <span class="block text-[11px] leading-4 text-[#344054]">
              {{ issue.message }}
            </span>
            <span class="mt-0.5 block text-[9px] font-medium text-[#667085]">
              {{ issue.code }}
            </span>
          </span>
        </button>
      </div>
    </section>

    <section
      v-if="
        validationCurrent &&
        validation?.passed &&
        !validation.warnings.length &&
        !validation.routeRegressions.length
      "
      class="grid min-h-36 place-items-center border-b border-[#e4e7eb] text-center"
    >
      <div>
        <Route class="mx-auto text-[#15803d]" :size="22" />
        <strong class="mt-2 block text-xs">结构检查通过</strong>
      </div>
    </section>

  </div>
</template>
