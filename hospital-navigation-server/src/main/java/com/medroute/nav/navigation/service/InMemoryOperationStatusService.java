package com.medroute.nav.navigation.service;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryOperationStatusService implements OperationStatusProvider {
    private final Set<UUID> closedConnectorIds = ConcurrentHashMap.newKeySet();
    private final Set<UUID> closedElementIds = ConcurrentHashMap.newKeySet();

    public Set<UUID> closedConnectorIds() {
        return Set.copyOf(closedConnectorIds);
    }

    public Set<UUID> closedElementIds() {
        return Set.copyOf(closedElementIds);
    }

    @Override
    public OperationStatusSnapshot status(
        UUID buildingId,
        UUID releaseId,
        Instant effectiveAt
    ) {
        return new OperationStatusSnapshot(
            closedElementIds(),
            closedConnectorIds()
        );
    }

    public void closeConnector(UUID connectorId) {
        closedConnectorIds.add(connectorId);
    }

    public void openConnector(UUID connectorId) {
        closedConnectorIds.remove(connectorId);
    }

    public void closeElement(UUID elementId) {
        closedElementIds.add(elementId);
    }

    public void openElement(UUID elementId) {
        closedElementIds.remove(elementId);
    }

    public void reset() {
        closedConnectorIds.clear();
        closedElementIds.clear();
    }
}
