<script setup lang="ts">
import {
  ApiError,
  MedRouteApiClient,
} from "@medroute/api-client";
import {
  buildFixedPointNavigationUrl,
  navigationUrlUsesLoopback,
} from "@medroute/map-core";
import {
  Copy,
  Download,
  QrCode,
  RefreshCw,
  TriangleAlert,
  X,
} from "@lucide/vue";
import { computed, onUnmounted, ref } from "vue";

const props = defineProps<{
  apiBase: string;
  buildingId: string;
  defaultNavigationBaseUrl: string;
}>();

const emit = defineEmits<{
  copied: [];
}>();

interface QrCodePoi {
  code: string;
  name: string;
}

const client = new MedRouteApiClient({ apiBase: props.apiBase });
const dialog = ref<HTMLDialogElement | null>(null);
const navigationUrlInput = ref<HTMLInputElement | null>(null);
const poi = ref<QrCodePoi | null>(null);
const floorCode = ref("");
const baseUrl = ref("");
const navigationUrl = ref("");
const qrImageUrl = ref("");
const qrBlob = ref<Blob | null>(null);
const busy = ref(false);
const error = ref("");
let generationRevision = 0;

const localOnly = computed(
  () =>
    Boolean(navigationUrl.value) &&
    navigationUrlUsesLoopback(navigationUrl.value),
);

function releaseQrCode(): void {
  if (qrImageUrl.value) {
    URL.revokeObjectURL(qrImageUrl.value);
  }
  qrImageUrl.value = "";
  qrBlob.value = null;
}

function updateNavigationUrl(): boolean {
  if (!poi.value) {
    return false;
  }
  try {
    navigationUrl.value = buildFixedPointNavigationUrl(
      baseUrl.value,
      props.buildingId,
      poi.value.code,
    );
    error.value = "";
    return true;
  } catch (caught) {
    navigationUrl.value = "";
    error.value =
      caught instanceof Error ? caught.message : "导航页面地址无效。";
    return false;
  }
}

function changeBaseUrl(): void {
  generationRevision += 1;
  releaseQrCode();
  updateNavigationUrl();
}

async function generate(): Promise<void> {
  if (!updateNavigationUrl() || busy.value) {
    return;
  }
  const revision = ++generationRevision;
  releaseQrCode();
  busy.value = true;
  error.value = "";
  try {
    const nextBlob = await client.generateNavigationQrCode(
      navigationUrl.value,
    );
    if (revision !== generationRevision) {
      return;
    }
    qrBlob.value = nextBlob;
    qrImageUrl.value = URL.createObjectURL(nextBlob);
  } catch (caught) {
    if (revision !== generationRevision) {
      return;
    }
    error.value =
      caught instanceof ApiError ? caught.message : "二维码生成失败。";
  } finally {
    if (revision === generationRevision) {
      busy.value = false;
    }
  }
}

function open(nextPoi: QrCodePoi, nextFloorCode: string): void {
  generationRevision += 1;
  poi.value = nextPoi;
  floorCode.value = nextFloorCode;
  baseUrl.value = props.defaultNavigationBaseUrl;
  error.value = "";
  releaseQrCode();
  updateNavigationUrl();
  dialog.value?.showModal();
  void generate();
}

function close(): void {
  dialog.value?.close();
}

async function copyNavigationUrl(): Promise<void> {
  if (!navigationUrl.value) {
    return;
  }
  try {
    await navigator.clipboard.writeText(navigationUrl.value);
  } catch {
    navigationUrlInput.value?.focus();
    navigationUrlInput.value?.select();
    document.execCommand("copy");
    navigationUrlInput.value?.setSelectionRange(0, 0);
  }
  emit("copied");
}

function download(): void {
  if (!qrImageUrl.value || !poi.value) {
    return;
  }
  const safeCode = poi.value.code.replace(/[^a-zA-Z0-9_-]/g, "_");
  const link = document.createElement("a");
  link.href = qrImageUrl.value;
  link.download = `medroute-${safeCode}.png`;
  link.click();
}

function closed(): void {
  generationRevision += 1;
  busy.value = false;
  releaseQrCode();
  poi.value = null;
}

onUnmounted(() => {
  generationRevision += 1;
  releaseQrCode();
});
defineExpose({ open, close });
</script>

<template>
  <dialog
    ref="dialog"
    class="m-auto w-[min(760px,calc(100vw-32px))] rounded-md border border-[#cfd5dc] bg-white p-0 text-[#172033] shadow-xl backdrop:bg-[#172033]/35"
    @close="closed"
  >
    <header
      class="flex items-center justify-between border-b border-[#e4e7eb] px-5 py-4"
    >
      <div>
        <p class="text-[10px] font-semibold text-[#087f8c]">固定起点</p>
        <h2 class="mt-0.5 text-base font-semibold">导航二维码</h2>
      </div>
      <button
        class="grid size-9 place-items-center rounded-md text-[#667085] hover:bg-[#f3f5f7]"
        type="button"
        title="关闭二维码窗口"
        @click="close"
      >
        <X :size="19" />
        <span class="sr-only">关闭二维码窗口</span>
      </button>
    </header>

    <div class="grid grid-cols-[260px_minmax(0,1fr)] gap-5 p-5">
      <div>
        <div
          class="grid aspect-square w-full place-items-center border border-[#d7dce2] bg-[#f8fafb] p-2"
        >
          <img
            v-if="qrImageUrl"
            class="size-full object-contain"
            :src="qrImageUrl"
            alt="固定点导航二维码"
          />
          <div v-else class="px-4 text-center">
            <RefreshCw
              v-if="busy"
              class="mx-auto animate-spin text-[#087f8c]"
              :size="26"
            />
            <QrCode v-else class="mx-auto text-[#98a2b3]" :size="30" />
            <p class="mt-3 text-xs text-[#667085]">
              {{
                busy
                  ? "正在生成二维码"
                  : error
                    ? "二维码生成失败"
                    : "等待生成"
              }}
            </p>
          </div>
        </div>
        <button
          class="mt-2.5 flex h-9 w-full items-center justify-center gap-1.5 rounded-md border border-[#cfd5dc] text-xs font-semibold text-[#344054] hover:bg-[#f3f5f7] active:translate-y-px disabled:opacity-40"
          type="button"
          :disabled="busy || !qrBlob"
          @click="download"
        >
          <Download :size="15" />
          下载 PNG
        </button>
      </div>

      <div class="min-w-0">
        <h3 class="truncate text-sm font-semibold">{{ poi?.name }}</h3>
        <p class="mt-1 text-[11px] text-[#667085]">
          {{ floorCode }} · {{ poi?.code }}
        </p>

        <label class="mt-5 block space-y-1.5">
          <span class="text-xs font-medium text-[#344054]">
            导航页面基础地址
          </span>
          <input
            v-model="baseUrl"
            class="h-9 w-full rounded-md border border-[#cfd5dc] px-2.5 text-xs outline-none focus:border-[#087f8c] focus:ring-2 focus:ring-[#b9dfe0]"
            :disabled="busy"
            @input="changeBaseUrl"
          />
        </label>

        <div
          v-if="localOnly"
          class="mt-3 flex gap-2 border-y border-[#fde68a] bg-[#fffbeb] px-3 py-2 text-[11px] leading-4 text-[#92400e]"
        >
          <TriangleAlert class="mt-0.5 shrink-0" :size="14" />
          当前地址只适合本机测试，打印前请改为手机能够访问的正式地址。
        </div>

        <label class="mt-4 block space-y-1.5">
          <span class="text-xs font-medium text-[#344054]">
            二维码导航地址
          </span>
          <input
            ref="navigationUrlInput"
            :value="navigationUrl"
            class="h-9 w-full rounded-md border border-[#d7dce2] bg-[#f3f5f7] px-2.5 text-[11px] text-[#4b5563]"
            readonly
          />
        </label>

        <p
          v-if="error"
          class="mt-3 flex gap-1.5 text-[11px] leading-4 text-[#b42318]"
          role="alert"
        >
          <TriangleAlert class="mt-0.5 shrink-0" :size="13" />
          {{ error }}
        </p>

        <div class="mt-5 grid grid-cols-2 gap-2">
          <button
            class="flex h-9 items-center justify-center gap-1.5 rounded-md border border-[#cfd5dc] text-xs font-semibold text-[#344054] hover:bg-[#f3f5f7] active:translate-y-px disabled:opacity-40"
            type="button"
            :disabled="!navigationUrl"
            @click="copyNavigationUrl"
          >
            <Copy :size="14" />
            复制地址
          </button>
          <button
            class="flex h-9 items-center justify-center gap-1.5 rounded-md bg-[#087f8c] text-xs font-semibold text-white hover:bg-[#066d77] active:translate-y-px disabled:opacity-40"
            type="button"
            :disabled="busy || !navigationUrl"
            @click="generate"
          >
            <RefreshCw :size="14" :class="{ 'animate-spin': busy }" />
            {{ qrImageUrl ? "重新生成" : "生成二维码" }}
          </button>
        </div>
      </div>
    </div>
  </dialog>
</template>
