import type {
  AdminWorkspace,
  DraftGraph,
  NavigationContext,
  NavigationPoiSearchResponse,
  NavigationRoute,
  ReleaseListResponse,
} from "@medroute/map-core";

export interface ApiClientOptions {
  apiBase: string;
  fetchImpl?: typeof fetch;
}

export interface WorkspaceResult {
  workspace: AdminWorkspace;
  etag: string;
}

export interface RouteRequest {
  buildingId: string;
  expectedReleaseId: string;
  startPoiId: string;
  endPoiId: string;
  routeMode: string;
}

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly details: unknown[];

  constructor(
    message: string,
    status: number,
    code = "API_ERROR",
    details: unknown[] = [],
  ) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
    this.details = details;
  }
}

export class MedRouteApiClient {
  readonly apiBase: string;
  private readonly fetchImpl: typeof fetch;

  constructor(options: ApiClientOptions) {
    this.apiBase = options.apiBase.replace(/\/$/, "");
    this.fetchImpl = options.fetchImpl ?? fetch;
  }

  async listReleases(buildingId: string): Promise<ReleaseListResponse> {
    const { body } = await this.request<ReleaseListResponse>(
      `/api/admin/buildings/${buildingId}/releases`,
      { admin: true },
    );
    return body;
  }

  async loadPreferredWorkspace(
    buildingId: string,
    preferredReleaseId?: string,
  ): Promise<WorkspaceResult> {
    const releases = await this.listReleases(buildingId);
    const preferred = preferredReleaseId
      ? releases.items.find((release) => release.id === preferredReleaseId)
      : undefined;
    const release =
      preferred ??
      releases.items.find((item) => item.status === "draft") ??
      releases.items.find((item) => item.active) ??
      releases.items[0];
    if (!release) {
      throw new ApiError("当前楼栋没有可维护的地图版本。", 404, "NO_RELEASE");
    }
    return this.loadWorkspace(release.id);
  }

  async loadWorkspace(releaseId: string): Promise<WorkspaceResult> {
    const { body, response } = await this.request<AdminWorkspace>(
      `/api/admin/releases/${releaseId}`,
      { admin: true },
    );
    return {
      workspace: body,
      etag:
        response.headers.get("ETag") ??
        String(body.release.contentRevision),
    };
  }

  async saveWorkspace(
    releaseId: string,
    etag: string,
    graph: DraftGraph,
  ): Promise<WorkspaceResult> {
    const { body, response } = await this.request<AdminWorkspace>(
      `/api/admin/releases/${releaseId}/workspace`,
      {
        admin: true,
        method: "PUT",
        headers: { "If-Match": etag },
        body: JSON.stringify(graph),
      },
    );
    return {
      workspace: body,
      etag:
        response.headers.get("ETag") ??
        String(body.release.contentRevision),
    };
  }

  async navigationContext(buildingId: string): Promise<NavigationContext> {
    const { body } = await this.request<NavigationContext>(
      `/api/buildings/${buildingId}/navigation-context`,
    );
    return body;
  }

  async navigationPois(
    buildingId: string,
    keyword = "",
  ): Promise<NavigationPoiSearchResponse> {
    const query = keyword ? `?keyword=${encodeURIComponent(keyword)}` : "";
    const { body } = await this.request<NavigationPoiSearchResponse>(
      `/api/buildings/${buildingId}/pois${query}`,
    );
    return body;
  }

  async calculateRoute(request: RouteRequest): Promise<NavigationRoute> {
    const { body } = await this.request<NavigationRoute>("/api/routes", {
      method: "POST",
      body: JSON.stringify(request),
    });
    return body;
  }

  private async request<T>(
    path: string,
    options: RequestInit & { admin?: boolean } = {},
  ): Promise<{ body: T; response: Response }> {
    const { admin, ...requestOptions } = options;
    const headers = new Headers(options.headers);
    headers.set("Accept", "application/json");
    headers.set("X-Request-Id", `web-${crypto.randomUUID()}`);
    if (admin) {
      headers.set("X-Admin-User", "local-admin");
    }
    if (options.body && !headers.has("Content-Type")) {
      headers.set("Content-Type", "application/json");
    }
    let response: Response;
    try {
      response = await this.fetchImpl(`${this.apiBase}${path}`, {
        cache: "no-store",
        ...requestOptions,
        headers,
      });
    } catch {
      throw new ApiError(
        `无法连接后端 ${this.apiBase}。`,
        0,
        "NETWORK_ERROR",
      );
    }
    const contentType = response.headers.get("Content-Type") ?? "";
    const data = contentType.includes("application/json")
      ? await response.json()
      : null;
    if (!response.ok) {
      const problem = data?.error;
      throw new ApiError(
        problem?.message ?? `请求失败（HTTP ${response.status}）。`,
        response.status,
        problem?.code,
        problem?.details,
      );
    }
    return { body: data as T, response };
  }
}

export function resolveMapImageUrl(
  imageUrl: string,
  apiBase: string,
  assetBase = window.location.origin,
): string {
  if (/^https?:\/\//i.test(imageUrl)) {
    return imageUrl;
  }
  if (imageUrl.startsWith("/api/")) {
    return `${apiBase.replace(/\/$/, "")}${imageUrl}`;
  }
  return new URL(imageUrl, assetBase).toString();
}
