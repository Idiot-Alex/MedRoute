package com.medroute.nav.migration;

import com.medroute.nav.dto.AdminWorkspaceResponse;
import com.medroute.nav.dto.CreateDraftRequest;
import com.medroute.nav.navigation.service.InMemoryPublishedGraphService;
import com.medroute.nav.navigation.service.MapAuthoringService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Transactional
@Rollback
class PostgresqlIdentitySequenceIntegrationTest {
    private static final List<String> DRAFT_ID_TABLES = List.of(
        "building_map_release",
        "path_node",
        "path_edge",
        "poi",
        "vertical_connector",
        "connector_stop",
        "vertical_link"
    );

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("medroute_test")
            .withUsername("medroute")
            .withPassword("medroute");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add(
            "spring.datasource.driver-class-name",
            () -> "org.postgresql.Driver"
        );
        registry.add(
            "spring.flyway.locations",
            () ->
                "classpath:db/migration/common," +
                "classpath:db/migration/postgresql"
        );
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MapAuthoringService authoringService;

    @Test
    void keepsGeneratedIdsAboveExplicitFixturesAndCopiesARealDraft() {
        Map<String, Long> fixtureMaxIds = currentMaxIds(DRAFT_ID_TABLES);

        AdminWorkspaceResponse draft = authoringService.createDraft(
            InMemoryPublishedGraphService.BUILDING_ID,
            new CreateDraftRequest(
                "POSTGRES-SEQUENCE-" + UUID.randomUUID(),
                InMemoryPublishedGraphService.RELEASE_ID,
                "验证 PostgreSQL identity 序列与显式夹具 ID 对齐"
            ),
            "postgres-integration-test"
        );
        long releaseId = jdbc.queryForObject(
            "SELECT id FROM building_map_release WHERE public_id = ?",
            Long.class,
            draft.release().id()
        );

        assertThat(releaseId)
            .as("generated building_map_release id")
            .isGreaterThan(fixtureMaxIds.get("building_map_release"));
        for (String table : DRAFT_ID_TABLES.subList(
            1,
            DRAFT_ID_TABLES.size()
        )) {
            Long minimumCopiedId = jdbc.queryForObject(
                "SELECT MIN(id) FROM " + quoted(table) +
                " WHERE release_id = ?",
                Long.class,
                releaseId
            );
            assertThat(minimumCopiedId)
                .as("first generated id copied into %s", table)
                .isNotNull()
                .isGreaterThan(fixtureMaxIds.get(table));
        }

        assertEveryIdentitySequenceIsAheadOfItsTable();
    }

    private Map<String, Long> currentMaxIds(List<String> tables) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (String table : tables) {
            result.put(table, currentMaxId(table, "id"));
        }
        return result;
    }

    private void assertEveryIdentitySequenceIsAheadOfItsTable() {
        List<IdentityColumn> identityColumns = jdbc.query(
            """
            SELECT table_name, column_name
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND is_identity = 'YES'
            ORDER BY table_name, ordinal_position
            """,
            (resultSet, rowNumber) -> new IdentityColumn(
                resultSet.getString("table_name"),
                resultSet.getString("column_name")
            )
        );

        assertThat(identityColumns).isNotEmpty();
        for (IdentityColumn identity : identityColumns) {
            long maximumId = currentMaxId(
                identity.tableName(),
                identity.columnName()
            );
            String sequenceName = jdbc.queryForObject(
                "SELECT pg_get_serial_sequence(?, ?)",
                String.class,
                "public." + identity.tableName(),
                identity.columnName()
            );
            assertThat(sequenceName)
                .as("identity sequence for %s", identity.tableName())
                .isNotBlank();

            long nextId = jdbc.queryForObject(
                "SELECT nextval(CAST(? AS regclass))",
                Long.class,
                sequenceName
            );
            assertThat(nextId)
                .as("next identity value for %s", identity.tableName())
                .isGreaterThan(maximumId);
        }
    }

    private long currentMaxId(String table, String column) {
        return jdbc.queryForObject(
            "SELECT COALESCE(MAX(" + quoted(column) + "), 0) FROM " +
            quoted(table),
            Long.class
        );
    }

    private String quoted(String identifier) {
        if (!identifier.matches("[a-z_][a-z0-9_]*")) {
            throw new IllegalArgumentException(
                "Unexpected SQL identifier: " + identifier
            );
        }
        return "\"" + identifier + "\"";
    }

    private record IdentityColumn(String tableName, String columnName) {
    }
}
