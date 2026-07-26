package com.medroute.nav.dto;

import com.medroute.nav.model.RouteMode;

import java.math.BigDecimal;

public record RouteRegressionCaseRequest(
    String code,
    String name,
    String startPoiCode,
    String endPoiCode,
    RouteMode routeMode,
    Boolean critical,
    Boolean enabled,
    BigDecimal maxDistanceMeters,
    Integer maxEstimatedSeconds
) {
    public boolean criticalOrDefault() {
        return critical == null || critical;
    }

    public boolean enabledOrDefault() {
        return enabled == null || enabled;
    }
}
