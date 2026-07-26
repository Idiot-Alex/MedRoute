package com.medroute.nav.navigation.repository;

import com.medroute.nav.dto.AdminRouteRegressionCaseResponse;
import com.medroute.nav.dto.RouteRegressionCaseRequest;
import com.medroute.nav.model.RouteMode;
import com.medroute.nav.navigation.service.NavigationResourceNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class JdbcRouteRegressionCaseRepository {
    private final JdbcTemplate jdbc;

    public JdbcRouteRegressionCaseRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<AdminRouteRegressionCaseResponse> list(UUID buildingId) {
        long buildingDatabaseId = buildingDatabaseId(buildingId);
        return listByBuildingDatabaseId(buildingDatabaseId, false);
    }

    @Transactional
    public ConfigurationSnapshot configurationForValidation(
        UUID buildingId
    ) {
        long buildingDatabaseId = buildingDatabaseId(buildingId);
        long revision = lockConfigurationRevision(buildingDatabaseId);
        return new ConfigurationSnapshot(
            revision,
            listByBuildingDatabaseId(buildingDatabaseId, true)
        );
    }

    @Transactional
    public AdminRouteRegressionCaseResponse create(
        UUID buildingId,
        RouteRegressionCaseRequest request,
        String actor
    ) {
        long buildingDatabaseId = buildingDatabaseId(buildingId);
        lockConfigurationRevision(buildingDatabaseId);
        UUID caseId = UUID.randomUUID();
        jdbc.update(
            """
            INSERT INTO building_route_regression_case (
                public_id,
                building_id,
                code,
                name,
                start_poi_code,
                end_poi_code,
                route_mode,
                critical,
                enabled,
                max_distance_meters,
                max_estimated_seconds,
                created_by,
                updated_by
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            caseId,
            buildingDatabaseId,
            request.code(),
            request.name(),
            request.startPoiCode(),
            request.endPoiCode(),
            request.routeMode().apiValue(),
            request.criticalOrDefault(),
            request.enabledOrDefault(),
            request.maxDistanceMeters(),
            request.maxEstimatedSeconds(),
            actor,
            actor
        );
        incrementConfigurationRevision(buildingDatabaseId);
        invalidateDraftValidations(buildingDatabaseId);
        return find(caseId);
    }

    @Transactional
    public AdminRouteRegressionCaseResponse update(
        UUID caseId,
        RouteRegressionCaseRequest request,
        String actor
    ) {
        CaseOwner owner = owner(caseId);
        lockConfigurationRevision(owner.buildingDatabaseId());
        int updated = jdbc.update(
            """
            UPDATE building_route_regression_case
            SET code = ?,
                name = ?,
                start_poi_code = ?,
                end_poi_code = ?,
                route_mode = ?,
                critical = ?,
                enabled = ?,
                max_distance_meters = ?,
                max_estimated_seconds = ?,
                updated_by = ?,
                updated_at = CURRENT_TIMESTAMP
            WHERE public_id = ?
            """,
            request.code(),
            request.name(),
            request.startPoiCode(),
            request.endPoiCode(),
            request.routeMode().apiValue(),
            request.criticalOrDefault(),
            request.enabledOrDefault(),
            request.maxDistanceMeters(),
            request.maxEstimatedSeconds(),
            actor,
            caseId
        );
        if (updated != 1) {
            throw new NavigationResourceNotFoundException(
                "Unknown route regression caseId: " + caseId
            );
        }
        incrementConfigurationRevision(owner.buildingDatabaseId());
        invalidateDraftValidations(owner.buildingDatabaseId());
        return find(caseId);
    }

    @Transactional
    public void delete(UUID caseId) {
        CaseOwner owner = owner(caseId);
        lockConfigurationRevision(owner.buildingDatabaseId());
        int deleted = jdbc.update(
            "DELETE FROM building_route_regression_case WHERE public_id = ?",
            caseId
        );
        if (deleted != 1) {
            throw new NavigationResourceNotFoundException(
                "Unknown route regression caseId: " + caseId
            );
        }
        incrementConfigurationRevision(owner.buildingDatabaseId());
        invalidateDraftValidations(owner.buildingDatabaseId());
    }

    private List<AdminRouteRegressionCaseResponse> listByBuildingDatabaseId(
        long buildingDatabaseId,
        boolean enabledOnly
    ) {
        String enabledPredicate = enabledOnly ? " AND enabled = TRUE" : "";
        return jdbc.query(
            """
            SELECT
                public_id,
                code,
                name,
                start_poi_code,
                end_poi_code,
                route_mode,
                critical,
                enabled,
                max_distance_meters,
                max_estimated_seconds,
                created_by,
                created_at,
                updated_by,
                updated_at
            FROM building_route_regression_case
            WHERE building_id = ?
            """
                + enabledPredicate
                + " ORDER BY enabled DESC, critical DESC, code",
            this::caseResponse,
            buildingDatabaseId
        );
    }

    private AdminRouteRegressionCaseResponse find(UUID caseId) {
        List<AdminRouteRegressionCaseResponse> cases = jdbc.query(
            """
            SELECT
                public_id,
                code,
                name,
                start_poi_code,
                end_poi_code,
                route_mode,
                critical,
                enabled,
                max_distance_meters,
                max_estimated_seconds,
                created_by,
                created_at,
                updated_by,
                updated_at
            FROM building_route_regression_case
            WHERE public_id = ?
            """,
            this::caseResponse,
            caseId
        );
        if (cases.isEmpty()) {
            throw new NavigationResourceNotFoundException(
                "Unknown route regression caseId: " + caseId
            );
        }
        return cases.get(0);
    }

    private CaseOwner owner(UUID caseId) {
        List<CaseOwner> owners = jdbc.query(
            """
            SELECT building_id
            FROM building_route_regression_case
            WHERE public_id = ?
            """,
            (resultSet, rowNumber) -> new CaseOwner(
                resultSet.getLong("building_id")
            ),
            caseId
        );
        if (owners.isEmpty()) {
            throw new NavigationResourceNotFoundException(
                "Unknown route regression caseId: " + caseId
            );
        }
        return owners.get(0);
    }

    private long buildingDatabaseId(UUID buildingId) {
        List<Long> ids = jdbc.query(
            "SELECT id FROM building WHERE public_id = ?",
            (resultSet, rowNumber) -> resultSet.getLong("id"),
            buildingId
        );
        if (ids.isEmpty()) {
            throw new NavigationResourceNotFoundException(
                "Unknown buildingId: " + buildingId
            );
        }
        return ids.get(0);
    }

    private void invalidateDraftValidations(long buildingDatabaseId) {
        jdbc.update(
            """
            UPDATE building_map_release
            SET validated_revision = NULL,
                validation_passed = NULL,
                last_validated_at = NULL,
                validated_route_regression_revision = NULL
            WHERE building_id = ?
              AND status = 'draft'
            """,
            buildingDatabaseId
        );
    }

    private long lockConfigurationRevision(long buildingDatabaseId) {
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

    private void incrementConfigurationRevision(long buildingDatabaseId) {
        int updated = jdbc.update(
            """
            UPDATE building_route_regression_config
            SET revision = revision + 1,
                updated_at = CURRENT_TIMESTAMP
            WHERE building_id = ?
            """,
            buildingDatabaseId
        );
        if (updated != 1) {
            throw new IllegalStateException(
                "Route regression configuration could not be updated"
            );
        }
    }

    private AdminRouteRegressionCaseResponse caseResponse(
        ResultSet resultSet,
        int rowNumber
    ) throws SQLException {
        return new AdminRouteRegressionCaseResponse(
            uuid(resultSet, "public_id"),
            resultSet.getString("code"),
            resultSet.getString("name"),
            resultSet.getString("start_poi_code"),
            resultSet.getString("end_poi_code"),
            RouteMode.from(resultSet.getString("route_mode")),
            resultSet.getBoolean("critical"),
            resultSet.getBoolean("enabled"),
            resultSet.getBigDecimal("max_distance_meters"),
            nullableInteger(resultSet, "max_estimated_seconds"),
            resultSet.getString("created_by"),
            instant(resultSet, "created_at"),
            resultSet.getString("updated_by"),
            instant(resultSet, "updated_at")
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
        Timestamp value = resultSet.getTimestamp(column);
        if (value == null) {
            throw new IllegalStateException(column + " must not be null");
        }
        return value.toInstant();
    }

    private Integer nullableInteger(ResultSet resultSet, String column)
        throws SQLException {
        int value = resultSet.getInt(column);
        return resultSet.wasNull() ? null : value;
    }

    private record CaseOwner(long buildingDatabaseId) {
    }

    public record ConfigurationSnapshot(
        long revision,
        List<AdminRouteRegressionCaseResponse> cases
    ) {
        public ConfigurationSnapshot {
            cases = List.copyOf(cases);
        }
    }
}
