<script setup lang="ts">
import type {
  GraphDependency,
  MapSelection,
} from "@medroute/map-core";
import {
  ArrowUpRight,
  Trash2,
  TriangleAlert,
  X,
} from "@lucide/vue";
import { ref } from "vue";

interface DeleteObjectDependency extends Omit<GraphDependency, "kind"> {
  kind: GraphDependency["kind"] | "route_regression_case";
}

const emit = defineEmits<{
  confirm: [selection: MapSelection];
  navigate: [
    kind: DeleteObjectDependency["kind"],
    id: string,
  ];
}>();

const dialog = ref<HTMLDialogElement | null>(null);
const selection = ref<MapSelection | null>(null);
const objectType = ref("");
const objectName = ref("");
const dependencies = ref<DeleteObjectDependency[]>([]);

function open(
  nextSelection: MapSelection,
  nextObjectType: string,
  nextObjectName: string,
  nextDependencies: DeleteObjectDependency[],
): void {
  selection.value = nextSelection;
  objectType.value = nextObjectType;
  objectName.value = nextObjectName;
  dependencies.value = nextDependencies;
  if (dialog.value && !dialog.value.open) {
    dialog.value.showModal();
  }
}

function close(): void {
  dialog.value?.close();
}

function confirm(): void {
  if (!selection.value || dependencies.value.length) {
    return;
  }
  emit("confirm", selection.value);
}

function navigate(
  dependency: DeleteObjectDependency,
): void {
  emit("navigate", dependency.kind, dependency.id);
  close();
}

defineExpose({ open, close });
</script>

<template>
  <dialog
    ref="dialog"
    class="m-auto w-[min(500px,calc(100vw-32px))] rounded-md border border-[#cfd5dc] bg-white p-0 text-[#172033] shadow-xl backdrop:bg-[#172033]/35"
  >
    <header
      class="flex items-center justify-between border-b border-[#e4e7eb] px-5 py-4"
    >
      <div>
        <p class="text-[10px] font-semibold text-[#b42318]">草稿修改</p>
        <h2 class="mt-0.5 text-base font-semibold">删除{{ objectType }}</h2>
      </div>
      <button
        class="grid size-9 place-items-center rounded-md text-[#667085] hover:bg-[#f3f5f7]"
        type="button"
        title="关闭删除窗口"
        @click="close"
      >
        <X :size="19" />
        <span class="sr-only">关闭删除窗口</span>
      </button>
    </header>

    <div class="p-5">
      <strong class="block text-sm">{{ objectName }}</strong>
      <p class="mt-2 text-xs leading-5 text-[#667085]">
        删除会在保存草稿后写入数据库，并使当前发布校验失效。
      </p>

      <div
        v-if="dependencies.length"
        class="mt-4 border-y border-[#fecaca] bg-[#fef2f2] px-3 py-3"
      >
        <p class="flex gap-2 text-xs font-semibold text-[#b42318]">
          <TriangleAlert class="shrink-0" :size="15" />
          仍有 {{ dependencies.length }} 个引用，暂时不能删除
        </p>
        <ul class="mt-3 divide-y divide-[#fecaca]">
          <li
            v-for="dependency in dependencies"
            :key="`${dependency.kind}:${dependency.id}`"
            class="flex items-center justify-between gap-3 py-2"
          >
            <div class="min-w-0">
              <strong class="block truncate text-xs">
                {{ dependency.label }}
              </strong>
              <span class="text-[10px] text-[#b45309]">
                {{ dependency.relation }}
              </span>
            </div>
            <button
              class="flex h-7 shrink-0 items-center gap-1 rounded-md border border-[#fca5a5] bg-white px-2 text-[10px] font-semibold text-[#b42318] hover:bg-[#fee2e2]"
              type="button"
              @click="navigate(dependency)"
            >
              处理
              <ArrowUpRight :size="11" />
            </button>
          </li>
        </ul>
      </div>

      <div
        v-else
        class="mt-4 flex gap-2 border-y border-[#fde68a] bg-[#fffbeb] px-3 py-3 text-[11px] leading-4 text-[#92400e]"
      >
        <TriangleAlert class="mt-0.5 shrink-0" :size="14" />
        此操作不会立即影响患者导航，只有草稿发布后才会生效。
      </div>

      <div class="mt-5 flex justify-end gap-2">
        <button
          class="h-9 rounded-md border border-[#cfd5dc] bg-white px-3 text-xs font-semibold text-[#344054] hover:bg-[#f3f5f7]"
          type="button"
          @click="close"
        >
          取消
        </button>
        <button
          class="flex h-9 items-center gap-1.5 rounded-md bg-[#b42318] px-3 text-xs font-semibold text-white hover:bg-[#912018] active:translate-y-px disabled:cursor-not-allowed disabled:opacity-40"
          type="button"
          :disabled="dependencies.length > 0"
          @click="confirm"
        >
          <Trash2 :size="14" />
          确认删除
        </button>
      </div>
    </div>
  </dialog>
</template>
