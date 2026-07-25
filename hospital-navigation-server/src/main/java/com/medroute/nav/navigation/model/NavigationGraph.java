package com.medroute.nav.navigation.model;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record NavigationGraph(
    UUID releaseId,
    UUID buildingId,
    Map<UUID, FloorSnapshot> floors,
    Map<UUID, GraphNode> nodes,
    Map<UUID, List<GraphArc>> outgoing,
    Map<UUID, PoiSnapshot> pois,
    Map<UUID, ConnectorSnapshot> connectors,
    Map<UUID, ConnectorStop> connectorStops,
    Map<UUID, VerticalLink> verticalLinks
) {
    public NavigationGraph {
        floors = Map.copyOf(floors);
        nodes = Map.copyOf(nodes);
        outgoing = outgoing.entrySet().stream().collect(
            java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                entry -> List.copyOf(entry.getValue())
            )
        );
        pois = Map.copyOf(pois);
        connectors = Map.copyOf(connectors);
        connectorStops = Map.copyOf(connectorStops);
        verticalLinks = Map.copyOf(verticalLinks);
    }

    public NavigationGraph(
        UUID releaseId,
        UUID buildingId,
        Map<UUID, FloorSnapshot> floors,
        Map<UUID, GraphNode> nodes,
        Map<UUID, List<GraphArc>> outgoing,
        Map<UUID, PoiSnapshot> pois,
        Map<UUID, ConnectorSnapshot> connectors
    ) {
        this(
            releaseId,
            buildingId,
            floors,
            nodes,
            outgoing,
            pois,
            connectors,
            Map.of(),
            Map.of()
        );
    }

    public List<GraphArc> outgoingFrom(UUID nodeId) {
        return outgoing.getOrDefault(nodeId, List.of());
    }
}
