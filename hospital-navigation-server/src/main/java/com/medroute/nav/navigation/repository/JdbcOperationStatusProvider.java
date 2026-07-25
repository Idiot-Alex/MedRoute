package com.medroute.nav.navigation.repository;

import com.medroute.nav.dto.CreateOperationClosureRequest;
import com.medroute.nav.dto.OperationClosureListResponse;
import com.medroute.nav.dto.OperationClosureListResponse.Closure;
import com.medroute.nav.dto.OperationClosureListResponse.Target;
import com.medroute.nav.navigation.service.NavigationResourceNotFoundException;
import com.medroute.nav.navigation.service.OperationConflictException;
import com.medroute.nav.navigation.service.OperationStatusProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Repository
public class JdbcOperationStatusProvider implements OperationStatusProvider {
    private final JdbcTemplate jdbc;

    public JdbcOperationStatusProvider(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public OperationStatusSnapshot status(
        UUID buildingId,
        UUID releaseId,
        Instant effectiveAt
    ) {
        Timestamp timestamp = Timestamp.from(effectiveAt);
        Set<UUID> closedElements = new LinkedHashSet<>();
        Set<UUID> closedConnectors = new LinkedHashSet<>();
        jdbc.query(
            """
            SELECT
                state.target_type,
                edge.public_id AS edge_public_id,
                link.public_id AS link_public_id,
                connector.public_id AS connector_public_id
            FROM edge_state_override state
            JOIN building_map_release release
                ON release.id = state.release_id
            JOIN building building
                ON building.id = state.building_id
            LEFT JOIN path_edge edge
                ON edge.id = state.path_edge_id
            LEFT JOIN vertical_link link
                ON link.id = state.vertical_link_id
            LEFT JOIN vertical_connector connector
                ON connector.id = state.vertical_connector_id
            WHERE building.public_id = ?
              AND release.public_id = ?
              AND state.revoked_at IS NULL
              AND state.effective_from <= ?
              AND (
                    state.effective_to IS NULL
                    OR state.effective_to > ?
              )
            """,
            resultSet -> {
                String targetType = resultSet.getString("target_type");
                if ("vertical_connector".equals(targetType)) {
                    closedConnectors.add(
                        uuid(resultSet.getObject("connector_public_id"))
                    );
                } else if ("path_edge".equals(targetType)) {
                    closedElements.add(
                        uuid(resultSet.getObject("edge_public_id"))
                    );
                } else if ("vertical_link".equals(targetType)) {
                    closedElements.add(
                        uuid(resultSet.getObject("link_public_id"))
                    );
                }
            },
            buildingId,
            releaseId,
            timestamp,
            timestamp
        );
        return new OperationStatusSnapshot(
            closedElements,
            closedConnectors
        );
    }

    @Transactional(readOnly = true)
    public OperationClosureListResponse listClosures(
        UUID buildingId,
        Instant currentTime
    ) {
        ActiveRelease release = activeRelease(buildingId);
        return listClosures(release, currentTime);
    }

    @Transactional
    public OperationClosureListResponse createClosure(
        UUID buildingId,
        CreateOperationClosureRequest request,
        String actor,
        Instant currentTime
    ) {
        ActiveRelease release = activeRelease(buildingId);
        lockActiveRelease(release);
        OperationTarget target = findTarget(
            release.databaseId(),
            request.targetType(),
            request.targetId()
        );
        Instant effectiveFrom = request.effectiveFrom() == null
            ? currentTime
            : request.effectiveFrom();
        ensureNoOverlap(
            release.databaseId(),
            target,
            effectiveFrom,
            request.effectiveTo()
        );

        Long pathEdgeId = "path_edge".equals(target.type())
            ? target.databaseId()
            : null;
        Long verticalLinkId = "vertical_link".equals(target.type())
            ? target.databaseId()
            : null;
        Long connectorId = "vertical_connector".equals(target.type())
            ? target.databaseId()
            : null;
        jdbc.update(
            """
            INSERT INTO edge_state_override (
                public_id,
                building_id,
                release_id,
                target_type,
                path_edge_id,
                vertical_link_id,
                vertical_connector_id,
                status,
                effective_from,
                effective_to,
                reason,
                created_by
            ) VALUES (?, ?, ?, ?, ?, ?, ?, 'closed', ?, ?, ?, ?)
            """,
            UUID.randomUUID(),
            release.buildingDatabaseId(),
            release.databaseId(),
            target.type(),
            pathEdgeId,
            verticalLinkId,
            connectorId,
            Timestamp.from(effectiveFrom),
            request.effectiveTo() == null
                ? null
                : Timestamp.from(request.effectiveTo()),
            request.reason().trim(),
            actor
        );
        return listClosures(release, currentTime);
    }

    @Transactional
    public OperationClosureListResponse revokeClosure(
        UUID closureId,
        String actor,
        Instant currentTime
    ) {
        List<ActiveRelease> releases = jdbc.query(
            """
            SELECT
                release.id AS release_db_id,
                release.public_id AS release_public_id,
                release.code AS release_code,
                building.id AS building_db_id,
                building.public_id AS building_public_id
            FROM edge_state_override state
            JOIN building_map_release release
                ON release.id = state.release_id
            JOIN building building
                ON building.id = state.building_id
            WHERE state.public_id = ?
              AND state.revoked_at IS NULL
            """,
            this::activeReleaseRow,
            closureId
        );
        if (releases.isEmpty()) {
            throw new NavigationResourceNotFoundException(
                "Unknown active operation closure: " + closureId
            );
        }
        ActiveRelease release = releases.get(0);
        lockActiveRelease(release);
        int updated = jdbc.update(
            """
            UPDATE edge_state_override
            SET revoked_by = ?,
                revoked_at = ?
            WHERE public_id = ?
              AND revoked_at IS NULL
            """,
            actor,
            Timestamp.from(currentTime),
            closureId
        );
        if (updated != 1) {
            throw new OperationConflictException(
                "Operation closure was already revoked"
            );
        }
        return listClosures(release, currentTime);
    }

    private OperationClosureListResponse listClosures(
        ActiveRelease release,
        Instant currentTime
    ) {
        List<Closure> closures = jdbc.query(
            """
            SELECT
                state.public_id,
                state.target_type,
                CASE state.target_type
                    WHEN 'path_edge' THEN edge.public_id
                    WHEN 'vertical_link' THEN link.public_id
                    ELSE connector.public_id
                END AS target_public_id,
                CASE state.target_type
                    WHEN 'path_edge' THEN edge.code
                    WHEN 'vertical_link' THEN link.code
                    ELSE connector.code
                END AS target_code,
                CASE state.target_type
                    WHEN 'path_edge' THEN edge.code
                    WHEN 'vertical_link' THEN link_connector.name
                    ELSE connector.name
                END AS target_name,
                state.effective_from,
                state.effective_to,
                state.reason,
                state.created_by,
                state.created_at
            FROM edge_state_override state
            LEFT JOIN path_edge edge
                ON edge.id = state.path_edge_id
            LEFT JOIN vertical_link link
                ON link.id = state.vertical_link_id
            LEFT JOIN vertical_connector link_connector
                ON link_connector.id = link.connector_id
            LEFT JOIN vertical_connector connector
                ON connector.id = state.vertical_connector_id
            WHERE state.release_id = ?
              AND state.revoked_at IS NULL
              AND (
                    state.effective_to IS NULL
                    OR state.effective_to > ?
              )
            ORDER BY state.effective_from, state.created_at
            """,
            this::closureRow,
            release.databaseId(),
            Timestamp.from(currentTime)
        );
        return new OperationClosureListResponse(
            release.buildingPublicId(),
            release.publicId(),
            release.code(),
            loadTargets(release.databaseId()),
            closures
        );
    }

    private List<Target> loadTargets(long releaseDatabaseId) {
        List<Target> targets = new java.util.ArrayList<>();
        targets.addAll(
            jdbc.query(
                """
                SELECT
                    connector.public_id,
                    connector.code,
                    connector.name
                FROM vertical_connector connector
                WHERE connector.release_id = ?
                  AND connector.base_status = 'enabled'
                ORDER BY connector.code
                """,
                (rs, rowNum) -> new Target(
                    "vertical_connector",
                    uuid(rs.getObject("public_id")),
                    rs.getString("code"),
                    rs.getString("name"),
                    null
                ),
                releaseDatabaseId
            )
        );
        targets.addAll(
            jdbc.query(
                """
                SELECT
                    edge.public_id,
                    edge.code,
                    floor.code AS floor_code
                FROM path_edge edge
                JOIN floor floor ON floor.id = edge.floor_id
                WHERE edge.release_id = ?
                  AND edge.base_status = 'enabled'
                ORDER BY floor.level_no, edge.code
                """,
                (rs, rowNum) -> new Target(
                    "path_edge",
                    uuid(rs.getObject("public_id")),
                    rs.getString("code"),
                    rs.getString("code"),
                    rs.getString("floor_code")
                ),
                releaseDatabaseId
            )
        );
        targets.addAll(
            jdbc.query(
                """
                SELECT
                    link.public_id,
                    link.code,
                    connector.name,
                    from_floor.code AS from_floor_code,
                    to_floor.code AS to_floor_code
                FROM vertical_link link
                JOIN vertical_connector connector
                    ON connector.id = link.connector_id
                JOIN connector_stop from_stop
                    ON from_stop.id = link.from_stop_id
                JOIN floor from_floor
                    ON from_floor.id = from_stop.floor_id
                JOIN connector_stop to_stop
                    ON to_stop.id = link.to_stop_id
                JOIN floor to_floor
                    ON to_floor.id = to_stop.floor_id
                WHERE link.release_id = ?
                  AND link.base_status = 'enabled'
                ORDER BY connector.code, from_floor.level_no, to_floor.level_no
                """,
                (rs, rowNum) -> new Target(
                    "vertical_link",
                    uuid(rs.getObject("public_id")),
                    rs.getString("code"),
                    rs.getString("name"),
                    rs.getString("from_floor_code")
                        + " ↔ "
                        + rs.getString("to_floor_code")
                ),
                releaseDatabaseId
            )
        );
        return targets;
    }

    private ActiveRelease activeRelease(UUID buildingId) {
        List<ActiveRelease> releases = jdbc.query(
            """
            SELECT
                release.id AS release_db_id,
                release.public_id AS release_public_id,
                release.code AS release_code,
                building.id AS building_db_id,
                building.public_id AS building_public_id
            FROM building_map_release release
            JOIN building building
                ON building.id = release.building_id
            WHERE building.public_id = ?
              AND release.status = 'published'
              AND release.is_active = TRUE
            """,
            this::activeReleaseRow,
            buildingId
        );
        if (releases.isEmpty()) {
            throw new NavigationResourceNotFoundException(
                "No active release for buildingId: " + buildingId
            );
        }
        return releases.get(0);
    }

    private void lockActiveRelease(ActiveRelease release) {
        Boolean active = jdbc.queryForObject(
            """
            SELECT is_active
            FROM building_map_release
            WHERE id = ?
            FOR UPDATE
            """,
            Boolean.class,
            release.databaseId()
        );
        if (!Boolean.TRUE.equals(active)) {
            throw new OperationConflictException(
                "Active release changed; retry the operation"
            );
        }
    }

    private OperationTarget findTarget(
        long releaseDatabaseId,
        String targetType,
        UUID targetId
    ) {
        String table;
        String codeColumn;
        String nameColumn;
        switch (targetType) {
            case "path_edge" -> {
                table = "path_edge";
                codeColumn = "code";
                nameColumn = "code";
            }
            case "vertical_link" -> {
                table = "vertical_link";
                codeColumn = "code";
                nameColumn = "code";
            }
            case "vertical_connector" -> {
                table = "vertical_connector";
                codeColumn = "code";
                nameColumn = "name";
            }
            default -> throw new IllegalArgumentException(
                "unsupported operation targetType: " + targetType
            );
        }
        List<OperationTarget> targets = jdbc.query(
            "SELECT id, public_id, " + codeColumn + " AS target_code, "
                + nameColumn + " AS target_name FROM " + table
                + " WHERE release_id = ? AND public_id = ?",
            (rs, rowNum) -> new OperationTarget(
                rs.getLong("id"),
                uuid(rs.getObject("public_id")),
                targetType,
                rs.getString("target_code"),
                rs.getString("target_name")
            ),
            releaseDatabaseId,
            targetId
        );
        if (targets.isEmpty()) {
            throw new NavigationResourceNotFoundException(
                "Unknown " + targetType + " target: " + targetId
            );
        }
        return targets.get(0);
    }

    private void ensureNoOverlap(
        long releaseDatabaseId,
        OperationTarget target,
        Instant effectiveFrom,
        Instant effectiveTo
    ) {
        String idColumn = switch (target.type()) {
            case "path_edge" -> "path_edge_id";
            case "vertical_link" -> "vertical_link_id";
            case "vertical_connector" -> "vertical_connector_id";
            default -> throw new IllegalArgumentException(
                "unsupported operation targetType: " + target.type()
            );
        };
        String windowSql = """
            SELECT effective_from, effective_to
            FROM edge_state_override
            WHERE release_id = ?
              AND target_type = ?
              AND %s = ?
              AND revoked_at IS NULL
            """.formatted(idColumn);
        List<OperationWindow> windows = jdbc.query(
            windowSql,
            (rs, rowNum) -> new OperationWindow(
                rs.getTimestamp("effective_from").toInstant(),
                nullableInstant(rs, "effective_to")
            ),
            releaseDatabaseId,
            target.type(),
            target.databaseId()
        );
        boolean overlaps = windows.stream().anyMatch(window ->
            (effectiveTo == null || window.effectiveFrom().isBefore(effectiveTo))
                && (
                    window.effectiveTo() == null
                        || window.effectiveTo().isAfter(effectiveFrom)
                )
        );
        if (overlaps) {
            throw new OperationConflictException(
                "Operation closure overlaps an existing closure"
            );
        }
    }

    private ActiveRelease activeReleaseRow(ResultSet rs, int rowNumber)
        throws SQLException {
        return new ActiveRelease(
            rs.getLong("release_db_id"),
            uuid(rs.getObject("release_public_id")),
            rs.getString("release_code"),
            rs.getLong("building_db_id"),
            uuid(rs.getObject("building_public_id"))
        );
    }

    private Closure closureRow(ResultSet rs, int rowNumber)
        throws SQLException {
        return new Closure(
            uuid(rs.getObject("public_id")),
            rs.getString("target_type"),
            uuid(rs.getObject("target_public_id")),
            rs.getString("target_code"),
            rs.getString("target_name"),
            rs.getTimestamp("effective_from").toInstant(),
            nullableInstant(rs, "effective_to"),
            rs.getString("reason"),
            rs.getString("created_by"),
            rs.getTimestamp("created_at").toInstant()
        );
    }

    private Instant nullableInstant(ResultSet rs, String column)
        throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private UUID uuid(Object value) {
        return value instanceof UUID uuid
            ? uuid
            : UUID.fromString(String.valueOf(value));
    }

    private record ActiveRelease(
        long databaseId,
        UUID publicId,
        String code,
        long buildingDatabaseId,
        UUID buildingPublicId
    ) {
    }

    private record OperationTarget(
        long databaseId,
        UUID publicId,
        String type,
        String code,
        String name
    ) {
    }

    private record OperationWindow(
        Instant effectiveFrom,
        Instant effectiveTo
    ) {
    }
}
