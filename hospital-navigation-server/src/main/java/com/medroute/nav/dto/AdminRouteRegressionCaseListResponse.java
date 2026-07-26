package com.medroute.nav.dto;

import java.util.List;

public record AdminRouteRegressionCaseListResponse(
    List<AdminRouteRegressionCaseResponse> items
) {
    public AdminRouteRegressionCaseListResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
