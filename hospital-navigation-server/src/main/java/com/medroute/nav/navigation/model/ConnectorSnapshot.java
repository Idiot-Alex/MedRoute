package com.medroute.nav.navigation.model;

import java.util.Set;
import java.util.UUID;

public record ConnectorSnapshot(
    UUID id,
    String code,
    String name,
    ArcType type,
    boolean accessible,
    Set<UUID> floorIds
) {
    public ConnectorSnapshot {
        floorIds = Set.copyOf(floorIds);
    }
}
