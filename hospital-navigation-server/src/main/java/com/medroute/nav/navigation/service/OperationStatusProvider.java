package com.medroute.nav.navigation.service;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public interface OperationStatusProvider {
    OperationStatusSnapshot status(
        UUID buildingId,
        UUID releaseId,
        Instant effectiveAt
    );

    record OperationStatusSnapshot(
        Set<UUID> closedElementIds,
        Set<UUID> closedConnectorIds
    ) {
        public OperationStatusSnapshot {
            closedElementIds = Set.copyOf(closedElementIds);
            closedConnectorIds = Set.copyOf(closedConnectorIds);
        }

        public static OperationStatusSnapshot empty() {
            return new OperationStatusSnapshot(Set.of(), Set.of());
        }
    }
}
