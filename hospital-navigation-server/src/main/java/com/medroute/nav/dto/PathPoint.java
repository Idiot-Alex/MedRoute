package com.medroute.nav.dto;

public record PathPoint(
    String nodeId,
    long floorId,
    double x,
    double y
) {
}
