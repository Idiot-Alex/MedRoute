(() => {
  "use strict";

  const DEFAULT_API_BASE = "http://127.0.0.1:8080";
  const DEFAULT_BUILDING_ID = "00000000-0000-0000-0000-000000000100";
  const query = new URLSearchParams(window.location.search);
  const apiBase = (query.get("api") || DEFAULT_API_BASE).replace(/\/$/, "");
  const buildingId =
    query.get("buildingId") || query.get("building") || DEFAULT_BUILDING_ID;
  const requestedStartPoi =
    query.get("startPoi") || query.get("startPoiCode") || "";
  const requestedEndPoi =
    query.get("endPoi") || query.get("endPoiCode") || "";
  const svgNamespace = "http://www.w3.org/2000/svg";
  const categoryLabels = {
    department: "科室",
    diagnostic: "医技",
    elevator: "电梯",
    entrance: "出入口",
    facility: "设施",
    pharmacy: "药房",
    service: "服务",
    stairs: "楼梯",
    toilet: "卫生间",
    window: "窗口",
  };

  const state = {
    context: null,
    route: null,
    selectedFloorId: null,
    routeMode: "normal",
    loadingRoute: false,
    poiSearchEndpoint: "start",
    poiSearchFloorId: "",
    poiSearchReturnFocus: null,
    guideStepIndex: 0,
  };

  const elements = {
    app: document.querySelector(".app-shell"),
    buildingName: document.querySelector("#building-name"),
    connectionStatus: document.querySelector("#connection-status"),
    routeForm: document.querySelector("#route-form"),
    startPoi: document.querySelector("#start-poi"),
    endPoi: document.querySelector("#end-poi"),
    startPoiTrigger: document.querySelector("#start-poi-trigger"),
    endPoiTrigger: document.querySelector("#end-poi-trigger"),
    startPoiValue: document.querySelector("#start-poi-value"),
    endPoiValue: document.querySelector("#end-poi-value"),
    swapRoute: document.querySelector("#swap-route"),
    calculateRoute: document.querySelector("#calculate-route"),
    formMessage: document.querySelector("#form-message"),
    modeButtons: [...document.querySelectorAll("[data-route-mode]")],
    floorCaption: document.querySelector("#floor-caption"),
    floorTabs: document.querySelector("#floor-tabs"),
    mapPanel: document.querySelector(".map-panel"),
    mapFrame: document.querySelector("#map-frame"),
    mapCanvas: document.querySelector("#map-canvas"),
    floorImage: document.querySelector("#floor-image"),
    routeOverlay: document.querySelector("#route-overlay"),
    mapEmptyState: document.querySelector("#map-empty-state"),
    floorTransition: document.querySelector("#floor-transition"),
    expandMap: document.querySelector("#expand-map"),
    expandMapIcon: document.querySelector("#expand-map-icon"),
    routeBrief: document.querySelector("#route-brief"),
    routeBriefTitle: document.querySelector("#route-brief-title"),
    routeBriefMeta: document.querySelector("#route-brief-meta"),
    showRouteSteps: document.querySelector("#show-route-steps"),
    routeModeLabel: document.querySelector("#route-mode-label"),
    routeSummary: document.querySelector("#route-summary"),
    routeSteps: document.querySelector("#route-steps"),
    poiSearchDialog: document.querySelector("#poi-search-dialog"),
    poiSearchEndpoint: document.querySelector("#poi-search-endpoint"),
    poiSearchInput: document.querySelector("#poi-search-input"),
    poiFloorFilters: document.querySelector("#poi-floor-filters"),
    poiSearchCount: document.querySelector("#poi-search-count"),
    poiSearchResults: document.querySelector("#poi-search-results"),
    poiSearchEmpty: document.querySelector("#poi-search-empty"),
    closePoiSearch: document.querySelector("#close-poi-search"),
    routeGuideDialog: document.querySelector("#route-guide-dialog"),
    routeGuideProgress: document.querySelector("#route-guide-progress"),
    routeGuideFloor: document.querySelector("#route-guide-floor"),
    routeGuideInstruction: document.querySelector(
      "#route-guide-instruction"
    ),
    closeRouteGuide: document.querySelector("#close-route-guide"),
    showStepMap: document.querySelector("#show-step-map"),
    previousRouteStep: document.querySelector("#previous-route-step"),
    nextRouteStep: document.querySelector("#next-route-step"),
  };

  function requestUrl(path) {
    return `${apiBase}${path}`;
  }

  async function fetchJson(path, options) {
    const requestOptions = { ...(options || {}) };
    if (!requestOptions.method || requestOptions.method.toUpperCase() === "GET") {
      requestOptions.cache = "no-store";
    }
    const response = await fetch(requestUrl(path), requestOptions);
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
    elements.startPoiTrigger.disabled = disabled;
    elements.endPoiTrigger.disabled = disabled;
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

  function updatePoiTriggerLabels() {
    const startPoi = state.context && getPoi(elements.startPoi.value);
    const endPoi = state.context && getPoi(elements.endPoi.value);
    elements.startPoiValue.textContent = startPoi
      ? `${startPoi.floorCode} · ${startPoi.name}`
      : "选择起点";
    elements.endPoiValue.textContent = endPoi
      ? `${endPoi.floorCode} · ${endPoi.name}`
      : "选择终点";
  }

  function populatePoiSelects() {
    const orderedPois = state.context.pois;
    const findPoi = (reference) =>
      orderedPois.find(
        (poi) =>
          poi.id === reference ||
          String(poi.code || "").toLowerCase() === reference.toLowerCase()
      );
    const defaults = {
      start:
        findPoi(requestedStartPoi) ||
        orderedPois.find((poi) => poi.code === "P-ENTRANCE") ||
        orderedPois[0],
      end:
        findPoi(requestedEndPoi) ||
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
    updatePoiTriggerLabels();
  }

  function currentFloor() {
    return state.context.floors.find(
      (floor) => floor.id === state.selectedFloorId
    );
  }

  function getPoi(id) {
    return state.context.pois.find((poi) => poi.id === id);
  }

  function normalizeSearchText(value) {
    return String(value || "")
      .normalize("NFKC")
      .toLocaleLowerCase("zh-CN");
  }

  function categoryLabel(category) {
    return categoryLabels[category] || category || "地点";
  }

  function poiMatchesSearch(poi, terms) {
    if (terms.length === 0) {
      return true;
    }
    const corpus = normalizeSearchText(
      [
        poi.name,
        poi.code,
        poi.floorCode,
        categoryLabel(poi.category),
        poi.category,
        ...(poi.searchKeywords || []),
      ].join(" ")
    );
    return terms.every((term) => corpus.includes(term));
  }

  function createPoiFloorFilter(label, floorId) {
    const selected = state.poiSearchFloorId === floorId;
    const button = document.createElement("button");
    button.type = "button";
    button.className = "poi-floor-filter";
    button.textContent = label;
    button.setAttribute("role", "tab");
    button.setAttribute("aria-selected", String(selected));
    button.addEventListener("click", () => {
      state.poiSearchFloorId = floorId;
      renderPoiSearch();
    });
    return button;
  }

  function renderPoiFloorFilters() {
    elements.poiFloorFilters.replaceChildren(
      createPoiFloorFilter("全部", "")
    );
    state.context.floors.forEach((floor) => {
      elements.poiFloorFilters.append(
        createPoiFloorFilter(floor.code, floor.id)
      );
    });
  }

  function createPoiSearchResult(poi, selectedPoiId) {
    const item = document.createElement("li");
    item.className = "poi-search-result";
    item.classList.toggle("is-selected", poi.id === selectedPoiId);

    const button = document.createElement("button");
    button.type = "button";
    button.dataset.poiId = poi.id;
    button.setAttribute("aria-label", `${poi.name}，${poi.floorCode}`);
    if (poi.id === selectedPoiId) {
      button.setAttribute("aria-current", "true");
    }

    const copy = document.createElement("span");
    copy.className = "poi-result-copy";
    const name = document.createElement("span");
    name.className = "poi-result-name";
    name.textContent = poi.name;
    const detail = document.createElement("span");
    detail.className = "poi-result-detail";
    const keywords = poi.searchKeywords || [];
    const chineseKeywords = keywords.filter((keyword) =>
      /[\u3400-\u9fff]/.test(keyword)
    );
    const visibleKeywords = (
      chineseKeywords.length > 0 ? chineseKeywords : keywords
    ).slice(0, 3);
    detail.textContent = [
      categoryLabel(poi.category),
      ...visibleKeywords,
    ].join(" · ");
    copy.append(name, detail);

    const floor = document.createElement("span");
    floor.className = "poi-result-floor";
    floor.textContent =
      poi.id === selectedPoiId ? `${poi.floorCode} ✓` : poi.floorCode;
    button.append(copy, floor);
    button.addEventListener("click", () => {
      selectPoiForEndpoint(poi.id);
    });
    item.append(button);
    return item;
  }

  function renderPoiSearch() {
    if (!state.context) {
      return;
    }
    renderPoiFloorFilters();
    const terms = normalizeSearchText(elements.poiSearchInput.value)
      .split(/\s+/)
      .filter(Boolean);
    const results = state.context.pois.filter(
      (poi) =>
        (!state.poiSearchFloorId || poi.floorId === state.poiSearchFloorId) &&
        poiMatchesSearch(poi, terms)
    );
    const selectedPoiId =
      state.poiSearchEndpoint === "start"
        ? elements.startPoi.value
        : elements.endPoi.value;
    elements.poiSearchResults.replaceChildren(
      ...results.map((poi) => createPoiSearchResult(poi, selectedPoiId))
    );
    elements.poiSearchCount.textContent = `${results.length} 个地点`;
    elements.poiSearchEmpty.hidden = results.length > 0;
    elements.poiSearchResults.hidden = results.length === 0;
  }

  function openPoiSearch(endpoint) {
    if (!state.context || elements.poiSearchDialog.open) {
      return;
    }
    state.poiSearchEndpoint = endpoint;
    state.poiSearchFloorId = "";
    state.poiSearchReturnFocus =
      endpoint === "start" ? elements.startPoiTrigger : elements.endPoiTrigger;
    elements.poiSearchEndpoint.textContent =
      endpoint === "start" ? "选择起点" : "选择终点";
    elements.poiSearchInput.value = "";
    renderPoiSearch();
    elements.poiSearchDialog.showModal();
    window.requestAnimationFrame(() => elements.poiSearchInput.focus());
  }

  function closePoiSearch() {
    if (!elements.poiSearchDialog.open) {
      return;
    }
    elements.poiSearchDialog.close();
    if (state.poiSearchReturnFocus) {
      state.poiSearchReturnFocus.focus();
    }
  }

  function syncPoiQuery() {
    const startPoi = getPoi(elements.startPoi.value);
    const endPoi = getPoi(elements.endPoi.value);
    const nextQuery = new URLSearchParams(window.location.search);
    nextQuery.delete("startPoiCode");
    nextQuery.delete("endPoiCode");
    if (startPoi) {
      nextQuery.set("startPoi", startPoi.code);
    }
    if (endPoi) {
      nextQuery.set("endPoi", endPoi.code);
    }
    const nextUrl = `${window.location.pathname}?${nextQuery.toString()}${window.location.hash}`;
    window.history.replaceState(null, "", nextUrl);
  }

  function selectPoiForEndpoint(poiId) {
    const select =
      state.poiSearchEndpoint === "start" ? elements.startPoi : elements.endPoi;
    select.value = poiId;
    updatePoiTriggerLabels();
    syncPoiQuery();
    clearRoute();
    closePoiSearch();
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
      return `${apiBase}${imageUrl}`;
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
    const imageUrl = resolveImageUrl(floor.imageUrl);
    if (elements.floorImage.dataset.imageUrl !== imageUrl) {
      elements.mapFrame.classList.add("is-loading");
      elements.floorImage.dataset.imageUrl = imageUrl;
      elements.floorImage.dataset.loadState = "loading";
      elements.floorImage.onload = () => {
        if (elements.floorImage.dataset.imageUrl !== imageUrl) {
          return;
        }
        elements.floorImage.dataset.loadState = "ready";
        elements.mapFrame.classList.remove("is-loading");
      };
      elements.floorImage.onerror = () => {
        if (elements.floorImage.dataset.imageUrl !== imageUrl) {
          return;
        }
        elements.floorImage.dataset.loadState = "error";
        elements.mapFrame.classList.remove("is-loading");
        elements.mapEmptyState.textContent = "该楼层底图加载失败。";
        elements.mapEmptyState.hidden = false;
      };
      elements.floorImage.src = imageUrl;
    } else if (
      elements.floorImage.complete &&
      elements.floorImage.naturalWidth > 0
    ) {
      elements.mapFrame.classList.remove("is-loading");
    }
    elements.floorImage.alt = `${floor.code} ${floor.name} 楼层分布图`;
    elements.routeOverlay.replaceChildren();
    elements.routeOverlay.setAttribute("viewBox", `0 0 ${width} ${height}`);
    renderFloorTabs();
    renderFloorTransition(floor.id);

    if (!state.route) {
      if (elements.floorImage.dataset.loadState !== "error") {
        elements.mapEmptyState.hidden = true;
      }
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
      elements.app.classList.remove("has-route");
      elements.routeBrief.hidden = true;
      elements.routeModeLabel.textContent = "尚未计算";
      elements.routeSummary.classList.add("is-empty");
      elements.routeSummary.replaceChildren();
      const message = document.createElement("p");
      message.textContent = "选择起点和终点后，路线会由后端服务计算。";
      elements.routeSummary.append(message);
      elements.routeSteps.replaceChildren();
      return;
    }

    elements.app.classList.add("has-route");
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
    renderRouteBrief();

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

  function renderRouteBrief() {
    const summary = state.route.summary;
    const floorCodes = [
      ...new Set(
        state.route.segments.map((segment) => {
          const floor = state.context.floors.find(
            (candidate) => candidate.id === segment.floorId
          );
          return floor ? floor.code : "跨层";
        })
      ),
    ];
    const floorText =
      floorCodes.length > 1
        ? `${floorCodes[0]} → ${floorCodes[floorCodes.length - 1]}`
        : `${floorCodes[0] || "当前楼层"} 同层`;
    const firstTransition = state.route.transitions[0];
    elements.routeBriefTitle.textContent =
      `${formatDuration(summary.estimatedSeconds)} · ` +
      `${formatDistance(summary.distanceMeters)} 米`;
    elements.routeBriefMeta.textContent = firstTransition
      ? `${floorText} · ${firstTransition.instruction}`
      : `${floorText}路线`;
    elements.routeBrief.hidden = false;
  }

  function renderRouteGuide() {
    if (!state.route || state.route.steps.length === 0) {
      return;
    }
    const lastIndex = state.route.steps.length - 1;
    state.guideStepIndex = Math.min(
      Math.max(state.guideStepIndex, 0),
      lastIndex
    );
    const step = state.route.steps[state.guideStepIndex];
    const floor = state.context.floors.find(
      (candidate) => candidate.id === step.floorId
    );
    const floorName = floor
      ? floor.name === floor.code
        ? floor.code
        : `${floor.code} ${floor.name}`
      : "跨层步骤";

    elements.routeGuideProgress.textContent =
      `步骤 ${state.guideStepIndex + 1} / ${state.route.steps.length}`;
    elements.routeGuideFloor.textContent = floorName;
    elements.routeGuideInstruction.textContent = step.instruction;
    elements.previousRouteStep.disabled = state.guideStepIndex === 0;
    elements.nextRouteStep.textContent =
      state.guideStepIndex === lastIndex ? "完成" : "下一步 →";

    if (floor && state.selectedFloorId !== floor.id) {
      state.selectedFloorId = floor.id;
      renderMap();
    }
  }

  function openRouteGuide() {
    if (
      !state.route ||
      state.route.steps.length === 0 ||
      elements.routeGuideDialog.open
    ) {
      return;
    }
    setMapExpanded(false);
    renderRouteGuide();
    elements.routeGuideDialog.showModal();
    window.requestAnimationFrame(() => elements.nextRouteStep.focus());
  }

  function closeRouteGuide() {
    if (!elements.routeGuideDialog.open) {
      return;
    }
    elements.routeGuideDialog.close();
    elements.showRouteSteps.focus();
  }

  function moveRouteGuide(direction) {
    if (!state.route) {
      return;
    }
    const nextIndex = state.guideStepIndex + direction;
    if (nextIndex >= state.route.steps.length) {
      closeRouteGuide();
      return;
    }
    if (nextIndex < 0) {
      return;
    }
    state.guideStepIndex = nextIndex;
    renderRouteGuide();
  }

  function showCurrentStepMap() {
    closeRouteGuide();
    elements.expandMap.focus({ preventScroll: true });
    scrollMapIntoView();
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

  function clearRoute() {
    if (elements.routeGuideDialog.open) {
      elements.routeGuideDialog.close();
    }
    state.route = null;
    state.guideStepIndex = 0;
    elements.formMessage.textContent = "";
    if (!state.context) {
      return;
    }
    renderRouteSummary();
    renderMap();
  }

  function setMapExpanded(expanded) {
    elements.mapPanel.classList.toggle("is-expanded", expanded);
    document.body.classList.toggle("map-expanded", expanded);
    elements.expandMap.setAttribute(
      "aria-label",
      expanded ? "退出全屏地图" : "全屏查看地图"
    );
    elements.expandMap.title = expanded ? "退出全屏地图" : "全屏查看地图";
    elements.expandMapIcon.textContent = expanded ? "×" : "⛶";
    if (expanded) {
      window.requestAnimationFrame(() => {
        elements.mapFrame.scrollLeft =
          (elements.mapCanvas.scrollWidth - elements.mapFrame.clientWidth) / 2;
        elements.mapFrame.scrollTop =
          (elements.mapCanvas.scrollHeight - elements.mapFrame.clientHeight) / 2;
      });
    }
  }

  function scrollMapIntoView() {
    if (!window.matchMedia("(max-width: 760px)").matches) {
      return;
    }
    const reducedMotion = window.matchMedia(
      "(prefers-reduced-motion: reduce)"
    ).matches;
    window.requestAnimationFrame(() => {
      elements.mapPanel.scrollIntoView({
        behavior: reducedMotion ? "auto" : "smooth",
        block: "start",
      });
    });
  }

  async function calculateRoute() {
    if (state.loadingRoute) {
      return;
    }
    if (elements.startPoi.value === elements.endPoi.value) {
      elements.formMessage.textContent = "起点和终点需要不同。";
      return;
    }

    clearRoute();
    state.loadingRoute = true;
    elements.formMessage.textContent = "正在向后端计算路线。";
    elements.routeForm.setAttribute("aria-busy", "true");
    elements.calculateRoute.textContent = "正在规划";
    setControlsDisabled(true);
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
      state.guideStepIndex = 0;
      state.selectedFloorId = chooseFloorForRoute(route);
      elements.formMessage.textContent = "";
      renderRouteSummary();
      renderMap();
      scrollMapIntoView();
    } catch (error) {
      elements.formMessage.textContent = error.message || "路线计算失败。";
    } finally {
      state.loadingRoute = false;
      elements.routeForm.removeAttribute("aria-busy");
      elements.calculateRoute.textContent = "规划路线";
      setControlsDisabled(false);
    }
  }

  async function loadContext() {
    setControlsDisabled(true);
    elements.connectionStatus.textContent = "正在读取已发布导航数据";
    elements.connectionStatus.dataset.state = "loading";
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
        buildingName: publishedContext.building.name,
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
      elements.buildingName.textContent = context.buildingName || "医院室内导航";
      populatePoiSelects();
      renderRouteSummary();
      renderMap();
      setControlsDisabled(false);
      elements.connectionStatus.textContent =
        `${context.floors.length} 层 · ${context.pois.length} 个地点`;
      elements.connectionStatus.dataset.state = "ready";
      elements.app.dataset.pageState = "ready";
    } catch (error) {
      elements.connectionStatus.textContent = "无法连接导航服务";
      elements.connectionStatus.dataset.state = "error";
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
    updatePoiTriggerLabels();
    syncPoiQuery();
    clearRoute();
  });

  [elements.startPoi, elements.endPoi].forEach((select) => {
    select.addEventListener("change", () => {
      updatePoiTriggerLabels();
      syncPoiQuery();
      clearRoute();
    });
  });

  elements.startPoiTrigger.addEventListener("click", () => {
    openPoiSearch("start");
  });

  elements.endPoiTrigger.addEventListener("click", () => {
    openPoiSearch("end");
  });

  elements.poiSearchInput.addEventListener("input", renderPoiSearch);
  elements.closePoiSearch.addEventListener("click", closePoiSearch);
  elements.poiSearchDialog.addEventListener("cancel", (event) => {
    event.preventDefault();
    closePoiSearch();
  });
  elements.poiSearchDialog.addEventListener("click", (event) => {
    if (event.target === elements.poiSearchDialog) {
      closePoiSearch();
    }
  });

  elements.modeButtons.forEach((button) => {
    button.addEventListener("click", () => {
      if (button.dataset.routeMode === state.routeMode) {
        return;
      }
      setRouteMode(button.dataset.routeMode);
      clearRoute();
    });
  });

  elements.expandMap.addEventListener("click", () => {
    setMapExpanded(!elements.mapPanel.classList.contains("is-expanded"));
  });

  elements.showRouteSteps.addEventListener("click", () => {
    openRouteGuide();
  });

  elements.closeRouteGuide.addEventListener("click", closeRouteGuide);
  elements.previousRouteStep.addEventListener("click", () => {
    moveRouteGuide(-1);
  });
  elements.nextRouteStep.addEventListener("click", () => {
    moveRouteGuide(1);
  });
  elements.showStepMap.addEventListener("click", showCurrentStepMap);
  elements.routeGuideDialog.addEventListener("cancel", (event) => {
    event.preventDefault();
    closeRouteGuide();
  });
  elements.routeGuideDialog.addEventListener("click", (event) => {
    if (event.target === elements.routeGuideDialog) {
      closeRouteGuide();
    }
  });

  document.addEventListener("keydown", (event) => {
    if (
      event.key === "Escape" &&
      elements.mapPanel.classList.contains("is-expanded")
    ) {
      setMapExpanded(false);
      elements.expandMap.focus();
    }
  });

  void loadContext();
})();
