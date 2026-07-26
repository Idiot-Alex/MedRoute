package com.medroute.nav.dto;

import java.util.List;
import java.util.UUID;

public record AdminValidationResponse(
    UUID releaseId,
    long contentRevision,
    boolean passed,
    List<Issue> errors,
    List<Issue> warnings,
    List<AdminRouteRegressionResult> routeRegressions
) {
    public AdminValidationResponse {
        errors = errors == null ? List.of() : List.copyOf(errors);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        routeRegressions = routeRegressions == null
            ? List.of()
            : List.copyOf(routeRegressions);
    }

    public AdminValidationResponse(
        UUID releaseId,
        long contentRevision,
        boolean passed,
        List<Issue> errors,
        List<Issue> warnings
    ) {
        this(
            releaseId,
            contentRevision,
            passed,
            errors,
            warnings,
            List.of()
        );
    }

    public record Issue(
        String code,
        String elementType,
        UUID elementId,
        String message
    ) {
    }
}
