package com.medroute.nav.dto;

import java.util.List;
import java.util.UUID;

public record AdminValidationResponse(
    UUID releaseId,
    long contentRevision,
    boolean passed,
    List<Issue> errors,
    List<Issue> warnings
) {
    public AdminValidationResponse {
        errors = errors == null ? List.of() : List.copyOf(errors);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public record Issue(
        String code,
        String elementType,
        UUID elementId,
        String message
    ) {
    }
}
