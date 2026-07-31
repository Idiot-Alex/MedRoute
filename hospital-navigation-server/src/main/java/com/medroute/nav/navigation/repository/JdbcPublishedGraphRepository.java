package com.medroute.nav.navigation.repository;

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
import com.medroute.nav.navigation.service.NavigationResourceNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Repository
public class JdbcPublishedGraphRepository implements PublishedGraphRepository {
    private final JdbcTemplate jdbc;

    public JdbcPublishedGraphRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public PublishedGraphSnapshot active(UUID buildingId) {
        List<ReleaseRow> rows = jdbc.query(
            """
            SELECT
                r.id AS release_db_id,
                r.public_id AS release_public_id,
                r.code AS release_code,
                r.published_at,
                b.public_id AS building_public_id,
                b.code AS building_code,
                b.name AS building_name
            FROM building_map_release r
            JOIN building b ON b.id = r.building_id
            WHERE b.public_id = ?
              AND r.status = 'published'
              AND r.is_active = TRUE
            """,
            this::releaseRow,
            buildingId
        );
        if (rows.isEmpty()) {
            throw new NavigationResourceNotFoundException(
                "No active published release for buildingId: " + buildingId
            );
        }
        return load(rows.get(0));
    }

    @Override
    @Transactional(readOnly = true)
    public PublishedGraphSnapshot published(UUID releaseId) {
        List<ReleaseRow> rows = jdbc.query(
            """
            SELECT
                r.id AS release_db_id,
                r.public_id AS release_public_id,
                r.code AS release_code,
                r.published_at,
                b.public_id AS building_public_id,
                b.code AS building_code,
                b.name AS building_name
            FROM building_map_release r
            JOIN building b ON b.id = r.building_id
            WHERE r.public_id = ?
              AND r.status = 'published'
            """,
            this::releaseRow,
            releaseId
        );
        if (rows.isEmpty()) {
            throw new NavigationResourceNotFoundException(
                "Unknown published releaseId: " + releaseId
            );
        }
        return load(rows.get(0));
    }

    private PublishedGraphSnapshot load(ReleaseRow release) {
        Map<UUID, FloorSnapshot> floors = loadFloors(release.databaseId());
        Map<UUID, GraphNode> nodes = loadNodes(release.databaseId());
        Map<UUID, List<GraphArc>> outgoing = new LinkedHashMap<>();
        loadHorizontalArcs(release.databaseId(), outgoing);
        Map<UUID, PoiSnapshot> pois = loadPois(release.databaseId());
        Map<UUID, VerticalConnector> connectorModels =
            loadConnectorModels(release.databaseId());
        Map<UUID, ConnectorStop> stops = loadStops(release.databaseId());
        Map<UUID, VerticalLink> links = loadLinks(release.databaseId());
        addVerticalArcs(outgoing, connectorModels, stops, links);
        Map<UUID, ConnectorSnapshot> connectors = connectorSnapshots(
            connectorModels,
            stops
        );

        NavigationGraph graph = new NavigationGraph(
            release.publicId(),
            release.buildingId(),
            floors,
            nodes,
            outgoing,
            pois,
            connectors,
            stops,
            links
        );
        return new PublishedGraphSnapshot(
            graph,
            release.buildingCode(),
            release.buildingName(),
            release.code(),
            release.publishedAt()
        );
    }

    private Map<UUID, FloorSnapshot> loadFloors(long releaseId) {
        Map<UUID, FloorSnapshot> floors = new LinkedHashMap<>();
        jdbc.query(
            """
            SELECT
                f.public_id AS floor_public_id,
                f.code AS floor_code,
                f.name AS floor_name,
                f.level_no,
                m.public_id AS map_public_id,
                m.revision_no,
                m.image_url,
                m.image_width,
                m.image_height
            FROM release_floor_map rfm
            JOIN floor f ON f.id = rfm.floor_id
            JOIN floor_map_revision m ON m.id = rfm.floor_map_revision_id
            WHERE rfm.release_id = ?
            ORDER BY f.level_no, f.code
            """,
            resultSet -> {
                FloorSnapshot floor = new FloorSnapshot(
                    uuid(resultSet, "floor_public_id"),
                    resultSet.getString("floor_code"),
                    resultSet.getString("floor_name"),
                    resultSet.getInt("level_no"),
                    uuid(resultSet, "map_public_id"),
                    resultSet.getInt("revision_no"),
                    resultSet.getString("image_url"),
                    resultSet.getInt("image_width"),
                    resultSet.getInt("image_height")
                );
                floors.put(floor.id(), floor);
            },
            releaseId
        );
        return floors;
    }

    private Map<UUID, GraphNode> loadNodes(long releaseId) {
        Map<UUID, GraphNode> nodes = new LinkedHashMap<>();
        jdbc.query(
            """
            SELECT
                n.public_id,
                n.code,
                f.public_id AS floor_public_id,
                n.x,
                n.y,
                n.node_type
            FROM path_node n
            JOIN floor f ON f.id = n.floor_id
            WHERE n.release_id = ?
              AND n.enabled = TRUE
            ORDER BY f.level_no, n.code
            """,
            resultSet -> {
                GraphNode node = new GraphNode(
                    uuid(resultSet, "public_id"),
                    resultSet.getString("code"),
                    uuid(resultSet, "floor_public_id"),
                    resultSet.getDouble("x"),
                    resultSet.getDouble("y"),
                    resultSet.getString("node_type")
                );
                nodes.put(node.id(), node);
            },
            releaseId
        );
        return nodes;
    }

    private void loadHorizontalArcs(
        long releaseId,
        Map<UUID, List<GraphArc>> outgoing
    ) {
        jdbc.query(
            """
            SELECT
                e.public_id,
                e.code,
                from_node.public_id AS from_node_public_id,
                to_node.public_id AS to_node_public_id,
                e.time_seconds,
                e.distance_meters,
                e.direction,
                e.access_scope,
                e.accessible,
                e.base_status
            FROM path_edge e
            JOIN path_node from_node ON from_node.id = e.from_node_id
            JOIN path_node to_node ON to_node.id = e.to_node_id
            WHERE e.release_id = ?
              AND from_node.enabled = TRUE
              AND to_node.enabled = TRUE
            ORDER BY e.code
            """,
            resultSet -> {
                GraphArc forward = new GraphArc(
                    uuid(resultSet, "public_id"),
                    resultSet.getString("code"),
                    uuid(resultSet, "from_node_public_id"),
                    uuid(resultSet, "to_node_public_id"),
                    resultSet.getInt("time_seconds"),
                    resultSet.getBigDecimal("distance_meters"),
                    ArcType.WALK,
                    accessScope(resultSet.getString("access_scope")),
                    resultSet.getBoolean("accessible"),
                    null,
                    "enabled".equals(resultSet.getString("base_status"))
                );
                GraphArcFactory.addHorizontal(
                    outgoing,
                    forward,
                    direction(resultSet.getString("direction"))
                );
            },
            releaseId
        );
    }

    private Map<UUID, PoiSnapshot> loadPois(long releaseId) {
        Map<UUID, List<String>> keywords = loadKeywords(releaseId);
        Map<UUID, PoiSnapshot> pois = new LinkedHashMap<>();
        jdbc.query(
            """
            SELECT
                p.public_id,
                p.code,
                p.name,
                p.category,
                f.public_id AS floor_public_id,
                n.public_id AS node_public_id,
                p.x,
                p.y,
                p.accessible
            FROM poi p
            JOIN floor f ON f.id = p.floor_id
            JOIN path_node n ON n.id = p.node_id
            WHERE p.release_id = ?
              AND p.enabled = TRUE
              AND p.access_scope = 'public'
              AND n.enabled = TRUE
            ORDER BY f.level_no, p.name
            """,
            resultSet -> {
                UUID id = uuid(resultSet, "public_id");
                PoiSnapshot poi = new PoiSnapshot(
                    id,
                    resultSet.getString("code"),
                    resultSet.getString("name"),
                    resultSet.getString("category"),
                    uuid(resultSet, "floor_public_id"),
                    uuid(resultSet, "node_public_id"),
                    resultSet.getDouble("x"),
                    resultSet.getDouble("y"),
                    resultSet.getBoolean("accessible"),
                    keywords.getOrDefault(id, List.of())
                );
                pois.put(poi.id(), poi);
            },
            releaseId
        );
        return pois;
    }

    private Map<UUID, List<String>> loadKeywords(long releaseId) {
        Map<UUID, List<String>> keywords = new LinkedHashMap<>();
        jdbc.query(
            """
            SELECT p.public_id AS poi_public_id, k.keyword
            FROM poi_keyword k
            JOIN poi p ON p.id = k.poi_id
            WHERE k.release_id = ?
            ORDER BY p.public_id, k.keyword
            """,
            resultSet -> {
                keywords
                    .computeIfAbsent(
                        uuid(resultSet, "poi_public_id"),
                        ignored -> new ArrayList<>()
                    )
                    .add(resultSet.getString("keyword"));
            },
            releaseId
        );
        return keywords;
    }

    private Map<UUID, VerticalConnector> loadConnectorModels(long releaseId) {
        Map<UUID, VerticalConnector> connectors = new LinkedHashMap<>();
        jdbc.query(
            """
            SELECT
                public_id,
                code,
                name,
                connector_type,
                access_scope,
                accessible,
                base_status
            FROM vertical_connector
            WHERE release_id = ?
            ORDER BY code
            """,
            resultSet -> {
                VerticalConnector connector = new VerticalConnector(
                    uuid(resultSet, "public_id"),
                    resultSet.getString("code"),
                    resultSet.getString("name"),
                    arcType(resultSet.getString("connector_type")),
                    accessScope(resultSet.getString("access_scope")),
                    resultSet.getBoolean("accessible"),
                    "enabled".equals(resultSet.getString("base_status"))
                );
                connectors.put(connector.id(), connector);
            },
            releaseId
        );
        return connectors;
    }

    private Map<UUID, ConnectorStop> loadStops(long releaseId) {
        Map<UUID, ConnectorStop> stops = new LinkedHashMap<>();
        jdbc.query(
            """
            SELECT
                s.public_id,
                s.stop_code,
                c.public_id AS connector_public_id,
                f.public_id AS floor_public_id,
                n.public_id AS node_public_id
            FROM connector_stop s
            JOIN vertical_connector c ON c.id = s.connector_id
            JOIN floor f ON f.id = s.floor_id
            JOIN path_node n ON n.id = s.node_id
            WHERE s.release_id = ?
              AND n.enabled = TRUE
            ORDER BY c.code, f.level_no
            """,
            resultSet -> {
                ConnectorStop stop = new ConnectorStop(
                    uuid(resultSet, "public_id"),
                    resultSet.getString("stop_code"),
                    uuid(resultSet, "connector_public_id"),
                    uuid(resultSet, "floor_public_id"),
                    uuid(resultSet, "node_public_id")
                );
                stops.put(stop.id(), stop);
            },
            releaseId
        );
        return stops;
    }

    private Map<UUID, VerticalLink> loadLinks(long releaseId) {
        Map<UUID, VerticalLink> links = new LinkedHashMap<>();
        jdbc.query(
            """
            SELECT
                l.public_id,
                l.code,
                c.public_id AS connector_public_id,
                from_stop.public_id AS from_stop_public_id,
                to_stop.public_id AS to_stop_public_id,
                l.time_seconds,
                l.distance_meters,
                l.direction,
                l.access_scope,
                l.accessible,
                l.base_status
            FROM vertical_link l
            JOIN vertical_connector c ON c.id = l.connector_id
            JOIN connector_stop from_stop ON from_stop.id = l.from_stop_id
            JOIN connector_stop to_stop ON to_stop.id = l.to_stop_id
            JOIN path_node from_node ON from_node.id = from_stop.node_id
            JOIN path_node to_node ON to_node.id = to_stop.node_id
            WHERE l.release_id = ?
              AND from_node.enabled = TRUE
              AND to_node.enabled = TRUE
            ORDER BY l.code
            """,
            resultSet -> {
                VerticalLink link = new VerticalLink(
                    uuid(resultSet, "public_id"),
                    resultSet.getString("code"),
                    uuid(resultSet, "connector_public_id"),
                    uuid(resultSet, "from_stop_public_id"),
                    uuid(resultSet, "to_stop_public_id"),
                    resultSet.getInt("time_seconds"),
                    nonNullDistance(resultSet.getBigDecimal("distance_meters")),
                    direction(resultSet.getString("direction")),
                    accessScope(resultSet.getString("access_scope")),
                    resultSet.getBoolean("accessible"),
                    "enabled".equals(resultSet.getString("base_status"))
                );
                links.put(link.id(), link);
            },
            releaseId
        );
        return links;
    }

    private void addVerticalArcs(
        Map<UUID, List<GraphArc>> outgoing,
        Map<UUID, VerticalConnector> connectors,
        Map<UUID, ConnectorStop> stops,
        Map<UUID, VerticalLink> links
    ) {
        for (VerticalLink link : links.values()) {
            VerticalConnector connector = require(
                connectors,
                link.connectorId(),
                "connector",
                link.code()
            );
            ConnectorStop fromStop = require(
                stops,
                link.fromStopId(),
                "from stop",
                link.code()
            );
            ConnectorStop toStop = require(
                stops,
                link.toStopId(),
                "to stop",
                link.code()
            );
            GraphArcFactory.addVertical(
                outgoing,
                link,
                connector,
                fromStop,
                toStop
            );
        }
    }

    private Map<UUID, ConnectorSnapshot> connectorSnapshots(
        Map<UUID, VerticalConnector> connectorModels,
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
        for (VerticalConnector connector : connectorModels.values()) {
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

    private ReleaseRow releaseRow(ResultSet resultSet, int rowNumber)
        throws SQLException {
        return new ReleaseRow(
            resultSet.getLong("release_db_id"),
            uuid(resultSet, "release_public_id"),
            resultSet.getString("release_code"),
            instant(resultSet, "published_at"),
            uuid(resultSet, "building_public_id"),
            resultSet.getString("building_code"),
            resultSet.getString("building_name")
        );
    }

    private UUID uuid(ResultSet resultSet, String column)
        throws SQLException {
        Object value = resultSet.getObject(column);
        return value instanceof UUID uuid
            ? uuid
            : UUID.fromString(String.valueOf(value));
    }

    private Instant instant(ResultSet resultSet, String column)
        throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        if (timestamp == null) {
            throw new IllegalStateException(column + " must not be null");
        }
        return timestamp.toInstant();
    }

    private EdgeDirection direction(String value) {
        return EdgeDirection.valueOf(value.toUpperCase(java.util.Locale.ROOT));
    }

    private AccessScope accessScope(String value) {
        return AccessScope.valueOf(value.toUpperCase(java.util.Locale.ROOT));
    }

    private ArcType arcType(String value) {
        return switch (value) {
            case "elevator" -> ArcType.ELEVATOR;
            case "stairs" -> ArcType.STAIRS;
            default -> throw new IllegalStateException(
                "Unknown connector type: " + value
            );
        };
    }

    private BigDecimal nonNullDistance(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private <T> T require(
        Map<UUID, T> values,
        UUID id,
        String type,
        String ownerCode
    ) {
        T value = values.get(id);
        if (value == null) {
            throw new IllegalStateException(
                ownerCode + " references a missing " + type + ": " + id
            );
        }
        return value;
    }

    private record ReleaseRow(
        long databaseId,
        UUID publicId,
        String code,
        Instant publishedAt,
        UUID buildingId,
        String buildingCode,
        String buildingName
    ) {
    }
}
