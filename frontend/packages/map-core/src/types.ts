export const DEFAULT_BUILDING_ID =
  "00000000-0000-0000-0000-000000000100";

export interface MapRevision {
  id: string;
  revisionNo: number;
  imageUrl: string;
  imageWidth: number;
  imageHeight: number;
}

export interface Floor {
  id: string;
  code: string;
  name: string;
  levelNo: number;
  mapRevision: MapRevision;
}

export interface Building {
  id: string;
  code: string;
  name: string;
}

export interface AdminRelease {
  id: string;
  code: string;
  status: "draft" | "published";
  contentRevision: number;
  basedOnReleaseId: string | null;
  description: string;
  createdBy: string;
  createdAt: string;
  publishedBy: string | null;
  publishedAt: string | null;
}

export interface PathNode {
  id: string;
  code: string;
  floorId: string;
  x: number;
  y: number;
  type: string;
  enabled: boolean;
}

export interface PathEdge {
  id: string;
  code: string;
  floorId: string;
  fromNodeId: string;
  toNodeId: string;
  timeSeconds: number;
  distanceMeters: number;
  direction: string;
  type: string;
  accessScope: string;
  accessible: boolean;
  enabled: boolean;
}

export interface Poi {
  id: string;
  code: string;
  name: string;
  category: string;
  floorId: string;
  nodeId: string;
  x: number;
  y: number;
  accessScope: string;
  accessible: boolean;
  enabled: boolean;
  keywords: string[];
}

export interface Connector {
  id: string;
  code: string;
  name: string;
  type: string;
  accessScope: string;
  accessible: boolean;
  enabled: boolean;
}

export interface ConnectorStop {
  id: string;
  code: string;
  connectorId: string;
  floorId: string;
  nodeId: string;
}

export interface VerticalLink {
  id: string;
  code: string;
  connectorId: string;
  fromStopId: string;
  toStopId: string;
  timeSeconds: number;
  distanceMeters: number;
  direction: string;
  accessScope: string;
  accessible: boolean;
  enabled: boolean;
}

export interface DraftGraph {
  nodes: PathNode[];
  edges: PathEdge[];
  pois: Poi[];
  connectors: Connector[];
  connectorStops: ConnectorStop[];
  verticalLinks: VerticalLink[];
}

export interface AdminWorkspace {
  building: Building;
  release: AdminRelease;
  floors: Floor[];
  graph: DraftGraph;
}

export interface ReleaseListItem {
  id: string;
  code: string;
  status: "draft" | "published";
  active: boolean;
  contentRevision: number;
  description: string;
}

export interface ReleaseListResponse {
  buildingId: string;
  activeReleaseId: string | null;
  items: ReleaseListItem[];
}

export interface NavigationRelease {
  id: string;
  code: string;
  publishedAt: string;
}

export interface NavigationContext {
  building: Building;
  release: NavigationRelease;
  floors: Floor[];
  supportedRouteModes: string[];
}

export interface NavigationPoi {
  id: string;
  code: string;
  name: string;
  category: string;
  floorId: string;
  floorCode: string;
  x: number;
  y: number;
  accessible: boolean;
  searchKeywords: string[];
}

export interface NavigationPoiSearchResponse {
  releaseId: string;
  items: NavigationPoi[];
  nextPageToken: string | null;
}

export interface RoutePoint {
  nodeId: string;
  x: number;
  y: number;
}

export interface RouteSegment {
  sequence: number;
  floorId: string;
  floorCode: string;
  mapRevisionId: string;
  distanceMeters: number;
  estimatedSeconds: number;
  points: RoutePoint[];
}

export interface RouteTransition {
  sequence: number;
  afterSegmentSequence: number;
  type: string;
  connectorId: string;
  connectorCode: string;
  connectorName: string;
  fromFloorId: string;
  fromFloorCode: string;
  toFloorId: string;
  toFloorCode: string;
  estimatedSeconds: number;
  instruction: string;
}

export interface RouteStep {
  sequence: number;
  type: string;
  floorId: string | null;
  instruction: string;
}

export interface RoutePoiRef {
  id: string;
  code: string;
  name: string;
  floorId: string;
  floorCode: string;
}

export interface NavigationRoute {
  releaseId: string;
  buildingId: string;
  routeMode: string;
  calculatedAt: string;
  startPoi: RoutePoiRef;
  endPoi: RoutePoiRef;
  summary: {
    distanceMeters: number;
    estimatedSeconds: number;
  };
  segments: RouteSegment[];
  transitions: RouteTransition[];
  steps: RouteStep[];
  warnings: unknown[];
}

export type EditorTool = "select" | "node" | "edge" | "poi";
export type FeatureKind = "node" | "edge" | "poi" | "stop";

export interface MapSelection {
  kind: FeatureKind;
  id: string;
}
