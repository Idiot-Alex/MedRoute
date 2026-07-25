package com.medroute.nav.dto;

import com.medroute.nav.model.RouteMode;

import java.util.UUID;

public record NavigationRouteRequest(
    UUID buildingId,
    UUID expectedReleaseId,
    UUID startPoiId,
    UUID endPoiId,
    RouteMode routeMode
) {
}
