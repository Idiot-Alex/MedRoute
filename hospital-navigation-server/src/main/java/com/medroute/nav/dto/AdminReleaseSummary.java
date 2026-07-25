package com.medroute.nav.dto;

import java.time.Instant;
import java.util.UUID;

public record AdminReleaseSummary(
    UUID id,
    String code,
    String status,
    boolean active,
    long contentRevision,
    UUID basedOnReleaseId,
    String description,
    String createdBy,
    Instant createdAt,
    String publishedBy,
    Instant publishedAt,
    Boolean validationPassed,
    Long validatedRevision
) {
}
