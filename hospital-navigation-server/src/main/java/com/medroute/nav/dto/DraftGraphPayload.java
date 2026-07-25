package com.medroute.nav.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record DraftGraphPayload(
    List<Node> nodes,
    List<Edge> edges,
    List<Poi> pois,
    List<Connector> connectors,
    List<ConnectorStop> connectorStops,
    List<VerticalLink> verticalLinks
) {
    public DraftGraphPayload {
        nodes = immutable(nodes);
        edges = immutable(edges);
        pois = immutable(pois);
        connectors = immutable(connectors);
        connectorStops = immutable(connectorStops);
        verticalLinks = immutable(verticalLinks);
    }

    private static <T> List<T> immutable(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    public record Node(
        UUID id,
        String code,
        UUID floorId,
        double x,
        double y,
        String type,
        boolean enabled
    ) {
    }

    public record Edge(
        UUID id,
        String code,
        UUID floorId,
        UUID fromNodeId,
        UUID toNodeId,
        int timeSeconds,
        BigDecimal distanceMeters,
        String direction,
        String type,
        String accessScope,
        boolean accessible,
        boolean enabled
    ) {
    }

    public record Poi(
        UUID id,
        String code,
        String name,
        String category,
        UUID floorId,
        UUID nodeId,
        double x,
        double y,
        String accessScope,
        boolean accessible,
        boolean enabled,
        List<String> keywords
    ) {
        public Poi {
            keywords = keywords == null ? List.of() : List.copyOf(keywords);
        }
    }

    public record Connector(
        UUID id,
        String code,
        String name,
        String type,
        String accessScope,
        boolean accessible,
        boolean enabled
    ) {
    }

    public record ConnectorStop(
        UUID id,
        String code,
        UUID connectorId,
        UUID floorId,
        UUID nodeId
    ) {
    }

    public record VerticalLink(
        UUID id,
        String code,
        UUID connectorId,
        UUID fromStopId,
        UUID toStopId,
        int timeSeconds,
        BigDecimal distanceMeters,
        String direction,
        String accessScope,
        boolean accessible,
        boolean enabled
    ) {
    }
}
