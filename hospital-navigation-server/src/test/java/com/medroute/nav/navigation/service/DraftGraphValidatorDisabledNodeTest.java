package com.medroute.nav.navigation.service;

import com.medroute.nav.dto.AdminValidationResponse;
import com.medroute.nav.dto.AdminWorkspaceResponse;
import com.medroute.nav.dto.DraftGraphPayload;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DraftGraphValidatorDisabledNodeTest {
    private static final UUID BUILDING_ID = id("00000001");
    private static final UUID RELEASE_ID = id("00000002");
    private static final UUID FLOOR_ID = id("00000003");
    private static final UUID MAP_ID = id("00000004");
    private static final UUID NODE_1_ID = id("00000011");
    private static final UUID NODE_2_ID = id("00000012");
    private static final UUID DISABLED_NODE_ID = id("00000013");
    private static final UUID EDGE_1_ID = id("00000021");
    private static final UUID EDGE_TO_DISABLED_NODE_ID = id("00000022");
    private static final UUID POI_1_ID = id("00000031");
    private static final UUID POI_2_ID = id("00000032");
    private static final UUID POI_AT_DISABLED_NODE_ID = id("00000033");
    private static final UUID CONNECTOR_ID = id("00000041");
    private static final UUID STOP_AT_DISABLED_NODE_ID = id("00000042");

    private final DraftGraphValidator validator = new DraftGraphValidator();

    @Test
    void blocksAnEnabledEdgeThatReferencesADisabledNode() {
        DraftGraphPayload base = baseGraph();
        List<DraftGraphPayload.Edge> edges = new ArrayList<>(base.edges());
        edges.add(
            edge(
                EDGE_TO_DISABLED_NODE_ID,
                "EDGE-TO-DISABLED-NODE",
                NODE_2_ID,
                DISABLED_NODE_ID,
                true
            )
        );

        AdminValidationResponse validation = validator.validateForPublish(
            workspace(
                new DraftGraphPayload(
                    base.nodes(),
                    edges,
                    base.pois(),
                    base.connectors(),
                    base.connectorStops(),
                    base.verticalLinks()
                )
            )
        );

        assertIssue(
            validation,
            "EDGE_REFERENCES_DISABLED_NODE",
            "path_edge",
            EDGE_TO_DISABLED_NODE_ID,
            "EDGE-TO-DISABLED-NODE"
        );
    }

    @Test
    void blocksAnEnabledPoiThatReferencesADisabledNode() {
        DraftGraphPayload base = baseGraph();
        List<DraftGraphPayload.Poi> pois = new ArrayList<>(base.pois());
        pois.add(
            poi(
                POI_AT_DISABLED_NODE_ID,
                "P-DISABLED-NODE",
                "停用节点科室",
                DISABLED_NODE_ID,
                true
            )
        );

        AdminValidationResponse validation = validator.validateForPublish(
            workspace(
                new DraftGraphPayload(
                    base.nodes(),
                    base.edges(),
                    pois,
                    base.connectors(),
                    base.connectorStops(),
                    base.verticalLinks()
                )
            )
        );

        assertIssue(
            validation,
            "POI_REFERENCES_DISABLED_NODE",
            "poi",
            POI_AT_DISABLED_NODE_ID,
            "停用节点科室"
        );
    }

    @Test
    void blocksAConnectorStopThatReferencesADisabledNode() {
        DraftGraphPayload base = baseGraph();
        DraftGraphPayload.Connector enabledConnector =
            new DraftGraphPayload.Connector(
                CONNECTOR_ID,
                "ELEV-ENABLED",
                "启用电梯",
                "elevator",
                "public",
                true,
                true
            );
        DraftGraphPayload.ConnectorStop stop =
            new DraftGraphPayload.ConnectorStop(
                STOP_AT_DISABLED_NODE_ID,
                "STOP-DISABLED-NODE",
                CONNECTOR_ID,
                FLOOR_ID,
                DISABLED_NODE_ID
            );

        AdminValidationResponse validation = validator.validateForPublish(
            workspace(
                new DraftGraphPayload(
                    base.nodes(),
                    base.edges(),
                    base.pois(),
                    List.of(enabledConnector),
                    List.of(stop),
                    List.of()
                )
            )
        );

        assertIssue(
            validation,
            "CONNECTOR_STOP_REFERENCES_DISABLED_NODE",
            "connector_stop",
            STOP_AT_DISABLED_NODE_ID,
            "STOP-DISABLED-NODE"
        );
    }

    @Test
    void allowsDisabledEdgesAndPoisToRemainAttachedWhileEditing() {
        DraftGraphPayload base = baseGraph();
        List<DraftGraphPayload.Edge> edges = new ArrayList<>(base.edges());
        edges.add(
            edge(
                EDGE_TO_DISABLED_NODE_ID,
                "DISABLED-EDGE",
                NODE_2_ID,
                DISABLED_NODE_ID,
                false
            )
        );
        List<DraftGraphPayload.Poi> pois = new ArrayList<>(base.pois());
        pois.add(
            poi(
                POI_AT_DISABLED_NODE_ID,
                "P-DISABLED",
                "已停用科室",
                DISABLED_NODE_ID,
                false
            )
        );
        DraftGraphPayload.Connector disabledConnector =
            new DraftGraphPayload.Connector(
                CONNECTOR_ID,
                "ELEV-DISABLED",
                "停用电梯",
                "elevator",
                "public",
                true,
                false
            );
        DraftGraphPayload.ConnectorStop historicalStop =
            new DraftGraphPayload.ConnectorStop(
                STOP_AT_DISABLED_NODE_ID,
                "STOP-DISABLED-NODE",
                CONNECTOR_ID,
                FLOOR_ID,
                DISABLED_NODE_ID
            );

        AdminValidationResponse validation = validator.validateForPublish(
            workspace(
                new DraftGraphPayload(
                    base.nodes(),
                    edges,
                    pois,
                    List.of(disabledConnector),
                    List.of(historicalStop),
                    base.verticalLinks()
                )
            )
        );

        assertThat(validation.passed()).isTrue();
        assertThat(validation.errors()).isEmpty();
    }

    private DraftGraphPayload baseGraph() {
        return new DraftGraphPayload(
            List.of(
                node(NODE_1_ID, "N-ENTRANCE", 10, 10, true),
                node(NODE_2_ID, "N-DESTINATION", 80, 80, true),
                node(DISABLED_NODE_ID, "N-DISABLED", 50, 50, false)
            ),
            List.of(
                edge(
                    EDGE_1_ID,
                    "EDGE-MAIN",
                    NODE_1_ID,
                    NODE_2_ID,
                    true
                )
            ),
            List.of(
                poi(
                    POI_1_ID,
                    "P-ENTRANCE",
                    "入口",
                    "entrance",
                    NODE_1_ID,
                    true
                ),
                poi(POI_2_ID, "P-DESTINATION", "目的地", NODE_2_ID, true)
            ),
            List.of(),
            List.of(),
            List.of()
        );
    }

    private DraftGraphPayload.Node node(
        UUID id,
        String code,
        double x,
        double y,
        boolean enabled
    ) {
        return new DraftGraphPayload.Node(
            id,
            code,
            FLOOR_ID,
            x,
            y,
            "poi_access",
            enabled
        );
    }

    private DraftGraphPayload.Edge edge(
        UUID id,
        String code,
        UUID fromNodeId,
        UUID toNodeId,
        boolean enabled
    ) {
        return new DraftGraphPayload.Edge(
            id,
            code,
            FLOOR_ID,
            fromNodeId,
            toNodeId,
            10,
            BigDecimal.TEN,
            "both",
            "corridor",
            "public",
            true,
            enabled
        );
    }

    private DraftGraphPayload.Poi poi(
        UUID id,
        String code,
        String name,
        UUID nodeId,
        boolean enabled
    ) {
        return poi(id, code, name, "department", nodeId, enabled);
    }

    private DraftGraphPayload.Poi poi(
        UUID id,
        String code,
        String name,
        String category,
        UUID nodeId,
        boolean enabled
    ) {
        return new DraftGraphPayload.Poi(
            id,
            code,
            name,
            category,
            FLOOR_ID,
            nodeId,
            20,
            20,
            "public",
            true,
            enabled,
            List.of()
        );
    }

    private AdminWorkspaceResponse workspace(DraftGraphPayload graph) {
        return new AdminWorkspaceResponse(
            new AdminWorkspaceResponse.Building(
                BUILDING_ID,
                "BUILDING",
                "测试楼栋"
            ),
            new AdminWorkspaceResponse.Release(
                RELEASE_ID,
                "DRAFT",
                "draft",
                0,
                null,
                "测试草稿",
                "test",
                Instant.EPOCH,
                null,
                null
            ),
            List.of(
                new AdminWorkspaceResponse.Floor(
                    FLOOR_ID,
                    "1F",
                    "一层",
                    1,
                    new AdminWorkspaceResponse.MapRevision(
                        MAP_ID,
                        1,
                        "/map.png",
                        100,
                        100
                    )
                )
            ),
            graph
        );
    }

    private void assertIssue(
        AdminValidationResponse validation,
        String code,
        String elementType,
        UUID elementId,
        String messageFragment
    ) {
        assertThat(validation.passed()).isFalse();
        assertThat(validation.errors()).anySatisfy(issue -> {
            assertThat(issue.code()).isEqualTo(code);
            assertThat(issue.elementType()).isEqualTo(elementType);
            assertThat(issue.elementId()).isEqualTo(elementId);
            assertThat(issue.message()).contains(messageFragment, "已停用");
        });
    }

    private static UUID id(String suffix) {
        return UUID.fromString("00000000-0000-0000-0000-" + suffix);
    }
}
