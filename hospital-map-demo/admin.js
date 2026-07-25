(() => {
  "use strict";

  const SVG_NS = "http://www.w3.org/2000/svg";
  const params = new URLSearchParams(window.location.search);
  const API_BASE = (params.get("api") || "http://127.0.0.1:8080").replace(/\/$/, "");
  const BUILDING_ID =
    params.get("building") || "00000000-0000-0000-0000-000000000100";

  const elements = {
    buildingName: document.querySelector("#buildingName"),
    previewLink: document.querySelector("#previewLink"),
    connectionState: document.querySelector("#connectionState"),
    connectionText: document.querySelector("#connectionText"),
    reloadButton: document.querySelector("#reloadButton"),
    releaseSelect: document.querySelector("#releaseSelect"),
    releaseMeta: document.querySelector("#releaseMeta"),
    createDraftButton: document.querySelector("#createDraftButton"),
    discardDraftButton: document.querySelector("#discardDraftButton"),
    floorList: document.querySelector("#floorList"),
    toolGrid: document.querySelector("#toolGrid"),
    toolHint: document.querySelector("#toolHint"),
    connectorSelect: document.querySelector("#connectorSelect"),
    connectorSummary: document.querySelector("#connectorSummary"),
    addConnectorButton: document.querySelector("#addConnectorButton"),
    addLinkButton: document.querySelector("#addLinkButton"),
    mapTitle: document.querySelector("#mapTitle"),
    mapCounts: document.querySelector("#mapCounts"),
    zoomRange: document.querySelector("#zoomRange"),
    zoomOutput: document.querySelector("#zoomOutput"),
    saveState: document.querySelector("#saveState"),
    replaceMapButton: document.querySelector("#replaceMapButton"),
    mapFileInput: document.querySelector("#mapFileInput"),
    saveButton: document.querySelector("#saveButton"),
    validateButton: document.querySelector("#validateButton"),
    publishButton: document.querySelector("#publishButton"),
    rollbackButton: document.querySelector("#rollbackButton"),
    mapScroll: document.querySelector("#mapScroll"),
    mapStage: document.querySelector("#mapStage"),
    mapMessage: document.querySelector("#mapMessage"),
    floorImage: document.querySelector("#floorImage"),
    graphOverlay: document.querySelector("#graphOverlay"),
    edgeLayer: document.querySelector("#edgeLayer"),
    nodeLayer: document.querySelector("#nodeLayer"),
    stopLayer: document.querySelector("#stopLayer"),
    poiLayer: document.querySelector("#poiLayer"),
    inspectorContent: document.querySelector("#inspectorContent"),
    validationContent: document.querySelector("#validationContent"),
    validationCount: document.querySelector("#validationCount"),
    operationCount: document.querySelector("#operationCount"),
    inspectorPanel: document.querySelector("#inspectorPanel"),
    validationPanel: document.querySelector("#validationPanel"),
    operationsPanel: document.querySelector("#operationsPanel"),
    inspectorTab: document.querySelector("#inspectorTab"),
    validationTab: document.querySelector("#validationTab"),
    operationsTab: document.querySelector("#operationsTab"),
    operationsRelease: document.querySelector("#operationsRelease"),
    closureForm: document.querySelector("#closureForm"),
    closureTypeInput: document.querySelector("#closureTypeInput"),
    closureTargetInput: document.querySelector("#closureTargetInput"),
    closureReasonInput: document.querySelector("#closureReasonInput"),
    createClosureButton: document.querySelector("#createClosureButton"),
    operationList: document.querySelector("#operationList"),
    publishActions: document.querySelector("#publishActions"),
    createDraftDialog: document.querySelector("#createDraftDialog"),
    createDraftForm: document.querySelector("#createDraftForm"),
    draftCodeInput: document.querySelector("#draftCodeInput"),
    draftDescriptionInput: document.querySelector("#draftDescriptionInput"),
    connectorDialog: document.querySelector("#connectorDialog"),
    connectorForm: document.querySelector("#connectorForm"),
    connectorNameInput: document.querySelector("#connectorNameInput"),
    connectorCodeInput: document.querySelector("#connectorCodeInput"),
    connectorTypeInput: document.querySelector("#connectorTypeInput"),
    connectorAccessibleInput: document.querySelector("#connectorAccessibleInput"),
    linkDialog: document.querySelector("#linkDialog"),
    linkForm: document.querySelector("#linkForm"),
    linkConnectorName: document.querySelector("#linkConnectorName"),
    fromStopInput: document.querySelector("#fromStopInput"),
    toStopInput: document.querySelector("#toStopInput"),
    linkTimeInput: document.querySelector("#linkTimeInput"),
    linkDirectionInput: document.querySelector("#linkDirectionInput"),
    linkAccessibleInput: document.querySelector("#linkAccessibleInput"),
    publishDialog: document.querySelector("#publishDialog"),
    publishForm: document.querySelector("#publishForm"),
    publishReasonInput: document.querySelector("#publishReasonInput"),
    rollbackDialog: document.querySelector("#rollbackDialog"),
    rollbackForm: document.querySelector("#rollbackForm"),
    rollbackReasonInput: document.querySelector("#rollbackReasonInput"),
    discardDraftDialog: document.querySelector("#discardDraftDialog"),
    discardDraftForm: document.querySelector("#discardDraftForm"),
    discardDraftCode: document.querySelector("#discardDraftCode"),
    toast: document.querySelector("#toast")
  };

  const toolHints = {
    select: "选择地图元素后，在右侧检查和修改属性。",
    node: "点击地图空白处新增路径节点。",
    edge: "依次点击两个同楼层节点，创建一条楼层内路径。",
    poi: "点击已有节点，将 POI 绑定到该节点。",
    stop: "先选择跨层设施，再点击该设施在本层的路径节点。"
  };

  const state = {
    releases: [],
    workspace: null,
    activeFloorId: null,
    selectedConnectorId: null,
    selection: null,
    edgeStartId: null,
    tool: "select",
    etag: null,
    dirty: false,
    busy: false,
    validation: null,
    operations: null,
    drag: null,
    ignoreMapClick: false,
    zoom: 100,
    activePanel: "inspector"
  };

  class ApiError extends Error {
    constructor(message, code, status, details) {
      super(message);
      this.name = "ApiError";
      this.code = code;
      this.status = status;
      this.details = details || [];
    }
  }

  async function request(path, options = {}) {
    const headers = new Headers(options.headers || {});
    headers.set("Accept", "application/json");
    headers.set("X-Admin-User", "local-admin");
    headers.set("X-Request-Id", `admin-${crypto.randomUUID()}`);
    if (
      options.body &&
      !(options.body instanceof FormData) &&
      !headers.has("Content-Type")
    ) {
      headers.set("Content-Type", "application/json");
    }

    let response;
    try {
      response = await fetch(`${API_BASE}${path}`, {
        cache: "no-store",
        ...options,
        headers
      });
    } catch (error) {
      throw new ApiError(
        `无法连接后端 ${API_BASE}。请确认数据库和后端服务已启动。`,
        "NETWORK_ERROR",
        0,
        []
      );
    }

    const contentType = response.headers.get("Content-Type") || "";
    const body = contentType.includes("application/json")
      ? await response.json()
      : null;
    if (!response.ok) {
      const apiError = body && body.error;
      throw new ApiError(
        (apiError && apiError.message) || `请求失败（HTTP ${response.status}）`,
        (apiError && apiError.code) || "HTTP_ERROR",
        response.status,
        (apiError && apiError.details) || []
      );
    }
    setConnection("connected", "已连接");
    return {
      data: body,
      etag: response.headers.get("ETag")
    };
  }

  async function loadAll(preferredReleaseId) {
    setBusy(true);
    showMapMessage("正在加载地图工作区", "正在读取发布版本和草稿数据。");
    try {
      const releaseResponse = await request(
        `/api/admin/buildings/${BUILDING_ID}/releases`
      );
      state.releases = releaseResponse.data.items || [];
      const operationResponse = await request(
        `/api/admin/buildings/${BUILDING_ID}/operations/closures`
      );
      state.operations = operationResponse.data;
      const preferred = preferredReleaseId
        ? state.releases.find((release) => release.id === preferredReleaseId)
        : null;
      const release =
        preferred ||
        state.releases.find((item) => item.status === "draft") ||
        state.releases.find((item) => item.active) ||
        state.releases[0];
      if (!release) {
        throw new ApiError(
          "当前楼栋没有可读取的发布版本。",
          "NO_RELEASE",
          404,
          []
        );
      }
      await loadWorkspace(release.id);
      setConnection("connected", "已连接");
    } catch (error) {
      handleError(error, true);
    } finally {
      setBusy(false);
    }
  }

  async function loadWorkspace(releaseId) {
    const previousFloorId = state.activeFloorId;
    const response = await request(`/api/admin/releases/${releaseId}`);
    state.workspace = response.data;
    state.etag =
      response.etag || `"${state.workspace.release.contentRevision}"`;
    state.activeFloorId = state.workspace.floors.some(
      (floor) => floor.id === previousFloorId
    )
      ? previousFloorId
      : state.workspace.floors[0] && state.workspace.floors[0].id;
    state.selectedConnectorId = state.workspace.graph.connectors.some(
      (connector) => connector.id === state.selectedConnectorId
    )
      ? state.selectedConnectorId
      : state.workspace.graph.connectors[0] &&
        state.workspace.graph.connectors[0].id;
    state.selection = null;
    state.edgeStartId = null;
    state.validation = null;
    state.dirty = false;
    state.tool = "select";
    elements.buildingName.textContent =
      `${state.workspace.building.name} · ${API_BASE}`;
    elements.previewLink.href =
      `multifloor.html?api=${encodeURIComponent(API_BASE)}` +
      `&building=${encodeURIComponent(BUILDING_ID)}`;
    renderAll();
  }

  function renderAll() {
    renderReleases();
    renderFloors();
    renderTools();
    renderConnectorControls();
    renderMap();
    renderInspector();
    renderValidation();
    renderOperations();
    updateActionStates();
  }

  function renderReleases() {
    if (!state.workspace) {
      return;
    }
    elements.releaseSelect.innerHTML = state.releases
      .map((release) => {
        const status = release.status === "draft"
          ? "草稿"
          : release.active
            ? "已发布，当前启用"
            : "历史发布";
        return (
          `<option value="${escapeAttribute(release.id)}"` +
          `${release.id === state.workspace.release.id ? " selected" : ""}>` +
          `${escapeHtml(release.code)}（${status}）</option>`
        );
      })
      .join("");
    elements.releaseSelect.disabled = state.busy;

    const release = state.workspace.release;
    const draft = release.status === "draft";
    elements.releaseMeta.className = `release-meta ${draft ? "draft" : "published"}`;
    elements.releaseMeta.innerHTML = draft
      ? (
        `<strong>可编辑草稿 · 修订 ${release.contentRevision}</strong>` +
        `<span>${escapeHtml(release.description || "未填写修改说明")}</span>`
      )
      : (
        `<strong>只读发布版本</strong>` +
        `<span>发布于 ${escapeHtml(formatDate(release.publishedAt))}</span>`
      );
  }

  function renderFloors() {
    if (!state.workspace) {
      elements.floorList.innerHTML = "";
      return;
    }
    elements.floorList.innerHTML = "";
    for (const floor of state.workspace.floors) {
      const button = document.createElement("button");
      button.type = "button";
      button.className =
        `floor-button${floor.id === state.activeFloorId ? " active" : ""}`;
      button.dataset.floorId = floor.id;
      button.setAttribute("role", "tab");
      button.setAttribute(
        "aria-selected",
        floor.id === state.activeFloorId ? "true" : "false"
      );
      button.textContent = floor.code;
      button.addEventListener("click", () => {
        state.activeFloorId = floor.id;
        state.selection = null;
        state.edgeStartId = null;
        renderFloors();
        renderMap();
        renderInspector();
      });
      elements.floorList.append(button);
    }
  }

  function renderTools() {
    const editable = isEditable();
    for (const button of elements.toolGrid.querySelectorAll("[data-tool]")) {
      const tool = button.dataset.tool;
      button.classList.toggle("active", state.tool === tool);
      button.disabled = state.busy || (!editable && tool !== "select");
    }
    elements.mapStage.className = `map-stage tool-${state.tool}`;
    elements.toolHint.textContent = !editable && state.tool !== "select"
      ? "发布版本为只读，请先新建草稿。"
      : toolHints[state.tool];
  }

  function renderConnectorControls() {
    if (!state.workspace) {
      return;
    }
    const connectors = state.workspace.graph.connectors;
    elements.connectorSelect.innerHTML =
      '<option value="">请选择电梯或楼梯</option>' +
      connectors
        .map((connector) => (
          `<option value="${escapeAttribute(connector.id)}"` +
          `${connector.id === state.selectedConnectorId ? " selected" : ""}>` +
          `${escapeHtml(connector.name)}</option>`
        ))
        .join("");
    const connector = findById(connectors, state.selectedConnectorId);
    if (!connector) {
      elements.connectorSummary.textContent = "选择设施后可标注停靠点。";
    } else {
      const stops = state.workspace.graph.connectorStops.filter(
        (stop) => stop.connectorId === connector.id
      );
      const floorCodes = stops
        .map((stop) => floorById(stop.floorId))
        .filter(Boolean)
        .sort((a, b) => a.levelNo - b.levelNo)
        .map((floor) => floor.code);
      elements.connectorSummary.textContent =
        `${connector.name}：` +
        (floorCodes.length ? `停靠 ${floorCodes.join("、")}` : "尚未标注停靠点");
    }
    elements.connectorSelect.disabled = !isEditable() || state.busy;
    elements.addConnectorButton.disabled = !isEditable() || state.busy;
    elements.addLinkButton.disabled =
      !isEditable() || !connector || state.busy;
  }

  function renderMap() {
    clearSvgLayer(elements.edgeLayer);
    clearSvgLayer(elements.nodeLayer);
    clearSvgLayer(elements.stopLayer);
    clearSvgLayer(elements.poiLayer);
    if (!state.workspace) {
      return;
    }
    const floor = currentFloor();
    if (!floor) {
      showMapMessage("没有楼层数据", "当前版本尚未绑定任何楼层地图。");
      return;
    }
    const map = floor.mapRevision;
    elements.mapTitle.textContent = `${floor.code} · ${floor.name}`;
    elements.graphOverlay.setAttribute(
      "viewBox",
      `0 0 ${map.imageWidth} ${map.imageHeight}`
    );
    elements.mapStage.style.aspectRatio =
      `${map.imageWidth} / ${map.imageHeight}`;
    elements.mapStage.style.width = `${state.zoom}%`;
    elements.floorImage.alt = `${state.workspace.building.name} ${floor.name} 楼层图`;
    elements.floorImage.onload = () => hideMapMessage();
    elements.floorImage.onerror = () => {
      showMapMessage(
        "楼层图片加载失败",
        `无法读取 ${map.imageUrl}，请检查静态资源服务。`
      );
    };
    elements.floorImage.src = resolveImageUrl(map.imageUrl);
    if (elements.floorImage.complete && elements.floorImage.naturalWidth > 0) {
      hideMapMessage();
    }

    const graph = state.workspace.graph;
    const nodes = graph.nodes.filter((node) => node.floorId === floor.id);
    const nodeMap = new Map(nodes.map((node) => [node.id, node]));
    const edges = graph.edges.filter((edge) => edge.floorId === floor.id);
    const pois = graph.pois.filter((poi) => poi.floorId === floor.id);
    const stops = graph.connectorStops.filter(
      (stop) => stop.floorId === floor.id
    );

    for (const edge of edges) {
      const from = nodeMap.get(edge.fromNodeId);
      const to = nodeMap.get(edge.toNodeId);
      if (!from || !to) {
        continue;
      }
      const line = svgElement("line", {
        x1: from.x,
        y1: from.y,
        x2: to.x,
        y2: to.y
      });
      line.classList.add("graph-edge");
      if (!edge.accessible) {
        line.classList.add("inaccessible");
      }
      if (!edge.enabled) {
        line.classList.add("disabled");
      }
      if (isSelected("edge", edge.id)) {
        line.classList.add("selected");
      }
      line.addEventListener("click", (event) => {
        event.stopPropagation();
        selectObject("edge", edge.id);
      });
      elements.edgeLayer.append(line);
    }

    for (const node of nodes) {
      const circle = svgElement("circle", {
        cx: node.x,
        cy: node.y,
        r: 10
      });
      circle.classList.add("graph-node", `type-${node.type}`);
      if (!node.enabled) {
        circle.classList.add("disabled");
      }
      if (state.edgeStartId === node.id) {
        circle.classList.add("edge-start");
      }
      if (isSelected("node", node.id)) {
        circle.classList.add("selected");
      }
      circle.addEventListener("click", (event) => {
        event.stopPropagation();
        handleNodeClick(node);
      });
      circle.addEventListener("pointerdown", (event) => {
        beginNodeDrag(event, node);
      });
      elements.nodeLayer.append(circle);
    }

    for (const stop of stops) {
      const node = nodeMap.get(stop.nodeId);
      if (!node) {
        continue;
      }
      const circle = svgElement("circle", {
        cx: node.x,
        cy: node.y,
        r: 18
      });
      circle.classList.add("stop-marker");
      if (isSelected("stop", stop.id)) {
        circle.classList.add("selected");
      }
      circle.addEventListener("click", (event) => {
        event.stopPropagation();
        selectObject("stop", stop.id);
      });
      elements.stopLayer.append(circle);
    }

    for (const poi of pois) {
      const group = svgElement("g", {});
      group.classList.add("poi-marker");
      if (isSelected("poi", poi.id)) {
        group.classList.add("selected");
      }
      const marker = svgElement("circle", {
        cx: poi.x,
        cy: poi.y,
        r: 12
      });
      const label = svgElement("text", {
        x: poi.x + 18,
        y: poi.y - 15
      });
      label.textContent = poi.name;
      group.append(marker, label);
      group.addEventListener("click", (event) => {
        event.stopPropagation();
        selectObject("poi", poi.id);
      });
      elements.poiLayer.append(group);
    }

    elements.mapCounts.textContent =
      `${nodes.length} 节点 · ${edges.length} 路径 · ` +
      `${pois.length} POI · ${stops.length} 停靠点`;
  }

  function renderInspector() {
    if (!state.workspace || !state.selection) {
      elements.inspectorContent.className = "empty-inspector";
      elements.inspectorContent.innerHTML =
        "<strong>未选择地图元素</strong>" +
        "<p>从中间地图选择节点、路径、POI 或停靠点。</p>";
      return;
    }
    const object = selectedObject();
    if (!object) {
      state.selection = null;
      renderInspector();
      return;
    }
    elements.inspectorContent.className = "inspector-form";
    switch (state.selection.kind) {
      case "node":
        renderNodeInspector(object);
        break;
      case "edge":
        renderEdgeInspector(object);
        break;
      case "poi":
        renderPoiInspector(object);
        break;
      case "connector":
        renderConnectorInspector(object);
        break;
      case "stop":
        renderStopInspector(object);
        break;
      case "link":
        renderLinkInspector(object);
        break;
      default:
        state.selection = null;
        renderInspector();
    }
  }

  function renderNodeInspector(node) {
    const floor = floorById(node.floorId);
    elements.inspectorContent.innerHTML =
      inspectorHeading("路径节点", node.code, floor && floor.code) +
      `<label class="field"><span>节点编码</span>` +
      `<input id="fieldCode" maxlength="80" value="${escapeAttribute(node.code)}"` +
      disabledAttribute() + ` /></label>` +
      `<label class="field"><span>节点类型</span>` +
      `<select id="fieldType"${disabledAttribute()}>` +
      options(
        [
          ["normal", "普通节点"],
          ["decision", "决策点"],
          ["poi_access", "POI 接入点"],
          ["connector_stop", "跨层设施落点"]
        ],
        node.type
      ) +
      `</select></label>` +
      `<div class="field-row">` +
      numberField("fieldX", "X 坐标", node.x, 0, "1") +
      numberField("fieldY", "Y 坐标", node.y, 0, "1") +
      `</div>` +
      checkField("fieldEnabled", "节点启用", node.enabled) +
      dependencyHtml(nodeDependencies(node.id)) +
      dangerZone("删除节点");
    bindText("fieldCode", (value) => {
      node.code = value;
      changed(true);
    });
    bindSelect("fieldType", (value) => {
      node.type = value;
      changed(true);
    });
    bindNumber("fieldX", (value) => {
      node.x = value;
      changed(true);
    });
    bindNumber("fieldY", (value) => {
      node.y = value;
      changed(true);
    });
    bindCheck("fieldEnabled", (value) => {
      node.enabled = value;
      changed(true);
    });
    bindDelete();
  }

  function renderEdgeInspector(edge) {
    const from = findById(state.workspace.graph.nodes, edge.fromNodeId);
    const to = findById(state.workspace.graph.nodes, edge.toNodeId);
    elements.inspectorContent.innerHTML =
      inspectorHeading("楼层内路径", edge.code, floorCode(edge.floorId)) +
      `<label class="field"><span>路径编码</span>` +
      `<input id="fieldCode" maxlength="80" value="${escapeAttribute(edge.code)}"` +
      disabledAttribute() + ` /></label>` +
      `<div class="read-only-grid">` +
      readOnlyRow("起点", from ? from.code : "引用缺失") +
      readOnlyRow("终点", to ? to.code : "引用缺失") +
      `</div>` +
      `<div class="field-row">` +
      numberField("fieldTime", "预计耗时（秒）", edge.timeSeconds, 1, "1") +
      numberField(
        "fieldDistance",
        "现场距离（米）",
        edge.distanceMeters,
        0,
        "0.1"
      ) +
      `</div>` +
      `<div class="field-row">` +
      `<label class="field"><span>通行方向</span>` +
      `<select id="fieldDirection"${disabledAttribute()}>` +
      options([["both", "双向"], ["forward", "起点到终点"]], edge.direction) +
      `</select></label>` +
      `<label class="field"><span>路径类型</span>` +
      `<select id="fieldType"${disabledAttribute()}>` +
      options(
        [
          ["walk", "步行"],
          ["corridor", "走廊"],
          ["door", "门"],
          ["ramp", "坡道"],
          ["virtual", "虚拟连接"]
        ],
        edge.type
      ) +
      `</select></label>` +
      `</div>` +
      `<p class="dialog-note">像素距离不能换算为现场米数，请按实测值填写。</p>` +
      checkField("fieldAccessible", "可用于无障碍路线", edge.accessible) +
      checkField("fieldEnabled", "路径启用", edge.enabled) +
      dangerZone("删除路径");
    bindText("fieldCode", (value) => {
      edge.code = value;
      changed(true);
    });
    bindNumber("fieldTime", (value) => {
      edge.timeSeconds = Math.round(value);
      changed(false);
    });
    bindNumber("fieldDistance", (value) => {
      edge.distanceMeters = value;
      changed(false);
    });
    bindSelect("fieldDirection", (value) => {
      edge.direction = value;
      changed(false);
    });
    bindSelect("fieldType", (value) => {
      edge.type = value;
      changed(false);
    });
    bindCheck("fieldAccessible", (value) => {
      edge.accessible = value;
      changed(true);
    });
    bindCheck("fieldEnabled", (value) => {
      edge.enabled = value;
      changed(true);
    });
    bindDelete();
  }

  function renderPoiInspector(poi) {
    const nodes = state.workspace.graph.nodes.filter(
      (node) => node.floorId === poi.floorId
    );
    elements.inspectorContent.innerHTML =
      inspectorHeading("导航 POI", poi.name, floorCode(poi.floorId)) +
      `<label class="field"><span>名称</span>` +
      `<input id="fieldName" maxlength="100" value="${escapeAttribute(poi.name)}"` +
      disabledAttribute() + ` /></label>` +
      `<div class="field-row">` +
      `<label class="field"><span>POI 编码</span>` +
      `<input id="fieldCode" maxlength="80" value="${escapeAttribute(poi.code)}"` +
      disabledAttribute() + ` /></label>` +
      `<label class="field"><span>分类</span>` +
      `<input id="fieldCategory" maxlength="50" value="${escapeAttribute(poi.category)}"` +
      disabledAttribute() + ` /></label>` +
      `</div>` +
      `<label class="field"><span>绑定路径节点</span>` +
      `<select id="fieldNode"${disabledAttribute()}>` +
      options(
        nodes.map((node) => [node.id, node.code]),
        poi.nodeId
      ) +
      `</select></label>` +
      `<div class="field-row">` +
      numberField("fieldX", "标签 X 坐标", poi.x, 0, "1") +
      numberField("fieldY", "标签 Y 坐标", poi.y, 0, "1") +
      `</div>` +
      `<label class="field"><span>搜索关键词（逗号分隔）</span>` +
      `<input id="fieldKeywords" value="${escapeAttribute(poi.keywords.join(", "))}"` +
      disabledAttribute() + ` /></label>` +
      checkField("fieldAccessible", "POI 可无障碍到达", poi.accessible) +
      checkField("fieldEnabled", "POI 启用", poi.enabled) +
      dangerZone("删除 POI");
    bindText("fieldName", (value) => {
      poi.name = value;
      changed(true);
    });
    bindText("fieldCode", (value) => {
      poi.code = value;
      changed(false);
    });
    bindText("fieldCategory", (value) => {
      poi.category = value;
      changed(false);
    });
    bindSelect("fieldNode", (value) => {
      poi.nodeId = value;
      changed(false);
    });
    bindNumber("fieldX", (value) => {
      poi.x = value;
      changed(true);
    });
    bindNumber("fieldY", (value) => {
      poi.y = value;
      changed(true);
    });
    bindText("fieldKeywords", (value) => {
      poi.keywords = value
        .split(/[，,]/)
        .map((keyword) => keyword.trim())
        .filter(Boolean);
      changed(false);
    });
    bindCheck("fieldAccessible", (value) => {
      poi.accessible = value;
      changed(false);
    });
    bindCheck("fieldEnabled", (value) => {
      poi.enabled = value;
      changed(true);
    });
    bindDelete();
  }

  function renderConnectorInspector(connector) {
    const stops = state.workspace.graph.connectorStops.filter(
      (stop) => stop.connectorId === connector.id
    );
    const links = state.workspace.graph.verticalLinks.filter(
      (link) => link.connectorId === connector.id
    );
    elements.inspectorContent.innerHTML =
      inspectorHeading(
        connector.type === "elevator" ? "电梯" : "楼梯",
        connector.name,
        connector.code
      ) +
      `<label class="field"><span>设施名称</span>` +
      `<input id="fieldName" maxlength="100" value="${escapeAttribute(connector.name)}"` +
      disabledAttribute() + ` /></label>` +
      `<div class="field-row">` +
      `<label class="field"><span>设施编码</span>` +
      `<input id="fieldCode" maxlength="80" value="${escapeAttribute(connector.code)}"` +
      disabledAttribute() + ` /></label>` +
      `<label class="field"><span>设施类型</span>` +
      `<select id="fieldType"${disabledAttribute()}>` +
      options([["elevator", "电梯"], ["stairs", "楼梯"]], connector.type) +
      `</select></label>` +
      `</div>` +
      checkField(
        "fieldAccessible",
        "可用于无障碍路线",
        connector.accessible
      ) +
      checkField("fieldEnabled", "设施启用", connector.enabled) +
      `<div class="inspector-group"><h3>停靠楼层</h3>` +
      connectorStopsHtml(stops) +
      `</div>` +
      `<div class="inspector-group"><h3>明确跨层连接</h3>` +
      connectorLinksHtml(links) +
      `</div>` +
      dangerZone("删除设施及配置");
    bindText("fieldName", (value) => {
      connector.name = value;
      changed(true);
    });
    bindText("fieldCode", (value) => {
      connector.code = value;
      changed(true);
    });
    bindSelect("fieldType", (value) => {
      connector.type = value;
      changed(false);
    });
    bindCheck("fieldAccessible", (value) => {
      connector.accessible = value;
      changed(false);
    });
    bindCheck("fieldEnabled", (value) => {
      connector.enabled = value;
      changed(false);
    });
    bindInspectorLinks();
    bindDelete();
  }

  function renderStopInspector(stop) {
    const connector = findById(
      state.workspace.graph.connectors,
      stop.connectorId
    );
    const floorNodes = state.workspace.graph.nodes.filter(
      (node) => node.floorId === stop.floorId
    );
    const linked = state.workspace.graph.verticalLinks.filter(
      (link) => link.fromStopId === stop.id || link.toStopId === stop.id
    );
    elements.inspectorContent.innerHTML =
      inspectorHeading(
        "设施停靠点",
        stop.code,
        `${connector ? connector.name : "设施缺失"} · ${floorCode(stop.floorId)}`
      ) +
      `<label class="field"><span>停靠点编码</span>` +
      `<input id="fieldCode" maxlength="80" value="${escapeAttribute(stop.code)}"` +
      disabledAttribute() + ` /></label>` +
      `<label class="field"><span>绑定路径节点</span>` +
      `<select id="fieldNode"${disabledAttribute()}>` +
      options(
        floorNodes.map((node) => [node.id, node.code]),
        stop.nodeId
      ) +
      `</select></label>` +
      `<div class="inspector-group"><h3>跨层连接</h3>` +
      connectorLinksHtml(linked) +
      `</div>` +
      dangerZone("删除停靠点");
    bindText("fieldCode", (value) => {
      stop.code = value;
      changed(true);
    });
    bindSelect("fieldNode", (value) => {
      stop.nodeId = value;
      changed(true);
    });
    bindInspectorLinks();
    bindDelete();
  }

  function renderLinkInspector(link) {
    const connector = findById(
      state.workspace.graph.connectors,
      link.connectorId
    );
    const stops = state.workspace.graph.connectorStops.filter(
      (stop) => stop.connectorId === link.connectorId
    );
    elements.inspectorContent.innerHTML =
      inspectorHeading(
        "跨层连接",
        link.code,
        connector ? connector.name : "设施缺失"
      ) +
      `<label class="field"><span>连接编码</span>` +
      `<input id="fieldCode" maxlength="80" value="${escapeAttribute(link.code)}"` +
      disabledAttribute() + ` /></label>` +
      `<div class="field-row">` +
      `<label class="field"><span>起始停靠点</span>` +
      `<select id="fieldFrom"${disabledAttribute()}>` +
      options(stops.map(stopOption), link.fromStopId) +
      `</select></label>` +
      `<label class="field"><span>目标停靠点</span>` +
      `<select id="fieldTo"${disabledAttribute()}>` +
      options(stops.map(stopOption), link.toStopId) +
      `</select></label>` +
      `</div>` +
      `<div class="field-row">` +
      numberField("fieldTime", "预计耗时（秒）", link.timeSeconds, 1, "1") +
      `<label class="field"><span>方向</span>` +
      `<select id="fieldDirection"${disabledAttribute()}>` +
      options([["both", "双向"], ["forward", "起点到终点"]], link.direction) +
      `</select></label>` +
      `</div>` +
      checkField("fieldAccessible", "连接可用于无障碍路线", link.accessible) +
      checkField("fieldEnabled", "连接启用", link.enabled) +
      dangerZone("删除跨层连接");
    bindText("fieldCode", (value) => {
      link.code = value;
      changed(false);
    });
    bindSelect("fieldFrom", (value) => {
      link.fromStopId = value;
      changed(false);
    });
    bindSelect("fieldTo", (value) => {
      link.toStopId = value;
      changed(false);
    });
    bindNumber("fieldTime", (value) => {
      link.timeSeconds = Math.round(value);
      changed(false);
    });
    bindSelect("fieldDirection", (value) => {
      link.direction = value;
      changed(false);
    });
    bindCheck("fieldAccessible", (value) => {
      link.accessible = value;
      changed(false);
    });
    bindCheck("fieldEnabled", (value) => {
      link.enabled = value;
      changed(false);
    });
    bindDelete();
  }

  function renderValidation() {
    if (!state.validation) {
      elements.validationContent.className = "validation-empty";
      elements.validationContent.innerHTML =
        "<strong>尚未执行发布校验</strong>" +
        "<p>保存草稿后运行校验，系统会检查可达性和跨层配置。</p>";
      elements.validationCount.hidden = true;
      return;
    }
    const errors = state.validation.errors || [];
    const warnings = state.validation.warnings || [];
    const count = errors.length + warnings.length;
    elements.validationCount.hidden = count === 0;
    elements.validationCount.textContent = String(count);
    elements.validationContent.className = "";
    elements.validationContent.innerHTML =
      `<div class="validation-summary${state.validation.passed ? " passed" : ""}">` +
      `<strong>${state.validation.passed ? "校验通过" : "校验未通过"}</strong>` +
      `<span>修订 ${state.validation.contentRevision}：` +
      `${errors.length} 个错误，${warnings.length} 个提醒</span>` +
      `</div>` +
      `<div class="issue-list">` +
      errors.map((issue) => issueHtml(issue, false)).join("") +
      warnings.map((issue) => issueHtml(issue, true)).join("") +
      `</div>`;
    for (const button of elements.validationContent.querySelectorAll(
      "[data-issue-id]"
    )) {
      button.addEventListener("click", () => {
        const kind = issueKind(button.dataset.issueType);
        if (!kind || !button.dataset.issueId) {
          return;
        }
        switchPanel("inspector");
        selectObject(kind, button.dataset.issueId);
      });
    }
  }

  function renderOperations() {
    const operations = state.operations;
    if (!operations) {
      elements.operationsRelease.textContent = "运营状态未加载";
      elements.operationList.innerHTML =
        `<div class="operation-empty">暂无运营状态数据</div>`;
      elements.operationCount.hidden = true;
      elements.createClosureButton.disabled = true;
      return;
    }
    elements.operationsRelease.textContent =
      `当前发布版本 · ${operations.releaseCode}`;
    const closures = operations.items || [];
    elements.operationCount.hidden = closures.length === 0;
    elements.operationCount.textContent = String(closures.length);
    renderOperationTargets();
    elements.closureTypeInput.disabled = state.busy;
    elements.closureReasonInput.disabled = state.busy;
    elements.createClosureButton.disabled =
      state.busy || elements.closureTargetInput.options.length === 0;

    if (closures.length === 0) {
      elements.operationList.innerHTML =
        `<div class="operation-empty">当前没有临时封闭</div>`;
      return;
    }
    elements.operationList.innerHTML = closures
      .map((closure) => {
        const targetType = {
          vertical_connector: "设施",
          path_edge: "楼层路径",
          vertical_link: "跨层连接"
        }[closure.targetType] || closure.targetType;
        const until = closure.effectiveTo
          ? `至 ${formatDate(closure.effectiveTo)}`
          : "持续至手动恢复";
        return (
          `<article class="operation-row">` +
          `<div class="operation-row-heading"><div>` +
          `<strong>${escapeHtml(closure.targetName)}</strong>` +
          `<span>${escapeHtml(targetType)} · ${escapeHtml(closure.targetCode)}</span>` +
          `</div><button class="mini-button" type="button"` +
          ` data-revoke-closure="${escapeAttribute(closure.id)}">恢复</button></div>` +
          `<p class="operation-reason">${escapeHtml(closure.reason)}</p>` +
          `<span class="operation-time">${escapeHtml(until)}</span>` +
          `</article>`
        );
      })
      .join("");
    for (const button of elements.operationList.querySelectorAll(
      "[data-revoke-closure]"
    )) {
      button.disabled = state.busy;
      button.addEventListener("click", () => {
        revokeOperationClosure(button.dataset.revokeClosure);
      });
    }
  }

  function renderOperationTargets() {
    if (!state.operations) {
      elements.closureTargetInput.innerHTML = "";
      elements.closureTargetInput.disabled = true;
      return;
    }
    const targetType = elements.closureTypeInput.value;
    const targets = (state.operations.targets || []).filter(
      (target) => target.targetType === targetType
    );
    const previous = elements.closureTargetInput.value;
    elements.closureTargetInput.innerHTML = targets
      .map((target) => {
        const floor = target.floorCode ? `${target.floorCode} · ` : "";
        return (
          `<option value="${escapeAttribute(target.id)}">` +
          `${escapeHtml(floor + target.name)}（${escapeHtml(target.code)}）` +
          `</option>`
        );
      })
      .join("");
    if (targets.some((target) => target.id === previous)) {
      elements.closureTargetInput.value = previous;
    }
    elements.closureTargetInput.disabled = state.busy || targets.length === 0;
  }

  function handleNodeClick(node) {
    if (!isEditable() && state.tool !== "select") {
      showToast("发布版本为只读，请先新建草稿。", true);
      return;
    }
    if (state.tool === "edge") {
      if (!state.edgeStartId) {
        state.edgeStartId = node.id;
        state.selection = {kind: "node", id: node.id};
        elements.toolHint.textContent = "已选择起点，请点击同楼层的另一个节点。";
        renderMap();
        renderInspector();
        return;
      }
      if (state.edgeStartId === node.id) {
        state.edgeStartId = null;
        elements.toolHint.textContent = toolHints.edge;
        renderMap();
        return;
      }
      addEdge(state.edgeStartId, node.id);
      state.edgeStartId = null;
      elements.toolHint.textContent = toolHints.edge;
      return;
    }
    if (state.tool === "poi") {
      addPoi(node);
      return;
    }
    if (state.tool === "stop") {
      addStop(node);
      return;
    }
    selectObject("node", node.id);
  }

  function beginNodeDrag(event, node) {
    if (
      !isEditable() ||
      state.tool !== "select" ||
      (event.pointerType === "mouse" && event.button !== 0)
    ) {
      return;
    }
    event.preventDefault();
    event.stopPropagation();
    state.selection = {kind: "node", id: node.id};
    state.drag = {
      pointerId: event.pointerId,
      nodeId: node.id,
      startClientX: event.clientX,
      startClientY: event.clientY,
      moved: false
    };
    elements.graphOverlay.setPointerCapture(event.pointerId);
    renderInspector();
    switchPanel("inspector");
  }

  function handleNodeDrag(event) {
    if (!state.drag || state.drag.pointerId !== event.pointerId) {
      return;
    }
    const distance = Math.hypot(
      event.clientX - state.drag.startClientX,
      event.clientY - state.drag.startClientY
    );
    if (!state.drag.moved && distance < 3) {
      return;
    }
    const point = mapPoint(event);
    const floor = currentFloor();
    const node = findById(
      state.workspace.graph.nodes,
      state.drag.nodeId
    );
    if (!point || !floor || !node) {
      return;
    }
    const nextX = Math.round(
      Math.min(Math.max(point.x, 0), floor.mapRevision.imageWidth) * 10
    ) / 10;
    const nextY = Math.round(
      Math.min(Math.max(point.y, 0), floor.mapRevision.imageHeight) * 10
    ) / 10;
    const deltaX = nextX - node.x;
    const deltaY = nextY - node.y;
    node.x = nextX;
    node.y = nextY;
    for (const poi of state.workspace.graph.pois) {
      if (poi.nodeId === node.id) {
        poi.x = Math.min(
          Math.max(poi.x + deltaX, 0),
          floor.mapRevision.imageWidth
        );
        poi.y = Math.min(
          Math.max(poi.y + deltaY, 0),
          floor.mapRevision.imageHeight
        );
      }
    }
    if (!state.drag.moved) {
      state.drag.moved = true;
      state.dirty = true;
      state.validation = null;
      updateActionStates();
      renderValidation();
    }
    renderMap();
  }

  function endNodeDrag(event) {
    if (!state.drag || state.drag.pointerId !== event.pointerId) {
      return;
    }
    const moved = state.drag.moved;
    if (elements.graphOverlay.hasPointerCapture(event.pointerId)) {
      elements.graphOverlay.releasePointerCapture(event.pointerId);
    }
    state.drag = null;
    state.ignoreMapClick = moved;
    if (moved) {
      renderInspector();
      window.setTimeout(() => {
        state.ignoreMapClick = false;
      }, 0);
    }
  }

  function handleMapClick(event) {
    if (state.ignoreMapClick) {
      state.ignoreMapClick = false;
      return;
    }
    if (!state.workspace || !isEditable()) {
      if (state.tool === "select") {
        state.selection = null;
        renderMap();
        renderInspector();
      }
      return;
    }
    const point = mapPoint(event);
    if (!point) {
      return;
    }
    if (state.tool === "node") {
      addNode(point);
      return;
    }
    if (state.tool === "poi" || state.tool === "stop") {
      const node = nearestNode(point);
      if (!node) {
        showToast("附近没有路径节点，请先新增或点击已有节点。", true);
        return;
      }
      if (state.tool === "poi") {
        addPoi(node);
      } else {
        addStop(node);
      }
      return;
    }
    if (state.tool === "select") {
      state.selection = null;
      renderMap();
      renderInspector();
    }
  }

  function addNode(point) {
    const floor = currentFloor();
    const node = {
      id: crypto.randomUUID(),
      code: nextCode(`N-${floor.code}`, state.workspace.graph.nodes),
      floorId: floor.id,
      x: Math.round(point.x),
      y: Math.round(point.y),
      type: "normal",
      enabled: true
    };
    state.workspace.graph.nodes.push(node);
    state.selection = {kind: "node", id: node.id};
    changed(true);
    renderInspector();
  }

  function addEdge(fromId, toId) {
    const from = findById(state.workspace.graph.nodes, fromId);
    const to = findById(state.workspace.graph.nodes, toId);
    if (!from || !to || from.floorId !== to.floorId) {
      showToast("楼层内路径的两个节点必须位于同一楼层。", true);
      return;
    }
    const floor = floorById(from.floorId);
    const edge = {
      id: crypto.randomUUID(),
      code: nextCode(`EDGE-${floor.code}`, state.workspace.graph.edges),
      floorId: floor.id,
      fromNodeId: from.id,
      toNodeId: to.id,
      timeSeconds: 10,
      distanceMeters: 0,
      direction: "both",
      type: "corridor",
      accessScope: "public",
      accessible: true,
      enabled: true
    };
    state.workspace.graph.edges.push(edge);
    state.selection = {kind: "edge", id: edge.id};
    changed(true);
    renderInspector();
    showToast("路径已创建，请在右侧填写现场距离和预计耗时。");
  }

  function addPoi(node) {
    const floor = floorById(node.floorId);
    const poi = {
      id: crypto.randomUUID(),
      code: nextCode(`P-${floor.code}`, state.workspace.graph.pois),
      name: "新 POI",
      category: "department",
      floorId: floor.id,
      nodeId: node.id,
      x: node.x,
      y: node.y,
      accessScope: "public",
      accessible: true,
      enabled: true,
      keywords: []
    };
    state.workspace.graph.pois.push(poi);
    state.selection = {kind: "poi", id: poi.id};
    changed(true);
    renderInspector();
  }

  function addStop(node) {
    const connector = findById(
      state.workspace.graph.connectors,
      state.selectedConnectorId
    );
    if (!connector) {
      showToast("请先在左侧选择停靠点所属的电梯或楼梯。", true);
      return;
    }
    const existing = state.workspace.graph.connectorStops.find(
      (stop) =>
        stop.connectorId === connector.id && stop.floorId === node.floorId
    );
    if (existing) {
      selectObject("stop", existing.id);
      showToast(`${connector.name} 在本层已有停靠点。`, true);
      return;
    }
    const floor = floorById(node.floorId);
    const stop = {
      id: crypto.randomUUID(),
      code: nextCode(
        `STOP-${connector.code}-${floor.code}`,
        state.workspace.graph.connectorStops
      ),
      connectorId: connector.id,
      floorId: floor.id,
      nodeId: node.id
    };
    state.workspace.graph.connectorStops.push(stop);
    node.type = "connector_stop";
    state.selection = {kind: "stop", id: stop.id};
    changed(true);
    renderConnectorControls();
    renderInspector();
    showToast("停靠点已标注，还需要配置明确的跨层连接。");
  }

  function deleteSelected() {
    if (!isEditable() || !state.selection) {
      return;
    }
    const {kind, id} = state.selection;
    const graph = state.workspace.graph;
    if (kind === "node") {
      const dependencies = nodeDependencies(id);
      if (dependencies.length) {
        showToast("该节点仍被路径、POI 或停靠点引用，不能删除。", true);
        return;
      }
    }
    if (kind === "stop") {
      const linked = graph.verticalLinks.some(
        (link) => link.fromStopId === id || link.toStopId === id
      );
      if (linked) {
        showToast("该停靠点仍有跨层连接，请先删除连接。", true);
        return;
      }
    }
    const label = kindLabel(kind);
    if (!window.confirm(`确认删除${label}？该操作会在保存草稿后生效。`)) {
      return;
    }
    if (kind === "connector") {
      const stopIds = new Set(
        graph.connectorStops
          .filter((stop) => stop.connectorId === id)
          .map((stop) => stop.id)
      );
      graph.verticalLinks = graph.verticalLinks.filter(
        (link) => link.connectorId !== id
      );
      graph.connectorStops = graph.connectorStops.filter(
        (stop) => !stopIds.has(stop.id)
      );
      graph.connectors = graph.connectors.filter(
        (connector) => connector.id !== id
      );
      state.selectedConnectorId =
        graph.connectors[0] && graph.connectors[0].id;
    } else {
      const property = kindProperty(kind);
      graph[property] = graph[property].filter((item) => item.id !== id);
    }
    state.selection = null;
    changed(true);
    renderConnectorControls();
    renderInspector();
  }

  async function saveGraph() {
    if (!state.workspace || !isEditable() || !state.dirty) {
      return state.workspace;
    }
    setBusy(true);
    elements.saveState.textContent = "保存中";
    elements.saveState.className = "save-state";
    try {
      const selection = state.selection && {...state.selection};
      const response = await request(
        `/api/admin/releases/${state.workspace.release.id}/workspace`,
        {
          method: "PUT",
          headers: {"If-Match": state.etag},
          body: JSON.stringify(state.workspace.graph)
        }
      );
      state.workspace = response.data;
      state.etag =
        response.etag || `"${state.workspace.release.contentRevision}"`;
      state.dirty = false;
      state.validation = null;
      state.selection = selection;
      updateReleaseFromWorkspace();
      renderAll();
      showToast(`草稿修订 ${state.workspace.release.contentRevision} 已保存。`);
      return state.workspace;
    } catch (error) {
      handleError(error);
      throw error;
    } finally {
      setBusy(false);
    }
  }

  async function replaceFloorMap(file) {
    if (!state.workspace || !isEditable() || !file) {
      return;
    }
    try {
      if (state.dirty) {
        await saveGraph();
      }
      const floor = currentFloor();
      if (!floor) {
        return;
      }
      setBusy(true);
      const formData = new FormData();
      formData.append("file", file);
      const response = await request(
        `/api/admin/releases/${state.workspace.release.id}` +
          `/floors/${floor.id}/map`,
        {
          method: "POST",
          headers: {"If-Match": state.etag},
          body: formData
        }
      );
      state.workspace = response.data;
      state.etag =
        response.etag || `"${state.workspace.release.contentRevision}"`;
      state.validation = null;
      state.dirty = false;
      state.selection = null;
      updateReleaseFromWorkspace();
      renderAll();
      showToast(
        `${floor.code} 底图已更新为 ${file.name}，标注坐标已按尺寸同比缩放。`
      );
    } catch (error) {
      handleError(error);
    } finally {
      elements.mapFileInput.value = "";
      setBusy(false);
    }
  }

  async function validateDraft() {
    if (!state.workspace || !isEditable()) {
      return;
    }
    try {
      if (state.dirty) {
        await saveGraph();
      }
      setBusy(true);
      const response = await request(
        `/api/admin/releases/${state.workspace.release.id}/validate`,
        {
          method: "POST",
          headers: {"If-Match": state.etag}
        }
      );
      state.validation = response.data;
      updateReleaseValidation(response.data);
      renderValidation();
      updateActionStates();
      switchPanel("validation");
      showToast(
        state.validation.passed
          ? "发布校验通过。"
          : `校验发现 ${state.validation.errors.length} 个错误。`,
        !state.validation.passed
      );
    } catch (error) {
      handleError(error);
    } finally {
      setBusy(false);
    }
  }

  async function publishDraft(reason) {
    setBusy(true);
    try {
      const response = await request(
        `/api/admin/releases/${state.workspace.release.id}/publish`,
        {
          method: "POST",
          headers: {"If-Match": state.etag},
          body: JSON.stringify({reason})
        }
      );
      elements.publishDialog.close();
      showToast(`版本 ${response.data.release.code} 已发布。`);
      await loadAll(response.data.release.id);
    } catch (error) {
      handleError(error);
    } finally {
      setBusy(false);
    }
  }

  async function rollbackRelease(reason) {
    setBusy(true);
    try {
      const response = await request(
        `/api/admin/releases/${state.workspace.release.id}/rollback`,
        {
          method: "POST",
          body: JSON.stringify({reason})
        }
      );
      elements.rollbackDialog.close();
      showToast(`已回滚启用版本 ${response.data.release.code}。`);
      await loadAll(response.data.release.id);
    } catch (error) {
      handleError(error);
    } finally {
      setBusy(false);
    }
  }

  async function createOperationClosure(event) {
    event.preventDefault();
    const targetId = elements.closureTargetInput.value;
    const reason = elements.closureReasonInput.value.trim();
    if (!targetId || !reason) {
      return;
    }
    setBusy(true);
    try {
      const response = await request(
        `/api/admin/buildings/${BUILDING_ID}/operations/closures`,
        {
          method: "POST",
          body: JSON.stringify({
            targetType: elements.closureTypeInput.value,
            targetId,
            reason
          })
        }
      );
      state.operations = response.data;
      elements.closureReasonInput.value = "";
      renderOperations();
      showToast("运营封闭已生效，新的路线计算会立即避开该对象。");
    } catch (error) {
      handleError(error);
    } finally {
      setBusy(false);
    }
  }

  async function revokeOperationClosure(closureId) {
    if (!closureId) {
      return;
    }
    setBusy(true);
    try {
      const response = await request(
        `/api/admin/operations/closures/${closureId}`,
        {method: "DELETE"}
      );
      state.operations = response.data;
      renderOperations();
      showToast("对象已恢复使用，新的路线计算会采用最新状态。");
    } catch (error) {
      handleError(error);
    } finally {
      setBusy(false);
    }
  }

  async function discardDraft() {
    if (!isEditable() || state.busy) {
      return;
    }
    const releaseId = state.workspace.release.id;
    setBusy(true);
    try {
      await request(`/api/admin/releases/${releaseId}`, {
        method: "DELETE",
        headers: {"If-Match": state.etag}
      });
      elements.discardDraftDialog.close();
      state.workspace = null;
      showToast("草稿已删除，发布版本未受影响。");
      await loadAll();
    } catch (error) {
      handleError(error);
    } finally {
      setBusy(false);
    }
  }

  async function createDraft(event) {
    event.preventDefault();
    const active = state.releases.find((release) => release.active);
    if (!active) {
      showToast("没有可复制的当前发布版本。", true);
      return;
    }
    setBusy(true);
    try {
      const response = await request(
        `/api/admin/buildings/${BUILDING_ID}/releases/drafts`,
        {
          method: "POST",
          body: JSON.stringify({
            code: elements.draftCodeInput.value.trim(),
            basedOnReleaseId: active.id,
            description: elements.draftDescriptionInput.value.trim()
          })
        }
      );
      elements.createDraftDialog.close();
      showToast(`草稿 ${response.data.release.code} 已创建。`);
      await loadAll(response.data.release.id);
    } catch (error) {
      handleError(error);
    } finally {
      setBusy(false);
    }
  }

  function createConnector(event) {
    event.preventDefault();
    const connector = {
      id: crypto.randomUUID(),
      code: elements.connectorCodeInput.value.trim(),
      name: elements.connectorNameInput.value.trim(),
      type: elements.connectorTypeInput.value,
      accessScope: "public",
      accessible: elements.connectorAccessibleInput.checked,
      enabled: true
    };
    state.workspace.graph.connectors.push(connector);
    state.selectedConnectorId = connector.id;
    state.selection = {kind: "connector", id: connector.id};
    elements.connectorDialog.close();
    changed(true);
    renderConnectorControls();
    renderInspector();
  }

  function createLink(event) {
    event.preventDefault();
    const connector = findById(
      state.workspace.graph.connectors,
      state.selectedConnectorId
    );
    const from = findById(
      state.workspace.graph.connectorStops,
      elements.fromStopInput.value
    );
    const to = findById(
      state.workspace.graph.connectorStops,
      elements.toStopInput.value
    );
    if (!connector || !from || !to || from.id === to.id) {
      showToast("请选择两个不同楼层的停靠点。", true);
      return;
    }
    if (from.floorId === to.floorId) {
      showToast("跨层连接的两个停靠点不能位于同一楼层。", true);
      return;
    }
    const duplicate = state.workspace.graph.verticalLinks.some(
      (link) =>
        link.connectorId === connector.id &&
        (
          (link.fromStopId === from.id && link.toStopId === to.id) ||
          (
            link.direction === "both" &&
            link.fromStopId === to.id &&
            link.toStopId === from.id
          )
        )
    );
    if (duplicate) {
      showToast("这两个停靠点之间已经存在连接。", true);
      return;
    }
    const link = {
      id: crypto.randomUUID(),
      code: nextCode(
        `VERT-${connector.code}`,
        state.workspace.graph.verticalLinks
      ),
      connectorId: connector.id,
      fromStopId: from.id,
      toStopId: to.id,
      timeSeconds: Number(elements.linkTimeInput.value),
      distanceMeters: 0,
      direction: elements.linkDirectionInput.value,
      accessScope: "public",
      accessible: elements.linkAccessibleInput.checked,
      enabled: true
    };
    state.workspace.graph.verticalLinks.push(link);
    state.selection = {kind: "link", id: link.id};
    elements.linkDialog.close();
    changed(true);
    renderInspector();
  }

  function openCreateDraftDialog() {
    const now = new Date();
    const part = [
      now.getFullYear(),
      String(now.getMonth() + 1).padStart(2, "0"),
      String(now.getDate()).padStart(2, "0"),
      "-",
      String(now.getHours()).padStart(2, "0"),
      String(now.getMinutes()).padStart(2, "0")
    ].join("");
    elements.draftCodeInput.value = `DRAFT-${part}`;
    elements.draftDescriptionInput.value = "";
    elements.createDraftDialog.showModal();
    elements.draftCodeInput.focus();
  }

  function openConnectorDialog() {
    if (!isEditable()) {
      return;
    }
    const count = state.workspace.graph.connectors.length + 1;
    elements.connectorNameInput.value = `${String.fromCharCode(64 + count)} 电梯`;
    elements.connectorCodeInput.value = `ELEV-${String.fromCharCode(64 + count)}`;
    elements.connectorTypeInput.value = "elevator";
    elements.connectorAccessibleInput.checked = true;
    elements.connectorDialog.showModal();
    elements.connectorNameInput.focus();
  }

  function openLinkDialog() {
    const connector = findById(
      state.workspace.graph.connectors,
      state.selectedConnectorId
    );
    if (!connector) {
      showToast("请先选择跨层设施。", true);
      return;
    }
    const stops = state.workspace.graph.connectorStops
      .filter((stop) => stop.connectorId === connector.id)
      .sort((a, b) => (
        (floorById(a.floorId) && floorById(a.floorId).levelNo) -
        (floorById(b.floorId) && floorById(b.floorId).levelNo)
      ));
    if (stops.length < 2) {
      showToast("至少标注两个楼层停靠点后才能配置连接。", true);
      return;
    }
    const stopOptions = stops
      .map((stop) => {
        const floor = floorById(stop.floorId);
        return (
          `<option value="${escapeAttribute(stop.id)}">` +
          `${escapeHtml(floor ? floor.code : "?")}：${escapeHtml(stop.code)}` +
          `</option>`
        );
      })
      .join("");
    elements.linkConnectorName.textContent =
      `${connector.name}：只有明确连接的停靠点之间才可通行。`;
    elements.fromStopInput.innerHTML = stopOptions;
    elements.toStopInput.innerHTML = stopOptions;
    elements.toStopInput.selectedIndex = 1;
    elements.linkTimeInput.value = "30";
    elements.linkDirectionInput.value = "both";
    elements.linkAccessibleInput.checked =
      connector.type === "elevator" && connector.accessible;
    elements.linkDialog.showModal();
  }

  function selectObject(kind, id) {
    const object = objectByKind(kind, id);
    if (!object) {
      return;
    }
    state.selection = {kind, id};
    if (kind === "connector") {
      state.selectedConnectorId = id;
      renderConnectorControls();
    }
    const floorId = object.floorId || stopFloorForLink(object);
    if (floorId && floorId !== state.activeFloorId) {
      state.activeFloorId = floorId;
      renderFloors();
    }
    renderMap();
    renderInspector();
    switchPanel("inspector");
  }

  function switchPanel(panel) {
    state.activePanel = panel;
    const panels = {
      inspector: [elements.inspectorTab, elements.inspectorPanel],
      validation: [elements.validationTab, elements.validationPanel],
      operations: [elements.operationsTab, elements.operationsPanel]
    };
    for (const [name, [tab, content]] of Object.entries(panels)) {
      const active = name === panel;
      tab.classList.toggle("active", active);
      tab.setAttribute("aria-selected", active ? "true" : "false");
      content.hidden = !active;
    }
    updatePublishActionsVisibility();
  }

  function changed(redrawMap) {
    state.dirty = true;
    state.validation = null;
    updateActionStates();
    renderValidation();
    if (redrawMap) {
      renderMap();
    }
  }

  function updateActionStates() {
    const editable = isEditable();
    const historical = isHistoricalPublished();
    elements.validateButton.hidden = !editable;
    elements.publishButton.hidden = !editable;
    elements.rollbackButton.hidden = !historical;
    elements.discardDraftButton.hidden = !editable;
    elements.saveButton.disabled =
      !editable || !state.dirty || state.busy;
    elements.replaceMapButton.disabled = !editable || state.busy;
    elements.validateButton.disabled =
      !editable || state.busy;
    elements.publishButton.disabled =
      !editable ||
      state.busy ||
      state.dirty ||
      !state.validation ||
      !state.validation.passed;
    elements.rollbackButton.disabled = !historical || state.busy;
    elements.discardDraftButton.disabled = !editable || state.busy;
    updatePublishActionsVisibility();
    elements.saveState.textContent = !state.workspace
      ? "未加载"
      : state.dirty
        ? "有未保存修改"
        : editable
          ? `修订 ${state.workspace.release.contentRevision}`
          : "只读";
    elements.saveState.className =
      `save-state ${state.dirty ? "dirty" : state.workspace ? "saved" : ""}`;
  }

  function isHistoricalPublished() {
    if (
      !state.workspace ||
      state.workspace.release.status !== "published"
    ) {
      return false;
    }
    const summary = state.releases.find(
      (release) => release.id === state.workspace.release.id
    );
    return Boolean(summary && !summary.active);
  }

  function updatePublishActionsVisibility() {
    const editable = isEditable();
    const historical = isHistoricalPublished();
    elements.publishActions.hidden =
      state.activePanel === "operations" || (!editable && !historical);
  }

  function setBusy(value) {
    state.busy = value;
    elements.reloadButton.disabled = value;
    elements.createDraftButton.disabled = value;
    if (state.workspace) {
      renderReleases();
      renderTools();
      renderConnectorControls();
    }
    renderOperations();
    updateActionStates();
  }

  function setConnection(kind, text) {
    elements.connectionState.className = `connection-state ${kind}`;
    elements.connectionText.textContent = text;
  }

  function resolveImageUrl(imageUrl) {
    if (/^https?:\/\//i.test(imageUrl)) {
      return imageUrl;
    }
    if (imageUrl.startsWith("/api/")) {
      return `${API_BASE}${imageUrl}`;
    }
    return new URL(imageUrl, window.location.origin).toString();
  }

  function showMapMessage(title, text) {
    elements.mapMessage.hidden = false;
    elements.mapMessage.innerHTML =
      `<strong>${escapeHtml(title)}</strong><span>${escapeHtml(text)}</span>`;
  }

  function hideMapMessage() {
    elements.mapMessage.hidden = true;
  }

  let toastTimer = null;
  function showToast(message, error = false) {
    window.clearTimeout(toastTimer);
    elements.toast.textContent = message;
    elements.toast.className = `toast${error ? " error" : ""}`;
    elements.toast.hidden = false;
    toastTimer = window.setTimeout(() => {
      elements.toast.hidden = true;
    }, 4200);
  }

  function handleError(error, mapError = false) {
    const apiError = error instanceof ApiError
      ? error
      : new ApiError(error.message || "发生未预期错误。", "UNKNOWN", 0, []);
    if (apiError.status === 0 || apiError.status >= 500) {
      setConnection("error", "连接异常");
    }
    const detail = apiError.details[0] && apiError.details[0].reason;
    showToast(detail ? `${apiError.message} ${detail}` : apiError.message, true);
    if (mapError) {
      showMapMessage("无法加载地图工作区", apiError.message);
    }
  }

  function updateReleaseFromWorkspace() {
    const index = state.releases.findIndex(
      (release) => release.id === state.workspace.release.id
    );
    if (index >= 0) {
      state.releases[index] = {
        ...state.releases[index],
        contentRevision: state.workspace.release.contentRevision,
        validationPassed: null,
        validatedRevision: null
      };
    }
  }

  function updateReleaseValidation(validation) {
    const release = state.releases.find(
      (item) => item.id === validation.releaseId
    );
    if (release) {
      release.validationPassed = validation.passed;
      release.validatedRevision = validation.contentRevision;
    }
  }

  function currentFloor() {
    return state.workspace &&
      state.workspace.floors.find((floor) => floor.id === state.activeFloorId);
  }

  function floorById(id) {
    return state.workspace &&
      state.workspace.floors.find((floor) => floor.id === id);
  }

  function floorCode(id) {
    const floor = floorById(id);
    return floor ? floor.code : "楼层缺失";
  }

  function isEditable() {
    return Boolean(
      state.workspace && state.workspace.release.status === "draft"
    );
  }

  function findById(items, id) {
    return items.find((item) => item.id === id);
  }

  function objectByKind(kind, id) {
    if (!state.workspace) {
      return null;
    }
    const property = kindProperty(kind);
    return property ? findById(state.workspace.graph[property], id) : null;
  }

  function selectedObject() {
    return state.selection &&
      objectByKind(state.selection.kind, state.selection.id);
  }

  function kindProperty(kind) {
    return {
      node: "nodes",
      edge: "edges",
      poi: "pois",
      connector: "connectors",
      stop: "connectorStops",
      link: "verticalLinks"
    }[kind];
  }

  function kindLabel(kind) {
    return {
      node: "节点",
      edge: "路径",
      poi: " POI",
      connector: "跨层设施及其全部配置",
      stop: "停靠点",
      link: "跨层连接"
    }[kind] || "元素";
  }

  function isSelected(kind, id) {
    return Boolean(
      state.selection &&
      state.selection.kind === kind &&
      state.selection.id === id
    );
  }

  function stopFloorForLink(object) {
    if (!state.selection || state.selection.kind !== "link") {
      return null;
    }
    const stop = findById(
      state.workspace.graph.connectorStops,
      object.fromStopId
    );
    return stop && stop.floorId;
  }

  function mapPoint(event) {
    const floor = currentFloor();
    if (!floor) {
      return null;
    }
    const rect = elements.graphOverlay.getBoundingClientRect();
    return {
      x:
        ((event.clientX - rect.left) / rect.width) *
        floor.mapRevision.imageWidth,
      y:
        ((event.clientY - rect.top) / rect.height) *
        floor.mapRevision.imageHeight
    };
  }

  function nearestNode(point) {
    const floor = currentFloor();
    const rect = elements.graphOverlay.getBoundingClientRect();
    const threshold =
      (30 * floor.mapRevision.imageWidth) / Math.max(rect.width, 1);
    let nearest = null;
    let distance = Number.POSITIVE_INFINITY;
    for (const node of state.workspace.graph.nodes) {
      if (node.floorId !== floor.id) {
        continue;
      }
      const candidate = Math.hypot(node.x - point.x, node.y - point.y);
      if (candidate < distance) {
        distance = candidate;
        nearest = node;
      }
    }
    return distance <= threshold ? nearest : null;
  }

  function nextCode(prefix, items) {
    const codes = new Set(items.map((item) => item.code));
    let index = 1;
    let code = `${prefix}-${String(index).padStart(2, "0")}`;
    while (codes.has(code)) {
      index += 1;
      code = `${prefix}-${String(index).padStart(2, "0")}`;
    }
    return code;
  }

  function nodeDependencies(nodeId) {
    const graph = state.workspace.graph;
    const dependencies = [];
    for (const edge of graph.edges) {
      if (edge.fromNodeId === nodeId || edge.toNodeId === nodeId) {
        dependencies.push({kind: "edge", id: edge.id, label: edge.code});
      }
    }
    for (const poi of graph.pois) {
      if (poi.nodeId === nodeId) {
        dependencies.push({kind: "poi", id: poi.id, label: poi.name});
      }
    }
    for (const stop of graph.connectorStops) {
      if (stop.nodeId === nodeId) {
        dependencies.push({kind: "stop", id: stop.id, label: stop.code});
      }
    }
    return dependencies;
  }

  function inspectorHeading(type, title, meta) {
    return (
      `<div class="inspector-heading"><div>` +
      `<span>${escapeHtml(type)}</span>` +
      `<strong>${escapeHtml(title)}</strong>` +
      `<span>${escapeHtml(meta || "")}</span>` +
      `</div></div>`
    );
  }

  function numberField(id, label, value, min, step) {
    return (
      `<label class="field"><span>${escapeHtml(label)}</span>` +
      `<input id="${id}" type="number" min="${min}" step="${step}" ` +
      `value="${escapeAttribute(value)}"${disabledAttribute()} /></label>`
    );
  }

  function checkField(id, label, checked) {
    return (
      `<label class="check-field">` +
      `<input id="${id}" type="checkbox"${checked ? " checked" : ""}` +
      disabledAttribute() +
      ` /><span>${escapeHtml(label)}</span></label>`
    );
  }

  function readOnlyRow(label, value) {
    return (
      `<div class="read-only-row"><span>${escapeHtml(label)}</span>` +
      `<strong>${escapeHtml(value)}</strong></div>`
    );
  }

  function dependencyHtml(dependencies) {
    if (!dependencies.length) {
      return "";
    }
    return (
      `<div class="inspector-group"><h3>引用关系</h3>` +
      `<div class="dependency-list">` +
      dependencies
        .map((dependency) => (
          `<div class="dependency-row"><span>${escapeHtml(dependency.label)}</span>` +
          `<button class="mini-button" type="button" data-select-kind="` +
          `${dependency.kind}" data-select-id="${escapeAttribute(dependency.id)}">` +
          `查看</button></div>`
        ))
        .join("") +
      `</div></div>`
    );
  }

  function dangerZone(label) {
    if (!isEditable()) {
      return "";
    }
    return (
      `<div class="danger-zone">` +
      `<button class="button danger full-width" id="deleteSelectedButton" type="button">` +
      `${escapeHtml(label)}</button></div>`
    );
  }

  function connectorStopsHtml(stops) {
    if (!stops.length) {
      return '<span class="read-only-value">尚未标注停靠点</span>';
    }
    return (
      `<div class="connector-list">` +
      stops
        .sort((a, b) => (
          (floorById(a.floorId) && floorById(a.floorId).levelNo) -
          (floorById(b.floorId) && floorById(b.floorId).levelNo)
        ))
        .map((stop) => (
          `<div class="connector-row"><div class="row-main">` +
          `<strong>${escapeHtml(floorCode(stop.floorId))}</strong>` +
          `<span>${escapeHtml(stop.code)}</span></div>` +
          `<button class="mini-button" type="button" data-select-kind="stop"` +
          ` data-select-id="${escapeAttribute(stop.id)}">查看</button></div>`
        ))
        .join("") +
      `</div>`
    );
  }

  function connectorLinksHtml(links) {
    if (!links.length) {
      return '<span class="read-only-value">尚未配置跨层连接</span>';
    }
    return (
      `<div class="connector-list">` +
      links
        .map((link) => {
          const from = findById(
            state.workspace.graph.connectorStops,
            link.fromStopId
          );
          const to = findById(
            state.workspace.graph.connectorStops,
            link.toStopId
          );
          const label = from && to
            ? `${floorCode(from.floorId)} ${link.direction === "both" ? "↔" : "→"} ` +
              `${floorCode(to.floorId)}`
            : link.code;
          return (
            `<div class="connector-row"><div class="row-main">` +
            `<strong>${escapeHtml(label)}</strong>` +
            `<span>${link.timeSeconds} 秒</span></div>` +
            `<button class="mini-button" type="button" data-select-kind="link"` +
            ` data-select-id="${escapeAttribute(link.id)}">查看</button></div>`
          );
        })
        .join("") +
      `</div>`
    );
  }

  function stopOption(stop) {
    return [stop.id, `${floorCode(stop.floorId)}：${stop.code}`];
  }

  function options(values, selected) {
    return values
      .map(([value, label]) => (
        `<option value="${escapeAttribute(value)}"` +
        `${String(value) === String(selected) ? " selected" : ""}>` +
        `${escapeHtml(label)}</option>`
      ))
      .join("");
  }

  function disabledAttribute() {
    return isEditable() ? "" : " disabled";
  }

  function bindText(id, callback) {
    const input = document.querySelector(`#${id}`);
    if (input && isEditable()) {
      input.addEventListener("input", () => callback(input.value));
    }
  }

  function bindSelect(id, callback) {
    const input = document.querySelector(`#${id}`);
    if (input && isEditable()) {
      input.addEventListener("change", () => callback(input.value));
    }
  }

  function bindNumber(id, callback) {
    const input = document.querySelector(`#${id}`);
    if (input && isEditable()) {
      input.addEventListener("change", () => {
        const value = Number(input.value);
        if (Number.isFinite(value)) {
          callback(value);
        }
      });
    }
  }

  function bindCheck(id, callback) {
    const input = document.querySelector(`#${id}`);
    if (input && isEditable()) {
      input.addEventListener("change", () => callback(input.checked));
    }
  }

  function bindDelete() {
    const button = document.querySelector("#deleteSelectedButton");
    if (button) {
      button.addEventListener("click", deleteSelected);
    }
    bindInspectorLinks();
  }

  function bindInspectorLinks() {
    for (const button of elements.inspectorContent.querySelectorAll(
      "[data-select-kind]"
    )) {
      button.addEventListener("click", () => {
        selectObject(button.dataset.selectKind, button.dataset.selectId);
      });
    }
  }

  function issueHtml(issue, warning) {
    const id = issue.elementId || "";
    return (
      `<button class="issue-button${warning ? " warning" : ""}" type="button"` +
      ` data-issue-id="${escapeAttribute(id)}"` +
      ` data-issue-type="${escapeAttribute(issue.elementType || "")}">` +
      `<strong>${escapeHtml(issue.code)}</strong>` +
      `<span>${escapeHtml(issue.message)}</span></button>`
    );
  }

  function issueKind(type) {
    return {
      path_node: "node",
      path_edge: "edge",
      poi: "poi",
      vertical_connector: "connector",
      connector_stop: "stop",
      vertical_link: "link"
    }[type];
  }

  function formatDate(value) {
    if (!value) {
      return "未发布";
    }
    return new Intl.DateTimeFormat("zh-CN", {
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit"
    }).format(new Date(value));
  }

  function svgElement(tag, attributes) {
    const element = document.createElementNS(SVG_NS, tag);
    for (const [name, value] of Object.entries(attributes)) {
      element.setAttribute(name, String(value));
    }
    return element;
  }

  function clearSvgLayer(layer) {
    while (layer.firstChild) {
      layer.firstChild.remove();
    }
  }

  function escapeHtml(value) {
    return String(value == null ? "" : value)
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;")
      .replaceAll('"', "&quot;")
      .replaceAll("'", "&#039;");
  }

  function escapeAttribute(value) {
    return escapeHtml(value);
  }

  function setupEvents() {
    elements.graphOverlay.addEventListener("click", handleMapClick);
    elements.graphOverlay.addEventListener("pointermove", handleNodeDrag);
    elements.graphOverlay.addEventListener("pointerup", endNodeDrag);
    elements.graphOverlay.addEventListener("pointercancel", endNodeDrag);
    elements.reloadButton.addEventListener("click", () => {
      if (
        state.dirty &&
        !window.confirm("当前有未保存修改，确认放弃并重新加载？")
      ) {
        return;
      }
      loadAll(state.workspace && state.workspace.release.id);
    });
    elements.releaseSelect.addEventListener("change", () => {
      const releaseId = elements.releaseSelect.value;
      if (
        state.dirty &&
        !window.confirm("当前有未保存修改，确认放弃并切换版本？")
      ) {
        elements.releaseSelect.value = state.workspace.release.id;
        return;
      }
      setBusy(true);
      showMapMessage("正在切换版本", "正在读取所选图数据。");
      loadWorkspace(releaseId)
        .then(() => setConnection("connected", "已连接"))
        .catch((error) => handleError(error, true))
        .finally(() => setBusy(false));
    });
    for (const button of elements.toolGrid.querySelectorAll("[data-tool]")) {
      button.addEventListener("click", () => {
        const tool = button.dataset.tool;
        if (!isEditable() && tool !== "select") {
          showToast("发布版本为只读，请先新建草稿。", true);
          return;
        }
        state.tool = tool;
        state.edgeStartId = null;
        renderTools();
        renderMap();
      });
    }
    elements.connectorSelect.addEventListener("change", () => {
      state.selectedConnectorId = elements.connectorSelect.value || null;
      renderConnectorControls();
      if (state.selectedConnectorId) {
        selectObject("connector", state.selectedConnectorId);
      }
    });
    elements.zoomRange.addEventListener("input", () => {
      state.zoom = Number(elements.zoomRange.value);
      elements.zoomOutput.textContent = `${state.zoom}%`;
      elements.mapStage.style.width = `${state.zoom}%`;
    });
    elements.saveButton.addEventListener("click", () => {
      saveGraph().catch(() => {});
    });
    elements.replaceMapButton.addEventListener("click", () => {
      if (isEditable() && !state.busy) {
        elements.mapFileInput.click();
      }
    });
    elements.mapFileInput.addEventListener("change", () => {
      const file = elements.mapFileInput.files[0];
      if (file) {
        replaceFloorMap(file);
      }
    });
    elements.validateButton.addEventListener("click", validateDraft);
    elements.publishButton.addEventListener("click", () => {
      if (state.validation && state.validation.passed && !state.dirty) {
        elements.publishReasonInput.value = "";
        elements.publishDialog.showModal();
        elements.publishReasonInput.focus();
      }
    });
    elements.rollbackButton.addEventListener("click", () => {
      if (isHistoricalPublished()) {
        elements.rollbackReasonInput.value = "";
        elements.rollbackDialog.showModal();
        elements.rollbackReasonInput.focus();
      }
    });
    elements.discardDraftButton.addEventListener("click", () => {
      if (isEditable() && !state.busy) {
        elements.discardDraftCode.textContent =
          state.workspace.release.code;
        elements.discardDraftDialog.showModal();
      }
    });
    elements.createDraftButton.addEventListener(
      "click",
      openCreateDraftDialog
    );
    elements.addConnectorButton.addEventListener(
      "click",
      openConnectorDialog
    );
    elements.addLinkButton.addEventListener("click", openLinkDialog);
    elements.createDraftForm.addEventListener("submit", createDraft);
    elements.connectorForm.addEventListener("submit", createConnector);
    elements.linkForm.addEventListener("submit", createLink);
    elements.publishForm.addEventListener("submit", (event) => {
      event.preventDefault();
      publishDraft(elements.publishReasonInput.value.trim());
    });
    elements.rollbackForm.addEventListener("submit", (event) => {
      event.preventDefault();
      rollbackRelease(elements.rollbackReasonInput.value.trim());
    });
    elements.discardDraftForm.addEventListener("submit", (event) => {
      event.preventDefault();
      discardDraft();
    });
    elements.closureForm.addEventListener(
      "submit",
      createOperationClosure
    );
    elements.closureTypeInput.addEventListener("change", () => {
      renderOperationTargets();
      elements.createClosureButton.disabled =
        state.busy || elements.closureTargetInput.options.length === 0;
    });
    for (const button of document.querySelectorAll("[data-close-dialog]")) {
      button.addEventListener("click", () => {
        const dialog = button.closest("dialog");
        if (dialog) {
          dialog.close();
        }
      });
    }
    elements.inspectorTab.addEventListener(
      "click",
      () => switchPanel("inspector")
    );
    elements.validationTab.addEventListener(
      "click",
      () => switchPanel("validation")
    );
    elements.operationsTab.addEventListener(
      "click",
      () => switchPanel("operations")
    );
    window.addEventListener("beforeunload", (event) => {
      if (state.dirty) {
        event.preventDefault();
      }
    });
  }

  setupEvents();
  elements.zoomOutput.textContent = `${state.zoom}%`;
  loadAll();
})();
