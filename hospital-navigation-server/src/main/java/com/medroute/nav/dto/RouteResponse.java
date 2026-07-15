package com.medroute.nav.dto;

import java.util.List;

public record RouteResponse(
    long hospitalId,
    String routeMode,
    PoiSummary startPoi,
    PoiSummary endPoi,
    double distance,
    int estimatedTime,
    List<PathPoint> path,
    List<String> steps
) {
}
