export function isDemoFeatureEnabled(
  isDevelopment: boolean,
  value: unknown,
): boolean {
  return isDevelopment && value === "true";
}

export function shouldUseDemoMode(
  featureEnabled: boolean,
  searchParams: URLSearchParams,
): boolean {
  return featureEnabled && searchParams.get("demo") === "1";
}
