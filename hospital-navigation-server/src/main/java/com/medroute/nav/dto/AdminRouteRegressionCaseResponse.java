package com.medroute.nav.dto;

import com.medroute.nav.model.RouteMode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AdminRouteRegressionCaseResponse(
    UUID id,
    String code,
    String name,
    String startPoiCode,
    String endPoiCode,
    RouteMode routeMode,
    boolean critical,
    boolean enabled,
    BigDecimal maxDistanceMeters,
    Integer maxEstimatedSeconds,
    String createdBy,
    Instant createdAt,
    String updatedBy,
    Instant updatedAt
) {
}
