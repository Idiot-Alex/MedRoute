package com.medroute.nav.navigation.model;

import com.medroute.nav.model.EdgeDirection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class GraphArcFactory {
    private GraphArcFactory() {
    }

    public static void addHorizontal(
        Map<UUID, List<GraphArc>> outgoing,
        GraphArc forward,
        EdgeDirection direction
    ) {
        add(outgoing, forward);
        if (direction == EdgeDirection.BOTH) {
            add(
                outgoing,
                new GraphArc(
                    forward.elementId(),
                    forward.code(),
                    forward.toNodeId(),
                    forward.fromNodeId(),
                    forward.timeSeconds(),
                    forward.distanceMeters(),
                    forward.type(),
                    forward.accessScope(),
                    forward.accessible(),
                    forward.connectorId(),
                    forward.enabled()
                )
            );
        }
    }

    public static void addVertical(
        Map<UUID, List<GraphArc>> outgoing,
        VerticalLink link,
        VerticalConnector connector,
        ConnectorStop fromStop,
        ConnectorStop toStop
    ) {
        if (
            !connector.id().equals(fromStop.connectorId())
                || !connector.id().equals(toStop.connectorId())
        ) {
            throw new IllegalStateException(
                "Vertical link stops belong to another connector: "
                    + link.code()
            );
        }

        add(
            outgoing,
            verticalArc(
                link,
                connector,
                fromStop.nodeId(),
                toStop.nodeId()
            )
        );
        if (link.direction() == EdgeDirection.BOTH) {
            add(
                outgoing,
                verticalArc(
                    link,
                    connector,
                    toStop.nodeId(),
                    fromStop.nodeId()
                )
            );
        }
    }

    public static GraphArc verticalArc(
        VerticalLink link,
        VerticalConnector connector,
        UUID fromNodeId,
        UUID toNodeId
    ) {
        return new GraphArc(
            link.id(),
            link.code(),
            fromNodeId,
            toNodeId,
            link.timeSeconds(),
            link.distanceMeters(),
            connector.type(),
            effectiveAccessScope(link, connector),
            link.accessible() && connector.accessible(),
            connector.id(),
            link.enabled() && connector.enabled()
        );
    }

    private static AccessScope effectiveAccessScope(
        VerticalLink link,
        VerticalConnector connector
    ) {
        return link.accessScope() == AccessScope.STAFF
                || connector.accessScope() == AccessScope.STAFF
            ? AccessScope.STAFF
            : AccessScope.PUBLIC;
    }

    private static void add(
        Map<UUID, List<GraphArc>> outgoing,
        GraphArc arc
    ) {
        outgoing
            .computeIfAbsent(arc.fromNodeId(), ignored -> new ArrayList<>())
            .add(arc);
    }
}
