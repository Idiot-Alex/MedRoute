package com.medroute.nav.navigation.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medroute.nav.dto.AdminReleaseSummary;
import com.medroute.nav.dto.AdminValidationResponse;
import com.medroute.nav.dto.AdminWorkspaceResponse;
import com.medroute.nav.dto.CreateDraftRequest;
import com.medroute.nav.dto.DraftGraphPayload;
import com.medroute.nav.navigation.service.DraftChangedException;
import com.medroute.nav.navigation.service.DraftValidationFailedException;
import com.medroute.nav.navigation.service.NavigationResourceNotFoundException;
import com.medroute.nav.navigation.service.OperationConflictException;
import com.medroute.nav.navigation.service.ReleaseImmutableException;
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
public class JdbcMapAuthoringRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcMapAuthoringRepository(
        JdbcTemplate jdbc,
        ObjectMapper objectMapper
    ) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<AdminReleaseSummary> listReleases(UUID buildingId) {
        ensureBuildingExists(buildingId);
        return jdbc.query(
            """
            SELECT
                r.public_id,
                r.code,
                r.status,
                r.is_active,
                r.content_revision,
                based.public_id AS based_on_public_id,
                r.description,
                r.created_by,
                r.created_at,
                r.published_by,
                r.published_at,
                r.validation_passed,
                r.validated_revision
            FROM building_map_release r
            JOIN building b ON b.id = r.building_id
            LEFT JOIN building_map_release based
                ON based.id = r.based_on_release_id
            WHERE b.public_id = ?
            ORDER BY r.created_at DESC, r.id DESC
            """,
            this::releaseSummary,
            buildingId
        );
    }

    @Transactional(readOnly = true)
    public AdminWorkspaceResponse workspace(UUID releaseId) {
        ReleaseRow release = findRelease(releaseId);
        return new AdminWorkspaceResponse(
            new AdminWorkspaceResponse.Building(
                release.buildingPublicId(),
                release.buildingCode(),
                release.buildingName()
            ),
            new AdminWorkspaceResponse.Release(
                release.publicId(),
                release.code(),
                release.status(),
                release.contentRevision(),
                release.basedOnPublicId(),
                release.description(),
                release.createdBy(),
                release.createdAt(),
                release.publishedBy(),
                release.publishedAt()
            ),
            loadFloors(release.databaseId()),
            loadGraph(release.databaseId())
        );
    }

    @Transactional
    public AdminWorkspaceResponse createDraft(
        UUID buildingId,
        CreateDraftRequest request,
        String actor
    ) {
        ReleaseRow source = findDraftSource(
            buildingId,
            request.basedOnReleaseId()
        );
        UUID draftId = UUID.randomUUID();
        int inserted = jdbc.update(
            """
            INSERT INTO building_map_release (
                public_id,
                building_id,
                code,
                status,
                is_active,
                content_revision,
                based_on_release_id,
                description,
                created_by
            ) VALUES (?, ?, ?, 'draft', FALSE, 0, ?, ?, ?)
            """,
            draftId,
            source.buildingDatabaseId(),
            request.code().trim(),
            source.databaseId(),
            nullableTrimmed(request.description()),
            actor
        );
        if (inserted != 1) {
            throw new OperationConflictException("Draft could not be created");
        }

        long draftDatabaseId = databaseReleaseId(draftId);
        copyReleaseFloorMaps(source.databaseId(), draftDatabaseId);
        copyNodes(source.databaseId(), draftDatabaseId);
        copyEdges(source.databaseId(), draftDatabaseId);
        copyPois(source.databaseId(), draftDatabaseId);
        copyConnectors(source.databaseId(), draftDatabaseId);
        copyStops(source.databaseId(), draftDatabaseId);
        copyVerticalLinks(source.databaseId(), draftDatabaseId);
        return workspace(draftId);
    }

    @Transactional
    public void deleteDraft(UUID releaseId, long expectedRevision) {
        ReleaseRow release = lockEditableRelease(
            releaseId,
            expectedRevision
        );
        List<Long> mapRevisionIds = jdbc.queryForList(
            """
            SELECT floor_map_revision_id
            FROM release_floor_map
            WHERE release_id = ?
            """,
            Long.class,
            release.databaseId()
        );

        jdbc.update(
            "DELETE FROM release_validation_run WHERE release_id = ?",
            release.databaseId()
        );
        jdbc.update(
            "DELETE FROM edge_state_override WHERE release_id = ?",
            release.databaseId()
        );
        deleteGraph(release.databaseId());
        jdbc.update(
            "DELETE FROM release_floor_map WHERE release_id = ?",
            release.databaseId()
        );
        int deleted = jdbc.update(
            """
            DELETE FROM building_map_release
            WHERE id = ?
              AND status = 'draft'
            """,
            release.databaseId()
        );
        if (deleted != 1) {
            throw new OperationConflictException(
                "Draft state changed during deletion"
            );
        }

        for (Long mapRevisionId : mapRevisionIds) {
            jdbc.update(
                """
                DELETE FROM floor_map_revision
                WHERE id = ?
                  AND NOT EXISTS (
                      SELECT 1
                      FROM release_floor_map
                      WHERE floor_map_revision_id = ?
                  )
                """,
                mapRevisionId,
                mapRevisionId
            );
        }
    }

    @Transactional
    public AdminWorkspaceResponse replaceGraph(
        UUID releaseId,
        long expectedRevision,
        DraftGraphPayload graph
    ) {
        ReleaseRow release = lockEditableRelease(
            releaseId,
            expectedRevision
        );
        long databaseId = release.databaseId();

        deleteGraph(databaseId);
        Map<UUID, Long> floorIds = floorDatabaseIds(databaseId);
        insertNodes(databaseId, floorIds, graph.nodes());
        Map<UUID, Long> nodeIds = publicDatabaseIds(
            "path_node",
            databaseId
        );
        insertEdges(databaseId, floorIds, nodeIds, graph.edges());
        insertPois(databaseId, floorIds, nodeIds, graph.pois());
        Map<UUID, Long> poiIds = publicDatabaseIds("poi", databaseId);
        insertKeywords(databaseId, poiIds, graph.pois());
        insertConnectors(databaseId, graph.connectors());
        Map<UUID, Long> connectorIds = publicDatabaseIds(
            "vertical_connector",
            databaseId
        );
        insertStops(
            databaseId,
            floorIds,
            nodeIds,
            connectorIds,
            graph.connectorStops()
        );
        Map<UUID, Long> stopIds = publicDatabaseIds(
            "connector_stop",
            databaseId
        );
        insertVerticalLinks(
            databaseId,
            connectorIds,
            stopIds,
            graph.verticalLinks()
        );

        touchDraft(databaseId);
        return workspace(releaseId);
    }

    @Transactional
    public AdminWorkspaceResponse replaceFloorMap(
        UUID releaseId,
        UUID floorId,
        long expectedRevision,
        String mimeType,
        String sha256,
        int imageWidth,
        int imageHeight,
        byte[] content,
        String actor
    ) {
        ReleaseRow release = lockEditableRelease(
            releaseId,
            expectedRevision
        );
        List<FloorMapRow> maps = jdbc.query(
            """
            SELECT
                f.id AS floor_db_id,
                fmr.image_width,
                fmr.image_height
            FROM release_floor_map rfm
            JOIN floor f ON f.id = rfm.floor_id
            JOIN floor_map_revision fmr
                ON fmr.id = rfm.floor_map_revision_id
            WHERE rfm.release_id = ?
              AND f.public_id = ?
            """,
            (rs, rowNum) -> new FloorMapRow(
                rs.getLong("floor_db_id"),
                rs.getInt("image_width"),
                rs.getInt("image_height")
            ),
            release.databaseId(),
            floorId
        );
        if (maps.isEmpty()) {
            throw new NavigationResourceNotFoundException(
                "Floor " + floorId + " is not bound to release " + releaseId
            );
        }
        FloorMapRow currentMap = maps.get(0);
        jdbc.queryForObject(
            "SELECT id FROM floor WHERE id = ? FOR UPDATE",
            Long.class,
            currentMap.floorDatabaseId()
        );

        Integer currentRevision = jdbc.queryForObject(
            """
            SELECT COALESCE(MAX(revision_no), 0)
            FROM floor_map_revision
            WHERE floor_id = ?
            """,
            Integer.class,
            currentMap.floorDatabaseId()
        );
        int nextRevision = (currentRevision == null ? 0 : currentRevision) + 1;
        UUID mapRevisionId = UUID.randomUUID();
        jdbc.update(
            """
            INSERT INTO floor_map_revision (
                public_id,
                floor_id,
                revision_no,
                image_url,
                image_width,
                image_height,
                mime_type,
                sha256,
                created_by
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            mapRevisionId,
            currentMap.floorDatabaseId(),
            nextRevision,
            "/api/map-images/" + mapRevisionId,
            imageWidth,
            imageHeight,
            mimeType,
            sha256,
            actor
        );
        Long mapRevisionDatabaseId = jdbc.queryForObject(
            "SELECT id FROM floor_map_revision WHERE public_id = ?",
            Long.class,
            mapRevisionId
        );
        jdbc.update(
            """
            INSERT INTO floor_map_asset (floor_map_revision_id, content)
            VALUES (?, ?)
            """,
            mapRevisionDatabaseId,
            content
        );
        jdbc.update(
            """
            UPDATE release_floor_map
            SET floor_map_revision_id = ?
            WHERE release_id = ?
              AND floor_id = ?
            """,
            mapRevisionDatabaseId,
            release.databaseId(),
            currentMap.floorDatabaseId()
        );

        BigDecimal widthScale = BigDecimal.valueOf(imageWidth)
            .divide(
                BigDecimal.valueOf(currentMap.imageWidth()),
                12,
                java.math.RoundingMode.HALF_UP
            );
        BigDecimal heightScale = BigDecimal.valueOf(imageHeight)
            .divide(
                BigDecimal.valueOf(currentMap.imageHeight()),
                12,
                java.math.RoundingMode.HALF_UP
            );
        jdbc.update(
            """
            UPDATE path_node
            SET x = x * ?,
                y = y * ?
            WHERE release_id = ?
              AND floor_id = ?
            """,
            widthScale,
            heightScale,
            release.databaseId(),
            currentMap.floorDatabaseId()
        );
        jdbc.update(
            """
            UPDATE poi
            SET x = x * ?,
                y = y * ?
            WHERE release_id = ?
              AND floor_id = ?
            """,
            widthScale,
            heightScale,
            release.databaseId(),
            currentMap.floorDatabaseId()
        );
        touchDraft(release.databaseId());
        return workspace(releaseId);
    }

    @Transactional
    public void recordValidation(
        AdminValidationResponse validation,
        long expectedRevision,
        String actor,
        long routeRegressionRevision
    ) {
        ReleaseRow release = lockEditableRelease(
            validation.releaseId(),
            expectedRevision
        );
        if (release.contentRevision() != validation.contentRevision()) {
            throw new DraftChangedException(
                validation.contentRevision(),
                release.contentRevision()
            );
        }

        jdbc.update(
            """
            INSERT INTO release_validation_run (
                release_id,
                content_revision,
                route_regression_revision,
                passed,
                result_text,
                checked_by
            ) VALUES (?, ?, ?, ?, ?, ?)
            """,
            release.databaseId(),
            validation.contentRevision(),
            routeRegressionRevision,
            validation.passed(),
            validationJson(validation),
            actor
        );
        jdbc.update(
            """
            UPDATE building_map_release
            SET validated_revision = ?,
                validation_passed = ?,
                last_validated_at = CURRENT_TIMESTAMP,
                validated_route_regression_revision = ?
            WHERE id = ?
            """,
            validation.contentRevision(),
            validation.passed(),
            routeRegressionRevision,
            release.databaseId()
        );
    }

    @Transactional
    public void publish(
        UUID releaseId,
        long expectedRevision,
        String actor,
        String reason
    ) {
        ReleaseRow visibleRelease = findRelease(releaseId);
        long routeRegressionRevision =
            lockRouteRegressionConfiguration(
                visibleRelease.buildingDatabaseId()
            );
        ReleaseRow release = lockEditableRelease(
            releaseId,
            expectedRevision
        );
        ValidationState validation = validationState(release.databaseId());
        if (
            validation.validatedRevision() == null
                || validation.validatedRevision() != release.contentRevision()
                || !Boolean.TRUE.equals(validation.passed())
                || validation.routeRegressionRevision() == null
                || validation.routeRegressionRevision()
                    != routeRegressionRevision
        ) {
            AdminValidationResponse failure = new AdminValidationResponse(
                release.publicId(),
                release.contentRevision(),
                false,
                List.of(
                    new AdminValidationResponse.Issue(
                        "VALIDATION_REQUIRED",
                        "release",
                        release.publicId(),
                        "请先对当前草稿版本执行校验并处理全部错误。"
                    )
                ),
                List.of()
            );
            throw new DraftValidationFailedException(failure);
        }

        Long previousReleaseId = jdbc.query(
            """
            SELECT id
            FROM building_map_release
            WHERE building_id = ?
              AND is_active = TRUE
              AND status = 'published'
            """,
            resultSet -> resultSet.next()
                ? resultSet.getLong("id")
                : null,
            release.buildingDatabaseId()
        );
        jdbc.update(
            """
            UPDATE building_map_release
            SET is_active = FALSE,
                updated_at = CURRENT_TIMESTAMP
            WHERE building_id = ?
              AND is_active = TRUE
            """,
            release.buildingDatabaseId()
        );
        int updated = jdbc.update(
            """
            UPDATE building_map_release
            SET status = 'published',
                is_active = TRUE,
                published_by = ?,
                published_at = CURRENT_TIMESTAMP,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
              AND status = 'draft'
            """,
            actor,
            release.databaseId()
        );
        if (updated != 1) {
            throw new OperationConflictException(
                "Draft state changed during publish"
            );
        }
        if (
            previousReleaseId != null
                && previousReleaseId != release.databaseId()
        ) {
            carryActiveOverrides(
                previousReleaseId,
                release.databaseId(),
                release.buildingDatabaseId(),
                actor
            );
        }
        jdbc.update(
            """
            INSERT INTO building_release_event (
                building_id,
                from_release_id,
                to_release_id,
                event_type,
                operated_by,
                reason
            ) VALUES (?, ?, ?, 'publish', ?, ?)
            """,
            release.buildingDatabaseId(),
            previousReleaseId,
            release.databaseId(),
            actor,
            nullableTrimmed(reason)
        );
    }

    @Transactional
    public void rollback(
        UUID targetReleaseId,
        String actor,
        String reason
    ) {
        List<Long> targetLocks = jdbc.query(
            """
            SELECT id
            FROM building_map_release
            WHERE public_id = ?
            FOR UPDATE
            """,
            (rs, rowNum) -> rs.getLong("id"),
            targetReleaseId
        );
        if (targetLocks.isEmpty()) {
            throw new NavigationResourceNotFoundException(
                "Unknown releaseId: " + targetReleaseId
            );
        }
        ReleaseRow target = findRelease(targetReleaseId);
        if (!"published".equals(target.status())) {
            throw new OperationConflictException(
                "Only a published release can be reactivated"
            );
        }
        Long currentReleaseId = jdbc.query(
            """
            SELECT id
            FROM building_map_release
            WHERE building_id = ?
              AND status = 'published'
              AND is_active = TRUE
            """,
            resultSet -> resultSet.next()
                ? resultSet.getLong("id")
                : null,
            target.buildingDatabaseId()
        );
        if (currentReleaseId == null) {
            throw new OperationConflictException(
                "Building has no active published release"
            );
        }
        if (currentReleaseId == target.databaseId()) {
            throw new OperationConflictException(
                "Target release is already active"
            );
        }
        jdbc.queryForObject(
            """
            SELECT id
            FROM building_map_release
            WHERE id = ?
            FOR UPDATE
            """,
            Long.class,
            currentReleaseId
        );

        jdbc.update(
            """
            UPDATE edge_state_override
            SET revoked_by = ?,
                revoked_at = CURRENT_TIMESTAMP
            WHERE release_id = ?
              AND revoked_at IS NULL
            """,
            actor,
            target.databaseId()
        );
        carryActiveOverrides(
            currentReleaseId,
            target.databaseId(),
            target.buildingDatabaseId(),
            actor
        );
        jdbc.update(
            """
            UPDATE building_map_release
            SET is_active = FALSE,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """,
            currentReleaseId
        );
        int activated = jdbc.update(
            """
            UPDATE building_map_release
            SET is_active = TRUE,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
              AND status = 'published'
            """,
            target.databaseId()
        );
        if (activated != 1) {
            throw new OperationConflictException(
                "Historical release could not be reactivated"
            );
        }
        jdbc.update(
            """
            INSERT INTO building_release_event (
                building_id,
                from_release_id,
                to_release_id,
                event_type,
                operated_by,
                reason
            ) VALUES (?, ?, ?, 'rollback', ?, ?)
            """,
            target.buildingDatabaseId(),
            currentReleaseId,
            target.databaseId(),
            actor,
            nullableTrimmed(reason)
        );
    }

    private void carryActiveOverrides(
        long sourceReleaseId,
        long targetReleaseId,
        long buildingDatabaseId,
        String actor
    ) {
        List<OperationOverrideRow> overrides = jdbc.query(
            """
            SELECT
                state.target_type,
                edge.public_id AS edge_public_id,
                link.public_id AS link_public_id,
                connector.public_id AS connector_public_id,
                state.effective_from,
                state.effective_to,
                state.reason,
                state.created_by
            FROM edge_state_override state
            LEFT JOIN path_edge edge
                ON edge.id = state.path_edge_id
            LEFT JOIN vertical_link link
                ON link.id = state.vertical_link_id
            LEFT JOIN vertical_connector connector
                ON connector.id = state.vertical_connector_id
            WHERE state.release_id = ?
              AND state.revoked_at IS NULL
              AND (
                    state.effective_to IS NULL
                    OR state.effective_to > CURRENT_TIMESTAMP
              )
            """,
            (rs, rowNum) -> new OperationOverrideRow(
                rs.getString("target_type"),
                switch (rs.getString("target_type")) {
                    case "path_edge" -> nullableUuid(rs, "edge_public_id");
                    case "vertical_link" -> nullableUuid(
                        rs,
                        "link_public_id"
                    );
                    case "vertical_connector" -> nullableUuid(
                        rs,
                        "connector_public_id"
                    );
                    default -> null;
                },
                instant(rs, "effective_from"),
                nullableInstant(rs, "effective_to"),
                rs.getString("reason"),
                rs.getString("created_by")
            ),
            sourceReleaseId
        );
        for (OperationOverrideRow override : overrides) {
            if (override.targetPublicId() == null) {
                continue;
            }
            String table = switch (override.targetType()) {
                case "path_edge" -> "path_edge";
                case "vertical_link" -> "vertical_link";
                case "vertical_connector" -> "vertical_connector";
                default -> null;
            };
            if (table == null) {
                continue;
            }
            List<Long> targetIds = jdbc.query(
                "SELECT id FROM " + table
                    + " WHERE release_id = ? AND public_id = ?",
                (rs, rowNum) -> rs.getLong("id"),
                targetReleaseId,
                override.targetPublicId()
            );
            if (targetIds.isEmpty()) {
                continue;
            }
            Long pathEdgeId = "path_edge".equals(override.targetType())
                ? targetIds.get(0)
                : null;
            Long verticalLinkId = "vertical_link".equals(
                override.targetType()
            )
                ? targetIds.get(0)
                : null;
            Long connectorId = "vertical_connector".equals(
                override.targetType()
            )
                ? targetIds.get(0)
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
                buildingDatabaseId,
                targetReleaseId,
                override.targetType(),
                pathEdgeId,
                verticalLinkId,
                connectorId,
                Timestamp.from(override.effectiveFrom()),
                override.effectiveTo() == null
                    ? null
                    : Timestamp.from(override.effectiveTo()),
                override.reason(),
                override.createdBy()
            );
        }
        jdbc.update(
            """
            UPDATE edge_state_override
            SET revoked_by = ?,
                revoked_at = CURRENT_TIMESTAMP
            WHERE release_id = ?
              AND revoked_at IS NULL
            """,
            actor,
            sourceReleaseId
        );
    }

    private void ensureBuildingExists(UUID buildingId) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM building WHERE public_id = ?",
            Integer.class,
            buildingId
        );
        if (count == null || count == 0) {
            throw new NavigationResourceNotFoundException(
                "Unknown buildingId: " + buildingId
            );
        }
    }

    private ReleaseRow findDraftSource(
        UUID buildingId,
        UUID basedOnReleaseId
    ) {
        String releaseClause = basedOnReleaseId == null
            ? "r.is_active = TRUE"
            : "r.public_id = ?";
        List<Object> arguments = new ArrayList<>();
        arguments.add(buildingId);
        if (basedOnReleaseId != null) {
            arguments.add(basedOnReleaseId);
        }
        List<ReleaseRow> rows = jdbc.query(
            """
            SELECT
                r.id AS release_db_id,
                r.public_id,
                r.code,
                r.status,
                r.content_revision,
                based.public_id AS based_on_public_id,
                r.description,
                r.created_by,
                r.created_at,
                r.published_by,
                r.published_at,
                b.id AS building_db_id,
                b.public_id AS building_public_id,
                b.code AS building_code,
                b.name AS building_name
            FROM building_map_release r
            JOIN building b ON b.id = r.building_id
            LEFT JOIN building_map_release based
                ON based.id = r.based_on_release_id
            WHERE b.public_id = ?
              AND r.status = 'published'
              AND
            """ + releaseClause,
            this::releaseRow,
            arguments.toArray()
        );
        if (rows.isEmpty()) {
            throw new NavigationResourceNotFoundException(
                basedOnReleaseId == null
                    ? "No active release for buildingId: " + buildingId
                    : "Unknown published basedOnReleaseId: " + basedOnReleaseId
            );
        }
        return rows.get(0);
    }

    private ReleaseRow findRelease(UUID releaseId) {
        List<ReleaseRow> rows = jdbc.query(
            """
            SELECT
                r.id AS release_db_id,
                r.public_id,
                r.code,
                r.status,
                r.content_revision,
                based.public_id AS based_on_public_id,
                r.description,
                r.created_by,
                r.created_at,
                r.published_by,
                r.published_at,
                b.id AS building_db_id,
                b.public_id AS building_public_id,
                b.code AS building_code,
                b.name AS building_name
            FROM building_map_release r
            JOIN building b ON b.id = r.building_id
            LEFT JOIN building_map_release based
                ON based.id = r.based_on_release_id
            WHERE r.public_id = ?
            """,
            this::releaseRow,
            releaseId
        );
        if (rows.isEmpty()) {
            throw new NavigationResourceNotFoundException(
                "Unknown releaseId: " + releaseId
            );
        }
        return rows.get(0);
    }

    private ReleaseRow lockEditableRelease(
        UUID releaseId,
        long expectedRevision
    ) {
        List<Long> lockedRows = jdbc.query(
            """
            SELECT id
            FROM building_map_release
            WHERE public_id = ?
            FOR UPDATE
            """,
            (rs, rowNum) -> rs.getLong("id"),
            releaseId
        );
        if (lockedRows.isEmpty()) {
            throw new NavigationResourceNotFoundException(
                "Unknown releaseId: " + releaseId
            );
        }
        ReleaseRow release = findRelease(releaseId);
        if (!"draft".equals(release.status())) {
            throw new ReleaseImmutableException(releaseId);
        }
        if (release.contentRevision() != expectedRevision) {
            throw new DraftChangedException(
                expectedRevision,
                release.contentRevision()
            );
        }
        return release;
    }

    private long databaseReleaseId(UUID releaseId) {
        Long id = jdbc.queryForObject(
            "SELECT id FROM building_map_release WHERE public_id = ?",
            Long.class,
            releaseId
        );
        if (id == null) {
            throw new NavigationResourceNotFoundException(
                "Unknown releaseId: " + releaseId
            );
        }
        return id;
    }

    private void touchDraft(long releaseDatabaseId) {
        jdbc.update(
            """
            UPDATE building_map_release
            SET content_revision = content_revision + 1,
                updated_at = CURRENT_TIMESTAMP,
                validated_revision = NULL,
                validation_passed = NULL,
                last_validated_at = NULL,
                validated_route_regression_revision = NULL
            WHERE id = ?
            """,
            releaseDatabaseId
        );
    }

    private void copyReleaseFloorMaps(long sourceId, long draftId) {
        jdbc.update(
            """
            INSERT INTO release_floor_map (
                release_id,
                building_id,
                floor_id,
                floor_map_revision_id
            )
            SELECT ?, building_id, floor_id, floor_map_revision_id
            FROM release_floor_map
            WHERE release_id = ?
            """,
            draftId,
            sourceId
        );
    }

    private void copyNodes(long sourceId, long draftId) {
        jdbc.update(
            """
            INSERT INTO path_node (
                release_id,
                floor_id,
                public_id,
                code,
                x,
                y,
                node_type,
                enabled,
                remark
            )
            SELECT ?, floor_id, public_id, code, x, y, node_type, enabled, remark
            FROM path_node
            WHERE release_id = ?
            """,
            draftId,
            sourceId
        );
    }

    private void copyEdges(long sourceId, long draftId) {
        jdbc.update(
            """
            INSERT INTO path_edge (
                release_id,
                floor_id,
                public_id,
                code,
                from_node_id,
                to_node_id,
                time_seconds,
                distance_meters,
                direction,
                edge_type,
                access_scope,
                accessible,
                base_status,
                remark
            )
            SELECT
                ?,
                old_edge.floor_id,
                old_edge.public_id,
                old_edge.code,
                new_from.id,
                new_to.id,
                old_edge.time_seconds,
                old_edge.distance_meters,
                old_edge.direction,
                old_edge.edge_type,
                old_edge.access_scope,
                old_edge.accessible,
                old_edge.base_status,
                old_edge.remark
            FROM path_edge old_edge
            JOIN path_node old_from ON old_from.id = old_edge.from_node_id
            JOIN path_node old_to ON old_to.id = old_edge.to_node_id
            JOIN path_node new_from
                ON new_from.release_id = ?
               AND new_from.public_id = old_from.public_id
            JOIN path_node new_to
                ON new_to.release_id = ?
               AND new_to.public_id = old_to.public_id
            WHERE old_edge.release_id = ?
            """,
            draftId,
            draftId,
            draftId,
            sourceId
        );
    }

    private void copyPois(long sourceId, long draftId) {
        jdbc.update(
            """
            INSERT INTO poi (
                release_id,
                floor_id,
                public_id,
                code,
                node_id,
                name,
                category,
                x,
                y,
                access_scope,
                accessible,
                enabled,
                remark
            )
            SELECT
                ?,
                old_poi.floor_id,
                old_poi.public_id,
                old_poi.code,
                new_node.id,
                old_poi.name,
                old_poi.category,
                old_poi.x,
                old_poi.y,
                old_poi.access_scope,
                old_poi.accessible,
                old_poi.enabled,
                old_poi.remark
            FROM poi old_poi
            JOIN path_node old_node ON old_node.id = old_poi.node_id
            JOIN path_node new_node
                ON new_node.release_id = ?
               AND new_node.public_id = old_node.public_id
            WHERE old_poi.release_id = ?
            """,
            draftId,
            draftId,
            sourceId
        );
        jdbc.update(
            """
            INSERT INTO poi_keyword (poi_id, release_id, keyword)
            SELECT new_poi.id, ?, keyword.keyword
            FROM poi_keyword keyword
            JOIN poi old_poi ON old_poi.id = keyword.poi_id
            JOIN poi new_poi
                ON new_poi.release_id = ?
               AND new_poi.public_id = old_poi.public_id
            WHERE keyword.release_id = ?
            """,
            draftId,
            draftId,
            sourceId
        );
    }

    private void copyConnectors(long sourceId, long draftId) {
        jdbc.update(
            """
            INSERT INTO vertical_connector (
                release_id,
                public_id,
                code,
                name,
                connector_type,
                access_scope,
                accessible,
                base_status,
                remark
            )
            SELECT
                ?,
                public_id,
                code,
                name,
                connector_type,
                access_scope,
                accessible,
                base_status,
                remark
            FROM vertical_connector
            WHERE release_id = ?
            """,
            draftId,
            sourceId
        );
    }

    private void copyStops(long sourceId, long draftId) {
        jdbc.update(
            """
            INSERT INTO connector_stop (
                release_id,
                connector_id,
                floor_id,
                node_id,
                public_id,
                stop_code
            )
            SELECT
                ?,
                new_connector.id,
                old_stop.floor_id,
                new_node.id,
                old_stop.public_id,
                old_stop.stop_code
            FROM connector_stop old_stop
            JOIN vertical_connector old_connector
                ON old_connector.id = old_stop.connector_id
            JOIN path_node old_node ON old_node.id = old_stop.node_id
            JOIN vertical_connector new_connector
                ON new_connector.release_id = ?
               AND new_connector.public_id = old_connector.public_id
            JOIN path_node new_node
                ON new_node.release_id = ?
               AND new_node.public_id = old_node.public_id
            WHERE old_stop.release_id = ?
            """,
            draftId,
            draftId,
            draftId,
            sourceId
        );
    }

    private void copyVerticalLinks(long sourceId, long draftId) {
        jdbc.update(
            """
            INSERT INTO vertical_link (
                release_id,
                connector_id,
                public_id,
                code,
                from_stop_id,
                to_stop_id,
                time_seconds,
                distance_meters,
                direction,
                access_scope,
                accessible,
                base_status,
                remark
            )
            SELECT
                ?,
                new_connector.id,
                old_link.public_id,
                old_link.code,
                new_from.id,
                new_to.id,
                old_link.time_seconds,
                old_link.distance_meters,
                old_link.direction,
                old_link.access_scope,
                old_link.accessible,
                old_link.base_status,
                old_link.remark
            FROM vertical_link old_link
            JOIN vertical_connector old_connector
                ON old_connector.id = old_link.connector_id
            JOIN connector_stop old_from
                ON old_from.id = old_link.from_stop_id
            JOIN connector_stop old_to
                ON old_to.id = old_link.to_stop_id
            JOIN vertical_connector new_connector
                ON new_connector.release_id = ?
               AND new_connector.public_id = old_connector.public_id
            JOIN connector_stop new_from
                ON new_from.release_id = ?
               AND new_from.public_id = old_from.public_id
            JOIN connector_stop new_to
                ON new_to.release_id = ?
               AND new_to.public_id = old_to.public_id
            WHERE old_link.release_id = ?
            """,
            draftId,
            draftId,
            draftId,
            draftId,
            sourceId
        );
    }

    private void deleteGraph(long releaseId) {
        jdbc.update("DELETE FROM vertical_link WHERE release_id = ?", releaseId);
        jdbc.update("DELETE FROM connector_stop WHERE release_id = ?", releaseId);
        jdbc.update(
            "DELETE FROM vertical_connector WHERE release_id = ?",
            releaseId
        );
        jdbc.update("DELETE FROM poi_keyword WHERE release_id = ?", releaseId);
        jdbc.update("DELETE FROM poi WHERE release_id = ?", releaseId);
        jdbc.update("DELETE FROM path_edge WHERE release_id = ?", releaseId);
        jdbc.update("DELETE FROM path_node WHERE release_id = ?", releaseId);
    }

    private Map<UUID, Long> floorDatabaseIds(long releaseId) {
        Map<UUID, Long> ids = new LinkedHashMap<>();
        jdbc.query(
            """
            SELECT f.public_id, f.id
            FROM release_floor_map rfm
            JOIN floor f ON f.id = rfm.floor_id
            WHERE rfm.release_id = ?
            """,
            resultSet -> {
                ids.put(
                    uuid(resultSet, "public_id"),
                    resultSet.getLong("id")
                );
            },
            releaseId
        );
        return ids;
    }

    private Map<UUID, Long> publicDatabaseIds(
        String table,
        long releaseId
    ) {
        Set<String> allowedTables = Set.of(
            "path_node",
            "poi",
            "vertical_connector",
            "connector_stop"
        );
        if (!allowedTables.contains(table)) {
            throw new IllegalArgumentException("Unsupported table: " + table);
        }
        Map<UUID, Long> ids = new LinkedHashMap<>();
        jdbc.query(
            "SELECT public_id, id FROM " + table + " WHERE release_id = ?",
            resultSet -> {
                ids.put(
                    uuid(resultSet, "public_id"),
                    resultSet.getLong("id")
                );
            },
            releaseId
        );
        return ids;
    }

    private void insertNodes(
        long releaseId,
        Map<UUID, Long> floorIds,
        List<DraftGraphPayload.Node> nodes
    ) {
        for (DraftGraphPayload.Node node : nodes) {
            jdbc.update(
                """
                INSERT INTO path_node (
                    release_id,
                    floor_id,
                    public_id,
                    code,
                    x,
                    y,
                    node_type,
                    enabled
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                releaseId,
                requireId(floorIds, node.floorId(), "floor"),
                node.id(),
                node.code(),
                node.x(),
                node.y(),
                node.type(),
                node.enabled()
            );
        }
    }

    private void insertEdges(
        long releaseId,
        Map<UUID, Long> floorIds,
        Map<UUID, Long> nodeIds,
        List<DraftGraphPayload.Edge> edges
    ) {
        for (DraftGraphPayload.Edge edge : edges) {
            jdbc.update(
                """
                INSERT INTO path_edge (
                    release_id,
                    floor_id,
                    public_id,
                    code,
                    from_node_id,
                    to_node_id,
                    time_seconds,
                    distance_meters,
                    direction,
                    edge_type,
                    access_scope,
                    accessible,
                    base_status
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                releaseId,
                requireId(floorIds, edge.floorId(), "floor"),
                edge.id(),
                edge.code(),
                requireId(nodeIds, edge.fromNodeId(), "from node"),
                requireId(nodeIds, edge.toNodeId(), "to node"),
                edge.timeSeconds(),
                edge.distanceMeters(),
                edge.direction(),
                edge.type(),
                edge.accessScope(),
                edge.accessible(),
                status(edge.enabled())
            );
        }
    }

    private void insertPois(
        long releaseId,
        Map<UUID, Long> floorIds,
        Map<UUID, Long> nodeIds,
        List<DraftGraphPayload.Poi> pois
    ) {
        for (DraftGraphPayload.Poi poi : pois) {
            jdbc.update(
                """
                INSERT INTO poi (
                    release_id,
                    floor_id,
                    public_id,
                    code,
                    node_id,
                    name,
                    category,
                    x,
                    y,
                    access_scope,
                    accessible,
                    enabled
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                releaseId,
                requireId(floorIds, poi.floorId(), "floor"),
                poi.id(),
                poi.code(),
                requireId(nodeIds, poi.nodeId(), "POI node"),
                poi.name(),
                poi.category(),
                poi.x(),
                poi.y(),
                poi.accessScope(),
                poi.accessible(),
                poi.enabled()
            );
        }
    }

    private void insertKeywords(
        long releaseId,
        Map<UUID, Long> poiIds,
        List<DraftGraphPayload.Poi> pois
    ) {
        for (DraftGraphPayload.Poi poi : pois) {
            Set<String> uniqueKeywords = new LinkedHashSet<>();
            for (String keyword : poi.keywords()) {
                String value = nullableTrimmed(keyword);
                if (value != null) {
                    uniqueKeywords.add(value);
                }
            }
            for (String keyword : uniqueKeywords) {
                jdbc.update(
                    """
                    INSERT INTO poi_keyword (poi_id, release_id, keyword)
                    VALUES (?, ?, ?)
                    """,
                    requireId(poiIds, poi.id(), "POI"),
                    releaseId,
                    keyword
                );
            }
        }
    }

    private void insertConnectors(
        long releaseId,
        List<DraftGraphPayload.Connector> connectors
    ) {
        for (DraftGraphPayload.Connector connector : connectors) {
            jdbc.update(
                """
                INSERT INTO vertical_connector (
                    release_id,
                    public_id,
                    code,
                    name,
                    connector_type,
                    access_scope,
                    accessible,
                    base_status
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                releaseId,
                connector.id(),
                connector.code(),
                connector.name(),
                connector.type(),
                connector.accessScope(),
                connector.accessible(),
                status(connector.enabled())
            );
        }
    }

    private void insertStops(
        long releaseId,
        Map<UUID, Long> floorIds,
        Map<UUID, Long> nodeIds,
        Map<UUID, Long> connectorIds,
        List<DraftGraphPayload.ConnectorStop> stops
    ) {
        for (DraftGraphPayload.ConnectorStop stop : stops) {
            jdbc.update(
                """
                INSERT INTO connector_stop (
                    release_id,
                    connector_id,
                    floor_id,
                    node_id,
                    public_id,
                    stop_code
                ) VALUES (?, ?, ?, ?, ?, ?)
                """,
                releaseId,
                requireId(connectorIds, stop.connectorId(), "connector"),
                requireId(floorIds, stop.floorId(), "floor"),
                requireId(nodeIds, stop.nodeId(), "stop node"),
                stop.id(),
                stop.code()
            );
        }
    }

    private void insertVerticalLinks(
        long releaseId,
        Map<UUID, Long> connectorIds,
        Map<UUID, Long> stopIds,
        List<DraftGraphPayload.VerticalLink> links
    ) {
        for (DraftGraphPayload.VerticalLink link : links) {
            jdbc.update(
                """
                INSERT INTO vertical_link (
                    release_id,
                    connector_id,
                    public_id,
                    code,
                    from_stop_id,
                    to_stop_id,
                    time_seconds,
                    distance_meters,
                    direction,
                    access_scope,
                    accessible,
                    base_status
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                releaseId,
                requireId(connectorIds, link.connectorId(), "connector"),
                link.id(),
                link.code(),
                requireId(stopIds, link.fromStopId(), "from stop"),
                requireId(stopIds, link.toStopId(), "to stop"),
                link.timeSeconds(),
                link.distanceMeters(),
                link.direction(),
                link.accessScope(),
                link.accessible(),
                status(link.enabled())
            );
        }
    }

    private List<AdminWorkspaceResponse.Floor> loadFloors(long releaseId) {
        return jdbc.query(
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
            (resultSet, rowNumber) -> new AdminWorkspaceResponse.Floor(
                uuid(resultSet, "floor_public_id"),
                resultSet.getString("floor_code"),
                resultSet.getString("floor_name"),
                resultSet.getInt("level_no"),
                new AdminWorkspaceResponse.MapRevision(
                    uuid(resultSet, "map_public_id"),
                    resultSet.getInt("revision_no"),
                    resultSet.getString("image_url"),
                    resultSet.getInt("image_width"),
                    resultSet.getInt("image_height")
                )
            ),
            releaseId
        );
    }

    private DraftGraphPayload loadGraph(long releaseId) {
        return new DraftGraphPayload(
            loadNodes(releaseId),
            loadEdges(releaseId),
            loadPois(releaseId),
            loadConnectors(releaseId),
            loadStops(releaseId),
            loadVerticalLinks(releaseId)
        );
    }

    private List<DraftGraphPayload.Node> loadNodes(long releaseId) {
        return jdbc.query(
            """
            SELECT
                n.public_id,
                n.code,
                f.public_id AS floor_public_id,
                n.x,
                n.y,
                n.node_type,
                n.enabled
            FROM path_node n
            JOIN floor f ON f.id = n.floor_id
            WHERE n.release_id = ?
            ORDER BY f.level_no, n.code
            """,
            (resultSet, rowNumber) -> new DraftGraphPayload.Node(
                uuid(resultSet, "public_id"),
                resultSet.getString("code"),
                uuid(resultSet, "floor_public_id"),
                resultSet.getDouble("x"),
                resultSet.getDouble("y"),
                resultSet.getString("node_type"),
                resultSet.getBoolean("enabled")
            ),
            releaseId
        );
    }

    private List<DraftGraphPayload.Edge> loadEdges(long releaseId) {
        return jdbc.query(
            """
            SELECT
                e.public_id,
                e.code,
                f.public_id AS floor_public_id,
                from_node.public_id AS from_node_public_id,
                to_node.public_id AS to_node_public_id,
                e.time_seconds,
                e.distance_meters,
                e.direction,
                e.edge_type,
                e.access_scope,
                e.accessible,
                e.base_status
            FROM path_edge e
            JOIN floor f ON f.id = e.floor_id
            JOIN path_node from_node ON from_node.id = e.from_node_id
            JOIN path_node to_node ON to_node.id = e.to_node_id
            WHERE e.release_id = ?
            ORDER BY f.level_no, e.code
            """,
            (resultSet, rowNumber) -> new DraftGraphPayload.Edge(
                uuid(resultSet, "public_id"),
                resultSet.getString("code"),
                uuid(resultSet, "floor_public_id"),
                uuid(resultSet, "from_node_public_id"),
                uuid(resultSet, "to_node_public_id"),
                resultSet.getInt("time_seconds"),
                resultSet.getBigDecimal("distance_meters"),
                resultSet.getString("direction"),
                resultSet.getString("edge_type"),
                resultSet.getString("access_scope"),
                resultSet.getBoolean("accessible"),
                "enabled".equals(resultSet.getString("base_status"))
            ),
            releaseId
        );
    }

    private List<DraftGraphPayload.Poi> loadPois(long releaseId) {
        Map<UUID, List<String>> keywords = loadKeywords(releaseId);
        return jdbc.query(
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
                p.access_scope,
                p.accessible,
                p.enabled
            FROM poi p
            JOIN floor f ON f.id = p.floor_id
            JOIN path_node n ON n.id = p.node_id
            WHERE p.release_id = ?
            ORDER BY f.level_no, p.name
            """,
            (resultSet, rowNumber) -> {
                UUID id = uuid(resultSet, "public_id");
                return new DraftGraphPayload.Poi(
                    id,
                    resultSet.getString("code"),
                    resultSet.getString("name"),
                    resultSet.getString("category"),
                    uuid(resultSet, "floor_public_id"),
                    uuid(resultSet, "node_public_id"),
                    resultSet.getDouble("x"),
                    resultSet.getDouble("y"),
                    resultSet.getString("access_scope"),
                    resultSet.getBoolean("accessible"),
                    resultSet.getBoolean("enabled"),
                    keywords.getOrDefault(id, List.of())
                );
            },
            releaseId
        );
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

    private List<DraftGraphPayload.Connector> loadConnectors(long releaseId) {
        return jdbc.query(
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
            (resultSet, rowNumber) -> new DraftGraphPayload.Connector(
                uuid(resultSet, "public_id"),
                resultSet.getString("code"),
                resultSet.getString("name"),
                resultSet.getString("connector_type"),
                resultSet.getString("access_scope"),
                resultSet.getBoolean("accessible"),
                "enabled".equals(resultSet.getString("base_status"))
            ),
            releaseId
        );
    }

    private List<DraftGraphPayload.ConnectorStop> loadStops(long releaseId) {
        return jdbc.query(
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
            ORDER BY c.code, f.level_no
            """,
            (resultSet, rowNumber) -> new DraftGraphPayload.ConnectorStop(
                uuid(resultSet, "public_id"),
                resultSet.getString("stop_code"),
                uuid(resultSet, "connector_public_id"),
                uuid(resultSet, "floor_public_id"),
                uuid(resultSet, "node_public_id")
            ),
            releaseId
        );
    }

    private List<DraftGraphPayload.VerticalLink> loadVerticalLinks(
        long releaseId
    ) {
        return jdbc.query(
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
            WHERE l.release_id = ?
            ORDER BY l.code
            """,
            (resultSet, rowNumber) -> new DraftGraphPayload.VerticalLink(
                uuid(resultSet, "public_id"),
                resultSet.getString("code"),
                uuid(resultSet, "connector_public_id"),
                uuid(resultSet, "from_stop_public_id"),
                uuid(resultSet, "to_stop_public_id"),
                resultSet.getInt("time_seconds"),
                nonNullDistance(
                    resultSet.getBigDecimal("distance_meters")
                ),
                resultSet.getString("direction"),
                resultSet.getString("access_scope"),
                resultSet.getBoolean("accessible"),
                "enabled".equals(resultSet.getString("base_status"))
            ),
            releaseId
        );
    }

    private AdminReleaseSummary releaseSummary(
        ResultSet resultSet,
        int rowNumber
    ) throws SQLException {
        return new AdminReleaseSummary(
            uuid(resultSet, "public_id"),
            resultSet.getString("code"),
            resultSet.getString("status"),
            resultSet.getBoolean("is_active"),
            resultSet.getLong("content_revision"),
            nullableUuid(resultSet, "based_on_public_id"),
            resultSet.getString("description"),
            resultSet.getString("created_by"),
            instant(resultSet, "created_at"),
            resultSet.getString("published_by"),
            nullableInstant(resultSet, "published_at"),
            nullableBoolean(resultSet, "validation_passed"),
            nullableLong(resultSet, "validated_revision")
        );
    }

    private ReleaseRow releaseRow(ResultSet resultSet, int rowNumber)
        throws SQLException {
        return new ReleaseRow(
            resultSet.getLong("release_db_id"),
            uuid(resultSet, "public_id"),
            resultSet.getString("code"),
            resultSet.getString("status"),
            resultSet.getLong("content_revision"),
            nullableUuid(resultSet, "based_on_public_id"),
            resultSet.getString("description"),
            resultSet.getString("created_by"),
            instant(resultSet, "created_at"),
            resultSet.getString("published_by"),
            nullableInstant(resultSet, "published_at"),
            resultSet.getLong("building_db_id"),
            uuid(resultSet, "building_public_id"),
            resultSet.getString("building_code"),
            resultSet.getString("building_name")
        );
    }

    private ValidationState validationState(long releaseId) {
        return jdbc.queryForObject(
            """
            SELECT
                validated_revision,
                validation_passed,
                validated_route_regression_revision
            FROM building_map_release
            WHERE id = ?
            """,
            (resultSet, rowNumber) -> new ValidationState(
                nullableLong(resultSet, "validated_revision"),
                nullableBoolean(resultSet, "validation_passed"),
                nullableLong(
                    resultSet,
                    "validated_route_regression_revision"
                )
            ),
            releaseId
        );
    }

    private long lockRouteRegressionConfiguration(
        long buildingDatabaseId
    ) {
        List<Long> revisions = jdbc.query(
            """
            SELECT revision
            FROM building_route_regression_config
            WHERE building_id = ?
            FOR UPDATE
            """,
            (resultSet, rowNumber) -> resultSet.getLong("revision"),
            buildingDatabaseId
        );
        if (revisions.isEmpty()) {
            throw new IllegalStateException(
                "Missing route regression configuration for building "
                    + buildingDatabaseId
            );
        }
        return revisions.get(0);
    }

    private String validationJson(AdminValidationResponse validation) {
        try {
            return objectMapper.writeValueAsString(validation);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException(
                "Validation result could not be serialized",
                error
            );
        }
    }

    private UUID uuid(ResultSet resultSet, String column)
        throws SQLException {
        Object value = resultSet.getObject(column);
        return value instanceof UUID uuid
            ? uuid
            : UUID.fromString(String.valueOf(value));
    }

    private UUID nullableUuid(ResultSet resultSet, String column)
        throws SQLException {
        Object value = resultSet.getObject(column);
        if (value == null) {
            return null;
        }
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

    private Instant nullableInstant(ResultSet resultSet, String column)
        throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private Boolean nullableBoolean(ResultSet resultSet, String column)
        throws SQLException {
        boolean value = resultSet.getBoolean(column);
        return resultSet.wasNull() ? null : value;
    }

    private Long nullableLong(ResultSet resultSet, String column)
        throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private BigDecimal nonNullDistance(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private Long requireId(
        Map<UUID, Long> ids,
        UUID publicId,
        String type
    ) {
        Long id = ids.get(publicId);
        if (id == null) {
            throw new IllegalArgumentException(
                "Unknown " + type + " id: " + publicId
            );
        }
        return id;
    }

    private String nullableTrimmed(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String status(boolean enabled) {
        return enabled ? "enabled" : "disabled";
    }

    private record ReleaseRow(
        long databaseId,
        UUID publicId,
        String code,
        String status,
        long contentRevision,
        UUID basedOnPublicId,
        String description,
        String createdBy,
        Instant createdAt,
        String publishedBy,
        Instant publishedAt,
        long buildingDatabaseId,
        UUID buildingPublicId,
        String buildingCode,
        String buildingName
    ) {
    }

    private record ValidationState(
        Long validatedRevision,
        Boolean passed,
        Long routeRegressionRevision
    ) {
    }

    private record FloorMapRow(
        long floorDatabaseId,
        int imageWidth,
        int imageHeight
    ) {
    }

    private record OperationOverrideRow(
        String targetType,
        UUID targetPublicId,
        Instant effectiveFrom,
        Instant effectiveTo,
        String reason,
        String createdBy
    ) {
    }
}
