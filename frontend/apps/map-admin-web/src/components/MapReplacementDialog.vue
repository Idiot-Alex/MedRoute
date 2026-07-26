<script setup lang="ts">
import {
  assessMapReplacement,
  type Floor,
  type MapImageDimensions,
} from "@medroute/map-core";
import {
  ImageUp,
  Scale,
  TriangleAlert,
} from "@lucide/vue";
import { computed, ref } from "vue";

const MAX_MAP_BYTES = 15 * 1024 * 1024;
const MAX_MAP_PIXELS = 50_000_000;
const SUPPORTED_TYPES = new Set(["image/png", "image/jpeg"]);

const props = defineProps<{
  floor: Floor;
  currentImageUrl: string;
  nodeCount: number;
  poiCount: number;
  busy: boolean;
}>();

const emit = defineEmits<{
  replace: [file: File, dimensions: MapImageDimensions];
}>();

const dialog = ref<HTMLDialogElement | null>(null);
const fileInput = ref<HTMLInputElement | null>(null);
const candidateFile = ref<File | null>(null);
const candidateUrl = ref("");
const candidateDimensions = ref<MapImageDimensions | null>(null);
const fileError = ref("");
const confirmed = ref(false);
const reading = ref(false);

const currentDimensions = computed<MapImageDimensions>(() => ({
  width: props.floor.mapRevision.imageWidth,
  height: props.floor.mapRevision.imageHeight,
}));

const assessment = computed(() =>
  candidateDimensions.value
    ? assessMapReplacement(
        currentDimensions.value,
        candidateDimensions.value,
      )
    : null,
);

const canReplace = computed(
  () =>
    Boolean(candidateFile.value && candidateDimensions.value) &&
    confirmed.value &&
    !reading.value &&
    !props.busy,
);

function cleanupPreview(): void {
  if (candidateUrl.value) {
    URL.revokeObjectURL(candidateUrl.value);
  }
  candidateUrl.value = "";
}

function reset(): void {
  cleanupPreview();
  candidateFile.value = null;
  candidateDimensions.value = null;
  fileError.value = "";
  confirmed.value = false;
  reading.value = false;
  if (fileInput.value) {
    fileInput.value.value = "";
  }
}

function open(): void {
  reset();
  dialog.value?.showModal();
}

function close(): void {
  dialog.value?.close();
}

function decodeDimensions(url: string): Promise<MapImageDimensions> {
  return new Promise((resolve, reject) => {
    const image = new Image();
    image.onload = () =>
      resolve({ width: image.naturalWidth, height: image.naturalHeight });
    image.onerror = () => reject(new Error("IMAGE_DECODE_FAILED"));
    image.src = url;
  });
}

async function selectFile(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  reset();
  if (!file) {
    return;
  }
  if (!SUPPORTED_TYPES.has(file.type)) {
    fileError.value = "请选择真实的 PNG 或 JPEG 图片。";
    return;
  }
  if (!file.size || file.size > MAX_MAP_BYTES) {
    fileError.value = "图片不能为空，且不得超过 15 MB。";
    return;
  }

  reading.value = true;
  const objectUrl = URL.createObjectURL(file);
  candidateUrl.value = objectUrl;
  try {
    const dimensions = await decodeDimensions(objectUrl);
    if (
      dimensions.width <= 0 ||
      dimensions.height <= 0 ||
      dimensions.width * dimensions.height > MAX_MAP_PIXELS
    ) {
      throw new Error("IMAGE_DIMENSIONS_INVALID");
    }
    candidateFile.value = file;
    candidateDimensions.value = dimensions;
  } catch (caught) {
    cleanupPreview();
    fileError.value =
      caught instanceof Error &&
      caught.message === "IMAGE_DIMENSIONS_INVALID"
        ? "图片尺寸无效或超过 5000 万像素。"
        : "浏览器无法解码这张图片。";
  } finally {
    reading.value = false;
  }
}

function scaleLabel(value: number): string {
  return `× ${value.toFixed(3).replace(/0+$/, "").replace(/\.$/, "")}`;
}

function submit(): void {
  if (
    canReplace.value &&
    candidateFile.value &&
    candidateDimensions.value
  ) {
    emit("replace", candidateFile.value, candidateDimensions.value);
  }
}

defineExpose({ open, close });
</script>

<template>
  <dialog
    ref="dialog"
    class="m-auto max-h-[calc(100vh-40px)] w-[720px] rounded-md border border-[#cfd5dc] bg-white p-0 text-[#172033] shadow-xl"
    @close="reset"
  >
    <form @submit.prevent="submit">
      <header
        class="flex items-start justify-between gap-4 border-b border-[#e4e7eb] p-5"
      >
        <div>
          <h2 class="text-base font-semibold">
            替换 {{ floor.code }} 底图
          </h2>
          <p class="mt-1 text-xs text-[#667085]">
            当前修订 {{ floor.mapRevision.revisionNo }}
          </p>
        </div>
        <ImageUp class="text-[#087f8c]" :size="22" />
      </header>

      <div class="max-h-[calc(100vh-190px)] overflow-y-auto p-5">
        <div class="flex items-center justify-between gap-3">
          <div class="min-w-0">
            <strong class="block truncate text-xs">
              {{ candidateFile?.name || "尚未选择候选图片" }}
            </strong>
            <span class="mt-0.5 block text-[10px] text-[#667085]">
              PNG/JPEG · 不超过 15 MB · 不超过 5000 万像素
            </span>
          </div>
          <label
            for="map-replacement-file"
            class="flex h-9 shrink-0 cursor-pointer items-center gap-1.5 rounded-md border border-[#cfd5dc] bg-white px-3 text-xs font-semibold text-[#344054] hover:bg-[#f3f5f7]"
          >
            <ImageUp :size="15" />
            选择图片
          </label>
          <input
            id="map-replacement-file"
            ref="fileInput"
            class="sr-only"
            type="file"
            accept=".png,.jpg,.jpeg,image/png,image/jpeg"
            @change="selectFile"
          />
        </div>

        <p
          v-if="fileError"
          class="mt-3 border-y border-[#fecaca] bg-[#fef2f2] px-2.5 py-2 text-xs text-[#b91c1c]"
          role="alert"
        >
          {{ fileError }}
        </p>

        <div class="mt-4 grid grid-cols-2 gap-3">
          <figure class="min-w-0">
            <figcaption
              class="mb-1.5 flex items-center justify-between text-[10px] text-[#667085]"
            >
              <span>当前底图</span>
              <span>
                {{ currentDimensions.width }} ×
                {{ currentDimensions.height }}
              </span>
            </figcaption>
            <div
              class="grid aspect-[5/3] place-items-center overflow-hidden border border-[#d7dce2] bg-[#eef1f4]"
            >
              <img
                :src="currentImageUrl"
                alt="当前楼层底图"
                class="max-h-full max-w-full object-contain"
              />
            </div>
          </figure>
          <figure class="min-w-0">
            <figcaption
              class="mb-1.5 flex items-center justify-between text-[10px] text-[#667085]"
            >
              <span>候选底图</span>
              <span v-if="candidateDimensions">
                {{ candidateDimensions.width }} ×
                {{ candidateDimensions.height }}
              </span>
            </figcaption>
            <div
              class="grid aspect-[5/3] place-items-center overflow-hidden border border-[#d7dce2] bg-[#eef1f4]"
            >
              <img
                v-if="candidateUrl"
                :src="candidateUrl"
                alt="候选楼层底图"
                class="max-h-full max-w-full object-contain"
              />
              <ImageUp v-else class="text-[#98a2b3]" :size="24" />
            </div>
          </figure>
        </div>

        <section
          v-if="assessment"
          class="mt-4 border-y px-3 py-3"
          :class="
            assessment.risk === 'nonuniform-scale'
              ? 'border-[#fecaca] bg-[#fef2f2]'
              : assessment.risk === 'uniform-scale'
                ? 'border-[#fed7aa] bg-[#fff7ed]'
                : 'border-[#bbf7d0] bg-[#f0fdf4]'
          "
        >
          <div class="grid grid-cols-[20px_minmax(0,1fr)] gap-2">
            <TriangleAlert
              v-if="assessment.risk === 'nonuniform-scale'"
              class="mt-0.5 text-[#b91c1c]"
              :size="16"
            />
            <Scale
              v-else
              class="mt-0.5"
              :class="
                assessment.risk === 'uniform-scale'
                  ? 'text-[#b45309]'
                  : 'text-[#15803d]'
              "
              :size="16"
            />
            <div>
              <strong class="block text-xs">
                {{
                  assessment.risk === "nonuniform-scale"
                    ? "长宽比例变化，标注将非等比缩放"
                    : assessment.risk === "uniform-scale"
                      ? "图片尺寸变化，标注将近似等比缩放"
                      : "图片尺寸一致，标注数值坐标不变"
                }}
              </strong>
              <p class="mt-1 text-[10px] leading-4 text-[#667085]">
                X {{ scaleLabel(assessment.widthScale) }} · Y
                {{ scaleLabel(assessment.heightScale) }} · 影响
                {{ nodeCount }} 个节点和 {{ poiCount }} 个 POI
              </p>
            </div>
          </div>
        </section>

        <label
          v-if="assessment"
          class="mt-4 grid grid-cols-[16px_minmax(0,1fr)] gap-2 text-[11px] leading-4 text-[#344054]"
        >
          <input
            v-model="confirmed"
            type="checkbox"
            class="mt-0.5 size-4 accent-[#087f8c]"
          />
          <span>
            我了解系统会按 X/Y 比例调整本层坐标，并会在发布前核对节点、POI
            和跨层停靠位置。
          </span>
        </label>
      </div>

      <footer
        class="flex justify-end gap-2 border-t border-[#e4e7eb] bg-[#f8fafb] p-4"
      >
        <button
          class="h-9 rounded-md border border-[#cfd5dc] bg-white px-3 text-xs font-semibold text-[#344054]"
          type="button"
          :disabled="busy"
          @click="close"
        >
          取消
        </button>
        <button
          class="h-9 rounded-md bg-[#087f8c] px-3 text-xs font-semibold text-white disabled:cursor-not-allowed disabled:opacity-40"
          type="submit"
          :disabled="!canReplace"
        >
          {{ busy ? "正在替换" : "确认替换" }}
        </button>
      </footer>
    </form>
  </dialog>
</template>
