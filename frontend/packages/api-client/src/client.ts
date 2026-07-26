import type {
  AdminValidation,
  AdminWorkspace,
  DraftGraph,
  NavigationContext,
  NavigationPoiSearchResponse,
  NavigationRoute,
  OperationClosureListResponse,
  ReleaseListResponse,
  RouteRegressionCase,
  RouteRegressionCaseListResponse,
  RouteRegressionCasePayload,
  CreateOperationClosurePayload,
} from "@medroute/map-core";

export interface ApiClientOptions {
  apiBase: string;
  fetchImpl?: typeof fetch;
  navigationTimeoutMs?: number;
}

export interface WorkspaceResult {
  workspace: AdminWorkspace;
  etag: string;
}

export interface ValidationResult {
  validation: AdminValidation;
  etag: string;
}

export interface CreateDraftRequest {
  code: string;
  basedOnReleaseId: string;
  description: string;
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
  private readonly navigationTimeoutMs: number;

  constructor(options: ApiClientOptions) {
    this.apiBase = options.apiBase.replace(/\/$/, "");
    this.fetchImpl = options.fetchImpl ?? fetch;
    this.navigationTimeoutMs = options.navigationTimeoutMs ?? 12_000;
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

  async createDraft(
    buildingId: string,
    request: CreateDraftRequest,
  ): Promise<WorkspaceResult> {
    const { body, response } = await this.request<AdminWorkspace>(
      `/api/admin/buildings/${buildingId}/releases/drafts`,
      {
        admin: true,
        method: "POST",
        body: JSON.stringify(request),
      },
    );
    return {
      workspace: body,
      etag:
        response.headers.get("ETag") ??
        String(body.release.contentRevision),
    };
  }

  async deleteDraft(releaseId: string, etag: string): Promise<void> {
    await this.request<void>(`/api/admin/releases/${releaseId}`, {
      admin: true,
      method: "DELETE",
      headers: { "If-Match": etag },
    });
  }

  async validateRelease(
    releaseId: string,
    etag: string,
  ): Promise<ValidationResult> {
    const { body, response } = await this.request<AdminValidation>(
      `/api/admin/releases/${releaseId}/validate`,
      {
        admin: true,
        method: "POST",
        headers: { "If-Match": etag },
      },
    );
    return {
      validation: body,
      etag: response.headers.get("ETag") ?? String(body.contentRevision),
    };
  }

  async replaceFloorMap(
    releaseId: string,
    floorId: string,
    etag: string,
    file: File,
  ): Promise<WorkspaceResult> {
    const formData = new FormData();
    formData.append("file", file, file.name);
    const { body, response } = await this.request<AdminWorkspace>(
      `/api/admin/releases/${releaseId}/floors/${floorId}/map`,
      {
        admin: true,
        method: "POST",
        headers: { "If-Match": etag },
        body: formData,
      },
    );
    return {
      workspace: body,
      etag:
        response.headers.get("ETag") ??
        String(body.release.contentRevision),
    };
  }

  async publishRelease(
    releaseId: string,
    etag: string,
    reason: string,
  ): Promise<NavigationContext> {
    const { body } = await this.request<NavigationContext>(
      `/api/admin/releases/${releaseId}/publish`,
      {
        admin: true,
        method: "POST",
        headers: { "If-Match": etag },
        body: JSON.stringify({ reason }),
      },
    );
    return body;
  }

  async rollbackRelease(
    releaseId: string,
    reason: string,
  ): Promise<NavigationContext> {
    const { body } = await this.request<NavigationContext>(
      `/api/admin/releases/${releaseId}/rollback`,
      {
        admin: true,
        method: "POST",
        body: JSON.stringify({ reason }),
      },
    );
    return body;
  }

  async listRouteRegressionCases(
    buildingId: string,
  ): Promise<RouteRegressionCaseListResponse> {
    const { body } = await this.request<RouteRegressionCaseListResponse>(
      `/api/admin/buildings/${buildingId}/route-regression-cases`,
      { admin: true },
    );
    return body;
  }

  async createRouteRegressionCase(
    buildingId: string,
    payload: RouteRegressionCasePayload,
  ): Promise<RouteRegressionCase> {
    const { body } = await this.request<RouteRegressionCase>(
      `/api/admin/buildings/${buildingId}/route-regression-cases`,
      {
        admin: true,
        method: "POST",
        body: JSON.stringify(payload),
      },
    );
    return body;
  }

  async updateRouteRegressionCase(
    caseId: string,
    payload: RouteRegressionCasePayload,
  ): Promise<RouteRegressionCase> {
    const { body } = await this.request<RouteRegressionCase>(
      `/api/admin/route-regression-cases/${caseId}`,
      {
        admin: true,
        method: "PUT",
        body: JSON.stringify(payload),
      },
    );
    return body;
  }

  async deleteRouteRegressionCase(caseId: string): Promise<void> {
    await this.request<void>(
      `/api/admin/route-regression-cases/${caseId}`,
      {
        admin: true,
        method: "DELETE",
      },
    );
  }

  async operationClosures(
    buildingId: string,
  ): Promise<OperationClosureListResponse> {
    const { body } = await this.request<OperationClosureListResponse>(
      `/api/admin/buildings/${buildingId}/operations/closures`,
      { admin: true },
    );
    return body;
  }

  async createOperationClosure(
    buildingId: string,
    payload: CreateOperationClosurePayload,
  ): Promise<OperationClosureListResponse> {
    const { body } = await this.request<OperationClosureListResponse>(
      `/api/admin/buildings/${buildingId}/operations/closures`,
      {
        admin: true,
        method: "POST",
        body: JSON.stringify(payload),
      },
    );
    return body;
  }

  async revokeOperationClosure(
    closureId: string,
  ): Promise<OperationClosureListResponse> {
    const { body } = await this.request<OperationClosureListResponse>(
      `/api/admin/operations/closures/${closureId}`,
      {
        admin: true,
        method: "DELETE",
      },
    );
    return body;
  }

  async generateNavigationQrCode(navigationUrl: string): Promise<Blob> {
    const { response } = await this.request<void>(
      "/api/admin/navigation-qr-code",
      {
        method: "POST",
        headers: { Accept: "image/png" },
        body: JSON.stringify({ navigationUrl }),
      },
    );
    const blob = await response.blob();
    if (
      !response.headers.get("Content-Type")?.includes("image/png") ||
      blob.type !== "image/png"
    ) {
      throw new ApiError(
        "后端未返回有效的 PNG 二维码。",
        response.status,
        "INVALID_QR_RESPONSE",
      );
    }
    return blob;
  }

  async navigationContext(
    buildingId: string,
    signal?: AbortSignal,
  ): Promise<NavigationContext> {
    const { body } = await this.request<NavigationContext>(
      `/api/buildings/${buildingId}/navigation-context`,
      { signal, timeoutMs: this.navigationTimeoutMs },
    );
    return body;
  }

  async navigationPois(
    buildingId: string,
    keyword = "",
    signal?: AbortSignal,
  ): Promise<NavigationPoiSearchResponse> {
    const query = keyword ? `?keyword=${encodeURIComponent(keyword)}` : "";
    const { body } = await this.request<NavigationPoiSearchResponse>(
      `/api/buildings/${buildingId}/pois${query}`,
      { signal, timeoutMs: this.navigationTimeoutMs },
    );
    return body;
  }

  async calculateRoute(
    request: RouteRequest,
    signal?: AbortSignal,
  ): Promise<NavigationRoute> {
    const { body } = await this.request<NavigationRoute>("/api/routes", {
      method: "POST",
      body: JSON.stringify(request),
      signal,
      timeoutMs: this.navigationTimeoutMs,
    });
    return body;
  }

  private async request<T>(
    path: string,
    options: RequestInit & {
      admin?: boolean;
      timeoutMs?: number;
    } = {},
  ): Promise<{ body: T; response: Response }> {
    const {
      admin,
      timeoutMs,
      signal,
      ...requestOptions
    } = options;
    const headers = new Headers(options.headers);
    if (!headers.has("Accept")) {
      headers.set("Accept", "application/json");
    }
    headers.set("X-Request-Id", `web-${crypto.randomUUID()}`);
    if (admin) {
      headers.set("X-Admin-User", "local-admin");
    }
    if (
      typeof options.body === "string" &&
      !headers.has("Content-Type")
    ) {
      headers.set("Content-Type", "application/json");
    }
    const controller = new AbortController();
    let timedOut = false;
    const abortFromCaller = () => controller.abort(signal?.reason);
    if (signal?.aborted) {
      abortFromCaller();
    } else {
      signal?.addEventListener("abort", abortFromCaller, { once: true });
    }
    const timeoutId =
      timeoutMs && timeoutMs > 0
        ? setTimeout(() => {
            timedOut = true;
            controller.abort();
          }, timeoutMs)
        : undefined;

    let response: Response;
    let data: unknown = null;
    try {
      response = await this.fetchImpl(`${this.apiBase}${path}`, {
        cache: "no-store",
        ...requestOptions,
        headers,
        signal: controller.signal,
      });
      const contentType = response.headers.get("Content-Type") ?? "";
      data = contentType.includes("application/json")
        ? await response.json()
        : null;
    } catch {
      if (timedOut) {
        throw new ApiError(
          "请求超时，请检查网络后重试。",
          0,
          "REQUEST_TIMEOUT",
        );
      }
      if (signal?.aborted) {
        throw new ApiError("请求已取消。", 0, "REQUEST_ABORTED");
      }
      throw new ApiError(
        `无法连接后端 ${this.apiBase}。`,
        0,
        "NETWORK_ERROR",
      );
    } finally {
      if (timeoutId) {
        clearTimeout(timeoutId);
      }
      signal?.removeEventListener("abort", abortFromCaller);
    }
    if (!response.ok) {
      const problem = (
        data as {
          error?: {
            message?: string;
            code?: string;
            details?: unknown[];
          };
        } | null
      )?.error;
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
