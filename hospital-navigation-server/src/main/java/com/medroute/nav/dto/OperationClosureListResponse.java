package com.medroute.nav.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OperationClosureListResponse(
    UUID buildingId,
    UUID releaseId,
    String releaseCode,
    List<Target> targets,
    List<Closure> items
) {
    public OperationClosureListResponse {
        targets = targets == null ? List.of() : List.copyOf(targets);
        items = items == null ? List.of() : List.copyOf(items);
    }

    public record Target(
        String targetType,
        UUID id,
        String code,
        String name,
        String floorCode
    ) {
    }

    public record Closure(
        UUID id,
        String targetType,
        UUID targetId,
        String targetCode,
        String targetName,
        Instant effectiveFrom,
        Instant effectiveTo,
        String reason,
        String createdBy,
        Instant createdAt
    ) {
    }
}
