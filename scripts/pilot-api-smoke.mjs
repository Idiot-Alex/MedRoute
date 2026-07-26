#!/usr/bin/env node

const args = new Set(process.argv.slice(2));
const writeMode = args.has("--write");
const publicOnly = args.has("--public-only");
const apiBase = (
  process.env.MEDROUTE_API_BASE ?? "http://127.0.0.1:8080"
).replace(/\/$/, "");
const buildingId =
  process.env.MEDROUTE_BUILDING_ID ??
  "00000000-0000-0000-0000-000000000100";
const expectedPoiCount = Number(
  process.env.MEDROUTE_EXPECTED_POI_COUNT ?? "1",
);
const requestedStartCode = process.env.MEDROUTE_START_POI_CODE ?? "";
const navigationBaseUrl =
  process.env.MEDROUTE_NAVIGATION_BASE_URL ?? "http://127.0.0.1:5174/";

function assert(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

function pass(message) {
  console.log(`[PASS] ${message}`);
}

class HttpError extends Error {
  constructor(message, status) {
    super(message);
    this.status = status;
  }
}

async function request(path, options = {}) {
  const response = await fetch(`${apiBase}${path}`, {
    ...options,
    headers: {
      Accept: "application/json",
      ...(options.body ? { "Content-Type": "application/json" } : {}),
      ...(options.headers ?? {}),
    },
  });
  if (!response.ok) {
    const body = await response.text();
    throw new HttpError(
      `${options.method ?? "GET"} ${path} returned ${response.status}: ${body}`,
      response.status,
    );
  }
  return response;
}

async function json(path, options) {
  return (await request(path, options)).json();
}

async function mapWithConcurrency(values, concurrency, worker) {
  const results = new Array(values.length);
  let nextIndex = 0;
  async function run() {
    while (nextIndex < values.length) {
      const index = nextIndex;
      nextIndex += 1;
      results[index] = await worker(values[index], index);
    }
  }
  await Promise.all(
    Array.from(
      { length: Math.min(concurrency, values.length) },
      () => run(),
    ),
  );
  return results;
}

async function calculateRoute(startPoi, endPoi, releaseId, routeMode) {
  return json("/api/routes", {
    method: "POST",
    body: JSON.stringify({
      buildingId,
      expectedReleaseId: releaseId,
      startPoiId: startPoi.id,
      endPoiId: endPoi.id,
      routeMode,
    }),
  });
}

async function routeTo(startPoi, endPoi, releaseId, routeMode) {
  try {
    const route = await calculateRoute(
      startPoi,
      endPoi,
      releaseId,
      routeMode,
    );
    assert(
      route.releaseId === releaseId,
      `${routeMode} route ${endPoi.code} used another release`,
    );
    assert(
      route.startPoi?.id === startPoi.id &&
        route.endPoi?.id === endPoi.id,
      `${routeMode} route ${endPoi.code} returned wrong endpoints`,
    );
    assert(
      route.segments?.length > 0 && route.steps?.length >= 2,
      `${routeMode} route ${endPoi.code} has no usable guidance`,
    );
    return { ok: true, code: endPoi.code, routeMode, route };
  } catch (error) {
    return {
      ok: false,
      code: endPoi.code,
      message: error instanceof Error ? error.message : String(error),
    };
  }
}

function assertRouteMatrix(results, routeMode) {
  const failures = results.filter((result) => !result.ok);
  assert(
    failures.length === 0,
    `${routeMode} route matrix failed:\n${failures
      .slice(0, 8)
      .map((failure) => `  ${failure.code}: ${failure.message}`)
      .join("\n")}`,
  );
}

async function verifyQrCode(startPoi) {
  const navigationUrl = new URL(navigationBaseUrl);
  navigationUrl.searchParams.set("building", buildingId);
  navigationUrl.searchParams.set("startPoi", startPoi.code);
  const response = await request("/api/admin/navigation-qr-code", {
    method: "POST",
    headers: { Accept: "image/png" },
    body: JSON.stringify({ navigationUrl: navigationUrl.toString() }),
  });
  assert(
    response.headers.get("content-type")?.includes("image/png"),
    "QR endpoint did not return image/png",
  );
  const bytes = new Uint8Array(await response.arrayBuffer());
  assert(bytes.length > 1000, "QR PNG is unexpectedly small");
  assert(
    bytes[0] === 0x89 &&
      bytes[1] === 0x50 &&
      bytes[2] === 0x4e &&
      bytes[3] === 0x47,
    "QR response has no PNG signature",
  );
  pass(`QR PNG generated for ${startPoi.code} (${bytes.length} bytes)`);
}

async function verifyReversibleClosure(
  operations,
  routeCandidates,
  startPoi,
  releaseId,
) {
  const activeTargetIds = new Set(
    operations.items.map(
      (item) => `${item.targetType}:${item.targetId}`,
    ),
  );
  const availableConnectors = new Map(
    operations.targets
      .filter(
        (item) =>
          item.targetType === "vertical_connector" &&
          !activeTargetIds.has(`${item.targetType}:${item.id}`),
      )
      .map((item) => [item.id, item]),
  );
  const scenario = routeCandidates.find((candidate) =>
    candidate.route?.transitions?.some((transition) =>
      availableConnectors.has(transition.connectorId),
    ),
  );
  const usedConnectorId = scenario?.route.transitions.find((transition) =>
    availableConnectors.has(transition.connectorId),
  )?.connectorId;
  const target =
    (usedConnectorId
      ? availableConnectors.get(usedConnectorId)
      : null) ??
    operations.targets.find(
      (item) => !activeTargetIds.has(`${item.targetType}:${item.id}`),
    );
  assert(target, "No open operation target is available for write smoke test");

  const reason = `pilot-api-smoke ${new Date().toISOString()}`;
  let closureId = "";
  try {
    const created = await json(
      `/api/admin/buildings/${buildingId}/operations/closures`,
      {
        method: "POST",
        headers: { "X-Admin-User": "pilot-api-smoke" },
        body: JSON.stringify({
          targetType: target.targetType,
          targetId: target.id,
          effectiveFrom: null,
          effectiveTo: null,
          reason,
        }),
      },
    );
    const closure = created.items.find(
      (item) =>
        item.targetType === target.targetType &&
        item.targetId === target.id &&
        item.reason === reason,
    );
    assert(closure, "Created closure is missing from operation response");
    closureId = closure.id;
    pass(`operation closure created for ${target.code}`);

    if (scenario && target.targetType === "vertical_connector") {
      const destination = {
        id: scenario.route.endPoi.id,
        code: scenario.route.endPoi.code,
      };
      try {
        const closedRoute = await calculateRoute(
          startPoi,
          destination,
          releaseId,
          scenario.routeMode,
        );
        assert(
          !closedRoute.transitions.some(
            (transition) => transition.connectorId === target.id,
          ),
          `Route still uses closed connector ${target.code}`,
        );
        assert(
          JSON.stringify(closedRoute.transitions) !==
            JSON.stringify(scenario.route.transitions) ||
            closedRoute.summary.distanceMeters !==
              scenario.route.summary.distanceMeters ||
            closedRoute.summary.estimatedSeconds !==
              scenario.route.summary.estimatedSeconds,
          "Closure did not change the affected route",
        );
        pass(
          `${destination.code} rerouted without closed connector ${target.code}`,
        );
      } catch (error) {
        assert(
          error instanceof HttpError && error.status === 422,
          `Affected route failed unexpectedly: ${
            error instanceof Error ? error.message : String(error)
          }`,
        );
        pass(
          `${destination.code} became explicitly unreachable after closing ${target.code}`,
        );
      }
    } else {
      console.log(
        "[SKIP] route impact: no open connector is used by the route matrix",
      );
    }
  } finally {
    if (closureId) {
      const revoked = await json(
        `/api/admin/operations/closures/${closureId}`,
        {
          method: "DELETE",
          headers: { "X-Admin-User": "pilot-api-smoke" },
        },
      );
      assert(
        !revoked.items.some((item) => item.id === closureId),
        "Revoked closure is still active",
      );
      pass(`operation closure revoked for ${target.code}`);
    }
  }

  if (scenario && target.targetType === "vertical_connector") {
    const restoredRoute = await calculateRoute(
      startPoi,
      {
        id: scenario.route.endPoi.id,
        code: scenario.route.endPoi.code,
      },
      releaseId,
      scenario.routeMode,
    );
    assert(
      restoredRoute.transitions.some(
        (transition) => transition.connectorId === target.id,
      ),
      `Restored route did not return to connector ${target.code}`,
    );
    assert(
      restoredRoute.summary.distanceMeters ===
        scenario.route.summary.distanceMeters &&
        restoredRoute.summary.estimatedSeconds ===
          scenario.route.summary.estimatedSeconds,
      "Restored route does not match the baseline cost",
    );
    pass(`baseline route restored through ${target.code}`);
  }
}

async function main() {
  const startedAt = Date.now();
  assert(
    !(publicOnly && writeMode),
    "--public-only cannot be combined with --write",
  );
  const [context, poiList, releases, operations] = await Promise.all([
    json(`/api/buildings/${buildingId}/navigation-context`),
    json(`/api/buildings/${buildingId}/pois`),
    publicOnly
      ? Promise.resolve(null)
      : json(`/api/admin/buildings/${buildingId}/releases`),
    publicOnly
      ? Promise.resolve(null)
      : json(`/api/admin/buildings/${buildingId}/operations/closures`),
  ]);

  const releaseId = context.release?.id;
  assert(releaseId, "Navigation context has no active release");
  if (publicOnly) {
    assert(
      poiList.releaseId === releaseId,
      "Context and POIs use different releases",
    );
  } else {
    const activeReleases = releases.items.filter(
      (item) => item.status === "published" && item.active,
    );
    assert(
      activeReleases.length === 1,
      "Building must have one active release",
    );
    assert(
      activeReleases[0].id === releaseId &&
        poiList.releaseId === releaseId &&
        operations.releaseId === releaseId,
      "Context, POIs, operations and release list use different releases",
    );
  }
  assert(context.floors?.length > 0, "Navigation context has no floors");
  for (const floor of context.floors) {
    assert(
      floor.mapRevision?.imageWidth > 0 &&
        floor.mapRevision?.imageHeight > 0 &&
        floor.mapRevision?.imageUrl,
      `Floor ${floor.code} has no valid map revision`,
    );
  }
  assert(
    poiList.items.length >= expectedPoiCount,
    `Expected at least ${expectedPoiCount} POIs, got ${poiList.items.length}`,
  );
  pass(
    `${context.floors.length} floors and ${poiList.items.length} POIs use release ${context.release.code}`,
  );

  const floorIds = new Set(context.floors.map((floor) => floor.id));
  assert(
    poiList.items.every((poi) => floorIds.has(poi.floorId)),
    "At least one POI refers to a floor outside navigation context",
  );

  const startPoi =
    poiList.items.find((poi) => poi.code === requestedStartCode) ??
    poiList.items
      .filter((poi) => poi.category === "entrance")
      .sort((left, right) =>
        `${left.floorCode}:${left.code}`.localeCompare(
          `${right.floorCode}:${right.code}`,
        ),
      )[0] ??
    poiList.items[0];
  assert(startPoi, "No start POI is available");
  if (requestedStartCode) {
    assert(
      startPoi.code === requestedStartCode,
      `Requested start POI ${requestedStartCode} does not exist`,
    );
  }

  const normalDestinations = poiList.items.filter(
    (poi) => poi.id !== startPoi.id,
  );
  const normalResults = await mapWithConcurrency(
    normalDestinations,
    4,
    (poi) => routeTo(startPoi, poi, releaseId, "normal"),
  );
  assertRouteMatrix(normalResults, "normal");
  pass(
    `${normalResults.length} normal routes are reachable from ${startPoi.code}`,
  );

  let accessibleResults = [];
  if (startPoi.accessible) {
    const accessibleDestinations = normalDestinations.filter(
      (poi) => poi.accessible,
    );
    accessibleResults = await mapWithConcurrency(
      accessibleDestinations,
      4,
      (poi) => routeTo(startPoi, poi, releaseId, "accessible"),
    );
    assertRouteMatrix(accessibleResults, "accessible");
    pass(
      `${accessibleResults.length} accessible routes are reachable from ${startPoi.code}`,
    );
  } else {
    console.log(
      `[SKIP] accessible matrix: start POI ${startPoi.code} is not accessible`,
    );
  }

  if (publicOnly) {
    console.log("[SKIP] admin release, operations and QR checks (public only)");
  } else {
    await verifyQrCode(startPoi);
  }
  if (writeMode) {
    await verifyReversibleClosure(
      operations,
      [...normalResults, ...accessibleResults],
      startPoi,
      releaseId,
    );
  } else {
    console.log("[SKIP] reversible operation closure (run with --write)");
  }

  pass(`pilot API smoke completed in ${Date.now() - startedAt} ms`);
}

main().catch((error) => {
  console.error(
    `[FAIL] ${error instanceof Error ? error.message : String(error)}`,
  );
  process.exitCode = 1;
});
