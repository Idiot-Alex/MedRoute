import { describe, expect, it } from "vitest";
import {
  canPublishRelease,
  canRollbackRelease,
  selectionForValidationIssue,
  validationMatchesRelease,
} from "./releases";
import type {
  AdminRelease,
  AdminValidation,
  ReleaseListItem,
} from "./types";

const release: AdminRelease = {
  id: "draft-1",
  code: "DRAFT-1",
  status: "draft",
  contentRevision: 4,
  basedOnReleaseId: "published-1",
  description: "",
  createdBy: "admin",
  createdAt: "2026-07-26T08:00:00Z",
  publishedBy: null,
  publishedAt: null,
};

const validation: AdminValidation = {
  releaseId: "draft-1",
  contentRevision: 4,
  passed: true,
  errors: [],
  warnings: [],
  routeRegressions: [],
};

describe("release workflow", () => {
  it("only accepts validation for the current content revision", () => {
    expect(validationMatchesRelease(validation, release)).toBe(true);
    expect(
      validationMatchesRelease(
        { ...validation, contentRevision: 3 },
        release,
      ),
    ).toBe(false);
  });

  it("only allows a clean and currently validated draft to publish", () => {
    expect(canPublishRelease(release, validation, false, false)).toBe(true);
    expect(canPublishRelease(release, validation, true, false)).toBe(false);
    expect(
      canPublishRelease(
        release,
        { ...validation, passed: false },
        false,
        false,
      ),
    ).toBe(false);
  });

  it("only allows rollback to a non-active published release", () => {
    const releases = [
      summary("published-1", true),
      summary("published-old", false),
    ];
    expect(canRollbackRelease("published-old", releases, false)).toBe(true);
    expect(canRollbackRelease("published-1", releases, false)).toBe(false);
  });

  it("maps backend validation element types to editor selections", () => {
    expect(
      selectionForValidationIssue({
        code: "ISOLATED_NODE",
        elementType: "path_node",
        elementId: "node-1",
        message: "",
      }),
    ).toEqual({ kind: "node", id: "node-1" });
    expect(
      selectionForValidationIssue({
        code: "ROUTE_FAILED",
        elementType: "route_regression_case",
        elementId: "case-1",
        message: "",
      }),
    ).toBeNull();
  });
});

function summary(id: string, active: boolean): ReleaseListItem {
  return {
    id,
    code: id,
    status: "published",
    active,
    contentRevision: 1,
    basedOnReleaseId: null,
    description: "",
    createdBy: "admin",
    createdAt: "2026-07-26T08:00:00Z",
    publishedBy: "admin",
    publishedAt: "2026-07-26T09:00:00Z",
    validationPassed: true,
    validatedRevision: 1,
  };
}
