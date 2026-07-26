import { describe, expect, it, vi } from "vitest";
import type {
  AdminValidation,
  AdminWorkspace,
  OperationClosureListResponse,
} from "@medroute/map-core";
import { MedRouteApiClient } from "./client";

describe("MedRouteApiClient admin workflows", () => {
  it("uploads a floor map as multipart data with optimistic locking", async () => {
    const fetchImpl = vi.fn(
      async (
        _input: RequestInfo | URL,
        _init?: RequestInit,
      ): Promise<Response> =>
        jsonResponse(workspace, { ETag: '"6"' }),
    );
    const client = new MedRouteApiClient({
      apiBase: "http://127.0.0.1:8080",
      fetchImpl,
    });
    const file = new File(["map"], "floor.jpg", {
      type: "image/jpeg",
    });

    const result = await client.replaceFloorMap(
      "release-1",
      "floor-1",
      '"5"',
      file,
    );

    expect(result.etag).toBe('"6"');
    expect(fetchImpl).toHaveBeenCalledOnce();
    const [url, options] = fetchImpl.mock.calls[0]!;
    const headers = new Headers(options?.headers);
    expect(url).toBe(
      "http://127.0.0.1:8080/api/admin/releases/release-1/floors/floor-1/map",
    );
    expect(options?.method).toBe("POST");
    expect(headers.get("If-Match")).toBe('"5"');
    expect(headers.get("X-Admin-User")).toBe("local-admin");
    expect(headers.get("Content-Type")).toBeNull();
    expect(options?.body).toBeInstanceOf(FormData);
    const uploadedFile = (options?.body as FormData).get("file");
    expect(uploadedFile).toBeInstanceOf(File);
    expect((uploadedFile as File).name).toBe("floor.jpg");
    expect((uploadedFile as File).type).toBe("image/jpeg");
    expect(await (uploadedFile as File).text()).toBe("map");
  });

  it("returns validation data and the response ETag", async () => {
    const fetchImpl = vi.fn(async () =>
      jsonResponse(validation, { ETag: '"5"' }),
    );
    const client = new MedRouteApiClient({
      apiBase: "http://127.0.0.1:8080",
      fetchImpl,
    });

    await expect(
      client.validateRelease("release-1", '"5"'),
    ).resolves.toEqual({
      validation,
      etag: '"5"',
    });
  });

  it("accepts successful responses without a JSON body", async () => {
    const fetchImpl = vi.fn(
      async () => new Response(null, { status: 204 }),
    );
    const client = new MedRouteApiClient({
      apiBase: "http://127.0.0.1:8080",
      fetchImpl,
    });

    await expect(
      client.deleteDraft("release-1", '"5"'),
    ).resolves.toBeUndefined();
  });

  it("requests a PNG QR code without the admin-only CORS header", async () => {
    const fetchImpl = vi.fn(
      async (
        _input: RequestInfo | URL,
        _init?: RequestInit,
      ): Promise<Response> =>
        new Response(new Blob(["png"], { type: "image/png" }), {
          status: 200,
          headers: { "Content-Type": "image/png" },
        }),
    );
    const client = new MedRouteApiClient({
      apiBase: "http://127.0.0.1:8080",
      fetchImpl,
    });

    const result = await client.generateNavigationQrCode(
      "https://nav.example.com/?startPoi=P-ENTRANCE",
    );

    expect(result.type).toBe("image/png");
    const [url, options] = fetchImpl.mock.calls[0]!;
    const headers = new Headers(options?.headers);
    expect(url).toBe(
      "http://127.0.0.1:8080/api/admin/navigation-qr-code",
    );
    expect(headers.get("Accept")).toBe("image/png");
    expect(headers.get("Content-Type")).toBe("application/json");
    expect(headers.get("X-Admin-User")).toBeNull();
  });

  it("creates an immediate operation closure with admin attribution", async () => {
    const fetchImpl = vi.fn(
      async (
        _input: RequestInfo | URL,
        _init?: RequestInit,
      ): Promise<Response> => jsonResponse(operationClosures),
    );
    const client = new MedRouteApiClient({
      apiBase: "http://127.0.0.1:8080",
      fetchImpl,
    });

    await expect(
      client.createOperationClosure("building-1", {
        targetType: "vertical_connector",
        targetId: "connector-1",
        effectiveFrom: null,
        effectiveTo: null,
        reason: "年度检修",
      }),
    ).resolves.toEqual(operationClosures);

    const [url, options] = fetchImpl.mock.calls[0]!;
    const headers = new Headers(options?.headers);
    expect(url).toBe(
      "http://127.0.0.1:8080/api/admin/buildings/building-1/operations/closures",
    );
    expect(options?.method).toBe("POST");
    expect(headers.get("X-Admin-User")).toBe("local-admin");
    expect(JSON.parse(String(options?.body))).toEqual({
      targetType: "vertical_connector",
      targetId: "connector-1",
      effectiveFrom: null,
      effectiveTo: null,
      reason: "年度检修",
    });
  });
});

function jsonResponse(
  body: unknown,
  headers: Record<string, string> = {},
): Response {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: {
      "Content-Type": "application/json",
      ...headers,
    },
  });
}

const workspace: AdminWorkspace = {
  building: {
    id: "building-1",
    code: "OUTPATIENT",
    name: "门诊楼",
  },
  release: {
    id: "release-1",
    code: "DRAFT-1",
    status: "draft",
    contentRevision: 6,
    basedOnReleaseId: "release-0",
    description: "",
    createdBy: "admin",
    createdAt: "2026-07-26T08:00:00Z",
    publishedBy: null,
    publishedAt: null,
  },
  floors: [],
  graph: {
    nodes: [],
    edges: [],
    pois: [],
    connectors: [],
    connectorStops: [],
    verticalLinks: [],
  },
};

const validation: AdminValidation = {
  releaseId: "release-1",
  contentRevision: 5,
  passed: true,
  errors: [],
  warnings: [],
  routeRegressions: [],
};

const operationClosures: OperationClosureListResponse = {
  buildingId: "building-1",
  releaseId: "release-1",
  releaseCode: "REL-1",
  targets: [],
  items: [],
};
