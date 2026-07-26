<script setup lang="ts">
import { resolveMapImageUrl } from "@medroute/api-client";
import {
  floorExtent,
  pixelToMap,
  type Floor,
  type NavigationPoi,
  type RouteSegment,
} from "@medroute/map-core";
import {
  createFloorProjection,
  createRouteLayer,
} from "@medroute/map-core/openlayers";
import Feature from "ol/Feature.js";
import Point from "ol/geom/Point.js";
import ImageLayer from "ol/layer/Image.js";
import VectorLayer from "ol/layer/Vector.js";
import Map from "ol/Map.js";
import View from "ol/View.js";
import { defaults as defaultControls } from "ol/control/defaults.js";
import { defaults as defaultInteractions } from "ol/interaction/defaults.js";
import ImageStatic from "ol/source/ImageStatic.js";
import VectorSource from "ol/source/Vector.js";
import {
  Circle as CircleStyle,
  Fill,
  Stroke,
  Style,
  Text,
} from "ol/style.js";
import { getCenter } from "ol/extent.js";
import { onBeforeUnmount, onMounted, ref, watch } from "vue";

const props = defineProps<{
  floor: Floor;
  segment: RouteSegment | null;
  startPoi: NavigationPoi | null;
  endPoi: NavigationPoi | null;
  apiBase: string;
  assetBase: string;
}>();

const target = ref<HTMLDivElement | null>(null);
let map: Map | null = null;
let resizeObserver: ResizeObserver | null = null;

function endpointStyle(label: string, color: string): Style {
  return new Style({
    image: new CircleStyle({
      radius: 11,
      fill: new Fill({ color }),
      stroke: new Stroke({ color: "#ffffff", width: 3 }),
    }),
    text: new Text({
      text: label,
      font: "700 11px system-ui, sans-serif",
      fill: new Fill({ color: "#ffffff" }),
    }),
    zIndex: 30,
  });
}

function setupMap(): void {
  if (!target.value) {
    return;
  }
  map?.setTarget(undefined);
  const projection = createFloorProjection(props.floor);
  const extent = floorExtent(props.floor);
  const imageUrl = resolveMapImageUrl(
    props.floor.mapRevision.imageUrl,
    props.apiBase,
    props.assetBase,
  );
  const endpointSource = new VectorSource<Feature<Point>>();
  if (props.startPoi?.floorId === props.floor.id) {
    const feature = new Feature({
      geometry: new Point(
        pixelToMap(
          [props.startPoi.x, props.startPoi.y],
          props.floor.mapRevision.imageHeight,
        ),
      ),
    });
    feature.setStyle(endpointStyle("起", "#15803d"));
    endpointSource.addFeature(feature);
  }
  if (props.endPoi?.floorId === props.floor.id) {
    const feature = new Feature({
      geometry: new Point(
        pixelToMap(
          [props.endPoi.x, props.endPoi.y],
          props.floor.mapRevision.imageHeight,
        ),
      ),
    });
    feature.setStyle(endpointStyle("终", "#b91c1c"));
    endpointSource.addFeature(feature);
  }
  const routeLayer = createRouteLayer(props.segment, props.floor);
  map = new Map({
    target: target.value,
    controls: defaultControls({
      attribution: false,
      rotate: false,
      zoom: true,
    }),
    interactions: defaultInteractions({
      altShiftDragRotate: false,
      pinchRotate: false,
    }),
    layers: [
      new ImageLayer({
        source: new ImageStatic({
          url: imageUrl,
          projection,
          imageExtent: extent,
        }),
      }),
      routeLayer,
      new VectorLayer({
        source: endpointSource,
        zIndex: 30,
      }),
    ],
    view: new View({
      projection,
      center: getCenter(extent),
      extent,
      showFullExtent: true,
      constrainOnlyCenter: false,
      maxZoom: 8,
    }),
  });
  requestAnimationFrame(() => {
    map?.updateSize();
    const routeFeature = routeLayer.getSource()?.getFeatures()[0];
    const routeExtent = routeFeature?.getGeometry()?.getExtent();
    map?.getView().fit(routeExtent ?? extent, {
      padding: [42, 32, 42, 32],
      maxZoom: 3,
    });
  });
}

onMounted(() => {
  setupMap();
  if (target.value) {
    resizeObserver = new ResizeObserver(() => map?.updateSize());
    resizeObserver.observe(target.value);
  }
});

watch(
  () => [
    props.floor.id,
    props.floor.mapRevision.id,
    props.segment?.sequence,
    props.startPoi?.id,
    props.endPoi?.id,
  ],
  setupMap,
);

onBeforeUnmount(() => {
  resizeObserver?.disconnect();
  map?.setTarget(undefined);
});
</script>

<template>
  <div
    ref="target"
    class="route-map"
    aria-label="当前楼层导航地图"
  ></div>
</template>
