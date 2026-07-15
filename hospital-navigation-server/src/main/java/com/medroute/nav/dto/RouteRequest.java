package com.medroute.nav.dto;

public record RouteRequest(
    long hospitalId,
    String startPoiId,
    String endPoiId,
    String routeMode
) {
}
