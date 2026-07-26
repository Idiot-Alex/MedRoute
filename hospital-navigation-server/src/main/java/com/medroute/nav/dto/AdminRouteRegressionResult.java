package com.medroute.nav.dto;

import com.medroute.nav.model.RouteMode;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record AdminRouteRegressionResult(
    UUID caseId,
    String caseCode,
    String caseName,
    RouteMode routeMode,
    boolean critical,
    String startPoiCode,
    String startPoiName,
    String endPoiCode,
    String endPoiName,
    boolean passed,
    String resultCode,
    BigDecimal distanceMeters,
    Long estimatedSeconds,
    List<String> connectorCodes,
    String message
) {
    public AdminRouteRegressionResult {
        connectorCodes = connectorCodes == null
            ? List.of()
            : List.copyOf(connectorCodes);
    }
}
