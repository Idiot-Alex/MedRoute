<script setup lang="ts">
import type {
  AdminRelease,
  ReleaseListItem,
} from "@medroute/map-core";
import {
  CheckCircle2,
  CircleDashed,
  CirclePlus,
  Trash2,
} from "@lucide/vue";
import { computed } from "vue";

const props = defineProps<{
  release: AdminRelease;
  releases: ReleaseListItem[];
  dirty: boolean;
  busy: boolean;
}>();

const emit = defineEmits<{
  selectRelease: [releaseId: string];
  createDraft: [];
  discardDraft: [];
}>();

const summary = computed(
  () =>
    props.releases.find((item) => item.id === props.release.id) ?? null,
);

function optionLabel(release: ReleaseListItem): string {
  if (release.status === "draft") {
    return `${release.code}（草稿）`;
  }
  return `${release.code}（${release.active ? "当前发布" : "历史版本"}）`;
}

function handleReleaseChange(event: Event): void {
  const select = event.target as HTMLSelectElement;
  const releaseId = select.value;
  select.value = props.release.id;
  emit("selectRelease", releaseId);
}
</script>

<template>
  <section class="border-b border-[#e4e7eb] p-4">
    <div class="mb-2 flex items-center justify-between gap-2">
      <p class="text-xs font-semibold text-[#344054]">工作版本</p>
      <button
        class="grid size-7 place-items-center rounded-md border border-[#d7dce2] bg-white text-[#4b5563] hover:bg-[#f3f5f7] active:translate-y-px disabled:opacity-40"
        type="button"
        title="从当前发布版本新建草稿"
        :disabled="busy || releases.some((item) => item.status === 'draft')"
        @click="emit('createDraft')"
      >
        <CirclePlus :size="15" />
        <span class="sr-only">新建草稿</span>
      </button>
    </div>

    <select
      :value="release.id"
      class="h-9 w-full rounded-md border border-[#cfd5dc] bg-white px-2 text-xs font-medium outline-none focus:border-[#087f8c]"
      :disabled="busy"
      aria-label="当前工作版本"
      @change="handleReleaseChange"
    >
      <option
        v-for="item in releases"
        :key="item.id"
        :value="item.id"
      >
        {{ optionLabel(item) }}
      </option>
    </select>

    <div class="mt-2 border-y border-[#e4e7eb] py-2.5">
      <div class="flex items-center justify-between gap-2">
        <strong class="truncate text-xs">{{ release.code }}</strong>
        <span
          class="shrink-0 rounded-sm px-1.5 py-0.5 text-[10px] font-semibold"
          :class="
            release.status === 'draft'
              ? 'bg-[#dff3f2] text-[#066d77]'
              : summary?.active
                ? 'bg-[#dcfce7] text-[#166534]'
                : 'bg-[#e5e7eb] text-[#4b5563]'
          "
        >
          {{
            release.status === "draft"
              ? "草稿"
              : summary?.active
                ? "当前发布"
                : "历史版本"
          }}
        </span>
      </div>
      <p class="mt-1 truncate text-[10px] leading-4 text-[#667085]">
        {{ release.description || "未填写版本说明" }}
      </p>
      <div class="mt-1.5 flex items-center gap-1.5 text-[10px] text-[#667085]">
        <CheckCircle2
          v-if="
            summary?.validationPassed &&
            summary.validatedRevision === release.contentRevision
          "
          :size="12"
          class="text-[#15803d]"
        />
        <CircleDashed v-else :size="12" />
        修订 {{ release.contentRevision }}
        <span v-if="dirty" class="font-semibold text-[#b45309]">未保存</span>
        <span
          v-else-if="
            summary?.validationPassed &&
            summary.validatedRevision === release.contentRevision
          "
          >已校验</span
        >
        <span v-else-if="release.status === 'draft'">待校验</span>
        <span v-else>只读</span>
      </div>
    </div>

    <button
      v-if="release.status === 'draft'"
      class="mt-2 flex h-8 w-full items-center justify-center gap-1.5 rounded-md border border-[#fecaca] bg-white text-[11px] font-semibold text-[#b91c1c] hover:bg-[#fef2f2] active:translate-y-px disabled:opacity-40"
      type="button"
      :disabled="busy"
      @click="emit('discardDraft')"
    >
      <Trash2 :size="13" />
      放弃当前草稿
    </button>
  </section>
</template>
