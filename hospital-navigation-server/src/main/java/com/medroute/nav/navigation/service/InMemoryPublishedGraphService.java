package com.medroute.nav.navigation.service;

import com.medroute.nav.navigation.model.AccessScope;
import com.medroute.nav.navigation.model.ArcType;
import com.medroute.nav.navigation.model.ConnectorSnapshot;
import com.medroute.nav.navigation.model.ConnectorStop;
import com.medroute.nav.navigation.model.FloorSnapshot;
import com.medroute.nav.navigation.model.GraphArc;
import com.medroute.nav.navigation.model.GraphNode;
import com.medroute.nav.navigation.model.NavigationGraph;
import com.medroute.nav.navigation.model.PoiSnapshot;
import com.medroute.nav.navigation.model.VerticalConnector;
import com.medroute.nav.navigation.model.VerticalLink;
import com.medroute.nav.model.EdgeDirection;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable stage-1 fixture. It deliberately models vertical links as explicit graph arcs:
 * sharing a connector code never creates an implicit link.
 */
@Service
public class InMemoryPublishedGraphService {
    public static final UUID HOSPITAL_ID = uuid("00000001");
    public static final UUID BUILDING_ID = uuid("00000100");
    public static final UUID RELEASE_ID = uuid("00000200");

    public static final UUID FLOOR_1_ID = uuid("00001001");
    public static final UUID FLOOR_2_ID = uuid("00001002");
    public static final UUID FLOOR_3_ID = uuid("00001003");

    public static final UUID POI_ENTRANCE_ID = uuid("00003001");
    public static final UUID POI_REGISTRATION_ID = uuid("00003002");
    public static final UUID POI_CLINIC_2F_ID = uuid("00003003");
    public static final UUID POI_PHARMACY_3F_ID = uuid("00003004");

    public static final UUID ELEVATOR_A_ID = uuid("00004001");
    public static final UUID ELEVATOR_B_ID = uuid("00004002");
    public static final UUID STAIRS_ID = uuid("00004003");

    public static final Instant PUBLISHED_AT = Instant.parse("2026-07-25T06:00:00Z");

    private static final UUID NODE_1F_ENTRANCE = uuid("00002001");
    private static final UUID NODE_1F_HUB = uuid("00002002");
    private static final UUID NODE_1F_ELEVATOR_A = uuid("00002003");
    private static final UUID NODE_1F_ELEVATOR_B = uuid("00002004");
    private static final UUID NODE_1F_STAIRS = uuid("00002005");
    private static final UUID NODE_1F_ONE_WAY = uuid("00002006");

    private static final UUID NODE_2F_HUB = uuid("00002011");
    private static final UUID NODE_2F_ELEVATOR_B = uuid("00002012");
    private static final UUID NODE_2F_STAIRS = uuid("00002013");
    private static final UUID NODE_2F_CLINIC = uuid("00002014");

    private static final UUID NODE_3F_HUB = uuid("00002021");
    private static final UUID NODE_3F_ELEVATOR_A = uuid("00002022");
    private static final UUID NODE_3F_ELEVATOR_B = uuid("00002023");
    private static final UUID NODE_3F_STAIRS = uuid("00002024");
    private static final UUID NODE_3F_PHARMACY = uuid("00002025");

    private final NavigationGraph graph;

    public InMemoryPublishedGraphService() {
        this(buildGraph());
    }

    public InMemoryPublishedGraphService(NavigationGraph graph) {
        this.graph = Objects.requireNonNull(graph, "graph");
    }

    public NavigationGraph activeGraph(UUID buildingId) {
        if (!graph.buildingId().equals(buildingId)) {
            throw new NavigationResourceNotFoundException(
                "Unknown buildingId: " + buildingId
            );
        }
        return graph;
    }

    public NavigationGraph publishedGraph(UUID releaseId) {
        if (!graph.releaseId().equals(releaseId)) {
            throw new NavigationResourceNotFoundException(
                "Unknown releaseId: " + releaseId
            );
        }
        return graph;
    }

    public UUID activeReleaseId(UUID buildingId) {
        return activeGraph(buildingId).releaseId();
    }

    public static NavigationGraph buildGraph() {
        Map<UUID, FloorSnapshot> floors = new LinkedHashMap<>();
        floors.put(
            FLOOR_1_ID,
            floor(FLOOR_1_ID, "1F", 1, uuid("00001101"), "/maps/outpatient/1f.png")
        );
        floors.put(
            FLOOR_2_ID,
            floor(FLOOR_2_ID, "2F", 2, uuid("00001102"), "/maps/outpatient/2f.png")
        );
        floors.put(
            FLOOR_3_ID,
            floor(FLOOR_3_ID, "3F", 3, uuid("00001103"), "/maps/outpatient/3f.png")
        );

        Map<UUID, GraphNode> nodes = new LinkedHashMap<>();
        addNode(nodes, NODE_1F_ENTRANCE, "N-1F-ENTRANCE", FLOOR_1_ID, 100, 300, "entrance");
        addNode(nodes, NODE_1F_HUB, "N-1F-HUB", FLOOR_1_ID, 350, 300, "normal");
        addNode(nodes, NODE_1F_ELEVATOR_A, "N-1F-ELEV-A", FLOOR_1_ID, 560, 220, "elevator");
        addNode(nodes, NODE_1F_ELEVATOR_B, "N-1F-ELEV-B", FLOOR_1_ID, 560, 320, "elevator");
        addNode(nodes, NODE_1F_STAIRS, "N-1F-STAIRS", FLOOR_1_ID, 560, 420, "stairs");
        addNode(nodes, NODE_1F_ONE_WAY, "N-1F-ONE-WAY", FLOOR_1_ID, 350, 500, "normal");

        addNode(nodes, NODE_2F_HUB, "N-2F-HUB", FLOOR_2_ID, 350, 300, "normal");
        addNode(nodes, NODE_2F_ELEVATOR_B, "N-2F-ELEV-B", FLOOR_2_ID, 560, 320, "elevator");
        addNode(nodes, NODE_2F_STAIRS, "N-2F-STAIRS", FLOOR_2_ID, 560, 420, "stairs");
        addNode(nodes, NODE_2F_CLINIC, "N-2F-CLINIC", FLOOR_2_ID, 150, 220, "normal");

        addNode(nodes, NODE_3F_HUB, "N-3F-HUB", FLOOR_3_ID, 350, 300, "normal");
        addNode(nodes, NODE_3F_ELEVATOR_A, "N-3F-ELEV-A", FLOOR_3_ID, 560, 220, "elevator");
        addNode(nodes, NODE_3F_ELEVATOR_B, "N-3F-ELEV-B", FLOOR_3_ID, 560, 320, "elevator");
        addNode(nodes, NODE_3F_STAIRS, "N-3F-STAIRS", FLOOR_3_ID, 560, 420, "stairs");
        addNode(nodes, NODE_3F_PHARMACY, "N-3F-PHARMACY", FLOOR_3_ID, 150, 220, "normal");

        Map<UUID, List<GraphArc>> outgoing = new LinkedHashMap<>();
        addBoth(outgoing, "EDGE-1F-ENTRANCE-HUB", "00005001", NODE_1F_ENTRANCE, NODE_1F_HUB, 30, 30, ArcType.WALK, true, null);
        addBoth(outgoing, "EDGE-1F-HUB-ELEV-A", "00005002", NODE_1F_HUB, NODE_1F_ELEVATOR_A, 15, 15, ArcType.WALK, true, null);
        addBoth(outgoing, "EDGE-1F-HUB-ELEV-B", "00005003", NODE_1F_HUB, NODE_1F_ELEVATOR_B, 20, 20, ArcType.WALK, true, null);
        addBoth(outgoing, "EDGE-1F-HUB-STAIRS", "00005004", NODE_1F_HUB, NODE_1F_STAIRS, 8, 8, ArcType.WALK, true, null);
        addForward(outgoing, "EDGE-1F-ONE-WAY", "00005005", NODE_1F_HUB, NODE_1F_ONE_WAY, 10, 10, ArcType.WALK, true, null);

        addBoth(outgoing, "EDGE-2F-ELEV-B-HUB", "00005011", NODE_2F_ELEVATOR_B, NODE_2F_HUB, 20, 20, ArcType.WALK, true, null);
        addBoth(outgoing, "EDGE-2F-STAIRS-HUB", "00005012", NODE_2F_STAIRS, NODE_2F_HUB, 8, 8, ArcType.WALK, true, null);
        addBoth(outgoing, "EDGE-2F-HUB-CLINIC", "00005013", NODE_2F_HUB, NODE_2F_CLINIC, 15, 15, ArcType.WALK, true, null);

        addBoth(outgoing, "EDGE-3F-ELEV-A-HUB", "00005021", NODE_3F_ELEVATOR_A, NODE_3F_HUB, 15, 15, ArcType.WALK, true, null);
        addBoth(outgoing, "EDGE-3F-ELEV-B-HUB", "00005022", NODE_3F_ELEVATOR_B, NODE_3F_HUB, 20, 20, ArcType.WALK, true, null);
        addBoth(outgoing, "EDGE-3F-STAIRS-HUB", "00005023", NODE_3F_STAIRS, NODE_3F_HUB, 8, 8, ArcType.WALK, true, null);
        addBoth(outgoing, "EDGE-3F-HUB-PHARMACY", "00005024", NODE_3F_HUB, NODE_3F_PHARMACY, 15, 15, ArcType.WALK, true, null);

        Map<UUID, VerticalConnector> verticalConnectors =
            buildVerticalConnectors();
        Map<UUID, ConnectorStop> connectorStops = buildConnectorStops();
        Map<UUID, VerticalLink> verticalLinks = buildVerticalLinks();
        verticalLinks.values().forEach(
            link -> addVerticalLink(
                outgoing,
                link,
                connectorStops,
                verticalConnectors
            )
        );

        Map<UUID, PoiSnapshot> pois = new LinkedHashMap<>();
        addPoi(pois, POI_ENTRANCE_ID, "P-ENTRANCE", "入口", "entrance", FLOOR_1_ID, NODE_1F_ENTRANCE, 100, 300, "大门");
        addPoi(pois, POI_REGISTRATION_ID, "P-REGISTRATION", "挂号处", "window", FLOOR_1_ID, NODE_1F_HUB, 350, 300, "挂号", "取号");
        addPoi(pois, POI_CLINIC_2F_ID, "P-CLINIC-2F", "二层诊室", "department", FLOOR_2_ID, NODE_2F_CLINIC, 150, 220, "诊室", "二楼");
        addPoi(pois, POI_PHARMACY_3F_ID, "P-PHARMACY-3F", "三层药房", "pharmacy", FLOOR_3_ID, NODE_3F_PHARMACY, 150, 220, "药房", "取药");

        Map<UUID, ConnectorSnapshot> connectors = new LinkedHashMap<>();
        connectors.put(
            ELEVATOR_A_ID,
            new ConnectorSnapshot(
                ELEVATOR_A_ID,
                "ELEV-A",
                "A 电梯",
                ArcType.ELEVATOR,
                true,
                Set.of(FLOOR_1_ID, FLOOR_3_ID)
            )
        );
        connectors.put(
            ELEVATOR_B_ID,
            new ConnectorSnapshot(
                ELEVATOR_B_ID,
                "ELEV-B",
                "B 电梯",
                ArcType.ELEVATOR,
                true,
                Set.of(FLOOR_1_ID, FLOOR_2_ID, FLOOR_3_ID)
            )
        );
        connectors.put(
            STAIRS_ID,
            new ConnectorSnapshot(
                STAIRS_ID,
                "STAIRS-A",
                "A 楼梯",
                ArcType.STAIRS,
                false,
                Set.of(FLOOR_1_ID, FLOOR_2_ID, FLOOR_3_ID)
            )
        );

        return new NavigationGraph(
            RELEASE_ID,
            BUILDING_ID,
            floors,
            nodes,
            outgoing,
            pois,
            connectors,
            connectorStops,
            verticalLinks
        );
    }

    private static Map<UUID, VerticalConnector> buildVerticalConnectors() {
        Map<UUID, VerticalConnector> connectors = new LinkedHashMap<>();
        connectors.put(
            ELEVATOR_A_ID,
            new VerticalConnector(
                ELEVATOR_A_ID,
                "ELEV-A",
                "A 电梯",
                ArcType.ELEVATOR,
                AccessScope.PUBLIC,
                true,
                true
            )
        );
        connectors.put(
            ELEVATOR_B_ID,
            new VerticalConnector(
                ELEVATOR_B_ID,
                "ELEV-B",
                "B 电梯",
                ArcType.ELEVATOR,
                AccessScope.PUBLIC,
                true,
                true
            )
        );
        connectors.put(
            STAIRS_ID,
            new VerticalConnector(
                STAIRS_ID,
                "STAIRS-A",
                "A 楼梯",
                ArcType.STAIRS,
                AccessScope.PUBLIC,
                false,
                true
            )
        );
        return connectors;
    }

    private static Map<UUID, ConnectorStop> buildConnectorStops() {
        Map<UUID, ConnectorStop> stops = new LinkedHashMap<>();
        addStop(stops, "00004101", "STOP-ELEV-A-1F", ELEVATOR_A_ID, FLOOR_1_ID, NODE_1F_ELEVATOR_A);
        addStop(stops, "00004102", "STOP-ELEV-A-3F", ELEVATOR_A_ID, FLOOR_3_ID, NODE_3F_ELEVATOR_A);

        addStop(stops, "00004111", "STOP-ELEV-B-1F", ELEVATOR_B_ID, FLOOR_1_ID, NODE_1F_ELEVATOR_B);
        addStop(stops, "00004112", "STOP-ELEV-B-2F", ELEVATOR_B_ID, FLOOR_2_ID, NODE_2F_ELEVATOR_B);
        addStop(stops, "00004113", "STOP-ELEV-B-3F", ELEVATOR_B_ID, FLOOR_3_ID, NODE_3F_ELEVATOR_B);

        addStop(stops, "00004121", "STOP-STAIRS-1F", STAIRS_ID, FLOOR_1_ID, NODE_1F_STAIRS);
        addStop(stops, "00004122", "STOP-STAIRS-2F", STAIRS_ID, FLOOR_2_ID, NODE_2F_STAIRS);
        addStop(stops, "00004123", "STOP-STAIRS-3F", STAIRS_ID, FLOOR_3_ID, NODE_3F_STAIRS);
        return stops;
    }

    private static Map<UUID, VerticalLink> buildVerticalLinks() {
        Map<UUID, VerticalLink> links = new LinkedHashMap<>();
        addLink(links, "00005101", "VERT-ELEV-A-1F-3F", ELEVATOR_A_ID, "00004101", "00004102", 50, true);
        addLink(links, "00005102", "VERT-ELEV-B-1F-2F", ELEVATOR_B_ID, "00004111", "00004112", 30, true);
        addLink(links, "00005103", "VERT-ELEV-B-2F-3F", ELEVATOR_B_ID, "00004112", "00004113", 30, true);
        addLink(links, "00005104", "VERT-STAIRS-1F-2F", STAIRS_ID, "00004121", "00004122", 18, false);
        addLink(links, "00005105", "VERT-STAIRS-2F-3F", STAIRS_ID, "00004122", "00004123", 18, false);
        return links;
    }

    private static void addStop(
        Map<UUID, ConnectorStop> stops,
        String idSuffix,
        String code,
        UUID connectorId,
        UUID floorId,
        UUID nodeId
    ) {
        UUID id = uuid(idSuffix);
        stops.put(
            id,
            new ConnectorStop(id, code, connectorId, floorId, nodeId)
        );
    }

    private static void addLink(
        Map<UUID, VerticalLink> links,
        String idSuffix,
        String code,
        UUID connectorId,
        String fromStopSuffix,
        String toStopSuffix,
        int timeSeconds,
        boolean accessible
    ) {
        UUID id = uuid(idSuffix);
        links.put(
            id,
            new VerticalLink(
                id,
                code,
                connectorId,
                uuid(fromStopSuffix),
                uuid(toStopSuffix),
                timeSeconds,
                BigDecimal.ZERO,
                EdgeDirection.BOTH,
                AccessScope.PUBLIC,
                accessible,
                true
            )
        );
    }

    private static FloorSnapshot floor(
        UUID floorId,
        String code,
        int levelNo,
        UUID mapRevisionId,
        String imageUrl
    ) {
        return new FloorSnapshot(
            floorId,
            code,
            code,
            levelNo,
            mapRevisionId,
            1,
            imageUrl,
            1000,
            620
        );
    }

    private static void addNode(
        Map<UUID, GraphNode> nodes,
        UUID id,
        String code,
        UUID floorId,
        double x,
        double y,
        String type
    ) {
        nodes.put(id, new GraphNode(id, code, floorId, x, y, type));
    }

    private static void addPoi(
        Map<UUID, PoiSnapshot> pois,
        UUID id,
        String code,
        String name,
        String category,
        UUID floorId,
        UUID nodeId,
        double x,
        double y,
        String... keywords
    ) {
        pois.put(
            id,
            new PoiSnapshot(
                id,
                code,
                name,
                category,
                floorId,
                nodeId,
                x,
                y,
                true,
                List.of(keywords)
            )
        );
    }

    private static void addBoth(
        Map<UUID, List<GraphArc>> outgoing,
        String code,
        String idSuffix,
        UUID firstNodeId,
        UUID secondNodeId,
        int timeSeconds,
        long distanceMeters,
        ArcType type,
        boolean accessible,
        UUID connectorId
    ) {
        UUID elementId = uuid(idSuffix);
        BigDecimal distance = BigDecimal.valueOf(distanceMeters);
        addArc(
            outgoing,
            new GraphArc(
                elementId,
                code,
                firstNodeId,
                secondNodeId,
                timeSeconds,
                distance,
                type,
                AccessScope.PUBLIC,
                accessible,
                connectorId,
                true
            )
        );
        addArc(
            outgoing,
            new GraphArc(
                elementId,
                code,
                secondNodeId,
                firstNodeId,
                timeSeconds,
                distance,
                type,
                AccessScope.PUBLIC,
                accessible,
                connectorId,
                true
            )
        );
    }

    private static void addForward(
        Map<UUID, List<GraphArc>> outgoing,
        String code,
        String idSuffix,
        UUID fromNodeId,
        UUID toNodeId,
        int timeSeconds,
        long distanceMeters,
        ArcType type,
        boolean accessible,
        UUID connectorId
    ) {
        addArc(
            outgoing,
            new GraphArc(
                uuid(idSuffix),
                code,
                fromNodeId,
                toNodeId,
                timeSeconds,
                BigDecimal.valueOf(distanceMeters),
                type,
                AccessScope.PUBLIC,
                accessible,
                connectorId,
                true
            )
        );
    }

    private static void addVerticalLink(
        Map<UUID, List<GraphArc>> outgoing,
        VerticalLink link,
        Map<UUID, ConnectorStop> stops,
        Map<UUID, VerticalConnector> connectors
    ) {
        VerticalConnector connector = connectors.get(link.connectorId());
        ConnectorStop fromStop = stops.get(link.fromStopId());
        ConnectorStop toStop = stops.get(link.toStopId());
        if (connector == null || fromStop == null || toStop == null) {
            throw new IllegalStateException(
                "Vertical link references missing connector or stop: "
                    + link.code()
            );
        }
        if (
            !connector.id().equals(fromStop.connectorId())
                || !connector.id().equals(toStop.connectorId())
        ) {
            throw new IllegalStateException(
                "Vertical link stops belong to different connectors: "
                    + link.code()
            );
        }

        addArc(
            outgoing,
            verticalArc(link, connector, fromStop.nodeId(), toStop.nodeId())
        );
        if (link.direction() == EdgeDirection.BOTH) {
            addArc(
                outgoing,
                verticalArc(link, connector, toStop.nodeId(), fromStop.nodeId())
            );
        }
    }

    static GraphArc verticalArc(
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

    private static void addArc(
        Map<UUID, List<GraphArc>> outgoing,
        GraphArc arc
    ) {
        outgoing.computeIfAbsent(arc.fromNodeId(), ignored -> new ArrayList<>()).add(arc);
    }

    private static UUID uuid(String suffix) {
        return UUID.fromString("00000000-0000-0000-0000-" + String.format("%012d", Long.parseLong(suffix)));
    }
}
