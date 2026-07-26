<script setup lang="ts">
import {
  filterNavigationPois,
  navigationCategoryLabel,
  normalizeNavigationSearch,
  type Floor,
  type NavigationPoi,
} from "@medroute/map-core";
import {
  Accessibility,
  Check,
  MapPin,
  Search,
  X,
} from "@lucide/vue";
import { computed, nextTick, ref } from "vue";

type Endpoint = "start" | "end";

const props = defineProps<{
  pois: NavigationPoi[];
  floors: Floor[];
  startPoiId: string;
  endPoiId: string;
}>();

const emit = defineEmits<{
  select: [endpoint: Endpoint, poiId: string];
}>();

const dialog = ref<HTMLDialogElement | null>(null);
const searchInput = ref<HTMLInputElement | null>(null);
const endpoint = ref<Endpoint>("start");
const query = ref("");
const floorId = ref("");

const results = computed(() =>
  filterNavigationPois(props.pois, query.value, floorId.value),
);

const selectedPoiId = computed(() =>
  endpoint.value === "start" ? props.startPoiId : props.endPoiId,
);

function open(nextEndpoint: Endpoint): void {
  endpoint.value = nextEndpoint;
  query.value = "";
  floorId.value = "";
  dialog.value?.showModal();
  void nextTick(() => searchInput.value?.focus());
}

function close(): void {
  dialog.value?.close();
}

function selectPoi(poiId: string): void {
  emit("select", endpoint.value, poiId);
  close();
}

function visibleKeywords(poi: NavigationPoi): string[] {
  const excluded = new Set([
    normalizeNavigationSearch(poi.name),
    normalizeNavigationSearch(navigationCategoryLabel(poi.category)),
  ]);
  const unique = poi.searchKeywords.filter((keyword, index, keywords) => {
    const normalized = normalizeNavigationSearch(keyword);
    return (
      normalized &&
      !excluded.has(normalized) &&
      keywords.findIndex(
        (candidate) => normalizeNavigationSearch(candidate) === normalized,
      ) === index
    );
  });
  const chinese = unique.filter((keyword) =>
    /[\u3400-\u9fff]/.test(keyword),
  );
  return (chinese.length ? chinese : unique).slice(0, 2);
}

defineExpose({ open, close });
</script>

<template>
  <dialog
    ref="dialog"
    class="fixed inset-x-0 bottom-0 top-auto m-0 h-[min(78dvh,680px)] w-full max-w-none rounded-t-md border border-b-0 border-[#cfd5dc] bg-white p-0 text-[#172033] shadow-xl backdrop:bg-[#172033]/35 sm:inset-0 sm:m-auto sm:h-[min(680px,calc(100dvh-40px))] sm:w-[520px] sm:rounded-md sm:border"
  >
    <div class="grid h-full grid-rows-[auto_auto_auto_minmax(0,1fr)]">
      <header
        class="flex items-center justify-between border-b border-[#e4e7eb] px-4 py-3.5"
      >
        <div>
          <p class="text-[10px] font-semibold text-[#087f8c]">
            {{ endpoint === "start" ? "选择起点" : "选择目的地" }}
          </p>
          <h2 class="mt-0.5 text-base font-semibold">院内地点</h2>
        </div>
        <button
          class="grid size-10 place-items-center rounded-md text-[#667085] hover:bg-[#f3f5f7] active:translate-y-px"
          type="button"
          title="关闭地点搜索"
          @click="close"
        >
          <X :size="20" />
          <span class="sr-only">关闭地点搜索</span>
        </button>
      </header>

      <div class="border-b border-[#e4e7eb] p-3.5">
        <label class="relative block">
          <Search
            class="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-[#667085]"
            :size="17"
          />
          <span class="sr-only">搜索地点</span>
          <input
            ref="searchInput"
            v-model="query"
            type="search"
            inputmode="search"
            autocomplete="off"
            placeholder="搜索科室、地点或关键词"
            class="h-11 w-full rounded-md border border-[#cfd5dc] bg-white pl-10 pr-3 text-sm outline-none focus:border-[#087f8c] focus:ring-2 focus:ring-[#b9dfe0]"
          />
        </label>
        <div
          class="mt-3 flex gap-1.5 overflow-x-auto"
          role="tablist"
          aria-label="按楼层筛选"
        >
          <button
            class="h-9 min-w-14 shrink-0 rounded-md border px-3 text-xs font-semibold"
            :class="
              floorId === ''
                ? 'border-[#087f8c] bg-[#e3f3f3] text-[#066d77]'
                : 'border-[#d7dce2] bg-white text-[#667085]'
            "
            type="button"
            role="tab"
            :aria-selected="floorId === ''"
            @click="floorId = ''"
          >
            全部
          </button>
          <button
            v-for="floor in floors"
            :key="floor.id"
            class="h-9 min-w-14 shrink-0 rounded-md border px-3 text-xs font-semibold"
            :class="
              floorId === floor.id
                ? 'border-[#087f8c] bg-[#e3f3f3] text-[#066d77]'
                : 'border-[#d7dce2] bg-white text-[#667085]'
            "
            type="button"
            role="tab"
            :aria-selected="floorId === floor.id"
            @click="floorId = floor.id"
          >
            {{ floor.code }}
          </button>
        </div>
      </div>

      <p
        class="border-b border-[#e4e7eb] px-4 py-2 text-[11px] text-[#667085]"
        aria-live="polite"
      >
        {{ results.length }} 个地点
      </p>

      <div class="min-h-0 overflow-y-auto">
        <ul
          v-if="results.length"
          class="divide-y divide-[#e4e7eb]"
          aria-label="地点搜索结果"
        >
          <li v-for="poi in results" :key="poi.id">
            <button
              class="grid min-h-16 w-full grid-cols-[36px_minmax(0,1fr)_44px] items-center gap-2 px-4 py-2.5 text-left hover:bg-[#f8fafb] active:bg-[#eef4f4]"
              type="button"
              :aria-label="`${poi.name}，${poi.floorCode}`"
              :aria-current="poi.id === selectedPoiId ? 'true' : undefined"
              @click="selectPoi(poi.id)"
            >
              <span
                class="grid size-9 place-items-center rounded-md"
                :class="
                  poi.id === selectedPoiId
                    ? 'bg-[#dff3f2] text-[#066d77]'
                    : 'bg-[#eef1f4] text-[#667085]'
                "
              >
                <Check v-if="poi.id === selectedPoiId" :size="17" />
                <MapPin v-else :size="17" />
              </span>
              <span class="min-w-0">
                <strong class="block truncate text-sm">{{ poi.name }}</strong>
                <span
                  class="mt-0.5 flex min-w-0 items-center gap-1 truncate text-[11px] text-[#667085]"
                >
                  {{ navigationCategoryLabel(poi.category) }}
                  <template v-if="visibleKeywords(poi).length">
                    · {{ visibleKeywords(poi).join(" · ") }}
                  </template>
                  <Accessibility
                    v-if="poi.accessible"
                    class="ml-0.5 shrink-0"
                    :size="12"
                    aria-label="无障碍可达"
                  />
                </span>
              </span>
              <span
                class="grid h-8 min-w-11 place-items-center rounded-md border border-[#d7dce2] bg-white px-1 text-xs font-bold text-[#344054]"
              >
                {{ poi.floorCode }}
              </span>
            </button>
          </li>
        </ul>
        <div
          v-else
          class="grid min-h-40 place-items-center p-6 text-center"
        >
          <div>
            <Search class="mx-auto text-[#98a2b3]" :size="24" />
            <strong class="mt-3 block text-sm">没有找到匹配地点</strong>
          </div>
        </div>
      </div>
    </div>
  </dialog>
</template>
