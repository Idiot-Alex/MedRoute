<script setup lang="ts">
import {
  ApiError,
  MedRouteApiClient,
  resolveMapImageUrl,
  type CreateDraftRequest,
} from "@medroute/api-client";
import {
  demoAdminWorkspace,
  demoNavigationPois,
  demoOperationClosures,
  demoReleaseList,
  demoRouteRegressionCases,
  demoValidation,
} from "@medroute/api-client/demo";
import {
  DEFAULT_BUILDING_ID,
  clampPixel,
  rescaleFloorCoordinates,
  canPublishRelease,
  canRollbackRelease,
  graphDependencies,
  graphForFloor,
  parsePoiKeywords,
  rebindEdgeEndpoint,
  rebindPoiToNode,
  removeBasicGraphObject,
  resolveNavigationBaseUrl,
  selectionForValidationIssue,
  stopsForConnector,
  validateConnectorRelations,
  moveNode,
  nextCode,
  selectedObject,
  type AdminValidation,
  type AdminWorkspace,
  type Connector,
  type ConnectorIssue,
  type ConnectorStop,
  type EditorTool,
  type GraphDependency,
  type MapSelection,
  type MapImageDimensions,
  type NavigationPoi,
  type OperationClosureListResponse,
  type OperationTargetType,
  type PathEdge,
  type PathNode,
  type PixelCoordinate,
  type Poi,
  type ReleaseListItem,
  type RouteRegressionCase,
  type RouteRegressionCasePayload,
  type CreateOperationClosurePayload,
  type ValidationIssue,
  type VerticalLink,
} from "@medroute/map-core";
import {
  CirclePlus,
  Cloud,
  GitBranch,
  ImageUp,
  LocateFixed,
  MapPin,
  MousePointer2,
  RefreshCw,
  Save,
  Server,
  TriangleAlert,
} from "@lucide/vue";
import {
  computed,
  defineAsyncComponent,
  onBeforeUnmount,
  onMounted,
  reactive,
  ref,
} from "vue";
import ConnectorValidation from "./components/ConnectorValidation.vue";
import CrossFloorInspector from "./components/CrossFloorInspector.vue";
import CrossFloorPanel from "./components/CrossFloorPanel.vue";
import DeleteGraphObjectDialog from "./components/DeleteGraphObjectDialog.vue";
import FloorMapEditor from "./components/FloorMapEditor.vue";
import MapReplacementDialog from "./components/MapReplacementDialog.vue";
import PublicationWorkbench from "./components/PublicationWorkbench.vue";
import QrCodeDialog from "./components/QrCodeDialog.vue";
import ReleaseDialogs from "./components/ReleaseDialogs.vue";
import ReleasePanel from "./components/ReleasePanel.vue";
import RouteRegressionDialog from "./components/RouteRegressionDialog.vue";
import {
  confirmDiscardUnsavedChanges,
  createUnsavedChangesBeforeUnloadHandler,
} from "./unsaved-changes";

const OperationsWorkbench = defineAsyncComponent(
  () => import("./components/OperationsWorkbench.vue"),
);

const BasicGraphInspector = defineAsyncComponent(
  () => import("./components/BasicGraphInspector.vue"),
);

const params = new URLSearchParams(window.location.search);
const apiBase = (
  params.get("api") ?? window.location.origin
).replace(/\/$/, "");
const buildingId = params.get("building") ?? DEFAULT_BUILDING_ID;
const assetBase = params.get("assets") ?? window.location.origin;
const client = new MedRouteApiClient({ apiBase });

const workspace = ref<AdminWorkspace | null>(null);
const releases = ref<ReleaseListItem[]>([]);
const regressionCases = ref<RouteRegressionCase[]>([]);
const operations = ref<OperationClosureListResponse | null>(null);
const publishedPois = ref<NavigationPoi[]>([]);
const releaseValidation = ref<AdminValidation | null>(null);
const etag = ref("");
const activeFloorId = ref("");
const tool = ref<EditorTool>("select");
const selection = ref<MapSelection | null>(null);
const edgeStartId = ref<string | null>(null);
const activeConnectorId = ref<string | null>(null);
const rightTab = ref<
  "properties" | "relations" | "publication" | "operations"
>("properties");
const dirty = ref(false);
const busy = ref(false);
const error = ref("");
const toast = ref("");
const renderRevision = ref(0);
const demoMode = ref(params.get("demo") === "1");
const demoInitialized = ref(false);
const demoWorkspaces = new Map<string, AdminWorkspace>();
const demoMapObjectUrls = new Set<string>();
const connectorDialog = ref<HTMLDialogElement | null>(null);
const linkDialog = ref<HTMLDialogElement | null>(null);
const releaseDialogs = ref<InstanceType<typeof ReleaseDialogs> | null>(null);
const regressionDialog = ref<
  InstanceType<typeof RouteRegressionDialog> | null
>(null);
const mapReplacementDialog = ref<
  InstanceType<typeof MapReplacementDialog> | null
>(null);
const qrCodeDialog = ref<InstanceType<typeof QrCodeDialog> | null>(null);
const deleteGraphObjectDialog = ref<
  InstanceType<typeof DeleteGraphObjectDialog> | null
>(null);
const connectorDraft = reactive({
  name: "",
  code: "",
  type: "elevator",
  accessScope: "public",
  accessible: true,
});
const linkDraft = reactive({
  connectorId: "",
  fromStopId: "",
  toStopId: "",
  timeSeconds: 30,
  distanceMeters: 0,
  direction: "both",
  accessible: true,
});

const tools = [
  { id: "select" as const, label: "选择", icon: MousePointer2 },
  { id: "node" as const, label: "节点", icon: CirclePlus },
  { id: "edge" as const, label: "路径", icon: GitBranch },
  { id: "poi" as const, label: "POI", icon: MapPin },
];

interface DeleteObjectDependency extends Omit<GraphDependency, "kind"> {
  kind: GraphDependency["kind"] | "route_regression_case";
}

const activeFloor = computed(
  () =>
    workspace.value?.floors.find(
      (floor) => floor.id === activeFloorId.value,
    ) ?? null,
);

const currentGraph = computed(() =>
  workspace.value && activeFloor.value
    ? graphForFloor(workspace.value.graph, activeFloor.value.id)
    : null,
);

const editable = computed(
  () => workspace.value?.release.status === "draft" && !busy.value,
);

const connectorIssues = computed<ConnectorIssue[]>(() =>
  workspace.value
    ? validateConnectorRelations(
        workspace.value.graph,
        workspace.value.floors,
      )
    : [],
);

const connectorErrorCount = computed(
  () =>
    connectorIssues.value.filter((issue) => issue.severity === "error")
      .length,
);

const publicationIssueCount = computed(() => {
  if (
    !workspace.value ||
    !releaseValidation.value ||
    releaseValidation.value.releaseId !== workspace.value.release.id ||
    releaseValidation.value.contentRevision !==
      workspace.value.release.contentRevision
  ) {
    return 0;
  }
  return (
    releaseValidation.value.errors.length +
    releaseValidation.value.warnings.length
  );
});

const activePublishedRelease = computed(
  () =>
    releases.value.find(
      (release) => release.status === "published" && release.active,
    ) ?? null,
);

const relationSelection = computed<MapSelection | null>(() =>
  selection.value &&
  ["connector", "stop", "link"].includes(selection.value.kind)
    ? selection.value
    : null,
);

const linkDialogConnector = computed<Connector | null>(
  () =>
    workspace.value?.graph.connectors.find(
      (connector) => connector.id === linkDraft.connectorId,
    ) ?? null,
);

const linkDialogStops = computed<ConnectorStop[]>(() =>
  workspace.value && linkDraft.connectorId
    ? stopsForConnector(
        workspace.value.graph,
        linkDraft.connectorId,
        workspace.value.floors,
      )
    : [],
);

const selectedNode = computed<PathNode | null>(() => {
  if (!workspace.value || selection.value?.kind !== "node") {
    return null;
  }
  return (
    (selectedObject(workspace.value.graph, selection.value) as PathNode) ??
    null
  );
});

const selectedEdge = computed<PathEdge | null>(() => {
  if (!workspace.value || selection.value?.kind !== "edge") {
    return null;
  }
  return (
    (selectedObject(workspace.value.graph, selection.value) as PathEdge) ??
    null
  );
});

const selectedPoi = computed<Poi | null>(() => {
  if (!workspace.value || selection.value?.kind !== "poi") {
    return null;
  }
  return (selectedObject(workspace.value.graph, selection.value) as Poi) ?? null;
});

const basicSelection = computed<MapSelection | null>(() =>
  selection.value &&
  ["node", "edge", "poi"].includes(selection.value.kind)
    ? selection.value
    : null,
);

const selectedGraphDependencies = computed<GraphDependency[]>(() =>
  workspace.value && basicSelection.value
    ? graphDependencies(workspace.value.graph, basicSelection.value)
    : [],
);

const selectedPoiAllowsQrCode = computed(
  () =>
    Boolean(
      selectedPoi.value?.enabled &&
        workspace.value &&
        workspace.value.release.id === activePublishedRelease.value?.id,
    ),
);

const imageUrl = computed(() =>
  activeFloor.value
    ? resolveMapImageUrl(
        activeFloor.value.mapRevision.imageUrl,
        apiBase,
        assetBase,
      )
    : "",
);

function resolveDefaultNavigationBaseUrl(): string {
  return resolveNavigationBaseUrl({
    currentOrigin: window.location.origin,
    apiBase,
    configuredUrl: params.get("navigation"),
    development: import.meta.env.DEV,
    apiExplicit: params.has("api"),
  });
}

const defaultNavigationBaseUrl = resolveDefaultNavigationBaseUrl();

function initializeDemoSession(): void {
  if (demoInitialized.value) {
    return;
  }
  const fixture = demoReleaseList();
  releases.value = fixture.items;
  regressionCases.value = demoRouteRegressionCases();
  operations.value = demoOperationClosures();
  publishedPois.value = demoNavigationPois();
  demoWorkspaces.clear();
  for (const release of releases.value) {
    demoWorkspaces.set(release.id, demoAdminWorkspace(release.id));
  }
  demoInitialized.value = true;
}

function cloneWorkspace(source: AdminWorkspace): AdminWorkspace {
  return JSON.parse(JSON.stringify(source)) as AdminWorkspace;
}

function syncDemoOperations(source: AdminWorkspace): void {
  const nextOperations = demoOperationClosures(
    source.release.id,
    source.release.code,
    source,
  );
  const validTargetKeys = new Set(
    nextOperations.targets.map(
      (target) => `${target.targetType}:${target.id}`,
    ),
  );
  nextOperations.items = (operations.value?.items ?? []).filter((item) =>
    validTargetKeys.has(`${item.targetType}:${item.targetId}`),
  );
  operations.value = nextOperations;
  publishedPois.value = demoNavigationPois(source);
}

function pruneDemoMapObjectUrls(): void {
  const referenced = new Set(
    [...demoWorkspaces.values()].flatMap((item) =>
      item.floors.map((floor) => floor.mapRevision.imageUrl),
    ),
  );
  for (const url of demoMapObjectUrls) {
    if (referenced.has(url)) {
      continue;
    }
    URL.revokeObjectURL(url);
    demoMapObjectUrls.delete(url);
  }
}

function clearDemoMapObjectUrls(): void {
  for (const url of demoMapObjectUrls) {
    URL.revokeObjectURL(url);
  }
  demoMapObjectUrls.clear();
}

function preferredRelease(
  preferredReleaseId?: string,
): ReleaseListItem | null {
  return (
    (preferredReleaseId
      ? releases.value.find((release) => release.id === preferredReleaseId)
      : null) ??
    releases.value.find((release) => release.status === "draft") ??
    releases.value.find((release) => release.active) ??
    releases.value[0] ??
    null
  );
}

function applyWorkspace(nextWorkspace: AdminWorkspace, nextEtag: string): void {
  const previousFloorId = activeFloorId.value;
  workspace.value = nextWorkspace;
  etag.value = nextEtag;
  activeFloorId.value = nextWorkspace.floors.some(
    (floor) => floor.id === previousFloorId,
  )
    ? previousFloorId
    : nextWorkspace.floors[0]?.id ?? "";
  activeConnectorId.value =
    nextWorkspace.graph.connectors[0]?.id ?? null;
  selection.value = null;
  edgeStartId.value = null;
  tool.value = "select";
  rightTab.value = "properties";
  releaseValidation.value = null;
  dirty.value = false;
  renderRevision.value += 1;
}

async function load(preferredReleaseId?: string): Promise<void> {
  busy.value = true;
  error.value = "";
  selection.value = null;
  edgeStartId.value = null;
  try {
    if (demoMode.value) {
      initializeDemoSession();
    } else {
      const [
        releaseList,
        regressionList,
        operationList,
        navigationPoiList,
      ] = await Promise.all([
        client.listReleases(buildingId),
        client.listRouteRegressionCases(buildingId),
        client.operationClosures(buildingId),
        client.navigationPois(buildingId),
      ]);
      if (navigationPoiList.releaseId !== operationList.releaseId) {
        throw new ApiError(
          "运营状态和发布 POI 来自不同地图版本，请重新加载。",
          409,
          "ACTIVE_RELEASE_CHANGED",
        );
      }
      releases.value = releaseList.items;
      regressionCases.value = regressionList.items;
      operations.value = operationList;
      publishedPois.value = navigationPoiList.items;
    }
    const release = preferredRelease(preferredReleaseId);
    if (!release) {
      throw new ApiError(
        "当前楼栋没有可维护的地图版本。",
        404,
        "NO_RELEASE",
      );
    }
    if (demoMode.value) {
      const nextWorkspace =
        demoWorkspaces.get(release.id) ??
        demoAdminWorkspace(release.id);
      applyWorkspace(
        cloneWorkspace(nextWorkspace),
        String(nextWorkspace.release.contentRevision),
      );
    } else {
      const result = await client.loadWorkspace(release.id);
      applyWorkspace(result.workspace, result.etag);
    }
  } catch (caught) {
    error.value =
      caught instanceof ApiError ? caught.message : "地图工作区加载失败。";
  } finally {
    busy.value = false;
  }
}

function confirmDiscardChanges(): boolean {
  return confirmDiscardUnsavedChanges(dirty.value, (message) =>
    window.confirm(message),
  );
}

async function reloadWorkspace(): Promise<void> {
  if (!confirmDiscardChanges()) {
    return;
  }
  await load(workspace.value?.release.id);
}

function useDemo(): void {
  clearDemoMapObjectUrls();
  demoMode.value = true;
  demoInitialized.value = false;
  releases.value = [];
  regressionCases.value = [];
  operations.value = null;
  publishedPois.value = [];
  demoWorkspaces.clear();
  void load();
}

async function switchRelease(releaseId: string): Promise<void> {
  if (!workspace.value || releaseId === workspace.value.release.id) {
    return;
  }
  if (!confirmDiscardChanges()) {
    return;
  }
  busy.value = true;
  try {
    if (demoMode.value) {
      const nextWorkspace =
        demoWorkspaces.get(releaseId) ??
        demoAdminWorkspace(releaseId);
      applyWorkspace(
        cloneWorkspace(nextWorkspace),
        String(nextWorkspace.release.contentRevision),
      );
    } else {
      const result = await client.loadWorkspace(releaseId);
      applyWorkspace(result.workspace, result.etag);
    }
  } catch (caught) {
    showToast(
      caught instanceof ApiError ? caught.message : "地图版本切换失败。",
    );
  } finally {
    busy.value = false;
  }
}

function changeFloor(floorId: string): void {
  activeFloorId.value = floorId;
  selection.value = null;
  edgeStartId.value = null;
  renderRevision.value += 1;
}

function chooseTool(nextTool: EditorTool): void {
  tool.value = nextTool;
  edgeStartId.value = null;
  if (nextTool !== "select") {
    selection.value = null;
  }
}

function invalidateReleaseValidation(): void {
  releaseValidation.value = null;
  for (const summary of releases.value.filter(
    (item) => item.status === "draft",
  )) {
    summary.validationPassed = null;
    summary.validatedRevision = null;
  }
}

function changed(message?: string): void {
  dirty.value = true;
  invalidateReleaseValidation();
  renderRevision.value += 1;
  if (message) {
    showToast(message);
  }
}

function select(nextSelection: MapSelection | null): void {
  selection.value = nextSelection;
  const connectorId = connectorIdForSelection(nextSelection);
  if (connectorId) {
    activeConnectorId.value = connectorId;
    rightTab.value = "properties";
  }
  renderRevision.value += 1;
}

function connectorIdForSelection(
  nextSelection: MapSelection | null,
): string | null {
  if (!workspace.value || !nextSelection) {
    return null;
  }
  if (nextSelection.kind === "connector") {
    return nextSelection.id;
  }
  if (nextSelection.kind === "stop") {
    return (
      workspace.value.graph.connectorStops.find(
        (stop) => stop.id === nextSelection.id,
      )?.connectorId ?? null
    );
  }
  if (nextSelection.kind === "link") {
    return (
      workspace.value.graph.verticalLinks.find(
        (link) => link.id === nextSelection.id,
      )?.connectorId ?? null
    );
  }
  return null;
}

function floorCode(floorId: string): string {
  return (
    workspace.value?.floors.find((floor) => floor.id === floorId)?.code ??
    "未知楼层"
  );
}

function selectConnector(connectorId: string): void {
  select({ kind: "connector", id: connectorId });
}

function addNode(coordinate: PixelCoordinate): void {
  if (!workspace.value || !activeFloor.value || !editable.value) {
    return;
  }
  const node: PathNode = {
    id: crypto.randomUUID(),
    code: nextCode(
      `N-${activeFloor.value.code}`,
      workspace.value.graph.nodes,
    ),
    floorId: activeFloor.value.id,
    x: Math.round(coordinate[0]),
    y: Math.round(coordinate[1]),
    type: "normal",
    enabled: true,
  };
  workspace.value.graph.nodes.push(node);
  selection.value = { kind: "node", id: node.id };
  changed("节点已创建");
}

function moveSelectedNode(
  nodeId: string,
  coordinate: PixelCoordinate,
): void {
  if (!workspace.value || !activeFloor.value || !editable.value) {
    return;
  }
  if (moveNode(workspace.value.graph, activeFloor.value, nodeId, coordinate)) {
    changed();
  }
}

function updateSelectedNodeCoordinate(
  axis: "x" | "y",
  value: number,
): void {
  if (!workspace.value || !selectedNode.value || !editable.value) {
    return;
  }
  const floor = workspace.value.floors.find(
    (item) => item.id === selectedNode.value?.floorId,
  );
  if (!floor) {
    return;
  }
  const coordinate: PixelCoordinate = [
    selectedNode.value.x,
    selectedNode.value.y,
  ];
  coordinate[axis === "x" ? 0 : 1] = value;
  if (moveNode(workspace.value.graph, floor, selectedNode.value.id, coordinate)) {
    changed();
  }
}

function updateSelectedPoiCoordinate(
  axis: "x" | "y",
  value: number,
): void {
  if (!workspace.value || !selectedPoi.value || !editable.value) {
    return;
  }
  const floor = workspace.value.floors.find(
    (item) => item.id === selectedPoi.value?.floorId,
  );
  if (!floor) {
    return;
  }
  const coordinate: PixelCoordinate = [
    selectedPoi.value.x,
    selectedPoi.value.y,
  ];
  coordinate[axis === "x" ? 0 : 1] = value;
  const [x, y] = clampPixel(
    coordinate,
    floor.mapRevision.imageWidth,
    floor.mapRevision.imageHeight,
  ).map((item) => Math.round(item * 10) / 10) as PixelCoordinate;
  if (x === selectedPoi.value.x && y === selectedPoi.value.y) {
    return;
  }
  selectedPoi.value.x = x;
  selectedPoi.value.y = y;
  changed();
}

function changeSelectedPoiNode(nodeId: string): void {
  if (!workspace.value || !selectedPoi.value || !editable.value) {
    return;
  }
  if (
    !rebindPoiToNode(
      workspace.value.graph,
      selectedPoi.value.id,
      nodeId,
    )
  ) {
    showToast("POI 只能绑定同一楼层的路径节点");
    return;
  }
  changed("POI 到达节点已更新");
}

function changeSelectedEdgeEndpoint(
  endpoint: "from" | "to",
  nodeId: string,
): void {
  if (!workspace.value || !selectedEdge.value || !editable.value) {
    return;
  }
  if (
    !rebindEdgeEndpoint(
      workspace.value.graph,
      selectedEdge.value.id,
      endpoint,
      nodeId,
    )
  ) {
    showToast("端点必须同层、互不相同，且不能形成重复路径");
    return;
  }
  changed("路径端点已更新");
}

function updateSelectedPoiKeywords(value: string): void {
  if (!selectedPoi.value || !editable.value) {
    return;
  }
  selectedPoi.value.keywords = parsePoiKeywords(value);
  changed("POI 搜索词已更新");
}

function chooseEdgeNode(nodeId: string): void {
  if (!workspace.value || !activeFloor.value || !editable.value) {
    return;
  }
  if (!edgeStartId.value) {
    edgeStartId.value = nodeId;
    renderRevision.value += 1;
    showToast("已选择起点，请点击第二个节点");
    return;
  }
  if (edgeStartId.value === nodeId) {
    edgeStartId.value = null;
    renderRevision.value += 1;
    return;
  }
  const duplicate = workspace.value.graph.edges.some(
    (edge) =>
      edge.floorId === activeFloor.value?.id &&
      ((edge.fromNodeId === edgeStartId.value && edge.toNodeId === nodeId) ||
        (edge.fromNodeId === nodeId && edge.toNodeId === edgeStartId.value)),
  );
  if (duplicate) {
    showToast("这两个节点之间已经有路径");
    edgeStartId.value = null;
    renderRevision.value += 1;
    return;
  }
  const edge: PathEdge = {
    id: crypto.randomUUID(),
    code: nextCode(
      `EDGE-${activeFloor.value.code}`,
      workspace.value.graph.edges,
    ),
    floorId: activeFloor.value.id,
    fromNodeId: edgeStartId.value,
    toNodeId: nodeId,
    timeSeconds: 10,
    distanceMeters: 0,
    direction: "both",
    type: "corridor",
    accessScope: "public",
    accessible: true,
    enabled: true,
  };
  workspace.value.graph.edges.push(edge);
  edgeStartId.value = null;
  selection.value = { kind: "edge", id: edge.id };
  tool.value = "select";
  changed("路径已创建，请填写距离和耗时");
}

function addPoi(nodeId: string): void {
  if (!workspace.value || !activeFloor.value || !editable.value) {
    return;
  }
  const node = workspace.value.graph.nodes.find(
    (candidate) => candidate.id === nodeId,
  );
  if (!node) {
    return;
  }
  const poi: Poi = {
    id: crypto.randomUUID(),
    code: nextCode(
      `P-${activeFloor.value.code}`,
      workspace.value.graph.pois,
    ),
    name: "新 POI",
    category: "department",
    floorId: activeFloor.value.id,
    nodeId,
    x: node.x,
    y: node.y,
    accessScope: "public",
    accessible: true,
    enabled: true,
    keywords: [],
  };
  workspace.value.graph.pois.push(poi);
  selection.value = { kind: "poi", id: poi.id };
  tool.value = "select";
  changed("POI 已绑定到节点");
}

function beginStop(connectorId: string): void {
  if (!workspace.value || !editable.value) {
    return;
  }
  activeConnectorId.value = connectorId;
  edgeStartId.value = null;
  tool.value = "stop";
  selection.value = { kind: "connector", id: connectorId };
  rightTab.value = "properties";
  renderRevision.value += 1;
  showToast("请点击当前楼层中与电梯或楼梯相连的路径节点");
}

function addStop(nodeId: string): void {
  if (
    !workspace.value ||
    !activeFloor.value ||
    !activeConnectorId.value ||
    !editable.value
  ) {
    return;
  }
  const connector = workspace.value.graph.connectors.find(
    (candidate) => candidate.id === activeConnectorId.value,
  );
  const node = workspace.value.graph.nodes.find(
    (candidate) =>
      candidate.id === nodeId &&
      candidate.floorId === activeFloor.value?.id,
  );
  if (!connector || !node) {
    return;
  }
  const existing = workspace.value.graph.connectorStops.find(
    (stop) =>
      stop.connectorId === connector.id &&
      stop.floorId === activeFloor.value?.id,
  );
  if (existing) {
    select({ kind: "stop", id: existing.id });
    tool.value = "select";
    showToast(`${connector.name}在本层已有停靠点`);
    return;
  }
  const stop: ConnectorStop = {
    id: crypto.randomUUID(),
    code: nextCode(
      `STOP-${connector.code}-${activeFloor.value.code}`,
      workspace.value.graph.connectorStops,
    ),
    connectorId: connector.id,
    floorId: activeFloor.value.id,
    nodeId: node.id,
  };
  workspace.value.graph.connectorStops.push(stop);
  node.type = "connector_stop";
  selection.value = { kind: "stop", id: stop.id };
  tool.value = "select";
  changed("停靠点已标注；请继续配置明确的跨层连接");
}

function rebindStop(stopId: string, nodeId: string): void {
  if (!workspace.value || !editable.value) {
    return;
  }
  const stop = workspace.value.graph.connectorStops.find(
    (candidate) => candidate.id === stopId,
  );
  const node = workspace.value.graph.nodes.find(
    (candidate) =>
      candidate.id === nodeId && candidate.floorId === stop?.floorId,
  );
  if (!stop || !node) {
    showToast("停靠点只能绑定同一楼层的路径节点");
    return;
  }
  stop.nodeId = node.id;
  node.type = "connector_stop";
  changed();
}

function openConnectorDialog(): void {
  if (!workspace.value || !editable.value) {
    return;
  }
  const usedCodes = new Set(
    workspace.value.graph.connectors.map((connector) =>
      connector.code.toUpperCase(),
    ),
  );
  let sequence = 1;
  let suffix = "A";
  while (usedCodes.has(`ELEV-${suffix}`)) {
    sequence += 1;
    suffix =
      sequence <= 26 ? String.fromCharCode(64 + sequence) : String(sequence);
  }
  connectorDraft.name = `${suffix} 电梯`;
  connectorDraft.code = `ELEV-${suffix}`;
  connectorDraft.type = "elevator";
  connectorDraft.accessScope = "public";
  connectorDraft.accessible = true;
  connectorDialog.value?.showModal();
}

function closeConnectorDialog(): void {
  connectorDialog.value?.close();
}

function createConnector(): void {
  if (!workspace.value || !editable.value) {
    return;
  }
  const code = connectorDraft.code.trim().toUpperCase();
  const name = connectorDraft.name.trim();
  if (!code || !name) {
    showToast("设施名称和编码不能为空");
    return;
  }
  if (
    workspace.value.graph.connectors.some(
      (connector) => connector.code.toUpperCase() === code,
    )
  ) {
    showToast("设施编码不能重复");
    return;
  }
  const connector: Connector = {
    id: crypto.randomUUID(),
    code,
    name,
    type: connectorDraft.type,
    accessScope: connectorDraft.accessScope,
    accessible: connectorDraft.accessible,
    enabled: true,
  };
  workspace.value.graph.connectors.push(connector);
  activeConnectorId.value = connector.id;
  selection.value = { kind: "connector", id: connector.id };
  rightTab.value = "properties";
  connectorDialog.value?.close();
  changed("设施已创建，请标注它实际停靠的楼层");
}

function openLinkDialog(connectorId: string): void {
  if (!workspace.value || !editable.value) {
    return;
  }
  const connector = workspace.value.graph.connectors.find(
    (candidate) => candidate.id === connectorId,
  );
  const stops = stopsForConnector(
    workspace.value.graph,
    connectorId,
    workspace.value.floors,
  );
  if (!connector || stops.length < 2) {
    showToast("至少标注两个楼层停靠点后才能配置连接");
    return;
  }
  const firstPair = stops
    .flatMap((from) =>
      stops
        .filter((to) => to.id !== from.id)
        .map((to) => [from, to] as const),
    )
    .find(
      ([from, to]) =>
        !workspace.value?.graph.verticalLinks.some(
          (link) =>
            link.connectorId === connectorId &&
            ((link.fromStopId === from.id && link.toStopId === to.id) ||
              (link.direction === "both" &&
                link.fromStopId === to.id &&
                link.toStopId === from.id)),
        ),
    );
  const [from, to] = firstPair ?? [stops[0]!, stops[1]!];
  linkDraft.connectorId = connector.id;
  linkDraft.fromStopId = from.id;
  linkDraft.toStopId = to.id;
  linkDraft.timeSeconds = 30;
  linkDraft.distanceMeters = 0;
  linkDraft.direction = "both";
  linkDraft.accessible =
    connector.type === "elevator" && connector.accessible;
  linkDialog.value?.showModal();
}

function closeLinkDialog(): void {
  linkDialog.value?.close();
}

function createLink(): void {
  if (!workspace.value || !editable.value) {
    return;
  }
  const connector = linkDialogConnector.value;
  const from = linkDialogStops.value.find(
    (stop) => stop.id === linkDraft.fromStopId,
  );
  const to = linkDialogStops.value.find(
    (stop) => stop.id === linkDraft.toStopId,
  );
  if (
    !connector ||
    !from ||
    !to ||
    from.id === to.id ||
    from.floorId === to.floorId
  ) {
    showToast("请选择两个不同楼层的停靠点");
    return;
  }
  const duplicate = workspace.value.graph.verticalLinks.some(
    (link) =>
      link.connectorId === connector.id &&
      ((link.fromStopId === from.id && link.toStopId === to.id) ||
        ((link.direction === "both" || linkDraft.direction === "both") &&
          link.fromStopId === to.id &&
          link.toStopId === from.id)),
  );
  if (duplicate) {
    showToast("这两个停靠点之间已经存在相同连接");
    return;
  }
  if (
    !Number.isFinite(linkDraft.timeSeconds) ||
    linkDraft.timeSeconds <= 0 ||
    !Number.isFinite(linkDraft.distanceMeters) ||
    linkDraft.distanceMeters < 0
  ) {
    showToast("耗时必须大于零，距离不能小于零");
    return;
  }
  const link: VerticalLink = {
    id: crypto.randomUUID(),
    code: nextCode(
      `VERT-${connector.code}`,
      workspace.value.graph.verticalLinks,
    ),
    connectorId: connector.id,
    fromStopId: from.id,
    toStopId: to.id,
    timeSeconds: Math.round(linkDraft.timeSeconds),
    distanceMeters: linkDraft.distanceMeters,
    direction: linkDraft.direction,
    accessScope: connector.accessScope,
    accessible: linkDraft.accessible,
    enabled: true,
  };
  workspace.value.graph.verticalLinks.push(link);
  selection.value = { kind: "link", id: link.id };
  activeConnectorId.value = connector.id;
  rightTab.value = "properties";
  linkDialog.value?.close();
  changed("跨层连接已创建");
}

function deleteRelation(): void {
  if (!workspace.value || !relationSelection.value || !editable.value) {
    return;
  }
  const current = relationSelection.value;
  const graph = workspace.value.graph;
  const labels = {
    connector: "设施及其所有停靠点和连接",
    stop: "停靠点及其相关连接",
    link: "跨层连接",
  } as const;
  if (
    !window.confirm(
      `确认删除${labels[current.kind as keyof typeof labels]}？`,
    )
  ) {
    return;
  }
  if (current.kind === "connector") {
    const stopIds = new Set(
      graph.connectorStops
        .filter((stop) => stop.connectorId === current.id)
        .map((stop) => stop.id),
    );
    graph.verticalLinks = graph.verticalLinks.filter(
      (link) =>
        link.connectorId !== current.id &&
        !stopIds.has(link.fromStopId) &&
        !stopIds.has(link.toStopId),
    );
    graph.connectorStops = graph.connectorStops.filter(
      (stop) => stop.connectorId !== current.id,
    );
    graph.connectors = graph.connectors.filter(
      (connector) => connector.id !== current.id,
    );
    activeConnectorId.value = graph.connectors[0]?.id ?? null;
  } else if (current.kind === "stop") {
    graph.verticalLinks = graph.verticalLinks.filter(
      (link) =>
        link.fromStopId !== current.id && link.toStopId !== current.id,
    );
    graph.connectorStops = graph.connectorStops.filter(
      (stop) => stop.id !== current.id,
    );
  } else if (current.kind === "link") {
    graph.verticalLinks = graph.verticalLinks.filter(
      (link) => link.id !== current.id,
    );
  }
  selection.value = null;
  changed("跨层配置已删除");
}

function dependenciesForBasicObject(
  current: MapSelection,
): DeleteObjectDependency[] {
  if (!workspace.value) {
    return [];
  }
  const dependencies: DeleteObjectDependency[] = [
    ...graphDependencies(workspace.value.graph, current),
  ];
  if (current.kind !== "poi") {
    return dependencies;
  }
  const poi = workspace.value.graph.pois.find(
    (item) => item.id === current.id,
  );
  if (!poi) {
    return dependencies;
  }
  dependencies.push(
    ...regressionCases.value
      .filter(
        (item) =>
          item.enabled &&
          (item.startPoiCode === poi.code || item.endPoiCode === poi.code),
      )
      .map((item) => {
        const usedAsStart = item.startPoiCode === poi.code;
        const usedAsEnd = item.endPoiCode === poi.code;
        return {
          kind: "route_regression_case" as const,
          id: item.id,
          label: item.name,
          relation:
            usedAsStart && usedAsEnd
              ? "关键路线起点和目的地"
              : usedAsStart
                ? "关键路线起点"
                : "关键路线目的地",
        };
      }),
  );
  return dependencies;
}

function openBasicObjectDelete(): void {
  if (
    !workspace.value ||
    !basicSelection.value ||
    !editable.value
  ) {
    return;
  }
  const current = basicSelection.value;
  const object = selectedObject(workspace.value.graph, current);
  if (!object) {
    return;
  }
  const objectType = {
    node: "节点",
    edge: "路径",
    poi: "POI",
  }[current.kind as "node" | "edge" | "poi"];
  const objectName =
    current.kind === "poi" && "name" in object
      ? `${object.name}（${object.code}）`
      : object.code;
  deleteGraphObjectDialog.value?.open(
    current,
    objectType,
    objectName,
    dependenciesForBasicObject(current),
  );
}

function confirmBasicObjectDelete(current: MapSelection): void {
  if (!workspace.value || !editable.value) {
    return;
  }
  const dependencies = dependenciesForBasicObject(current);
  if (dependencies.length) {
    showToast("引用关系已变化，请先处理所有引用");
    const object = selectedObject(workspace.value.graph, current);
    if (object) {
      const objectType = {
        node: "节点",
        edge: "路径",
        poi: "POI",
      }[current.kind as "node" | "edge" | "poi"];
      const objectName =
        current.kind === "poi" && "name" in object
          ? `${object.name}（${object.code}）`
          : object.code;
      deleteGraphObjectDialog.value?.open(
        current,
        objectType,
        objectName,
        dependencies,
      );
    }
    return;
  }
  if (!removeBasicGraphObject(workspace.value.graph, current)) {
    showToast("对象不存在或仍有引用，删除未执行");
    return;
  }
  deleteGraphObjectDialog.value?.close();
  selection.value = null;
  changed("图元素已从草稿删除");
}

function navigateObjectDependency(
  kind: DeleteObjectDependency["kind"],
  id: string,
): void {
  if (kind === "route_regression_case") {
    rightTab.value = "publication";
    openRegressionCase(id);
    return;
  }
  if (!workspace.value) {
    return;
  }
  const nextSelection: MapSelection = { kind, id };
  const object = selectedObject(workspace.value.graph, nextSelection);
  if (!object) {
    showToast("当前草稿中找不到该引用对象");
    return;
  }
  let floorId = "floorId" in object ? String(object.floorId) : null;
  if (kind === "link") {
    const fromStop = workspace.value.graph.connectorStops.find(
      (item) => item.id === (object as VerticalLink).fromStopId,
    );
    floorId = fromStop?.floorId ?? null;
  }
  if (floorId && floorId !== activeFloorId.value) {
    changeFloor(floorId);
  }
  select(nextSelection);
  rightTab.value = "properties";
}

function navigateIssue(issue: ConnectorIssue): void {
  if (issue.floorId) {
    activeFloorId.value = issue.floorId;
    edgeStartId.value = null;
  }
  select({ kind: issue.elementKind, id: issue.elementId });
  rightTab.value = "properties";
}

function updateSelected(): void {
  changed();
}

function updateCurrentReleaseSummary(
  patch: Partial<ReleaseListItem>,
): void {
  if (!workspace.value) {
    return;
  }
  const summary = releases.value.find(
    (item) => item.id === workspace.value?.release.id,
  );
  if (summary) {
    Object.assign(summary, patch);
  }
}

function openMapReplacementDialog(): void {
  if (!workspace.value || workspace.value.release.status !== "draft") {
    return;
  }
  if (dirty.value) {
    showToast("请先保存当前草稿修改，再替换底图");
    return;
  }
  mapReplacementDialog.value?.open();
}

async function replaceFloorMap(
  file: File,
  dimensions: MapImageDimensions,
): Promise<void> {
  if (
    !workspace.value ||
    !activeFloor.value ||
    workspace.value.release.status !== "draft" ||
    dirty.value
  ) {
    return;
  }
  const floorId = activeFloor.value.id;
  const currentDimensions = {
    width: activeFloor.value.mapRevision.imageWidth,
    height: activeFloor.value.mapRevision.imageHeight,
  };
  busy.value = true;
  try {
    if (demoMode.value) {
      rescaleFloorCoordinates(
        workspace.value.graph,
        floorId,
        currentDimensions,
        dimensions,
      );
      const imageUrl = URL.createObjectURL(file);
      demoMapObjectUrls.add(imageUrl);
      const revisionNo = activeFloor.value.mapRevision.revisionNo + 1;
      activeFloor.value.mapRevision = {
        id: crypto.randomUUID(),
        revisionNo,
        imageUrl,
        imageWidth: dimensions.width,
        imageHeight: dimensions.height,
      };
      workspace.value.release.contentRevision += 1;
      etag.value = String(workspace.value.release.contentRevision);
      invalidateReleaseValidation();
      updateCurrentReleaseSummary({
        contentRevision: workspace.value.release.contentRevision,
        validationPassed: null,
        validatedRevision: null,
      });
      demoWorkspaces.set(
        workspace.value.release.id,
        cloneWorkspace(workspace.value),
      );
      pruneDemoMapObjectUrls();
      selection.value = null;
      renderRevision.value += 1;
      mapReplacementDialog.value?.close();
      showToast(
        `${activeFloor.value.code} 底图已替换为修订 ${revisionNo}，请核对本层标注`,
      );
      return;
    }
    const result = await client.replaceFloorMap(
      workspace.value.release.id,
      floorId,
      etag.value,
      file,
    );
    applyWorkspace(result.workspace, result.etag);
    updateCurrentReleaseSummary({
      contentRevision: result.workspace.release.contentRevision,
      validationPassed: null,
      validatedRevision: null,
    });
    mapReplacementDialog.value?.close();
    showToast(`${activeFloor.value?.code ?? "楼层"}底图已替换，请核对标注`);
  } catch (caught) {
    showToast(
      caught instanceof ApiError ? caught.message : "楼层底图替换失败。",
    );
  } finally {
    busy.value = false;
  }
}

function openCreateDraftDialog(): void {
  releaseDialogs.value?.openDraft();
}

function openPublishDialog(): void {
  if (
    workspace.value &&
    canPublishRelease(
      workspace.value.release,
      releaseValidation.value,
      dirty.value,
      busy.value,
    )
  ) {
    releaseDialogs.value?.openPublish();
  }
}

function openRollbackDialog(): void {
  if (
    workspace.value &&
    canRollbackRelease(
      workspace.value.release.id,
      releases.value,
      busy.value,
    )
  ) {
    releaseDialogs.value?.openRollback();
  }
}

function openDiscardDialog(): void {
  if (workspace.value?.release.status === "draft" && !busy.value) {
    releaseDialogs.value?.openDiscard();
  }
}

async function createDraft(request: CreateDraftRequest): Promise<void> {
  if (!confirmDiscardChanges()) {
    return;
  }
  busy.value = true;
  try {
    if (demoMode.value) {
      const active = releases.value.find(
        (item) => item.status === "published" && item.active,
      );
      if (!active) {
        throw new ApiError(
          "没有可复制的当前发布版本。",
          409,
          "NO_ACTIVE_RELEASE",
        );
      }
      const source =
        demoWorkspaces.get(active.id) ?? demoAdminWorkspace(active.id);
      const createdAt = new Date().toISOString();
      const releaseId = crypto.randomUUID();
      const nextWorkspace = cloneWorkspace(source);
      nextWorkspace.release = {
        id: releaseId,
        code: request.code,
        status: "draft",
        contentRevision: 0,
        basedOnReleaseId: active.id,
        description: request.description,
        createdBy: "local-admin",
        createdAt,
        publishedBy: null,
        publishedAt: null,
      };
      releases.value.unshift({
        ...nextWorkspace.release,
        active: false,
        validationPassed: null,
        validatedRevision: null,
      });
      demoWorkspaces.set(releaseId, cloneWorkspace(nextWorkspace));
      releaseDialogs.value?.closeDraft();
      applyWorkspace(nextWorkspace, "0");
      showToast(`草稿 ${request.code} 已创建`);
      return;
    }
    const result = await client.createDraft(buildingId, request);
    const releaseList = await client.listReleases(buildingId);
    releases.value = releaseList.items;
    releaseDialogs.value?.closeDraft();
    applyWorkspace(result.workspace, result.etag);
    showToast(`草稿 ${result.workspace.release.code} 已创建`);
  } catch (caught) {
    showToast(
      caught instanceof ApiError ? caught.message : "草稿创建失败。",
    );
  } finally {
    busy.value = false;
  }
}

async function discardDraft(): Promise<void> {
  if (!workspace.value || workspace.value.release.status !== "draft") {
    return;
  }
  const releaseId = workspace.value.release.id;
  busy.value = true;
  try {
    if (demoMode.value) {
      demoWorkspaces.delete(releaseId);
      releases.value = releases.value.filter(
        (item) => item.id !== releaseId,
      );
    } else {
      await client.deleteDraft(releaseId, etag.value);
      releases.value = (
        await client.listReleases(buildingId)
      ).items;
    }
    releaseDialogs.value?.closeDiscard();
    const nextRelease = preferredRelease();
    if (!nextRelease) {
      throw new ApiError(
        "删除草稿后没有可读取的发布版本。",
        404,
        "NO_RELEASE",
      );
    }
    if (demoMode.value) {
      const nextWorkspace =
        demoWorkspaces.get(nextRelease.id) ??
        demoAdminWorkspace(nextRelease.id);
      applyWorkspace(
        cloneWorkspace(nextWorkspace),
        String(nextWorkspace.release.contentRevision),
      );
      pruneDemoMapObjectUrls();
    } else {
      const result = await client.loadWorkspace(nextRelease.id);
      applyWorkspace(result.workspace, result.etag);
    }
    showToast("草稿已删除，发布版本未受影响");
  } catch (caught) {
    showToast(
      caught instanceof ApiError ? caught.message : "草稿删除失败。",
    );
  } finally {
    busy.value = false;
  }
}

function openRegressionCase(caseId?: string): void {
  const regressionCase =
    regressionCases.value.find((item) => item.id === caseId) ?? null;
  regressionDialog.value?.open(regressionCase);
}

async function saveRegressionCase(
  caseId: string | null,
  payload: RouteRegressionCasePayload,
): Promise<void> {
  busy.value = true;
  try {
    if (demoMode.value) {
      const now = new Date().toISOString();
      if (caseId) {
        const existing = regressionCases.value.find(
          (item) => item.id === caseId,
        );
        if (!existing) {
          throw new ApiError(
            "关键路线不存在。",
            404,
            "REGRESSION_CASE_NOT_FOUND",
          );
        }
        Object.assign(existing, payload, {
          updatedBy: "local-admin",
          updatedAt: now,
        });
      } else {
        if (
          regressionCases.value.some(
            (item) =>
              item.code.toUpperCase() === payload.code.toUpperCase(),
          )
        ) {
          throw new ApiError(
            "关键路线编码不能重复。",
            409,
            "REGRESSION_CASE_CODE_DUPLICATE",
          );
        }
        regressionCases.value.push({
          id: crypto.randomUUID(),
          ...payload,
          createdBy: "local-admin",
          createdAt: now,
          updatedBy: "local-admin",
          updatedAt: now,
        });
      }
    } else if (caseId) {
      await client.updateRouteRegressionCase(caseId, payload);
      regressionCases.value = (
        await client.listRouteRegressionCases(buildingId)
      ).items;
    } else {
      await client.createRouteRegressionCase(buildingId, payload);
      regressionCases.value = (
        await client.listRouteRegressionCases(buildingId)
      ).items;
    }
    invalidateReleaseValidation();
    regressionDialog.value?.close();
    showToast(caseId ? "关键路线已更新" : "关键路线已新增");
  } catch (caught) {
    showToast(
      caught instanceof ApiError ? caught.message : "关键路线保存失败。",
    );
  } finally {
    busy.value = false;
  }
}

async function deleteRegressionCase(caseId: string): Promise<void> {
  const regressionCase = regressionCases.value.find(
    (item) => item.id === caseId,
  );
  if (
    !regressionCase ||
    !window.confirm(`确认删除关键路线“${regressionCase.name}”？`)
  ) {
    return;
  }
  busy.value = true;
  try {
    if (demoMode.value) {
      regressionCases.value = regressionCases.value.filter(
        (item) => item.id !== caseId,
      );
    } else {
      await client.deleteRouteRegressionCase(caseId);
      regressionCases.value = (
        await client.listRouteRegressionCases(buildingId)
      ).items;
    }
    invalidateReleaseValidation();
    showToast("关键路线已删除");
  } catch (caught) {
    showToast(
      caught instanceof ApiError ? caught.message : "关键路线删除失败。",
    );
  } finally {
    busy.value = false;
  }
}

async function validateDraft(): Promise<void> {
  if (!workspace.value || workspace.value.release.status !== "draft") {
    return;
  }
  if (dirty.value && !(await save())) {
    return;
  }
  busy.value = true;
  try {
    if (demoMode.value) {
      releaseValidation.value = demoValidation(
        workspace.value,
        regressionCases.value,
      );
    } else {
      const result = await client.validateRelease(
        workspace.value.release.id,
        etag.value,
      );
      releaseValidation.value = result.validation;
      etag.value = result.etag;
    }
    updateCurrentReleaseSummary({
      validationPassed: releaseValidation.value.passed,
      validatedRevision: releaseValidation.value.contentRevision,
    });
    rightTab.value = "publication";
    showToast(
      releaseValidation.value.passed
        ? "发布校验和关键路线回归通过"
        : `校验发现 ${releaseValidation.value.errors.length} 个错误`,
    );
  } catch (caught) {
    showToast(
      caught instanceof ApiError ? caught.message : "发布校验失败。",
    );
  } finally {
    busy.value = false;
  }
}

async function publishDraft(reason: string): Promise<void> {
  if (
    !workspace.value ||
    !canPublishRelease(
      workspace.value.release,
      releaseValidation.value,
      dirty.value,
      busy.value,
    )
  ) {
    return;
  }
  const releaseId = workspace.value.release.id;
  busy.value = true;
  try {
    if (demoMode.value) {
      for (const item of releases.value.filter(
        (release) => release.status === "published",
      )) {
        item.active = false;
      }
      const publishedAt = new Date().toISOString();
      workspace.value.release.status = "published";
      workspace.value.release.publishedBy = "local-admin";
      workspace.value.release.publishedAt = publishedAt;
      updateCurrentReleaseSummary({
        status: "published",
        active: true,
        publishedBy: "local-admin",
        publishedAt,
        validationPassed: true,
        validatedRevision: workspace.value.release.contentRevision,
      });
      demoWorkspaces.set(releaseId, cloneWorkspace(workspace.value));
      syncDemoOperations(workspace.value);
      releaseDialogs.value?.closePublish();
      rightTab.value = "publication";
      showToast(`版本 ${workspace.value.release.code} 已在演示会话中发布`);
      return;
    }
    const context = await client.publishRelease(
      releaseId,
      etag.value,
      reason,
    );
    releaseDialogs.value?.closePublish();
    await load(context.release.id);
    showToast(`版本 ${context.release.code} 已发布`);
  } catch (caught) {
    showToast(
      caught instanceof ApiError ? caught.message : "版本发布失败。",
    );
  } finally {
    busy.value = false;
  }
}

async function rollbackRelease(reason: string): Promise<void> {
  if (
    !workspace.value ||
    !canRollbackRelease(
      workspace.value.release.id,
      releases.value,
      busy.value,
    )
  ) {
    return;
  }
  const releaseId = workspace.value.release.id;
  busy.value = true;
  try {
    if (demoMode.value) {
      for (const item of releases.value.filter(
        (release) => release.status === "published",
      )) {
        item.active = item.id === releaseId;
      }
      syncDemoOperations(workspace.value);
      releaseDialogs.value?.closeRollback();
      rightTab.value = "publication";
      showToast(`已在演示会话中回滚启用 ${workspace.value.release.code}`);
      return;
    }
    const context = await client.rollbackRelease(releaseId, reason);
    releaseDialogs.value?.closeRollback();
    await load(context.release.id);
    showToast(`已回滚启用版本 ${context.release.code}`);
  } catch (caught) {
    showToast(
      caught instanceof ApiError ? caught.message : "版本回滚失败。",
    );
  } finally {
    busy.value = false;
  }
}

async function refreshOperations(showSuccess = true): Promise<void> {
  busy.value = true;
  try {
    if (!demoMode.value) {
      const [operationList, navigationPoiList] = await Promise.all([
        client.operationClosures(buildingId),
        client.navigationPois(buildingId),
      ]);
      if (navigationPoiList.releaseId !== operationList.releaseId) {
        throw new ApiError(
          "当前发布版本正在切换，请稍后重试。",
          409,
          "ACTIVE_RELEASE_CHANGED",
        );
      }
      operations.value = operationList;
      publishedPois.value = navigationPoiList.items;
    }
    if (showSuccess) {
      showToast("运营状态已刷新");
    }
  } catch (caught) {
    showToast(
      caught instanceof ApiError ? caught.message : "运营状态刷新失败。",
    );
  } finally {
    busy.value = false;
  }
}

async function createOperationClosure(
  payload: CreateOperationClosurePayload,
): Promise<void> {
  busy.value = true;
  try {
    if (demoMode.value) {
      if (!operations.value) {
        throw new ApiError(
          "运营状态尚未加载。",
          409,
          "OPERATIONS_NOT_READY",
        );
      }
      const target = operations.value.targets.find(
        (item) =>
          item.targetType === payload.targetType &&
          item.id === payload.targetId,
      );
      if (!target) {
        throw new ApiError(
          "封闭对象不属于当前启用版本。",
          409,
          "OPERATION_TARGET_NOT_FOUND",
        );
      }
      const effectiveFrom =
        payload.effectiveFrom ?? new Date().toISOString();
      operations.value.items.unshift({
        id: crypto.randomUUID(),
        targetType: target.targetType,
        targetId: target.id,
        targetCode: target.code,
        targetName: target.name,
        effectiveFrom,
        effectiveTo: payload.effectiveTo,
        reason: payload.reason,
        createdBy: "local-admin",
        createdAt: effectiveFrom,
      });
    } else {
      operations.value = await client.createOperationClosure(
        buildingId,
        payload,
      );
    }
    showToast("运营封闭已生效，新的路线会立即避开该对象");
  } catch (caught) {
    showToast(
      caught instanceof ApiError ? caught.message : "运营封闭创建失败。",
    );
  } finally {
    busy.value = false;
  }
}

async function revokeOperationClosure(closureId: string): Promise<void> {
  const closure = operations.value?.items.find(
    (item) => item.id === closureId,
  );
  if (!closure) {
    return;
  }
  busy.value = true;
  try {
    if (demoMode.value) {
      if (operations.value) {
        operations.value.items = operations.value.items.filter(
          (item) => item.id !== closureId,
        );
      }
    } else {
      operations.value = await client.revokeOperationClosure(closureId);
    }
    showToast(`${closure.targetName} 已恢复，新的路线会采用最新状态`);
  } catch (caught) {
    showToast(
      caught instanceof ApiError ? caught.message : "运营封闭恢复失败。",
    );
  } finally {
    busy.value = false;
  }
}

async function navigateOperationTarget(
  targetType: OperationTargetType,
  targetId: string,
): Promise<void> {
  if (!workspace.value || !operations.value) {
    return;
  }
  if (workspace.value.release.id !== operations.value.releaseId) {
    await switchRelease(operations.value.releaseId);
    if (workspace.value?.release.id !== operations.value.releaseId) {
      showToast("请先切换到当前启用版本再定位运营对象");
      return;
    }
  }

  let nextSelection: MapSelection | null = null;
  let floorId: string | null = null;
  if (targetType === "path_edge") {
    const edge = workspace.value.graph.edges.find(
      (item) => item.id === targetId,
    );
    nextSelection = edge ? { kind: "edge", id: edge.id } : null;
    floorId = edge?.floorId ?? null;
  } else if (targetType === "vertical_connector") {
    const connector = workspace.value.graph.connectors.find(
      (item) => item.id === targetId,
    );
    nextSelection = connector
      ? { kind: "connector", id: connector.id }
      : null;
  } else {
    const link = workspace.value.graph.verticalLinks.find(
      (item) => item.id === targetId,
    );
    const fromStop = workspace.value.graph.connectorStops.find(
      (item) => item.id === link?.fromStopId,
    );
    nextSelection = link ? { kind: "link", id: link.id } : null;
    floorId = fromStop?.floorId ?? null;
  }

  if (!nextSelection) {
    showToast("当前版本中找不到该运营对象");
    return;
  }
  if (floorId && floorId !== activeFloorId.value) {
    changeFloor(floorId);
  }
  select(nextSelection);
  rightTab.value = "properties";
}

function openSelectedPoiQrCode(): void {
  if (
    !workspace.value ||
    !selectedPoi.value ||
    !selectedPoiAllowsQrCode.value
  ) {
    showToast("仅当前启用版本中的有效 POI 可生成二维码");
    return;
  }
  const floor = workspace.value.floors.find(
    (item) => item.id === selectedPoi.value?.floorId,
  );
  qrCodeDialog.value?.open(selectedPoi.value, floor?.code ?? "?");
}

function openPublishedPoiQrCode(poi: NavigationPoi): void {
  qrCodeDialog.value?.open(poi, poi.floorCode);
}

function navigateValidationIssue(issue: ValidationIssue): void {
  if (issue.elementType === "route_regression_case") {
    openRegressionCase(issue.elementId);
    return;
  }
  if (!workspace.value) {
    return;
  }
  const nextSelection = selectionForValidationIssue(issue);
  if (!nextSelection) {
    showToast(issue.message);
    return;
  }
  const object = selectedObject(workspace.value.graph, nextSelection);
  let floorId =
    object && "floorId" in object ? String(object.floorId) : null;
  if (nextSelection.kind === "link") {
    const link = workspace.value.graph.verticalLinks.find(
      (item) => item.id === nextSelection.id,
    );
    floorId =
      workspace.value.graph.connectorStops.find(
        (stop) => stop.id === link?.fromStopId,
      )?.floorId ?? null;
  }
  if (floorId) {
    activeFloorId.value = floorId;
    edgeStartId.value = null;
  }
  select(nextSelection);
  rightTab.value = "properties";
}

async function save(): Promise<boolean> {
  if (
    !workspace.value ||
    workspace.value.release.status !== "draft" ||
    busy.value
  ) {
    return false;
  }
  if (!dirty.value) {
    return true;
  }
  const blockingIssues = connectorIssues.value.filter(
    (issue) => issue.blockingSave,
  );
  if (blockingIssues.length) {
    rightTab.value = "relations";
    showToast(`有 ${blockingIssues.length} 个跨层结构问题阻止保存`);
    return false;
  }
  busy.value = true;
  try {
    if (demoMode.value) {
      workspace.value.release.contentRevision += 1;
      etag.value = String(workspace.value.release.contentRevision);
      updateCurrentReleaseSummary({
        contentRevision: workspace.value.release.contentRevision,
        validationPassed: null,
        validatedRevision: null,
      });
      demoWorkspaces.set(
        workspace.value.release.id,
        cloneWorkspace(workspace.value),
      );
      dirty.value = false;
      showToast("演示模式已在浏览器内保存，不写入后端");
      return true;
    }
    const result = await client.saveWorkspace(
      workspace.value.release.id,
      etag.value,
      workspace.value.graph,
    );
    workspace.value = result.workspace;
    etag.value = result.etag;
    updateCurrentReleaseSummary({
      contentRevision: result.workspace.release.contentRevision,
      description: result.workspace.release.description,
      validationPassed: null,
      validatedRevision: null,
    });
    dirty.value = false;
    renderRevision.value += 1;
    showToast(`草稿修订 ${workspace.value.release.contentRevision} 已保存`);
    return true;
  } catch (caught) {
    showToast(
      caught instanceof ApiError ? caught.message : "草稿保存失败。",
    );
    return false;
  } finally {
    busy.value = false;
  }
}

function showToast(message: string): void {
  toast.value = message;
  window.setTimeout(() => {
    if (toast.value === message) {
      toast.value = "";
    }
  }, 2600);
}

const beforeUnloadHandler = createUnsavedChangesBeforeUnloadHandler(
  () => dirty.value,
);

onMounted(() => {
  window.addEventListener("beforeunload", beforeUnloadHandler);
  void load();
});

onBeforeUnmount(() => {
  window.removeEventListener("beforeunload", beforeUnloadHandler);
});
</script>

<template>
  <div
    class="grid h-[100dvh] grid-rows-[56px_minmax(0,1fr)] bg-[#eef1f4] text-[#172033]"
  >
    <header
      class="flex items-center justify-between border-b border-[#d7dce2] bg-white px-4"
    >
      <div class="flex min-w-0 items-center gap-3">
        <span
          class="grid size-8 shrink-0 place-items-center rounded-md bg-[#087f8c] text-sm font-bold text-white"
          >M</span
        >
        <div class="min-w-0">
          <h1 class="truncate text-sm font-semibold">MedRoute 地图维护</h1>
          <p class="truncate text-xs text-[#667085]">
            {{ workspace?.building.name ?? "正在连接地图服务" }}
          </p>
        </div>
      </div>
      <div class="flex items-center gap-2">
        <span
          class="flex items-center gap-1.5 rounded-md border border-[#d7dce2] px-2 py-1 text-xs text-[#4b5563]"
        >
          <Cloud v-if="demoMode" :size="14" />
          <Server v-else :size="14" />
          {{ demoMode ? "演示数据" : apiBase }}
        </span>
        <button
          class="grid size-8 place-items-center rounded-md border border-[#d7dce2] bg-white text-[#4b5563] hover:bg-[#f3f5f7] active:translate-y-px disabled:opacity-50"
          type="button"
          title="重新加载"
          :disabled="busy"
          @click="reloadWorkspace"
        >
          <RefreshCw :size="16" :class="{ 'animate-spin': busy }" />
          <span class="sr-only">重新加载</span>
        </button>
        <button
          class="flex h-8 items-center gap-1.5 whitespace-nowrap rounded-md bg-[#087f8c] px-3 text-xs font-semibold text-white hover:bg-[#066d77] active:translate-y-px disabled:cursor-not-allowed disabled:opacity-40"
          type="button"
          :disabled="!dirty || !editable"
          @click="save"
        >
          <Save :size="15" />
          保存草稿
        </button>
      </div>
    </header>

    <main
      v-if="workspace && activeFloor"
      class="grid min-h-0 grid-cols-[224px_minmax(0,1fr)_340px]"
    >
      <aside
        class="min-h-0 overflow-y-auto border-r border-[#d7dce2] bg-white"
        aria-label="地图编辑控制"
      >
        <ReleasePanel
          :release="workspace.release"
          :releases="releases"
          :dirty="dirty"
          :busy="busy"
          @select-release="switchRelease"
          @create-draft="openCreateDraftDialog"
          @discard-draft="openDiscardDialog"
        />

        <section class="border-b border-[#e4e7eb] p-4">
          <p class="mb-2 text-xs font-semibold text-[#344054]">楼层</p>
          <div class="grid grid-cols-3 gap-1">
            <button
              v-for="floor in workspace.floors"
              :key="floor.id"
              type="button"
              class="h-8 rounded-md border text-xs font-semibold active:translate-y-px"
              :class="
                floor.id === activeFloorId
                  ? 'border-[#087f8c] bg-[#e3f3f3] text-[#066d77]'
                  : 'border-[#d7dce2] bg-white text-[#4b5563] hover:bg-[#f3f5f7]'
              "
              @click="changeFloor(floor.id)"
            >
              {{ floor.code }}
            </button>
          </div>
        </section>

        <section class="border-b border-[#e4e7eb] p-4">
          <p class="mb-2 text-xs font-semibold text-[#344054]">标注工具</p>
          <div class="grid grid-cols-2 gap-1.5">
            <button
              v-for="item in tools"
              :key="item.id"
              type="button"
              class="flex h-9 items-center gap-2 rounded-md border px-2.5 text-xs font-medium active:translate-y-px disabled:opacity-40"
              :class="
                tool === item.id
                  ? 'border-[#087f8c] bg-[#087f8c] text-white'
                  : 'border-[#d7dce2] bg-white text-[#344054] hover:bg-[#f3f5f7]'
              "
              :disabled="item.id !== 'select' && !editable"
              :title="item.label"
              @click="chooseTool(item.id)"
            >
              <component :is="item.icon" :size="15" />
              {{ item.label }}
            </button>
          </div>
          <p class="mt-2 text-[11px] leading-4 text-[#667085]">
            <template v-if="tool === 'node'">点击地图空白处创建节点。</template>
            <template v-else-if="tool === 'edge'">
              {{ edgeStartId ? "点击第二个节点完成路径。" : "依次点击两个节点创建路径。" }}
            </template>
            <template v-else-if="tool === 'poi'">点击节点并将 POI 绑定到它。</template>
            <template v-else-if="tool === 'stop'">
              点击{{ activeFloor.code }}中的路径节点标注设施停靠点。
            </template>
            <template v-else>点击元素查看属性；直接拖动节点调整位置。</template>
          </p>
        </section>

        <CrossFloorPanel
          :graph="workspace.graph"
          :floors="workspace.floors"
          :active-floor-id="activeFloorId"
          :selected-connector-id="activeConnectorId"
          :issues="connectorIssues"
          :editable="editable"
          @create-connector="openConnectorDialog"
          @select-connector="selectConnector"
          @begin-stop="beginStop"
          @create-link="openLinkDialog"
        />

        <section class="p-4">
          <p class="mb-2 text-xs font-semibold text-[#344054]">本层数据</p>
          <dl class="grid grid-cols-2 gap-x-3 gap-y-2 text-xs">
            <div>
              <dt class="text-[#667085]">节点</dt>
              <dd class="mt-0.5 font-semibold">{{ currentGraph?.nodes.length }}</dd>
            </div>
            <div>
              <dt class="text-[#667085]">路径</dt>
              <dd class="mt-0.5 font-semibold">{{ currentGraph?.edges.length }}</dd>
            </div>
            <div>
              <dt class="text-[#667085]">POI</dt>
              <dd class="mt-0.5 font-semibold">{{ currentGraph?.pois.length }}</dd>
            </div>
            <div>
              <dt class="text-[#667085]">停靠节点</dt>
              <dd class="mt-0.5 font-semibold">{{ currentGraph?.stopNodeIds.size }}</dd>
            </div>
          </dl>
        </section>
      </aside>

      <section class="grid min-w-0 grid-rows-[48px_minmax(0,1fr)]">
        <div
          class="flex items-center justify-between border-b border-[#d7dce2] bg-[#f8fafb] px-4"
        >
          <div>
            <h2 class="text-sm font-semibold">
              {{ activeFloor.code }} · {{ activeFloor.name }}
            </h2>
            <p class="text-[11px] text-[#667085]">
              {{ activeFloor.mapRevision.imageWidth }} ×
              {{ activeFloor.mapRevision.imageHeight }} px
            </p>
          </div>
          <div class="flex items-center gap-2">
            <button
              class="flex h-8 items-center gap-1.5 rounded-md border border-[#cfd5dc] bg-white px-2.5 text-[11px] font-semibold text-[#344054] hover:bg-[#f3f5f7] active:translate-y-px disabled:cursor-not-allowed disabled:opacity-40"
              type="button"
              :disabled="workspace.release.status !== 'draft' || busy"
              @click="openMapReplacementDialog"
            >
              <ImageUp :size="14" />
              替换底图
            </button>
            <span class="flex items-center gap-1.5 text-[11px] text-[#667085]">
              <LocateFixed :size="14" />
              左上角像素坐标
            </span>
          </div>
        </div>
        <div class="relative min-h-0 overflow-hidden">
          <FloorMapEditor
            :floor="activeFloor"
            :graph="workspace.graph"
            :image-url="imageUrl"
            :selection="selection"
            :tool="tool"
            :edge-start-id="edgeStartId"
            :editable="editable"
            :revision="renderRevision"
            @select="select"
            @add-node="addNode"
            @add-poi="addPoi"
            @add-stop="addStop"
            @choose-edge-node="chooseEdgeNode"
            @move-node="moveSelectedNode"
          />
          <span
            v-if="demoMode"
            class="pointer-events-none absolute bottom-3 left-3 rounded-md border border-[#d7dce2] bg-white/95 px-2 py-1 text-[11px] text-[#667085]"
            >测试标注，未经现场核验</span
          >
        </div>
      </section>

      <aside
        class="grid min-h-0 grid-rows-[44px_minmax(0,1fr)] border-l border-[#d7dce2] bg-white"
        aria-label="元素属性、跨层关系、发布和运营工作台"
      >
        <div
          class="grid h-11 grid-cols-4 border-b border-[#d7dce2] bg-[#f8fafb] px-2"
          role="tablist"
          aria-label="右侧面板"
        >
          <button
            class="relative text-xs font-semibold"
            :class="
              rightTab === 'properties'
                ? 'text-[#066d77]'
                : 'text-[#667085]'
            "
            type="button"
            role="tab"
            :aria-selected="rightTab === 'properties'"
            @click="rightTab = 'properties'"
          >
            属性
            <span
              v-if="rightTab === 'properties'"
              class="absolute inset-x-3 bottom-0 h-0.5 bg-[#087f8c]"
            ></span>
          </button>
          <button
            class="relative flex items-center justify-center gap-1.5 text-xs font-semibold"
            :class="
              rightTab === 'relations'
                ? 'text-[#066d77]'
                : 'text-[#667085]'
            "
            type="button"
            role="tab"
            :aria-selected="rightTab === 'relations'"
            @click="rightTab = 'relations'"
          >
            关系
            <span
              v-if="connectorErrorCount"
              class="grid min-w-4 place-items-center rounded-sm bg-[#fee2e2] px-1 text-[10px] leading-4 text-[#b91c1c]"
            >
              {{ connectorErrorCount }}
            </span>
            <span
              v-if="rightTab === 'relations'"
              class="absolute inset-x-3 bottom-0 h-0.5 bg-[#087f8c]"
            ></span>
          </button>
          <button
            class="relative flex items-center justify-center gap-1.5 text-xs font-semibold"
            :class="
              rightTab === 'publication'
                ? 'text-[#066d77]'
                : 'text-[#667085]'
            "
            type="button"
            role="tab"
            :aria-selected="rightTab === 'publication'"
            @click="rightTab = 'publication'"
          >
            发布
            <span
              v-if="publicationIssueCount"
              class="grid min-w-4 place-items-center rounded-sm bg-[#fee2e2] px-1 text-[10px] leading-4 text-[#b91c1c]"
            >
              {{ publicationIssueCount }}
            </span>
            <span
              v-if="rightTab === 'publication'"
              class="absolute inset-x-3 bottom-0 h-0.5 bg-[#087f8c]"
            ></span>
          </button>
          <button
            class="relative flex items-center justify-center gap-1 text-xs font-semibold"
            :class="
              rightTab === 'operations'
                ? 'text-[#066d77]'
                : 'text-[#667085]'
            "
            type="button"
            role="tab"
            :aria-selected="rightTab === 'operations'"
            @click="rightTab = 'operations'"
          >
            运营
            <span
              v-if="operations?.items.length"
              class="grid min-w-4 place-items-center rounded-sm bg-[#fee2e2] px-1 text-[10px] leading-4 text-[#b91c1c]"
            >
              {{ operations.items.length }}
            </span>
            <span
              v-if="rightTab === 'operations'"
              class="absolute inset-x-3 bottom-0 h-0.5 bg-[#087f8c]"
            ></span>
          </button>
        </div>

        <div class="min-h-0 overflow-y-auto p-4">
          <template v-if="rightTab === 'properties'">
            <div class="mb-4 flex items-center justify-between">
              <h2 class="text-sm font-semibold">属性</h2>
              <span class="text-[11px] text-[#667085]">
                {{ selection ? selection.kind.toUpperCase() : "未选择" }}
              </span>
            </div>

            <CrossFloorInspector
              v-if="relationSelection"
              :graph="workspace.graph"
              :floors="workspace.floors"
              :active-floor-id="activeFloorId"
              :selection="relationSelection"
              :editable="editable"
              @changed="updateSelected"
              @select="select"
              @switch-floor="changeFloor"
              @begin-stop="beginStop"
              @create-link="openLinkDialog"
              @rebind-stop="rebindStop"
              @delete-selection="deleteRelation"
            />

            <BasicGraphInspector
              v-else-if="basicSelection"
              :graph="workspace.graph"
              :selection="basicSelection"
              :editable="editable"
              :dependencies="selectedGraphDependencies"
              :qr-code-allowed="selectedPoiAllowsQrCode"
              @changed="updateSelected"
              @move-node="updateSelectedNodeCoordinate"
              @move-poi="updateSelectedPoiCoordinate"
              @rebind-edge="changeSelectedEdgeEndpoint"
              @rebind-poi="changeSelectedPoiNode"
              @update-keywords="updateSelectedPoiKeywords"
              @delete-selection="openBasicObjectDelete"
              @navigate-dependency="navigateObjectDependency"
              @open-qr-code="openSelectedPoiQrCode"
            />

            <div
              v-else
              class="grid min-h-40 place-items-center border-y border-[#e4e7eb] py-8 text-center"
            >
              <div>
                <MousePointer2 class="mx-auto text-[#98a2b3]" :size="22" />
                <strong class="mt-3 block text-xs">未选择地图元素</strong>
                <p class="mt-1 text-[11px] leading-4 text-[#667085]">
                  点击地图元素，或从左侧选择跨层设施。
                </p>
              </div>
            </div>
          </template>

          <ConnectorValidation
            v-else-if="rightTab === 'relations'"
            :issues="connectorIssues"
            :floors="workspace.floors"
            @navigate="navigateIssue"
          />

          <PublicationWorkbench
            v-else-if="rightTab === 'publication'"
            :release="workspace.release"
            :releases="releases"
            :validation="releaseValidation"
            :regression-cases="regressionCases"
            :dirty="dirty"
            :busy="busy"
            @validate="validateDraft"
            @publish="openPublishDialog"
            @rollback="openRollbackDialog"
            @create-case="openRegressionCase()"
            @edit-case="openRegressionCase"
            @delete-case="deleteRegressionCase"
            @navigate-issue="navigateValidationIssue"
          />

          <OperationsWorkbench
            v-else
            :operations="operations"
            :pois="publishedPois"
            :busy="busy"
            @create="createOperationClosure"
            @revoke="revokeOperationClosure"
            @refresh="refreshOperations"
            @navigate="navigateOperationTarget"
            @qr-code="openPublishedPoiQrCode"
          />
        </div>
      </aside>
    </main>

    <main
      v-else
      class="grid min-h-0 place-items-center bg-[#eef1f4] p-8"
    >
      <section
        class="w-full max-w-md rounded-md border border-[#d7dce2] bg-white p-6"
      >
        <TriangleAlert class="text-[#b45309]" :size="24" />
        <h2 class="mt-4 text-base font-semibold">
          {{ busy ? "正在加载地图工作区" : "无法加载地图工作区" }}
        </h2>
        <p class="mt-2 text-sm leading-6 text-[#667085]">
          {{
            busy
              ? `正在连接 ${apiBase}`
              : error || "请确认后端服务和数据库已经启动。"
          }}
        </p>
        <div v-if="!busy" class="mt-5 flex gap-2">
          <button
            class="h-9 whitespace-nowrap rounded-md bg-[#087f8c] px-3 text-xs font-semibold text-white active:translate-y-px"
            type="button"
            @click="load()"
          >
            重试连接
          </button>
          <button
            class="h-9 whitespace-nowrap rounded-md border border-[#cfd5dc] bg-white px-3 text-xs font-semibold text-[#344054] active:translate-y-px"
            type="button"
            @click="useDemo"
          >
            使用演示数据
          </button>
        </div>
      </section>
    </main>

    <ReleaseDialogs
      v-if="workspace"
      ref="releaseDialogs"
      :release="workspace.release"
      :releases="releases"
      @create-draft="createDraft"
      @publish="publishDraft"
      @rollback="rollbackRelease"
      @discard="discardDraft"
    />

    <RouteRegressionDialog
      v-if="workspace"
      ref="regressionDialog"
      :graph="workspace.graph"
      :floors="workspace.floors"
      @save="saveRegressionCase"
    />

    <MapReplacementDialog
      v-if="workspace && activeFloor"
      ref="mapReplacementDialog"
      :floor="activeFloor"
      :current-image-url="imageUrl"
      :node-count="currentGraph?.nodes.length ?? 0"
      :poi-count="currentGraph?.pois.length ?? 0"
      :busy="busy"
      @replace="replaceFloorMap"
    />

    <QrCodeDialog
      ref="qrCodeDialog"
      :api-base="apiBase"
      :building-id="buildingId"
      :default-navigation-base-url="defaultNavigationBaseUrl"
      @copied="showToast('导航地址已复制')"
    />

    <DeleteGraphObjectDialog
      ref="deleteGraphObjectDialog"
      @confirm="confirmBasicObjectDelete"
      @navigate="navigateObjectDependency"
    />

    <dialog
      ref="connectorDialog"
      class="m-auto w-[420px] rounded-md border border-[#cfd5dc] bg-white p-0 text-[#172033] shadow-xl"
      @close="closeConnectorDialog"
    >
      <form class="p-5" @submit.prevent="createConnector">
        <div class="flex items-start justify-between">
          <div>
            <h2 class="text-base font-semibold">新增跨层设施</h2>
            <p class="mt-1 text-xs leading-5 text-[#667085]">
              先定义电梯或楼梯，再到它实际停靠的楼层标注节点。
            </p>
          </div>
          <CirclePlus class="text-[#087f8c]" :size="22" />
        </div>

        <div class="mt-5 grid grid-cols-2 gap-3">
          <label class="col-span-2 block space-y-1.5">
            <span class="text-xs font-medium text-[#344054]">设施名称</span>
            <input
              v-model="connectorDraft.name"
              required
              autofocus
              class="h-9 w-full rounded-md border border-[#cfd5dc] px-2.5 text-xs outline-none focus:border-[#087f8c] focus:ring-2 focus:ring-[#b9dfe0]"
            />
          </label>
          <label class="block space-y-1.5">
            <span class="text-xs font-medium text-[#344054]">设施编码</span>
            <input
              v-model="connectorDraft.code"
              required
              class="h-9 w-full rounded-md border border-[#cfd5dc] px-2.5 text-xs uppercase outline-none focus:border-[#087f8c]"
            />
          </label>
          <label class="block space-y-1.5">
            <span class="text-xs font-medium text-[#344054]">设施类型</span>
            <select
              v-model="connectorDraft.type"
              class="h-9 w-full rounded-md border border-[#cfd5dc] bg-white px-2.5 text-xs"
            >
              <option value="elevator">电梯</option>
              <option value="stairs">楼梯</option>
            </select>
          </label>
          <label class="block space-y-1.5">
            <span class="text-xs font-medium text-[#344054]">开放范围</span>
            <select
              v-model="connectorDraft.accessScope"
              class="h-9 w-full rounded-md border border-[#cfd5dc] bg-white px-2.5 text-xs"
            >
              <option value="public">公众可用</option>
              <option value="staff">仅员工</option>
            </select>
          </label>
          <label class="flex items-end gap-2 pb-2 text-xs text-[#344054]">
            <input
              v-model="connectorDraft.accessible"
              type="checkbox"
              class="size-4 accent-[#087f8c]"
            />
            支持无障碍通行
          </label>
        </div>

        <div class="mt-6 flex justify-end gap-2">
          <button
            class="h-9 rounded-md border border-[#cfd5dc] bg-white px-3 text-xs font-semibold text-[#344054]"
            type="button"
            @click="closeConnectorDialog"
          >
            取消
          </button>
          <button
            class="h-9 rounded-md bg-[#087f8c] px-3 text-xs font-semibold text-white"
            type="submit"
          >
            创建设施
          </button>
        </div>
      </form>
    </dialog>

    <dialog
      ref="linkDialog"
      class="m-auto w-[460px] rounded-md border border-[#cfd5dc] bg-white p-0 text-[#172033] shadow-xl"
      @close="closeLinkDialog"
    >
      <form class="p-5" @submit.prevent="createLink">
        <div>
          <h2 class="text-base font-semibold">配置跨层连接</h2>
          <p class="mt-1 text-xs leading-5 text-[#667085]">
            {{ linkDialogConnector?.name }} 只有明确配置的楼层对才可用于导航，允许跨过中间楼层。
          </p>
        </div>

        <div class="mt-5 grid grid-cols-2 gap-3">
          <label class="block space-y-1.5">
            <span class="text-xs font-medium text-[#344054]">起始停靠点</span>
            <select
              v-model="linkDraft.fromStopId"
              class="h-9 w-full rounded-md border border-[#cfd5dc] bg-white px-2.5 text-xs"
            >
              <option
                v-for="stop in linkDialogStops"
                :key="stop.id"
                :value="stop.id"
              >
                {{ floorCode(stop.floorId) }}
              </option>
            </select>
          </label>
          <label class="block space-y-1.5">
            <span class="text-xs font-medium text-[#344054]">到达停靠点</span>
            <select
              v-model="linkDraft.toStopId"
              class="h-9 w-full rounded-md border border-[#cfd5dc] bg-white px-2.5 text-xs"
            >
              <option
                v-for="stop in linkDialogStops"
                :key="stop.id"
                :value="stop.id"
              >
                {{ floorCode(stop.floorId) }}
              </option>
            </select>
          </label>
          <label class="block space-y-1.5">
            <span class="text-xs font-medium text-[#344054]">预计耗时（秒）</span>
            <input
              v-model.number="linkDraft.timeSeconds"
              type="number"
              min="1"
              required
              class="h-9 w-full rounded-md border border-[#cfd5dc] px-2.5 text-xs outline-none focus:border-[#087f8c]"
            />
          </label>
          <label class="block space-y-1.5">
            <span class="text-xs font-medium text-[#344054]">估算距离（米）</span>
            <input
              v-model.number="linkDraft.distanceMeters"
              type="number"
              min="0"
              step="0.1"
              required
              class="h-9 w-full rounded-md border border-[#cfd5dc] px-2.5 text-xs outline-none focus:border-[#087f8c]"
            />
          </label>
          <label class="block space-y-1.5">
            <span class="text-xs font-medium text-[#344054]">通行方向</span>
            <select
              v-model="linkDraft.direction"
              class="h-9 w-full rounded-md border border-[#cfd5dc] bg-white px-2.5 text-xs"
            >
              <option value="both">双向通行</option>
              <option value="forward">仅起始到到达</option>
            </select>
          </label>
          <label class="flex items-end gap-2 pb-2 text-xs text-[#344054]">
            <input
              v-model="linkDraft.accessible"
              type="checkbox"
              class="size-4 accent-[#087f8c]"
            />
            支持无障碍通行
          </label>
        </div>

        <div class="mt-6 flex justify-end gap-2">
          <button
            class="h-9 rounded-md border border-[#cfd5dc] bg-white px-3 text-xs font-semibold text-[#344054]"
            type="button"
            @click="closeLinkDialog"
          >
            取消
          </button>
          <button
            class="h-9 rounded-md bg-[#087f8c] px-3 text-xs font-semibold text-white"
            type="submit"
          >
            保存连接
          </button>
        </div>
      </form>
    </dialog>

    <div
      v-if="toast"
      class="fixed bottom-4 left-1/2 z-50 -translate-x-1/2 rounded-md bg-[#172033] px-3 py-2 text-xs font-medium text-white shadow-lg"
      role="status"
    >
      {{ toast }}
    </div>
  </div>
</template>
