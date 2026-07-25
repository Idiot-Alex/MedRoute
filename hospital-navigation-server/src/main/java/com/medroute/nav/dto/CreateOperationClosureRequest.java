package com.medroute.nav.dto;

import java.time.Instant;
import java.util.UUID;

public record CreateOperationClosureRequest(
    String targetType,
    UUID targetId,
    Instant effectiveFrom,
    Instant effectiveTo,
    String reason
) {
}
