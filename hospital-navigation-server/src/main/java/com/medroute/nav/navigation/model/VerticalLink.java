package com.medroute.nav.navigation.model;

import com.medroute.nav.model.EdgeDirection;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record VerticalLink(
    UUID id,
    String code,
    UUID connectorId,
    UUID fromStopId,
    UUID toStopId,
    int timeSeconds,
    BigDecimal distanceMeters,
    EdgeDirection direction,
    AccessScope accessScope,
    boolean accessible,
    boolean enabled
) {
    public VerticalLink {
        id = Objects.requireNonNull(id, "id");
        connectorId = Objects.requireNonNull(connectorId, "connectorId");
        fromStopId = Objects.requireNonNull(fromStopId, "fromStopId");
        toStopId = Objects.requireNonNull(toStopId, "toStopId");
        direction = Objects.requireNonNull(direction, "direction");
        accessScope = Objects.requireNonNull(accessScope, "accessScope");
        if (timeSeconds <= 0) {
            throw new IllegalArgumentException(
                "timeSeconds must be positive: " + code
            );
        }
        if (distanceMeters == null || distanceMeters.signum() < 0) {
            throw new IllegalArgumentException(
                "distanceMeters must be non-negative: " + code
            );
        }
    }
}
