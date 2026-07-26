import type {
  AdminRelease,
  AdminValidation,
  MapSelection,
  ReleaseListItem,
  ValidationIssue,
} from "./types";

const issueSelectionKinds: Record<string, MapSelection["kind"]> = {
  path_node: "node",
  path_edge: "edge",
  poi: "poi",
  vertical_connector: "connector",
  connector_stop: "stop",
  vertical_link: "link",
};

export function validationMatchesRelease(
  validation: AdminValidation | null,
  release: AdminRelease,
): boolean {
  return (
    validation?.releaseId === release.id &&
    validation.contentRevision === release.contentRevision
  );
}

export function canPublishRelease(
  release: AdminRelease,
  validation: AdminValidation | null,
  dirty: boolean,
  busy: boolean,
): boolean {
  return (
    release.status === "draft" &&
    !dirty &&
    !busy &&
    validationMatchesRelease(validation, release) &&
    validation?.passed === true
  );
}

export function canRollbackRelease(
  releaseId: string,
  releases: ReleaseListItem[],
  busy: boolean,
): boolean {
  const release = releases.find((item) => item.id === releaseId);
  return Boolean(
    release &&
      release.status === "published" &&
      !release.active &&
      !busy,
  );
}

export function selectionForValidationIssue(
  issue: ValidationIssue,
): MapSelection | null {
  const kind = issueSelectionKinds[issue.elementType];
  return kind ? { kind, id: issue.elementId } : null;
}
