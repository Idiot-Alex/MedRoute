package com.medroute.nav.navigation.repository;

import com.medroute.nav.navigation.model.GraphArc;
import com.medroute.nav.navigation.model.NavigationGraph;
import com.medroute.nav.navigation.service.InMemoryPublishedGraphService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class JdbcPublishedGraphDisabledNodeIntegrationTest {
    private static final UUID HUB_NODE_ID = id("00002002");
    private static final UUID ELEVATOR_B_2F_NODE_ID = id("00002012");
    private static final UUID ELEVATOR_B_2F_STOP_ID = id("00004112");
    private static final UUID ELEVATOR_B_1F_2F_LINK_ID = id("00005102");
    private static final UUID ELEVATOR_B_2F_3F_LINK_ID = id("00005103");

    @Autowired
    private PublishedGraphRepository graphRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void excludesHorizontalEdgesAndPoisAttachedToADisabledNode() {
        disableNode("N-1F-HUB");

        NavigationGraph graph = activeGraph();

        assertThat(graph.nodes()).doesNotContainKey(HUB_NODE_ID);
        assertThat(graph.pois())
            .doesNotContainKey(InMemoryPublishedGraphService.POI_REGISTRATION_ID);
        assertThat(graph.outgoing().values())
            .flatExtracting(arcs -> arcs)
            .noneMatch(arc -> references(arc, HUB_NODE_ID));
    }

    @Test
    void excludesConnectorStopsAndVerticalLinksAttachedToADisabledNode() {
        disableNode("N-2F-ELEV-B");

        NavigationGraph graph = activeGraph();

        assertThat(graph.nodes()).doesNotContainKey(ELEVATOR_B_2F_NODE_ID);
        assertThat(graph.connectorStops())
            .doesNotContainKey(ELEVATOR_B_2F_STOP_ID);
        assertThat(graph.verticalLinks())
            .doesNotContainKeys(
                ELEVATOR_B_1F_2F_LINK_ID,
                ELEVATOR_B_2F_3F_LINK_ID
            );
        assertThat(
            graph.connectors()
                .get(InMemoryPublishedGraphService.ELEVATOR_B_ID)
                .floorIds()
        ).doesNotContain(InMemoryPublishedGraphService.FLOOR_2_ID);
        assertThat(graph.outgoing().values())
            .flatExtracting(arcs -> arcs)
            .noneMatch(arc -> references(arc, ELEVATOR_B_2F_NODE_ID));
    }

    private NavigationGraph activeGraph() {
        return graphRepository.active(InMemoryPublishedGraphService.BUILDING_ID)
            .graph();
    }

    private void disableNode(String code) {
        int updated = jdbc.update(
            """
            UPDATE path_node
            SET enabled = FALSE
            WHERE release_id = (
                SELECT id
                FROM building_map_release
                WHERE public_id = ?
            )
              AND code = ?
            """,
            InMemoryPublishedGraphService.RELEASE_ID,
            code
        );
        assertThat(updated).isEqualTo(1);
    }

    private boolean references(GraphArc arc, UUID nodeId) {
        return nodeId.equals(arc.fromNodeId())
            || nodeId.equals(arc.toNodeId());
    }

    private static UUID id(String suffix) {
        return UUID.fromString("00000000-0000-0000-0000-" + suffix);
    }
}
