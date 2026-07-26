#!/usr/bin/env node

import { createHash, randomUUID } from "node:crypto";
import { readFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const repoRoot = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  "..",
);
const apiBase = (
  process.env.MEDROUTE_API_BASE ?? "http://127.0.0.1:8080"
).replace(/\/$/, "");
const buildingId =
  process.env.MEDROUTE_BUILDING_ID ??
  "00000000-0000-0000-0000-000000000100";
const requestedFloorCode =
  process.env.MEDROUTE_FLOOR_CODE ?? "";
const imagePath =
  process.env.MEDROUTE_MAP_IMAGE ??
  path.join(
    repoRoot,
    "hospital-map-demo/assets/hndfsrmyy/outpatient-1f.jpg",
  );
const actor =
  process.env.MEDROUTE_ADMIN_USER ?? "pilot-authoring-smoke";

function assert(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

function pass(message) {
  console.log(`[PASS] ${message}`);
}

function closeEnough(actual, expected) {
  return Math.abs(actual - expected) <= 0.001;
}

function imageMimeType(bytes) {
  if (
    bytes[0] === 0x89 &&
    bytes[1] === 0x50 &&
    bytes[2] === 0x4e &&
    bytes[3] === 0x47
  ) {
    return "image/png";
  }
  if (bytes[0] === 0xff && bytes[1] === 0xd8) {
    return "image/jpeg";
  }
  throw new Error("MEDROUTE_MAP_IMAGE must be a PNG or JPEG file");
}

async function request(pathOrUrl, options = {}) {
  const url = /^https?:\/\//i.test(pathOrUrl)
    ? pathOrUrl
    : `${apiBase}${pathOrUrl}`;
  const headers = new Headers(options.headers);
  headers.set("X-Request-Id", `authoring-smoke-${randomUUID()}`);
  if (options.admin !== false) {
    headers.set("X-Admin-User", actor);
  }
  if (typeof options.body === "string") {
    headers.set("Content-Type", "application/json");
  }
  if (!headers.has("Accept")) {
    headers.set("Accept", "application/json");
  }
  const { admin: _admin, ...fetchOptions } = options;
  const response = await fetch(url, {
    cache: "no-store",
    ...fetchOptions,
    headers,
  });
  if (!response.ok) {
    const body = await response.text();
    throw new Error(
      `${options.method ?? "GET"} ${pathOrUrl} returned ${response.status}: ${body}`,
    );
  }
  return response;
}

async function json(pathOrUrl, options) {
  const response = await request(pathOrUrl, options);
  return {
    body: await response.json(),
    etag: response.headers.get("ETag"),
  };
}

function assertScaledElements(before, after, floorId, widthScale, heightScale) {
  const afterById = new Map(after.map((item) => [item.id, item]));
  for (const item of before) {
    const candidate = afterById.get(item.id);
    assert(candidate, `Element ${item.id} disappeared after map upload`);
    const expectedX = item.floorId === floorId
      ? item.x * widthScale
      : item.x;
    const expectedY = item.floorId === floorId
      ? item.y * heightScale
      : item.y;
    assert(
      closeEnough(candidate.x, expectedX) &&
        closeEnough(candidate.y, expectedY),
      `Element ${item.id} was not scaled as expected`,
    );
  }
}

async function deleteDraft(releaseId, etag) {
  try {
    await request(`/api/admin/releases/${releaseId}`, {
      method: "DELETE",
      headers: { "If-Match": etag },
    });
  } catch (error) {
    const current = await json(`/api/admin/releases/${releaseId}`);
    await request(`/api/admin/releases/${releaseId}`, {
      method: "DELETE",
      headers: { "If-Match": current.etag },
    });
    console.log(
      `[WARN] Cleanup retried after revision refresh: ${
        error instanceof Error ? error.message : String(error)
      }`,
    );
  }
}

async function main() {
  const startedAt = Date.now();
  const initialReleases = (
    await json(`/api/admin/buildings/${buildingId}/releases`)
  ).body;
  const activeReleases = initialReleases.items.filter(
    (item) => item.status === "published" && item.active,
  );
  assert(activeReleases.length === 1, "Building must have one active release");
  const activeReleaseId = activeReleases[0].id;
  const draftCode =
    `SMOKE-${Date.now().toString(36).toUpperCase()}-${randomUUID()
      .slice(0, 8)
      .toUpperCase()}`;

  let draftId = "";
  let etag = "";
  try {
    const created = await json(
      `/api/admin/buildings/${buildingId}/releases/drafts`,
      {
        method: "POST",
        body: JSON.stringify({
          code: draftCode,
          basedOnReleaseId: activeReleaseId,
          description: "Temporary authoring smoke draft; safe to delete.",
        }),
      },
    );
    draftId = created.body.release.id;
    etag = created.etag;
    assert(etag, "Draft creation returned no ETag");
    assert(
      created.body.release.status === "draft" &&
        created.body.release.contentRevision === 0,
      "Draft was not created at revision 0",
    );
    pass(`temporary draft ${draftCode} created`);

    const floor = requestedFloorCode
      ? created.body.floors.find(
          (item) => item.code === requestedFloorCode,
        )
      : created.body.floors[0];
    assert(
      floor,
      requestedFloorCode
        ? `Floor ${requestedFloorCode} does not exist`
        : "Draft has no floor",
    );
    const beforeWidth = floor.mapRevision.imageWidth;
    const beforeHeight = floor.mapRevision.imageHeight;
    const beforeRevisionId = floor.mapRevision.id;
    const beforeNodes = structuredClone(created.body.graph.nodes);
    const beforePois = structuredClone(created.body.graph.pois);
    const imageBytes = await readFile(imagePath);
    const mimeType = imageMimeType(imageBytes);
    const formData = new FormData();
    formData.append(
      "file",
      new Blob([imageBytes], { type: mimeType }),
      path.basename(imagePath),
    );

    const uploaded = await json(
      `/api/admin/releases/${draftId}/floors/${floor.id}/map`,
      {
        method: "POST",
        headers: { "If-Match": etag },
        body: formData,
      },
    );
    etag = uploaded.etag;
    assert(etag, "Map upload returned no ETag");
    assert(
      uploaded.body.release.contentRevision === 1,
      "Map upload did not increment content revision",
    );
    const uploadedFloor = uploaded.body.floors.find(
      (item) => item.id === floor.id,
    );
    assert(uploadedFloor, "Uploaded floor disappeared");
    assert(
      uploadedFloor.mapRevision.id !== beforeRevisionId,
      "Map upload reused the prior revision",
    );
    const widthScale =
      uploadedFloor.mapRevision.imageWidth / beforeWidth;
    const heightScale =
      uploadedFloor.mapRevision.imageHeight / beforeHeight;
    assertScaledElements(
      beforeNodes,
      uploaded.body.graph.nodes,
      floor.id,
      widthScale,
      heightScale,
    );
    assertScaledElements(
      beforePois,
      uploaded.body.graph.pois,
      floor.id,
      widthScale,
      heightScale,
    );
    pass(
      `${floor.code} map uploaded at ${uploadedFloor.mapRevision.imageWidth}x${uploadedFloor.mapRevision.imageHeight}; coordinates scaled ${widthScale.toFixed(3)}x${heightScale.toFixed(3)}`,
    );

    const imageResponse = await request(
      uploadedFloor.mapRevision.imageUrl,
      {
        admin: false,
        headers: { Accept: mimeType },
      },
    );
    const downloadedBytes = new Uint8Array(
      await imageResponse.arrayBuffer(),
    );
    const inputHash = createHash("sha256").update(imageBytes).digest("hex");
    const downloadedHash = createHash("sha256")
      .update(downloadedBytes)
      .digest("hex");
    assert(inputHash === downloadedHash, "Downloaded map bytes changed");
    assert(
      imageResponse.headers.get("Content-Type")?.includes(mimeType) &&
        imageResponse.headers.get("ETag") &&
        imageResponse.headers.get("Cache-Control")?.includes("immutable"),
      "Map response is missing immutable image headers",
    );
    pass(`uploaded map bytes and immutable cache headers verified`);

    const graph = structuredClone(uploaded.body.graph);
    const movedNode = graph.nodes.find(
      (item) => item.floorId === floor.id,
    );
    assert(movedNode, `Floor ${floor.code} has no node to edit`);
    const delta = movedNode.x + 0.25 <=
      uploadedFloor.mapRevision.imageWidth
      ? 0.25
      : -0.25;
    movedNode.x += delta;
    const saved = await json(
      `/api/admin/releases/${draftId}/workspace`,
      {
        method: "PUT",
        headers: { "If-Match": etag },
        body: JSON.stringify(graph),
      },
    );
    etag = saved.etag;
    assert(etag, "Workspace save returned no ETag");
    assert(
      saved.body.release.contentRevision === 2,
      "Workspace save did not increment content revision",
    );
    const persisted = await json(`/api/admin/releases/${draftId}`);
    etag = persisted.etag;
    const persistedNode = persisted.body.graph.nodes.find(
      (item) => item.id === movedNode.id,
    );
    assert(
      persistedNode && closeEnough(persistedNode.x, movedNode.x),
      "Workspace edit was not persisted",
    );
    pass(`workspace edit persisted at revision 2`);

    const validated = await json(
      `/api/admin/releases/${draftId}/validate`,
      {
        method: "POST",
        headers: { "If-Match": etag },
      },
    );
    etag = validated.etag ?? etag;
    assert(
      validated.body.contentRevision === 2 &&
        validated.body.passed,
      `Draft validation failed: ${JSON.stringify(
        validated.body.errors?.slice(0, 5),
      )}`,
    );
    pass(
      `draft validation passed with ${validated.body.routeRegressions.length} route regressions`,
    );
  } finally {
    if (draftId) {
      await deleteDraft(draftId, etag);
      pass(`temporary draft ${draftCode} deleted`);
    }
    const finalReleases = (
      await json(`/api/admin/buildings/${buildingId}/releases`)
    ).body;
    assert(
      !finalReleases.items.some((item) => item.id === draftId),
      "Temporary draft remains after cleanup",
    );
    assert(
      finalReleases.items.some(
        (item) => item.id === activeReleaseId && item.active,
      ),
      "Authoring smoke changed the active release",
    );
  }

  pass(`pilot authoring smoke completed in ${Date.now() - startedAt} ms`);
}

main().catch((error) => {
  console.error(
    `[FAIL] ${error instanceof Error ? error.message : String(error)}`,
  );
  process.exitCode = 1;
});
