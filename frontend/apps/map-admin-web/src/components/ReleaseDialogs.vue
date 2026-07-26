<script setup lang="ts">
import type { CreateDraftRequest } from "@medroute/api-client";
import type {
  AdminRelease,
  ReleaseListItem,
} from "@medroute/map-core";
import {
  CirclePlus,
  RotateCcw,
  Send,
  Trash2,
} from "@lucide/vue";
import { reactive, ref } from "vue";

const props = defineProps<{
  release: AdminRelease;
  releases: ReleaseListItem[];
}>();

const emit = defineEmits<{
  createDraft: [request: CreateDraftRequest];
  publish: [reason: string];
  rollback: [reason: string];
  discard: [];
}>();

const draftDialog = ref<HTMLDialogElement | null>(null);
const publishDialog = ref<HTMLDialogElement | null>(null);
const rollbackDialog = ref<HTMLDialogElement | null>(null);
const discardDialog = ref<HTMLDialogElement | null>(null);
const draft = reactive({ code: "", description: "" });
const publishReason = ref("");
const rollbackReason = ref("");

function activePublishedRelease(): ReleaseListItem | null {
  return (
    props.releases.find(
      (item) => item.status === "published" && item.active,
    ) ?? null
  );
}

function nextDraftCode(): string {
  const base = "DRAFT-MAP";
  let sequence = 1;
  const used = new Set(
    props.releases.map((item) => item.code.toUpperCase()),
  );
  while (used.has(`${base}-${sequence}`)) {
    sequence += 1;
  }
  return `${base}-${sequence}`;
}

function openDraft(): void {
  draft.code = nextDraftCode();
  draft.description = "";
  draftDialog.value?.showModal();
}

function openPublish(): void {
  publishReason.value = "";
  publishDialog.value?.showModal();
}

function openRollback(): void {
  rollbackReason.value = "";
  rollbackDialog.value?.showModal();
}

function openDiscard(): void {
  discardDialog.value?.showModal();
}

function submitDraft(): void {
  const active = activePublishedRelease();
  if (!active || !draft.code.trim()) {
    return;
  }
  emit("createDraft", {
    code: draft.code.trim().toUpperCase(),
    basedOnReleaseId: active.id,
    description: draft.description.trim(),
  });
}

function submitPublish(): void {
  const reason = publishReason.value.trim();
  if (reason) {
    emit("publish", reason);
  }
}

function submitRollback(): void {
  const reason = rollbackReason.value.trim();
  if (reason) {
    emit("rollback", reason);
  }
}

function closeDraft(): void {
  draftDialog.value?.close();
}

function closePublish(): void {
  publishDialog.value?.close();
}

function closeRollback(): void {
  rollbackDialog.value?.close();
}

function closeDiscard(): void {
  discardDialog.value?.close();
}

defineExpose({
  openDraft,
  openPublish,
  openRollback,
  openDiscard,
  closeDraft,
  closePublish,
  closeRollback,
  closeDiscard,
});
</script>

<template>
  <dialog
    ref="draftDialog"
    class="m-auto w-[440px] rounded-md border border-[#cfd5dc] bg-white p-0 text-[#172033] shadow-xl"
  >
    <form class="p-5" @submit.prevent="submitDraft">
      <div class="flex items-start justify-between gap-3">
        <div>
          <h2 class="text-base font-semibold">新建地图草稿</h2>
          <p class="mt-1 text-xs text-[#667085]">
            复制当前发布版本
          </p>
        </div>
        <CirclePlus class="text-[#087f8c]" :size="22" />
      </div>
      <div class="mt-5 space-y-3">
        <label class="block space-y-1.5">
          <span class="text-xs font-medium text-[#344054]">草稿编码</span>
          <input
            v-model="draft.code"
            required
            autofocus
            maxlength="80"
            class="h-9 w-full rounded-md border border-[#cfd5dc] px-2.5 text-xs uppercase outline-none focus:border-[#087f8c] focus:ring-2 focus:ring-[#b9dfe0]"
          />
        </label>
        <label class="block space-y-1.5">
          <span class="text-xs font-medium text-[#344054]">修改说明</span>
          <textarea
            v-model="draft.description"
            rows="3"
            maxlength="500"
            class="w-full resize-none rounded-md border border-[#cfd5dc] px-2.5 py-2 text-xs leading-5 outline-none focus:border-[#087f8c]"
          ></textarea>
        </label>
      </div>
      <div class="mt-6 flex justify-end gap-2">
        <button
          class="h-9 rounded-md border border-[#cfd5dc] bg-white px-3 text-xs font-semibold text-[#344054]"
          type="button"
          @click="closeDraft"
        >
          取消
        </button>
        <button
          class="h-9 rounded-md bg-[#087f8c] px-3 text-xs font-semibold text-white"
          type="submit"
        >
          创建草稿
        </button>
      </div>
    </form>
  </dialog>

  <dialog
    ref="publishDialog"
    class="m-auto w-[440px] rounded-md border border-[#cfd5dc] bg-white p-0 text-[#172033] shadow-xl"
  >
    <form class="p-5" @submit.prevent="submitPublish">
      <div class="flex items-start justify-between gap-3">
        <div>
          <h2 class="text-base font-semibold">发布 {{ release.code }}</h2>
          <p class="mt-1 text-xs text-[#667085]">
            发布后版本只读并立即供新导航请求使用
          </p>
        </div>
        <Send class="text-[#087f8c]" :size="22" />
      </div>
      <label class="mt-5 block space-y-1.5">
        <span class="text-xs font-medium text-[#344054]">发布原因</span>
        <textarea
          v-model="publishReason"
          required
          autofocus
          rows="3"
          maxlength="500"
          class="w-full resize-none rounded-md border border-[#cfd5dc] px-2.5 py-2 text-xs leading-5 outline-none focus:border-[#087f8c] focus:ring-2 focus:ring-[#b9dfe0]"
        ></textarea>
      </label>
      <div class="mt-6 flex justify-end gap-2">
        <button
          class="h-9 rounded-md border border-[#cfd5dc] bg-white px-3 text-xs font-semibold text-[#344054]"
          type="button"
          @click="closePublish"
        >
          取消
        </button>
        <button
          class="h-9 rounded-md bg-[#087f8c] px-3 text-xs font-semibold text-white"
          type="submit"
        >
          确认发布
        </button>
      </div>
    </form>
  </dialog>

  <dialog
    ref="rollbackDialog"
    class="m-auto w-[440px] rounded-md border border-[#cfd5dc] bg-white p-0 text-[#172033] shadow-xl"
  >
    <form class="p-5" @submit.prevent="submitRollback">
      <div class="flex items-start justify-between gap-3">
        <div>
          <h2 class="text-base font-semibold">回滚到 {{ release.code }}</h2>
          <p class="mt-1 text-xs text-[#667085]">
            移动导航将立即切换到此历史版本
          </p>
        </div>
        <RotateCcw class="text-[#b91c1c]" :size="22" />
      </div>
      <label class="mt-5 block space-y-1.5">
        <span class="text-xs font-medium text-[#344054]">回滚原因</span>
        <textarea
          v-model="rollbackReason"
          required
          autofocus
          rows="3"
          maxlength="500"
          class="w-full resize-none rounded-md border border-[#cfd5dc] px-2.5 py-2 text-xs leading-5 outline-none focus:border-[#b91c1c] focus:ring-2 focus:ring-[#fecaca]"
        ></textarea>
      </label>
      <div class="mt-6 flex justify-end gap-2">
        <button
          class="h-9 rounded-md border border-[#cfd5dc] bg-white px-3 text-xs font-semibold text-[#344054]"
          type="button"
          @click="closeRollback"
        >
          取消
        </button>
        <button
          class="h-9 rounded-md bg-[#b91c1c] px-3 text-xs font-semibold text-white"
          type="submit"
        >
          确认回滚
        </button>
      </div>
    </form>
  </dialog>

  <dialog
    ref="discardDialog"
    class="m-auto w-[420px] rounded-md border border-[#cfd5dc] bg-white p-0 text-[#172033] shadow-xl"
  >
    <form class="p-5" @submit.prevent="emit('discard')">
      <div class="flex items-start justify-between gap-3">
        <div>
          <h2 class="text-base font-semibold">放弃 {{ release.code }}</h2>
          <p class="mt-1 text-xs leading-5 text-[#b91c1c]">
            草稿路网和草稿独占底图将永久删除
          </p>
        </div>
        <Trash2 class="text-[#b91c1c]" :size="22" />
      </div>
      <div class="mt-6 flex justify-end gap-2">
        <button
          class="h-9 rounded-md border border-[#cfd5dc] bg-white px-3 text-xs font-semibold text-[#344054]"
          type="button"
          @click="closeDiscard"
        >
          取消
        </button>
        <button
          class="h-9 rounded-md bg-[#b91c1c] px-3 text-xs font-semibold text-white"
          type="submit"
        >
          确认放弃
        </button>
      </div>
    </form>
  </dialog>
</template>
