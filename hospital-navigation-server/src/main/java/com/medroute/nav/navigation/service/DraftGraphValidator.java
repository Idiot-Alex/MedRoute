package com.medroute.nav.navigation.service;

import com.medroute.nav.dto.AdminValidationResponse;
import com.medroute.nav.dto.AdminWorkspaceResponse;
import com.medroute.nav.dto.DraftGraphPayload;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

@Component
public class DraftGraphValidator {
    private static final Set<String> NODE_TYPES = Set.of(
        "normal",
        "decision",
        "poi_access",
        "connector_stop"
    );
    private static final Set<String> EDGE_TYPES = Set.of(
        "walk",
        "corridor",
        "door",
        "ramp",
        "virtual"
    );
    private static final Set<String> DIRECTIONS = Set.of("forward", "both");
    private static final Set<String> ACCESS_SCOPES = Set.of("public", "staff");
    private static final Set<String> CONNECTOR_TYPES = Set.of(
        "elevator",
        "stairs"
    );

    public void validateForSave(
        List<AdminWorkspaceResponse.Floor> floors,
        DraftGraphPayload graph
    ) {
        if (graph == null) {
            throw new IllegalArgumentException("graph is required");
        }
        Map<UUID, AdminWorkspaceResponse.Floor> floorById = index(
            floors,
            AdminWorkspaceResponse.Floor::id,
            AdminWorkspaceResponse.Floor::code,
            "floor"
        );
        Map<UUID, DraftGraphPayload.Node> nodes = index(
            graph.nodes(),
            DraftGraphPayload.Node::id,
            DraftGraphPayload.Node::code,
            "node"
        );
        Map<UUID, DraftGraphPayload.Connector> connectors = index(
            graph.connectors(),
            DraftGraphPayload.Connector::id,
            DraftGraphPayload.Connector::code,
            "connector"
        );
        Map<UUID, DraftGraphPayload.ConnectorStop> stops = index(
            graph.connectorStops(),
            DraftGraphPayload.ConnectorStop::id,
            DraftGraphPayload.ConnectorStop::code,
            "connector stop"
        );
        index(
            graph.edges(),
            DraftGraphPayload.Edge::id,
            DraftGraphPayload.Edge::code,
            "edge"
        );
        index(
            graph.pois(),
            DraftGraphPayload.Poi::id,
            DraftGraphPayload.Poi::code,
            "POI"
        );
        index(
            graph.verticalLinks(),
            DraftGraphPayload.VerticalLink::id,
            DraftGraphPayload.VerticalLink::code,
            "vertical link"
        );

        for (DraftGraphPayload.Node node : graph.nodes()) {
            requireReference(floorById, node.floorId(), "node floor");
            requireText(node.type(), "node type");
            if (!NODE_TYPES.contains(node.type())) {
                throw new IllegalArgumentException(
                    "Unsupported node type: " + node.type()
                );
            }
            requireCoordinate(node.x(), "node x");
            requireCoordinate(node.y(), "node y");
        }

        for (DraftGraphPayload.Edge edge : graph.edges()) {
            requireReference(floorById, edge.floorId(), "edge floor");
            DraftGraphPayload.Node from = requireReference(
                nodes,
                edge.fromNodeId(),
                "edge fromNodeId"
            );
            DraftGraphPayload.Node to = requireReference(
                nodes,
                edge.toNodeId(),
                "edge toNodeId"
            );
            if (from.id().equals(to.id())) {
                throw new IllegalArgumentException(
                    "Edge endpoints must be different: " + edge.code()
                );
            }
            if (
                !edge.floorId().equals(from.floorId())
                    || !edge.floorId().equals(to.floorId())
            ) {
                throw new IllegalArgumentException(
                    "A path edge cannot cross floors: " + edge.code()
                );
            }
            requirePositive(edge.timeSeconds(), "edge timeSeconds");
            requireDistance(edge.distanceMeters(), "edge distanceMeters");
            requireAllowed(edge.direction(), DIRECTIONS, "edge direction");
            requireAllowed(edge.type(), EDGE_TYPES, "edge type");
            requireAllowed(
                edge.accessScope(),
                ACCESS_SCOPES,
                "edge accessScope"
            );
        }

        for (DraftGraphPayload.Poi poi : graph.pois()) {
            requireReference(floorById, poi.floorId(), "POI floor");
            DraftGraphPayload.Node node = requireReference(
                nodes,
                poi.nodeId(),
                "POI nodeId"
            );
            if (!poi.floorId().equals(node.floorId())) {
                throw new IllegalArgumentException(
                    "POI and its node must be on the same floor: " + poi.code()
                );
            }
            requireText(poi.name(), "POI name");
            requireText(poi.category(), "POI category");
            requireAllowed(
                poi.accessScope(),
                ACCESS_SCOPES,
                "POI accessScope"
            );
            requireCoordinate(poi.x(), "POI x");
            requireCoordinate(poi.y(), "POI y");
        }

        for (DraftGraphPayload.Connector connector : graph.connectors()) {
            requireText(connector.name(), "connector name");
            requireAllowed(
                connector.type(),
                CONNECTOR_TYPES,
                "connector type"
            );
            requireAllowed(
                connector.accessScope(),
                ACCESS_SCOPES,
                "connector accessScope"
            );
        }

        Set<String> connectorFloorPairs = new HashSet<>();
        for (DraftGraphPayload.ConnectorStop stop : graph.connectorStops()) {
            requireReference(
                connectors,
                stop.connectorId(),
                "stop connectorId"
            );
            requireReference(floorById, stop.floorId(), "stop floorId");
            DraftGraphPayload.Node node = requireReference(
                nodes,
                stop.nodeId(),
                "stop nodeId"
            );
            if (!stop.floorId().equals(node.floorId())) {
                throw new IllegalArgumentException(
                    "Connector stop and node must be on the same floor: "
                        + stop.code()
                );
            }
            String pair = stop.connectorId() + ":" + stop.floorId();
            if (!connectorFloorPairs.add(pair)) {
                throw new IllegalArgumentException(
                    "A connector can have only one stop per floor"
                );
            }
        }

        for (DraftGraphPayload.VerticalLink link : graph.verticalLinks()) {
            requireReference(
                connectors,
                link.connectorId(),
                "link connectorId"
            );
            DraftGraphPayload.ConnectorStop from = requireReference(
                stops,
                link.fromStopId(),
                "link fromStopId"
            );
            DraftGraphPayload.ConnectorStop to = requireReference(
                stops,
                link.toStopId(),
                "link toStopId"
            );
            if (from.id().equals(to.id())) {
                throw new IllegalArgumentException(
                    "Vertical link endpoints must be different: " + link.code()
                );
            }
            if (
                !link.connectorId().equals(from.connectorId())
                    || !link.connectorId().equals(to.connectorId())
            ) {
                throw new IllegalArgumentException(
                    "Vertical link stops must belong to its connector: "
                        + link.code()
                );
            }
            if (from.floorId().equals(to.floorId())) {
                throw new IllegalArgumentException(
                    "Vertical link stops must be on different floors: "
                        + link.code()
                );
            }
            requirePositive(link.timeSeconds(), "link timeSeconds");
            requireDistance(link.distanceMeters(), "link distanceMeters");
            requireAllowed(link.direction(), DIRECTIONS, "link direction");
            requireAllowed(
                link.accessScope(),
                ACCESS_SCOPES,
                "link accessScope"
            );
        }
    }

    public AdminValidationResponse validateForPublish(
        AdminWorkspaceResponse workspace
    ) {
        List<AdminValidationResponse.Issue> errors = new ArrayList<>();
        List<AdminValidationResponse.Issue> warnings = new ArrayList<>();
        DraftGraphPayload graph = workspace.graph();
        Map<UUID, AdminWorkspaceResponse.Floor> floors = new LinkedHashMap<>();
        for (AdminWorkspaceResponse.Floor floor : workspace.floors()) {
            floors.put(floor.id(), floor);
        }
        Map<UUID, DraftGraphPayload.Node> nodes = new LinkedHashMap<>();
        for (DraftGraphPayload.Node node : graph.nodes()) {
            nodes.put(node.id(), node);
            checkCoordinates(node, floors.get(node.floorId()), errors);
        }
        for (DraftGraphPayload.Poi poi : graph.pois()) {
            checkCoordinates(poi, floors.get(poi.floorId()), errors);
        }
        checkEnabledNodeReferences(graph, nodes, errors);
        checkConnectors(graph, errors);
        checkIsolatedNodes(graph, nodes, errors);
        checkReachability(graph, nodes, errors, warnings);

        return new AdminValidationResponse(
            workspace.release().id(),
            workspace.release().contentRevision(),
            errors.isEmpty(),
            errors,
            warnings
        );
    }

    private void checkCoordinates(
        DraftGraphPayload.Node node,
        AdminWorkspaceResponse.Floor floor,
        List<AdminValidationResponse.Issue> errors
    ) {
        if (
            floor == null
                || node.x() > floor.mapRevision().imageWidth()
                || node.y() > floor.mapRevision().imageHeight()
        ) {
            errors.add(
                issue(
                    "NODE_OUTSIDE_MAP",
                    "path_node",
                    node.id(),
                    node.code() + " 的坐标超出楼层图片范围。"
                )
            );
        }
    }

    private void checkEnabledNodeReferences(
        DraftGraphPayload graph,
        Map<UUID, DraftGraphPayload.Node> nodes,
        List<AdminValidationResponse.Issue> errors
    ) {
        for (DraftGraphPayload.Edge edge : graph.edges()) {
            if (
                edge.enabled()
                    && (
                        isDisabled(nodes, edge.fromNodeId())
                            || isDisabled(nodes, edge.toNodeId())
                    )
            ) {
                errors.add(
                    issue(
                        "EDGE_REFERENCES_DISABLED_NODE",
                        "path_edge",
                        edge.id(),
                        edge.code() + " 引用了已停用的路径节点。"
                    )
                );
            }
        }
        for (DraftGraphPayload.Poi poi : graph.pois()) {
            if (poi.enabled() && isDisabled(nodes, poi.nodeId())) {
                errors.add(
                    issue(
                        "POI_REFERENCES_DISABLED_NODE",
                        "poi",
                        poi.id(),
                        poi.name() + " 绑定了已停用的路径节点。"
                    )
                );
            }
        }
        Map<UUID, DraftGraphPayload.Connector> connectors = new HashMap<>();
        for (DraftGraphPayload.Connector connector : graph.connectors()) {
            connectors.put(connector.id(), connector);
        }
        for (DraftGraphPayload.ConnectorStop stop : graph.connectorStops()) {
            DraftGraphPayload.Connector connector = connectors.get(
                stop.connectorId()
            );
            if (
                connector != null
                    && connector.enabled()
                    && isDisabled(nodes, stop.nodeId())
            ) {
                errors.add(
                    issue(
                        "CONNECTOR_STOP_REFERENCES_DISABLED_NODE",
                        "connector_stop",
                        stop.id(),
                        stop.code() + " 绑定了已停用的路径节点。"
                    )
                );
            }
        }
    }

    private boolean isDisabled(
        Map<UUID, DraftGraphPayload.Node> nodes,
        UUID nodeId
    ) {
        DraftGraphPayload.Node node = nodes.get(nodeId);
        return node != null && !node.enabled();
    }

    private void checkCoordinates(
        DraftGraphPayload.Poi poi,
        AdminWorkspaceResponse.Floor floor,
        List<AdminValidationResponse.Issue> errors
    ) {
        if (
            floor == null
                || poi.x() > floor.mapRevision().imageWidth()
                || poi.y() > floor.mapRevision().imageHeight()
        ) {
            errors.add(
                issue(
                    "POI_OUTSIDE_MAP",
                    "poi",
                    poi.id(),
                    poi.name() + " 的标注坐标超出楼层图片范围。"
                )
            );
        }
    }

    private void checkConnectors(
        DraftGraphPayload graph,
        List<AdminValidationResponse.Issue> errors
    ) {
        Map<UUID, List<DraftGraphPayload.ConnectorStop>> stopsByConnector =
            new HashMap<>();
        Map<UUID, Integer> linkDegree = new HashMap<>();
        for (DraftGraphPayload.ConnectorStop stop : graph.connectorStops()) {
            stopsByConnector
                .computeIfAbsent(
                    stop.connectorId(),
                    ignored -> new ArrayList<>()
                )
                .add(stop);
        }
        for (DraftGraphPayload.VerticalLink link : graph.verticalLinks()) {
            if (!link.enabled()) {
                continue;
            }
            linkDegree.merge(link.fromStopId(), 1, Integer::sum);
            linkDegree.merge(link.toStopId(), 1, Integer::sum);
        }

        for (DraftGraphPayload.Connector connector : graph.connectors()) {
            if (!connector.enabled()) {
                continue;
            }
            List<DraftGraphPayload.ConnectorStop> stops = stopsByConnector
                .getOrDefault(connector.id(), List.of());
            if (stops.size() < 2) {
                errors.add(
                    issue(
                        "CONNECTOR_HAS_TOO_FEW_STOPS",
                        "vertical_connector",
                        connector.id(),
                        connector.name() + " 至少需要配置两个楼层停靠点。"
                    )
                );
            }
            for (DraftGraphPayload.ConnectorStop stop : stops) {
                if (linkDegree.getOrDefault(stop.id(), 0) == 0) {
                    errors.add(
                        issue(
                            "CONNECTOR_STOP_NOT_LINKED",
                            "connector_stop",
                            stop.id(),
                            stop.code() + " 未配置任何可通行跨层连接。"
                        )
                    );
                }
            }
        }
    }

    private void checkIsolatedNodes(
        DraftGraphPayload graph,
        Map<UUID, DraftGraphPayload.Node> nodes,
        List<AdminValidationResponse.Issue> errors
    ) {
        Set<UUID> incident = new HashSet<>();
        for (DraftGraphPayload.Edge edge : graph.edges()) {
            if (edge.enabled()) {
                incident.add(edge.fromNodeId());
                incident.add(edge.toNodeId());
            }
        }
        Map<UUID, DraftGraphPayload.ConnectorStop> stops = new HashMap<>();
        for (DraftGraphPayload.ConnectorStop stop : graph.connectorStops()) {
            stops.put(stop.id(), stop);
        }
        for (DraftGraphPayload.VerticalLink link : graph.verticalLinks()) {
            if (!link.enabled()) {
                continue;
            }
            DraftGraphPayload.ConnectorStop from = stops.get(link.fromStopId());
            DraftGraphPayload.ConnectorStop to = stops.get(link.toStopId());
            if (from != null) {
                incident.add(from.nodeId());
            }
            if (to != null) {
                incident.add(to.nodeId());
            }
        }
        for (DraftGraphPayload.Node node : nodes.values()) {
            if (node.enabled() && !incident.contains(node.id())) {
                errors.add(
                    issue(
                        "ISOLATED_NODE",
                        "path_node",
                        node.id(),
                        node.code() + " 没有连接任何可通行路径。"
                    )
                );
            }
        }
    }

    private void checkReachability(
        DraftGraphPayload graph,
        Map<UUID, DraftGraphPayload.Node> nodes,
        List<AdminValidationResponse.Issue> errors,
        List<AdminValidationResponse.Issue> warnings
    ) {
        List<DraftGraphPayload.Poi> publicPois = graph.pois().stream()
            .filter(DraftGraphPayload.Poi::enabled)
            .filter(poi -> "public".equals(poi.accessScope()))
            .toList();
        if (publicPois.isEmpty()) {
            errors.add(
                issue(
                    "NO_PUBLIC_POI",
                    "release",
                    null,
                    "草稿中没有可用于导航的公共 POI。"
                )
            );
            return;
        }
        DraftGraphPayload.Poi start = publicPois.stream()
            .filter(poi -> "entrance".equals(poi.category()))
            .findFirst()
            .orElse(publicPois.get(0));
        if (!"entrance".equals(start.category())) {
            warnings.add(
                issue(
                    "NO_ENTRANCE_POI",
                    "release",
                    null,
                    "未找到入口 POI，可达性校验暂时从第一个公共 POI 开始。"
                )
            );
        }

        Map<UUID, DraftGraphPayload.Connector> connectors = new HashMap<>();
        for (DraftGraphPayload.Connector connector : graph.connectors()) {
            connectors.put(connector.id(), connector);
        }
        Map<UUID, DraftGraphPayload.ConnectorStop> stops = new HashMap<>();
        for (DraftGraphPayload.ConnectorStop stop : graph.connectorStops()) {
            stops.put(stop.id(), stop);
        }

        Set<UUID> normalReachable = reachable(
            start.nodeId(),
            adjacency(
                graph,
                stops,
                connectors,
                edge -> edge.enabled()
                    && "public".equals(edge.accessScope()),
                link -> link.enabled()
                    && "public".equals(link.accessScope()),
                connector -> connector.enabled()
                    && "public".equals(connector.accessScope())
            )
        );
        for (DraftGraphPayload.Poi poi : publicPois) {
            if (!normalReachable.contains(poi.nodeId())) {
                errors.add(
                    issue(
                        "POI_UNREACHABLE",
                        "poi",
                        poi.id(),
                        poi.name() + " 从入口不可达。"
                    )
                );
            }
        }

        List<DraftGraphPayload.Poi> accessiblePois = publicPois.stream()
            .filter(DraftGraphPayload.Poi::accessible)
            .toList();
        if (accessiblePois.isEmpty()) {
            return;
        }
        DraftGraphPayload.Poi accessibleStart = publicPois.stream()
            .filter(poi -> "entrance".equals(poi.category()))
            .filter(DraftGraphPayload.Poi::accessible)
            .findFirst()
            .orElse(null);
        if (accessibleStart == null) {
            errors.add(
                issue(
                    "NO_ACCESSIBLE_ENTRANCE_POI",
                    "release",
                    null,
                    "存在无障碍 POI，但未配置启用的公共无障碍入口。"
                )
            );
            return;
        }
        Set<UUID> accessibleReachable = reachable(
            accessibleStart.nodeId(),
            adjacency(
                graph,
                stops,
                connectors,
                edge -> edge.enabled()
                    && edge.accessible()
                    && "public".equals(edge.accessScope()),
                link -> link.enabled()
                    && link.accessible()
                    && "public".equals(link.accessScope()),
                connector -> connector.enabled()
                    && connector.accessible()
                    && "elevator".equals(connector.type())
                    && "public".equals(connector.accessScope())
            )
        );
        for (DraftGraphPayload.Poi poi : accessiblePois) {
            if (!accessibleReachable.contains(poi.nodeId())) {
                errors.add(
                    issue(
                        "ACCESSIBLE_POI_UNREACHABLE",
                        "poi",
                        poi.id(),
                        poi.name() + " 在无障碍模式下从入口不可达。"
                    )
                );
            }
        }
    }

    private Map<UUID, Set<UUID>> adjacency(
        DraftGraphPayload graph,
        Map<UUID, DraftGraphPayload.ConnectorStop> stops,
        Map<UUID, DraftGraphPayload.Connector> connectors,
        Predicate<DraftGraphPayload.Edge> edgeAllowed,
        Predicate<DraftGraphPayload.VerticalLink> linkAllowed,
        Predicate<DraftGraphPayload.Connector> connectorAllowed
    ) {
        Map<UUID, Set<UUID>> adjacency = new HashMap<>();
        for (DraftGraphPayload.Edge edge : graph.edges()) {
            if (!edgeAllowed.test(edge)) {
                continue;
            }
            connect(adjacency, edge.fromNodeId(), edge.toNodeId());
            if ("both".equals(edge.direction())) {
                connect(adjacency, edge.toNodeId(), edge.fromNodeId());
            }
        }
        for (DraftGraphPayload.VerticalLink link : graph.verticalLinks()) {
            DraftGraphPayload.Connector connector = connectors.get(
                link.connectorId()
            );
            DraftGraphPayload.ConnectorStop from = stops.get(
                link.fromStopId()
            );
            DraftGraphPayload.ConnectorStop to = stops.get(link.toStopId());
            if (
                connector == null
                    || from == null
                    || to == null
                    || !linkAllowed.test(link)
                    || !connectorAllowed.test(connector)
            ) {
                continue;
            }
            connect(adjacency, from.nodeId(), to.nodeId());
            if ("both".equals(link.direction())) {
                connect(adjacency, to.nodeId(), from.nodeId());
            }
        }
        return adjacency;
    }

    private void connect(
        Map<UUID, Set<UUID>> adjacency,
        UUID from,
        UUID to
    ) {
        adjacency
            .computeIfAbsent(from, ignored -> new LinkedHashSet<>())
            .add(to);
    }

    private Set<UUID> reachable(
        UUID start,
        Map<UUID, Set<UUID>> adjacency
    ) {
        Set<UUID> visited = new LinkedHashSet<>();
        Queue<UUID> queue = new ArrayDeque<>();
        visited.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            UUID current = queue.remove();
            for (UUID next : adjacency.getOrDefault(current, Set.of())) {
                if (visited.add(next)) {
                    queue.add(next);
                }
            }
        }
        return visited;
    }

    private AdminValidationResponse.Issue issue(
        String code,
        String elementType,
        UUID elementId,
        String message
    ) {
        return new AdminValidationResponse.Issue(
            code,
            elementType,
            elementId,
            message
        );
    }

    private <T> Map<UUID, T> index(
        List<T> values,
        java.util.function.Function<T, UUID> idFunction,
        java.util.function.Function<T, String> codeFunction,
        String type
    ) {
        Map<UUID, T> result = new LinkedHashMap<>();
        Set<String> codes = new HashSet<>();
        for (T value : values) {
            UUID id = idFunction.apply(value);
            String code = requireText(codeFunction.apply(value), type + " code");
            if (id == null) {
                throw new IllegalArgumentException(type + " id is required");
            }
            if (result.putIfAbsent(id, value) != null) {
                throw new IllegalArgumentException(
                    "Duplicate " + type + " id: " + id
                );
            }
            if (!codes.add(code)) {
                throw new IllegalArgumentException(
                    "Duplicate " + type + " code: " + code
                );
            }
        }
        return result;
    }

    private <K, V> V requireReference(
        Map<K, V> values,
        K id,
        String field
    ) {
        if (id == null || !values.containsKey(id)) {
            throw new IllegalArgumentException(
                field + " references an unknown id: " + id
            );
        }
        return values.get(id);
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private void requireAllowed(
        String value,
        Set<String> allowed,
        String field
    ) {
        requireText(value, field);
        if (!allowed.contains(value)) {
            throw new IllegalArgumentException(
                "Unsupported " + field + ": " + value
            );
        }
    }

    private void requireCoordinate(double value, String field) {
        if (!Double.isFinite(value) || value < 0) {
            throw new IllegalArgumentException(
                field + " must be a finite non-negative number"
            );
        }
    }

    private void requirePositive(int value, String field) {
        if (value <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
    }

    private void requireDistance(BigDecimal value, String field) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(
                field + " must be non-negative"
            );
        }
    }
}
