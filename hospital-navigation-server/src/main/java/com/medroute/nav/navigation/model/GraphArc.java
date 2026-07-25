package com.medroute.nav.navigation.model;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record GraphArc(
    UUID elementId,
    String code,
    UUID fromNodeId,
    UUID toNodeId,
    int timeSeconds,
    BigDecimal distanceMeters,
    ArcType type,
    AccessScope accessScope,
    boolean accessible,
    UUID connectorId,
    boolean enabled
) {
    public GraphArc {
        elementId = Objects.requireNonNull(elementId, "elementId");
        fromNodeId = Objects.requireNonNull(fromNodeId, "fromNodeId");
        toNodeId = Objects.requireNonNull(toNodeId, "toNodeId");
        type = Objects.requireNonNull(type, "type");
        accessScope = Objects.requireNonNull(accessScope, "accessScope");
        if (type.vertical() && connectorId == null) {
            throw new IllegalArgumentException(
                "Vertical arc must reference a connector: " + code
            );
        }
        if (timeSeconds <= 0) {
            throw new IllegalArgumentException("timeSeconds must be positive: " + code);
        }
        if (distanceMeters == null || distanceMeters.signum() < 0) {
            throw new IllegalArgumentException(
                "distanceMeters must be non-negative: " + code
            );
        }
    }

    public boolean vertical() {
        return type.vertical();
    }
}
