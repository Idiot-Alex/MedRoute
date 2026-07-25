package com.medroute.nav.navigation.repository;

import com.medroute.nav.dto.NavigationRouteRequest;
import com.medroute.nav.model.RouteMode;
import com.medroute.nav.navigation.model.NavigationGraph;
import com.medroute.nav.navigation.service.InMemoryPublishedGraphService;
import com.medroute.nav.navigation.service.MultiFloorRouteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class JdbcPublishedGraphRepositoryIntegrationTest {
    @Autowired
    private PublishedGraphRepository graphRepository;

    @Autowired
    private MultiFloorRouteService routeService;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void migratesAndLoadsThePublishedMultifloorGraph() {
        PublishedGraphSnapshot snapshot = graphRepository.active(
            InMemoryPublishedGraphService.BUILDING_ID
        );
        NavigationGraph graph = snapshot.graph();

        assertThat(snapshot.releaseCode()).isEqualTo("REL-TEST-001");
        assertThat(graph.releaseId())
            .isEqualTo(InMemoryPublishedGraphService.RELEASE_ID);
        assertThat(graph.floors()).hasSize(3);
        assertThat(graph.nodes()).hasSize(15);
        assertThat(graph.pois()).hasSize(4);
        assertThat(
            graph.pois()
                .get(InMemoryPublishedGraphService.POI_CLINIC_2F_ID)
                .searchKeywords()
        ).contains("jianyanke", "jyk");
        assertThat(
            graph.connectors()
                .get(InMemoryPublishedGraphService.ELEVATOR_A_ID)
                .floorIds()
        ).containsExactlyInAnyOrder(
            InMemoryPublishedGraphService.FLOOR_1_ID,
            InMemoryPublishedGraphService.FLOOR_3_ID
        );
        assertThat(
            graph.connectors()
                .get(InMemoryPublishedGraphService.ELEVATOR_B_ID)
                .floorIds()
        ).containsExactlyInAnyOrder(
            InMemoryPublishedGraphService.FLOOR_1_ID,
            InMemoryPublishedGraphService.FLOOR_2_ID,
            InMemoryPublishedGraphService.FLOOR_3_ID
        );
    }

    @Test
    void calculatesAccessibleRouteFromTheDatabaseGraph() {
        var response = routeService.calculateRoute(
            new NavigationRouteRequest(
                InMemoryPublishedGraphService.BUILDING_ID,
                InMemoryPublishedGraphService.RELEASE_ID,
                InMemoryPublishedGraphService.POI_ENTRANCE_ID,
                InMemoryPublishedGraphService.POI_ULTRASOUND_3F_ID,
                RouteMode.ACCESSIBLE
            )
        );

        assertThat(response.summary().estimatedSeconds()).isEqualTo(125);
        assertThat(response.transitions()).hasSize(1);
        assertThat(response.transitions().get(0).connectorCode())
            .isEqualTo("ELEV-A");
    }

    @Test
    @Transactional
    void excludesAConnectorWithAnActiveDatabaseOverride() {
        int inserted = jdbc.update(
            """
            INSERT INTO edge_state_override (
                public_id,
                building_id,
                release_id,
                target_type,
                vertical_connector_id,
                effective_from,
                reason,
                created_by
            )
            SELECT
                ?,
                release.building_id,
                release.id,
                'vertical_connector',
                connector.id,
                ?,
                ?,
                ?
            FROM building_map_release release
            JOIN vertical_connector connector
                ON connector.release_id = release.id
            WHERE release.public_id = ?
              AND connector.public_id = ?
            """,
            UUID.randomUUID(),
            Timestamp.from(Instant.parse("2020-01-01T00:00:00Z")),
            "集成测试：A 电梯停运",
            "integration-test",
            InMemoryPublishedGraphService.RELEASE_ID,
            InMemoryPublishedGraphService.ELEVATOR_A_ID
        );
        assertThat(inserted).isEqualTo(1);

        var response = routeService.calculateRoute(
            new NavigationRouteRequest(
                InMemoryPublishedGraphService.BUILDING_ID,
                InMemoryPublishedGraphService.RELEASE_ID,
                InMemoryPublishedGraphService.POI_ENTRANCE_ID,
                InMemoryPublishedGraphService.POI_ULTRASOUND_3F_ID,
                RouteMode.ACCESSIBLE
            )
        );

        assertThat(response.summary().estimatedSeconds()).isEqualTo(145);
        assertThat(response.transitions()).hasSize(2);
        assertThat(response.transitions())
            .extracting(transition -> transition.connectorCode())
            .containsOnly("ELEV-B");
    }
}
