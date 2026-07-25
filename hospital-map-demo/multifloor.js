(() => {
  "use strict";

  const DEFAULT_API_BASE = "http://127.0.0.1:8080";
  const DEFAULT_BUILDING_ID = "00000000-0000-0000-0000-000000000100";
  const query = new URLSearchParams(window.location.search);
  const apiBase = (query.get("api") || DEFAULT_API_BASE).replace(/\/$/, "");
  const buildingId = query.get("buildingId") || DEFAULT_BUILDING_ID;
  const svgNamespace = "http://www.w3.org/2000/svg";

  const state = {
    context: null,
    route: null,
    selectedFloorId: null,
    routeMode: "normal",
    loadingRoute: false,
  };

  const elements = {
    app: document.querySelector(".app-shell"),
    connectionStatus: document.querySelector("#connection-status"),
    routeForm: document.querySelector("#route-form"),
    startPoi: document.querySelector("#start-poi"),
    endPoi: document.querySelector("#end-poi"),
    swapRoute: document.querySelector("#swap-route"),
    calculateRoute: document.querySelector("#calculate-route"),
    formMessage: document.querySelector("#form-message"),
    modeButtons: [...document.querySelectorAll("[data-route-mode]")],
    floorCaption: document.querySelector("#floor-caption"),
    floorTabs: document.querySelector("#floor-tabs"),
    mapFrame: document.querySelector("#map-frame"),
    floorImage: document.querySelector("#floor-image"),
    routeOverlay: document.querySelector("#route-overlay"),
    mapEmptyState: document.querySelector("#map-empty-state"),
    floorTransition: document.querySelector("#floor-transition"),
    routeModeLabel: document.querySelector("#route-mode-label"),
    routeSummary: document.querySelector("#route-summary"),
    routeSteps: document.querySelector("#route-steps"),
  };

  function requestUrl(path) {
    return `${apiBase}${path}`;
  }

  async function fetchJson(path, options) {
    const response = await fetch(requestUrl(path), options);
    const body = await response.json().catch(() => null);
    if (!response.ok) {
      const detail = body && body.error && body.error.message;
      throw new Error(detail || `请求失败 (${response.status})`);
    }
    return body;
  }

  function setControlsDisabled(disabled) {
    elements.startPoi.disabled = disabled;
    elements.endPoi.disabled = disabled;
    elements.swapRoute.disabled = disabled;
    elements.calculateRoute.disabled = disabled;
    const supportedModes = new Set(
      (state.context && state.context.supportedRouteModes) || []
    );
    elements.modeButtons.forEach((button) => {
      button.disabled =
        disabled ||
        (supportedModes.size > 0 && !supportedModes.has(button.dataset.routeMode));
    });
  }

  function displayFloorName(poi) {
    return `${poi.floorCode} ${poi.name}`;
  }

  function populatePoiSelects() {
    const orderedPois = state.context.pois;
    const defaults = {
      start: orderedPois.find((poi) => poi.code === "P-ENTRANCE") || orderedPois[0],
      end:
        orderedPois.find((poi) => poi.code === "P-ULTRASOUND-3F") ||
        orderedPois[orderedPois.length - 1],
    };

    [elements.startPoi, elements.endPoi].forEach((select) => {
      select.replaceChildren();
      orderedPois.forEach((poi) => {
        const option = document.createElement("option");
        option.value = poi.id;
        option.textContent = displayFloorName(poi);
        select.append(option);
      });
    });

    elements.startPoi.value = defaults.start.id;
    elements.endPoi.value = defaults.end.id;
  }

  function currentFloor() {
    return state.context.floors.find(
      (floor) => floor.id === state.selectedFloorId
    );
  }

  function getPoi(id) {
    return state.context.pois.find((poi) => poi.id === id);
  }

  function routeModeLabel() {
    return state.routeMode === "accessible" ? "无障碍路线" : "普通路线";
  }

  function setRouteMode(mode) {
    state.routeMode = mode;
    elements.modeButtons.forEach((button) => {
      const selected = button.dataset.routeMode === mode;
      button.classList.toggle("is-selected", selected);
      button.setAttribute("aria-pressed", String(selected));
    });
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

  function createSvgElement(name, attributes) {
    const element = document.createElementNS(svgNamespace, name);
    Object.entries(attributes).forEach(([key, value]) => {
      element.setAttribute(key, String(value));
    });
    return element;
  }

  function pointString(points) {
    return points.map((point) => `${point.x},${point.y}`).join(" ");
  }

  function addEndpointMarker(point, label) {
    const circle = createSvgElement("circle", {
      class: "route-point",
      cx: point.x,
      cy: point.y,
      r: 17,
    });
    const text = createSvgElement("text", {
      class: "route-marker-text",
      x: point.x,
      y: point.y + 1,
    });
    text.textContent = label;
    elements.routeOverlay.append(circle, text);
  }

  function routeSegmentForFloor(floorId) {
    if (!state.route) {
      return null;
    }
    return state.route.segments.find((segment) => segment.floorId === floorId);
  }

  function renderFloorTabs() {
    elements.floorTabs.replaceChildren();
    state.context.floors.forEach((floor) => {
      const selected = floor.id === state.selectedFloorId;
      const button = document.createElement("button");
      button.type = "button";
      button.className = "floor-tab";
      button.classList.toggle("is-selected", selected);
      button.textContent = floor.code;
      button.setAttribute("role", "tab");
      button.setAttribute("aria-selected", String(selected));
      button.addEventListener("click", () => {
        state.selectedFloorId = floor.id;
        renderMap();
      });
      elements.floorTabs.append(button);
    });
  }

  function renderFloorTransition(floorId) {
    if (!state.route) {
      elements.floorTransition.textContent = "";
      return;
    }
    const actions = state.route.transitions
      .filter(
        (transition) =>
          transition.fromFloorId === floorId || transition.toFloorId === floorId
      )
      .map((transition) => transition.instruction);
    elements.floorTransition.textContent = actions.join(" ");
  }

  function renderMap() {
    const floor = currentFloor();
    if (!floor) {
      return;
    }
    const segment = routeSegmentForFloor(floor.id);
    const width = floor.imageWidth || 1000;
    const height = floor.imageHeight || 800;

    elements.floorCaption.textContent =
      floor.name === floor.code ? floor.code : `${floor.code} ${floor.name}`;
    elements.floorImage.src = resolveImageUrl(floor.imageUrl);
    elements.floorImage.alt = `${floor.code} 测试楼层分布图`;
    elements.routeOverlay.replaceChildren();
    elements.routeOverlay.setAttribute("viewBox", `0 0 ${width} ${height}`);
    elements.mapFrame.classList.remove("is-loading");
    renderFloorTabs();
    renderFloorTransition(floor.id);

    if (!state.route) {
      elements.mapEmptyState.hidden = true;
      return;
    }

    if (!segment || segment.points.length === 0) {
      elements.mapEmptyState.textContent = "本路线未经过该楼层。";
      elements.mapEmptyState.hidden = false;
      return;
    }

    elements.mapEmptyState.hidden = true;
    if (segment.points.length > 1) {
      const points = pointString(segment.points);
      elements.routeOverlay.append(
        createSvgElement("polyline", {
          class: "route-underlay",
          points,
        }),
        createSvgElement("polyline", {
          class: "route-line",
          points,
        })
      );
    }

    const isFirstSegment = segment.sequence === 1;
    const isLastSegment =
      segment.sequence === state.route.segments[state.route.segments.length - 1].sequence;
    if (isFirstSegment) {
      addEndpointMarker(segment.points[0], "起");
    }
    if (isLastSegment) {
      addEndpointMarker(segment.points[segment.points.length - 1], "终");
    }
    if (!isFirstSegment && !isLastSegment && segment.points.length === 1) {
      addEndpointMarker(segment.points[0], "转");
    }
  }

  function renderRouteSummary() {
    if (!state.route) {
      elements.routeModeLabel.textContent = "尚未计算";
      elements.routeSummary.classList.add("is-empty");
      elements.routeSummary.replaceChildren();
      const message = document.createElement("p");
      message.textContent = "选择起点和终点后，路线会由后端服务计算。";
      elements.routeSummary.append(message);
      elements.routeSteps.replaceChildren();
      return;
    }

    const summary = state.route.summary;
    const startName = state.route.startPoi.name;
    const endName = state.route.endPoi.name;
    elements.routeModeLabel.textContent = routeModeLabel();
    elements.routeSummary.classList.remove("is-empty");
    elements.routeSummary.replaceChildren();

    const summaryGrid = document.createElement("div");
    summaryGrid.className = "summary-grid";
    summaryGrid.append(
      summaryItem("预计距离", `${formatDistance(summary.distanceMeters)} 米`),
      summaryItem("预计时间", formatDuration(summary.estimatedSeconds))
    );
    const routeText = document.createElement("p");
    routeText.className = "summary-route";
    routeText.textContent = `${startName} 至 ${endName}`;
    elements.routeSummary.append(summaryGrid, routeText);

    elements.routeSteps.replaceChildren();
    state.route.steps.forEach((step) => {
      const item = document.createElement("li");
      item.className = "route-step";
      const index = document.createElement("span");
      index.className = "step-index";
      index.textContent = String(step.sequence);
      const content = document.createElement("div");
      const floorCode = state.context.floors.find(
        (floor) => floor.id === step.floorId
      );
      const floor = document.createElement("p");
      floor.className = "step-floor";
      floor.textContent = floorCode ? floorCode.code : "跨层";
      const instruction = document.createElement("p");
      instruction.textContent = step.instruction;
      content.append(floor, instruction);
      item.append(index, content);
      elements.routeSteps.append(item);
    });
  }

  function summaryItem(label, value) {
    const item = document.createElement("div");
    item.className = "summary-item";
    const labelElement = document.createElement("span");
    labelElement.textContent = label;
    const valueElement = document.createElement("strong");
    valueElement.textContent = value;
    item.append(labelElement, valueElement);
    return item;
  }

  function formatDistance(distance) {
    const numeric = Number(distance);
    return Number.isInteger(numeric) ? String(numeric) : numeric.toFixed(1);
  }

  function formatDuration(seconds) {
    const minutes = Math.floor(seconds / 60);
    const remainder = seconds % 60;
    if (minutes === 0) {
      return `${remainder} 秒`;
    }
    return remainder === 0 ? `${minutes} 分钟` : `${minutes} 分 ${remainder} 秒`;
  }

  function chooseFloorForRoute(route) {
    const startSegment = route.segments[0];
    return startSegment ? startSegment.floorId : state.context.floors[0].id;
  }

  async function calculateRoute() {
    if (state.loadingRoute) {
      return;
    }
    if (elements.startPoi.value === elements.endPoi.value) {
      elements.formMessage.textContent = "起点和终点需要不同。";
      return;
    }

    state.loadingRoute = true;
    elements.formMessage.textContent = "正在向后端计算路线。";
    elements.calculateRoute.disabled = true;
    try {
      const route = await fetchJson("/api/routes", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          buildingId: state.context.buildingId,
          expectedReleaseId: state.context.releaseId,
          startPoiId: elements.startPoi.value,
          endPoiId: elements.endPoi.value,
          routeMode: state.routeMode,
        }),
      });
      state.route = route;
      state.selectedFloorId = chooseFloorForRoute(route);
      elements.formMessage.textContent = "";
      renderRouteSummary();
      renderMap();
    } catch (error) {
      elements.formMessage.textContent = error.message || "路线计算失败。";
    } finally {
      state.loadingRoute = false;
      elements.calculateRoute.disabled = false;
    }
  }

  async function loadContext() {
    setControlsDisabled(true);
    elements.connectionStatus.textContent = "正在读取已发布导航数据";
    try {
      const publishedContext = await fetchJson(
        `/api/buildings/${encodeURIComponent(buildingId)}/navigation-context`
      );
      const poiResponse = await fetchJson(
        `/api/buildings/${encodeURIComponent(buildingId)}/pois`
      );
      if (poiResponse.releaseId !== publishedContext.release.id) {
        throw new Error("楼层底图与地点数据不属于同一发布版本。请刷新后重试。");
      }
      const context = {
        buildingId: publishedContext.building.id,
        releaseId: publishedContext.release.id,
        supportedRouteModes: publishedContext.supportedRouteModes,
        floors: publishedContext.floors.map((floor) => ({
          id: floor.id,
          code: floor.code,
          name: floor.name,
          imageUrl: floor.mapRevision.imageUrl,
          imageWidth: floor.mapRevision.imageWidth,
          imageHeight: floor.mapRevision.imageHeight,
        })),
        pois: poiResponse.items,
      };
      if (!context.floors || context.floors.length === 0 || !context.pois || context.pois.length < 2) {
        throw new Error("当前发布版本缺少可导航的楼层或地点。");
      }
      state.context = context;
      state.selectedFloorId = context.floors[0].id;
      populatePoiSelects();
      renderRouteSummary();
      renderMap();
      setControlsDisabled(false);
      elements.connectionStatus.textContent = `已加载 ${context.floors.length} 层和 ${context.pois.length} 个测试地点`;
      elements.app.dataset.pageState = "ready";
    } catch (error) {
      elements.connectionStatus.textContent = "无法连接导航服务";
      elements.formMessage.textContent = `${error.message || "读取导航数据失败。"} 请确认后端已重启并监听 ${apiBase}。`;
      elements.mapEmptyState.textContent = "后端服务不可用，暂时无法加载测试底图。";
      elements.mapEmptyState.hidden = false;
      elements.mapFrame.classList.remove("is-loading");
      elements.app.dataset.pageState = "error";
    }
  }

  elements.routeForm.addEventListener("submit", (event) => {
    event.preventDefault();
    void calculateRoute();
  });

  elements.swapRoute.addEventListener("click", () => {
    const previousStart = elements.startPoi.value;
    elements.startPoi.value = elements.endPoi.value;
    elements.endPoi.value = previousStart;
  });

  elements.modeButtons.forEach((button) => {
    button.addEventListener("click", () => {
      setRouteMode(button.dataset.routeMode);
    });
  });

  void loadContext();
})();
