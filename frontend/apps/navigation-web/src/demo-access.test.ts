import { describe, expect, it } from "vitest";
import {
  isDemoFeatureEnabled,
  shouldUseDemoMode,
} from "./demo-access";

describe("isDemoFeatureEnabled", () => {
  it("only enables demo data in development with the explicit true value", () => {
    expect(isDemoFeatureEnabled(true, "true")).toBe(true);
    expect(isDemoFeatureEnabled(false, "true")).toBe(false);
    expect(isDemoFeatureEnabled(true, undefined)).toBe(false);
    expect(isDemoFeatureEnabled(true, "false")).toBe(false);
    expect(isDemoFeatureEnabled(true, "1")).toBe(false);
    expect(isDemoFeatureEnabled(true, "TRUE")).toBe(false);
  });
});

describe("shouldUseDemoMode", () => {
  it("requires both the build-time feature flag and demo query", () => {
    expect(
      shouldUseDemoMode(true, new URLSearchParams("?demo=1")),
    ).toBe(true);
    expect(
      shouldUseDemoMode(false, new URLSearchParams("?demo=1")),
    ).toBe(false);
    expect(
      shouldUseDemoMode(true, new URLSearchParams("?demo=0")),
    ).toBe(false);
    expect(shouldUseDemoMode(true, new URLSearchParams())).toBe(false);
  });
});
