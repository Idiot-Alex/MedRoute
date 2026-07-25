package com.medroute.nav.navigation.model;

import java.util.Objects;
import java.util.UUID;

public record VerticalConnector(
    UUID id,
    String code,
    String name,
    ArcType type,
    AccessScope accessScope,
    boolean accessible,
    boolean enabled
) {
    public VerticalConnector {
        id = Objects.requireNonNull(id, "id");
        type = Objects.requireNonNull(type, "type");
        accessScope = Objects.requireNonNull(accessScope, "accessScope");
        if (!type.vertical()) {
            throw new IllegalArgumentException(
                "Vertical connector type must be elevator or stairs: " + code
            );
        }
    }
}
