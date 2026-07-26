package com.medroute.nav.navigation.service;

import com.medroute.nav.dto.AdminWorkspaceResponse;
import com.medroute.nav.dto.DraftGraphPayload;
import com.medroute.nav.model.EdgeDirection;
import com.medroute.nav.navigation.model.AccessScope;
import com.medroute.nav.navigation.model.ArcType;
import com.medroute.nav.navigation.model.ConnectorSnapshot;
import com.medroute.nav.navigation.model.ConnectorStop;
import com.medroute.nav.navigation.model.FloorSnapshot;
import com.medroute.nav.navigation.model.GraphArc;
import com.medroute.nav.navigation.model.GraphArcFactory;
import com.medroute.nav.navigation.model.GraphNode;
import com.medroute.nav.navigation.model.NavigationGraph;
import com.medroute.nav.navigation.model.PoiSnapshot;
import com.medroute.nav.navigation.model.VerticalConnector;
import com.medroute.nav.navigation.model.VerticalLink;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class DraftNavigationGraphFactory {
    public NavigationGraph create(AdminWorkspaceResponse workspace) {
        DraftGraphPayload graph = workspace.graph();
        Map<UUID, FloorSnapshot> floors = floors(workspace.floors());
        Map<UUID, DraftGraphPayload.Node> sourceNodes = new LinkedHashMap<>();
        Map<UUID, GraphNode> nodes = new LinkedHashMap<>();
        for (DraftGraphPayload.Node node : graph.nodes()) {
            sourceNodes.put(node.id(), node);
            if (node.enabled()) {
                nodes.put(
                    node.id(),
                    new GraphNode(
                        node.id(),
                        node.code(),
                        node.floorId(),
                        node.x(),
                        node.y(),
                        node.type()
                    )
                );
            }
        }

        Map<UUID, List<GraphArc>> outgoing = new LinkedHashMap<>();
        addHorizontalArcs(graph.edges(), sourceNodes, outgoing);

        Map<UUID, VerticalConnector> connectorModels = connectorModels(
            graph.connectors()
        );
        Map<UUID, ConnectorStop> stops = stops(graph.connectorStops());
        Map<UUID, VerticalLink> links = links(graph.verticalLinks());
        addVerticalArcs(
            outgoing,
            connectorModels,
            stops,
            links,
            sourceNodes
        );

        return new NavigationGraph(
            workspace.release().id(),
            workspace.building().id(),
            floors,
            nodes,
            outgoing,
            pois(graph.pois()),
            connectorSnapshots(connectorModels, stops),
            stops,
            links
        );
    }

    private Map<UUID, FloorSnapshot> floors(
        List<AdminWorkspaceResponse.Floor> source
    ) {
        Map<UUID, FloorSnapshot> floors = new LinkedHashMap<>();
        for (AdminWorkspaceResponse.Floor floor : source) {
            AdminWorkspaceResponse.MapRevision map = floor.mapRevision();
            floors.put(
                floor.id(),
                new FloorSnapshot(
                    floor.id(),
                    floor.code(),
                    floor.name(),
                    floor.levelNo(),
                    map.id(),
                    map.revisionNo(),
                    map.imageUrl(),
                    map.imageWidth(),
                    map.imageHeight()
                )
            );
        }
        return floors;
    }

    private void addHorizontalArcs(
        List<DraftGraphPayload.Edge> edges,
        Map<UUID, DraftGraphPayload.Node> nodes,
        Map<UUID, List<GraphArc>> outgoing
    ) {
        for (DraftGraphPayload.Edge edge : edges) {
            DraftGraphPayload.Node from = nodes.get(edge.fromNodeId());
            DraftGraphPayload.Node to = nodes.get(edge.toNodeId());
            if (
                from == null
                    || to == null
                    || !from.enabled()
                    || !to.enabled()
            ) {
                continue;
            }
            GraphArc forward = new GraphArc(
                edge.id(),
                edge.code(),
                edge.fromNodeId(),
                edge.toNodeId(),
                edge.timeSeconds(),
                edge.distanceMeters(),
                ArcType.WALK,
                accessScope(edge.accessScope()),
                edge.accessible(),
                null,
                edge.enabled()
            );
            GraphArcFactory.addHorizontal(
                outgoing,
                forward,
                EdgeDirection.from(edge.direction())
            );
        }
    }

    private Map<UUID, PoiSnapshot> pois(
        List<DraftGraphPayload.Poi> source
    ) {
        Map<UUID, PoiSnapshot> pois = new LinkedHashMap<>();
        for (DraftGraphPayload.Poi poi : source) {
            if (!poi.enabled() || !"public".equals(poi.accessScope())) {
                continue;
            }
            pois.put(
                poi.id(),
                new PoiSnapshot(
                    poi.id(),
                    poi.code(),
                    poi.name(),
                    poi.category(),
                    poi.floorId(),
                    poi.nodeId(),
                    poi.x(),
                    poi.y(),
                    poi.accessible(),
                    poi.keywords()
                )
            );
        }
        return pois;
    }

    private Map<UUID, VerticalConnector> connectorModels(
        List<DraftGraphPayload.Connector> source
    ) {
        Map<UUID, VerticalConnector> connectors = new LinkedHashMap<>();
        for (DraftGraphPayload.Connector connector : source) {
            connectors.put(
                connector.id(),
                new VerticalConnector(
                    connector.id(),
                    connector.code(),
                    connector.name(),
                    arcType(connector.type()),
                    accessScope(connector.accessScope()),
                    connector.accessible(),
                    connector.enabled()
                )
            );
        }
        return connectors;
    }

    private Map<UUID, ConnectorStop> stops(
        List<DraftGraphPayload.ConnectorStop> source
    ) {
        Map<UUID, ConnectorStop> stops = new LinkedHashMap<>();
        for (DraftGraphPayload.ConnectorStop stop : source) {
            stops.put(
                stop.id(),
                new ConnectorStop(
                    stop.id(),
                    stop.code(),
                    stop.connectorId(),
                    stop.floorId(),
                    stop.nodeId()
                )
            );
        }
        return stops;
    }

    private Map<UUID, VerticalLink> links(
        List<DraftGraphPayload.VerticalLink> source
    ) {
        Map<UUID, VerticalLink> links = new LinkedHashMap<>();
        for (DraftGraphPayload.VerticalLink link : source) {
            links.put(
                link.id(),
                new VerticalLink(
                    link.id(),
                    link.code(),
                    link.connectorId(),
                    link.fromStopId(),
                    link.toStopId(),
                    link.timeSeconds(),
                    link.distanceMeters(),
                    EdgeDirection.from(link.direction()),
                    accessScope(link.accessScope()),
                    link.accessible(),
                    link.enabled()
                )
            );
        }
        return links;
    }

    private void addVerticalArcs(
        Map<UUID, List<GraphArc>> outgoing,
        Map<UUID, VerticalConnector> connectors,
        Map<UUID, ConnectorStop> stops,
        Map<UUID, VerticalLink> links,
        Map<UUID, DraftGraphPayload.Node> nodes
    ) {
        for (VerticalLink link : links.values()) {
            VerticalConnector connector = connectors.get(link.connectorId());
            ConnectorStop from = stops.get(link.fromStopId());
            ConnectorStop to = stops.get(link.toStopId());
            if (
                connector == null
                    || from == null
                    || to == null
                    || !enabledNode(nodes, from.nodeId())
                    || !enabledNode(nodes, to.nodeId())
            ) {
                continue;
            }
            GraphArcFactory.addVertical(
                outgoing,
                link,
                connector,
                from,
                to
            );
        }
    }

    private boolean enabledNode(
        Map<UUID, DraftGraphPayload.Node> nodes,
        UUID nodeId
    ) {
        DraftGraphPayload.Node node = nodes.get(nodeId);
        return node != null && node.enabled();
    }

    private Map<UUID, ConnectorSnapshot> connectorSnapshots(
        Map<UUID, VerticalConnector> connectors,
        Map<UUID, ConnectorStop> stops
    ) {
        Map<UUID, Set<UUID>> floorIds = new LinkedHashMap<>();
        for (ConnectorStop stop : stops.values()) {
            floorIds
                .computeIfAbsent(
                    stop.connectorId(),
                    ignored -> new LinkedHashSet<>()
                )
                .add(stop.floorId());
        }
        Map<UUID, ConnectorSnapshot> snapshots = new LinkedHashMap<>();
        for (VerticalConnector connector : connectors.values()) {
            snapshots.put(
                connector.id(),
                new ConnectorSnapshot(
                    connector.id(),
                    connector.code(),
                    connector.name(),
                    connector.type(),
                    connector.accessible(),
                    floorIds.getOrDefault(connector.id(), Set.of())
                )
            );
        }
        return snapshots;
    }

    private AccessScope accessScope(String value) {
        return AccessScope.valueOf(value.toUpperCase(Locale.ROOT));
    }

    private ArcType arcType(String value) {
        return switch (value) {
            case "elevator" -> ArcType.ELEVATOR;
            case "stairs" -> ArcType.STAIRS;
            default -> throw new IllegalArgumentException(
                "Unknown connector type: " + value
            );
        };
    }
}
