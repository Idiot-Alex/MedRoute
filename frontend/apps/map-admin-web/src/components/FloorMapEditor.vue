<script setup lang="ts">
import {
  createFloorLayerBundle,
  refreshGraphSources,
  setEdgeSketch,
  type FloorLayerBundle,
} from "@medroute/map-core/openlayers";
import {
  floorExtent,
  graphForFloor,
  mapToPixel,
  nodeMap,
  pixelToMap,
  type DraftGraph,
  type EditorTool,
  type Floor,
  type MapSelection,
  type PixelCoordinate,
} from "@medroute/map-core";
import Feature from "ol/Feature.js";
import type Point from "ol/geom/Point.js";
import Translate from "ol/interaction/Translate.js";
import { defaults as defaultInteractions } from "ol/interaction/defaults.js";
import Map from "ol/Map.js";
import type MapBrowserEvent from "ol/MapBrowserEvent.js";
import View from "ol/View.js";
import { getCenter } from "ol/extent.js";
import { onBeforeUnmount, onMounted, ref, watch } from "vue";

const props = defineProps<{
  floor: Floor;
  graph: DraftGraph;
  imageUrl: string;
  selection: MapSelection | null;
  tool: EditorTool;
  edgeStartId: string | null;
  editable: boolean;
  revision: number;
}>();

const emit = defineEmits<{
  select: [selection: MapSelection | null];
  addNode: [coordinate: PixelCoordinate];
  addPoi: [nodeId: string];
  addStop: [nodeId: string];
  chooseEdgeNode: [nodeId: string];
  moveNode: [nodeId: string, coordinate: PixelCoordinate];
}>();

const target = ref<HTMLDivElement | null>(null);
let map: Map | null = null;
let bundle: FloorLayerBundle | null = null;
let translate: Translate | null = null;
let resizeObserver: ResizeObserver | null = null;
let draggingNodeId: string | null = null;

function setupMap(): void {
  if (!target.value) {
    return;
  }
  map?.setTarget(undefined);
  bundle = createFloorLayerBundle(props.floor, props.imageUrl);
  const extent = floorExtent(props.floor);
  map = new Map({
    target: target.value,
    layers: [
      bundle.image,
      bundle.layers.edges,
      bundle.layers.nodes,
      bundle.layers.stops,
      bundle.layers.pois,
      bundle.layers.sketch,
    ],
    interactions: defaultInteractions({
      altShiftDragRotate: false,
      pinchRotate: false,
    }),
    view: new View({
      projection: bundle.projection,
      center: getCenter(extent),
      extent,
      showFullExtent: true,
      constrainOnlyCenter: false,
      maxZoom: 8,
    }),
  });
  translate = new Translate({
    layers: [bundle.layers.nodes],
    hitTolerance: 10,
  });
  map.addInteraction(translate);
  translate.on("translatestart", (event) => {
    const feature = event.features.item(0);
    draggingNodeId = String(feature?.get("objectId") ?? "") || null;
    if (draggingNodeId) {
      emit("select", { kind: "node", id: draggingNodeId });
    }
  });
  translate.on("translating", (event) => {
    const feature = event.features.item(0) as Feature<Point> | undefined;
    if (feature && draggingNodeId) {
      previewConnectedGeometry(feature, draggingNodeId);
    }
  });
  translate.on("translateend", (event) => {
    const feature = event.features.item(0) as Feature<Point> | undefined;
    const coordinate = feature?.getGeometry()?.getCoordinates();
    if (draggingNodeId && coordinate) {
      emit(
        "moveNode",
        draggingNodeId,
        mapToPixel(
          coordinate as PixelCoordinate,
          props.floor.mapRevision.imageHeight,
        ),
      );
    }
    draggingNodeId = null;
  });
  map.on("singleclick", handleMapClick);
  map.on("pointermove", handlePointerMove);
  refresh();
  requestAnimationFrame(() => {
    map?.updateSize();
    map?.getView().fit(extent, {
      padding: [30, 30, 30, 30],
    });
  });
}

function refresh(): void {
  if (!bundle) {
    return;
  }
  refreshGraphSources(
    bundle.sources,
    props.graph,
    props.floor,
    props.selection,
    props.edgeStartId,
  );
  if (props.tool !== "edge" || !props.edgeStartId) {
    setEdgeSketch(bundle.sources.sketch, null, null);
  }
  translate?.setActive(props.editable && props.tool === "select");
  if (target.value) {
    target.value.style.cursor = {
      select: "default",
      node: "crosshair",
      edge: "crosshair",
      poi: "copy",
      stop: "copy",
    }[props.tool];
  }
}

function nodeFeatureAt(event: MapBrowserEvent): Feature | null {
  if (!map || !bundle) {
    return null;
  }
  let result: Feature | null = null;
  map.forEachFeatureAtPixel(
    event.pixel,
    (feature) => {
      result = feature as Feature;
      return feature;
    },
    {
      hitTolerance: 12,
      layerFilter: (layer) => layer === bundle?.layers.nodes,
    },
  );
  return result;
}

function anyFeatureAt(event: MapBrowserEvent): Feature | null {
  if (!map || !bundle) {
    return null;
  }
  let result: Feature | null = null;
  map.forEachFeatureAtPixel(
    event.pixel,
    (feature) => {
      result = feature as Feature;
      return feature;
    },
    {
      hitTolerance: 10,
      layerFilter: (layer) =>
        layer === bundle?.layers.nodes ||
        layer === bundle?.layers.edges ||
        layer === bundle?.layers.stops ||
        layer === bundle?.layers.pois,
    },
  );
  return result;
}

function handleMapClick(event: MapBrowserEvent): void {
  if (!props.editable && props.tool !== "select") {
    return;
  }
  if (props.tool === "node") {
    emit(
      "addNode",
      mapToPixel(
        event.coordinate as PixelCoordinate,
        props.floor.mapRevision.imageHeight,
      ),
    );
    return;
  }
  if (
    props.tool === "edge" ||
    props.tool === "poi" ||
    props.tool === "stop"
  ) {
    const feature = nodeFeatureAt(event);
    const nodeId = String(feature?.get("objectId") ?? "");
    if (!nodeId) {
      return;
    }
    if (props.tool === "edge") {
      emit("chooseEdgeNode", nodeId);
    } else if (props.tool === "poi") {
      emit("addPoi", nodeId);
    } else {
      emit("addStop", nodeId);
    }
    return;
  }
  const feature = anyFeatureAt(event);
  const kind = feature?.get("kind");
  const id = feature?.get("objectId");
  emit(
    "select",
    kind && id ? { kind, id: String(id) } as MapSelection : null,
  );
}

function handlePointerMove(event: MapBrowserEvent): void {
  if (!map || !bundle) {
    return;
  }
  if (props.tool !== "edge" || !props.edgeStartId) {
    return;
  }
  const current = graphForFloor(props.graph, props.floor.id);
  const fromNode = current.nodes.find(
    (node) => node.id === props.edgeStartId,
  );
  if (!fromNode) {
    return;
  }
  const candidate = nodeFeatureAt(event);
  const endCoordinate =
    (candidate?.getGeometry() as Point | undefined)?.getCoordinates() ??
    event.coordinate;
  setEdgeSketch(
    bundle.sources.sketch,
    pixelToMap(
      [fromNode.x, fromNode.y],
      props.floor.mapRevision.imageHeight,
    ),
    endCoordinate as PixelCoordinate,
  );
}

function previewConnectedGeometry(
  nodeFeature: Feature<Point>,
  nodeId: string,
): void {
  if (!bundle) {
    return;
  }
  const coordinate = nodeFeature.getGeometry()?.getCoordinates();
  if (!coordinate) {
    return;
  }
  const current = graphForFloor(props.graph, props.floor.id);
  const nodes = nodeMap(current.nodes);
  for (const edge of current.edges) {
    if (edge.fromNodeId !== nodeId && edge.toNodeId !== nodeId) {
      continue;
    }
    const from = nodes.get(edge.fromNodeId);
    const to = nodes.get(edge.toNodeId);
    const edgeFeature = bundle.sources.edges.getFeatureById(
      `edge:${edge.id}`,
    );
    if (!from || !to || !edgeFeature) {
      continue;
    }
    const fromCoordinate =
      edge.fromNodeId === nodeId
        ? coordinate
        : pixelToMap(
            [from.x, from.y],
            props.floor.mapRevision.imageHeight,
          );
    const toCoordinate =
      edge.toNodeId === nodeId
        ? coordinate
        : pixelToMap([to.x, to.y], props.floor.mapRevision.imageHeight);
    edgeFeature.getGeometry()?.setCoordinates([
      fromCoordinate,
      toCoordinate,
    ]);
  }
}

onMounted(() => {
  setupMap();
  if (target.value) {
    resizeObserver = new ResizeObserver(() => map?.updateSize());
    resizeObserver.observe(target.value);
  }
});

watch(
  () => [props.floor.id, props.floor.mapRevision.id, props.imageUrl],
  setupMap,
);
watch(
  () => [
    props.revision,
    props.selection?.kind,
    props.selection?.id,
    props.edgeStartId,
    props.tool,
    props.editable,
  ],
  refresh,
);

onBeforeUnmount(() => {
  resizeObserver?.disconnect();
  map?.setTarget(undefined);
});
</script>

<template>
  <div
    ref="target"
    class="floor-map"
    aria-label="楼层地图编辑区域"
  ></div>
</template>
