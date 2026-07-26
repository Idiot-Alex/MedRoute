package com.medroute.nav.navigation.service;

import com.medroute.nav.model.RouteMode;
import com.medroute.nav.navigation.model.AccessScope;
import com.medroute.nav.navigation.model.ArcType;
import com.medroute.nav.navigation.model.ConnectorSnapshot;
import com.medroute.nav.navigation.model.GraphArc;
import com.medroute.nav.navigation.model.NavigationGraph;

import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

public final class PublicRouteArcPolicy {
    private PublicRouteArcPolicy() {
    }

    public static Predicate<GraphArc> allowed(
        NavigationGraph graph,
        RouteMode routeMode,
        Set<UUID> closedElements,
        Set<UUID> closedConnectors
    ) {
        if (routeMode == null || !routeMode.supported()) {
            throw new IllegalArgumentException(
                "A supported public route mode is required"
            );
        }
        Set<UUID> unavailableElements = closedElements == null
            ? Set.of()
            : Set.copyOf(closedElements);
        Set<UUID> unavailableConnectors = closedConnectors == null
            ? Set.of()
            : Set.copyOf(closedConnectors);

        return arc -> {
            if (
                !arc.enabled()
                    || arc.accessScope() != AccessScope.PUBLIC
                    || unavailableElements.contains(arc.elementId())
                    || (
                        arc.connectorId() != null
                            && unavailableConnectors.contains(
                                arc.connectorId()
                            )
                    )
            ) {
                return false;
            }
            if (routeMode != RouteMode.ACCESSIBLE) {
                return true;
            }
            if (!arc.accessible() || arc.type() == ArcType.STAIRS) {
                return false;
            }
            ConnectorSnapshot connector = arc.connectorId() == null
                ? null
                : graph.connectors().get(arc.connectorId());
            return connector == null || connector.accessible();
        };
    }
}
