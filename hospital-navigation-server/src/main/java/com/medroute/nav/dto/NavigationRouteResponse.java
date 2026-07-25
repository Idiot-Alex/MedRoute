package com.medroute.nav.dto;

import com.medroute.nav.model.RouteMode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record NavigationRouteResponse(
    UUID releaseId,
    UUID buildingId,
    RouteMode routeMode,
    Instant calculatedAt,
    PoiRef startPoi,
    PoiRef endPoi,
    RouteSummary summary,
    List<RouteSegment> segments,
    List<RouteTransition> transitions,
    List<RouteStep> steps,
    List<Object> warnings
) {
    public record PoiRef(
        UUID id,
        String code,
        String name,
        UUID floorId,
        String floorCode
    ) {
    }

    public record RouteSummary(
        BigDecimal distanceMeters,
        long estimatedSeconds
    ) {
    }

    public record RouteSegment(
        int sequence,
        UUID floorId,
        String floorCode,
        UUID mapRevisionId,
        BigDecimal distanceMeters,
        long estimatedSeconds,
        List<RoutePoint> points
    ) {
    }

    public record RoutePoint(
        UUID nodeId,
        double x,
        double y
    ) {
    }

    public record RouteTransition(
        int sequence,
        int afterSegmentSequence,
        String type,
        UUID connectorId,
        String connectorCode,
        String connectorName,
        UUID fromFloorId,
        String fromFloorCode,
        UUID toFloorId,
        String toFloorCode,
        long estimatedSeconds,
        String instruction
    ) {
    }

    public record RouteStep(
        int sequence,
        String type,
        UUID floorId,
        String instruction
    ) {
    }
}
