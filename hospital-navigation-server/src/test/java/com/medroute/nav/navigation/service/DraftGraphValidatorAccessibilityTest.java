package com.medroute.nav.navigation.service;

import com.medroute.nav.dto.AdminValidationResponse;
import com.medroute.nav.dto.AdminWorkspaceResponse;
import com.medroute.nav.dto.DraftGraphPayload;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DraftGraphValidatorAccessibilityTest {
    private static final UUID BUILDING_ID = id(1);
    private static final UUID RELEASE_ID = id(2);
    private static final UUID FLOOR_ID = id(3);
    private static final UUID MAP_REVISION_ID = id(4);
    private static final UUID STANDARD_ENTRANCE_NODE_ID = id(10);
    private static final UUID ACCESSIBLE_ENTRANCE_NODE_ID = id(11);
    private static final UUID DESTINATION_NODE_ID = id(12);
    private static final UUID STANDARD_ENTRANCE_POI_ID = id(20);
    private static final UUID ACCESSIBLE_ENTRANCE_POI_ID = id(21);
    private static final UUID DESTINATION_POI_ID = id(22);

    private final DraftGraphValidator validator = new DraftGraphValidator();

    @Test
    void validatesFromAnAccessibleEntranceEvenWhenTheFirstEntranceIsNotAccessible() {
        DraftGraphPayload graph = graph(
            List.of(
                node(STANDARD_ENTRANCE_NODE_ID, "N-STANDARD", 100),
                node(ACCESSIBLE_ENTRANCE_NODE_ID, "N-ACCESSIBLE", 200),
                node(DESTINATION_NODE_ID, "N-DESTINATION", 300)
            ),
            List.of(
                edge(
                    id(30),
                    "E-STANDARD-TO-ACCESSIBLE",
                    STANDARD_ENTRANCE_NODE_ID,
                    ACCESSIBLE_ENTRANCE_NODE_ID,
                    true
                ),
                edge(
                    id(31),
                    "E-ACCESSIBLE-TO-DESTINATION",
                    ACCESSIBLE_ENTRANCE_NODE_ID,
                    DESTINATION_NODE_ID,
                    false
                )
            ),
            List.of(
                poi(
                    STANDARD_ENTRANCE_POI_ID,
                    "P-STANDARD-ENTRANCE",
                    "普通入口",
                    "entrance",
                    STANDARD_ENTRANCE_NODE_ID,
                    100,
                    false
                ),
                poi(
                    ACCESSIBLE_ENTRANCE_POI_ID,
                    "P-ACCESSIBLE-ENTRANCE",
                    "无障碍入口",
                    "entrance",
                    ACCESSIBLE_ENTRANCE_NODE_ID,
                    200,
                    true
                ),
                poi(
                    DESTINATION_POI_ID,
                    "P-DESTINATION",
                    "无障碍目的地",
                    "clinic",
                    DESTINATION_NODE_ID,
                    300,
                    true
                )
            )
        );

        AdminValidationResponse validation = validator.validateForPublish(
            workspace(graph)
        );

        assertThat(validation.errors())
            .filteredOn(issue ->
                issue.code().equals("ACCESSIBLE_POI_UNREACHABLE")
            )
            .extracting(AdminValidationResponse.Issue::elementId)
            .containsExactly(DESTINATION_POI_ID);
        assertThat(validation.errors())
            .extracting(AdminValidationResponse.Issue::code)
            .doesNotContain("NO_ACCESSIBLE_ENTRANCE_POI");
    }

    @Test
    void rejectsAccessiblePoisWhenThereIsNoAccessibleEntrance() {
        DraftGraphPayload graph = graph(
            List.of(
                node(STANDARD_ENTRANCE_NODE_ID, "N-STANDARD", 100),
                node(DESTINATION_NODE_ID, "N-DESTINATION", 300)
            ),
            List.of(
                edge(
                    id(30),
                    "E-TO-DESTINATION",
                    STANDARD_ENTRANCE_NODE_ID,
                    DESTINATION_NODE_ID,
                    true
                )
            ),
            List.of(
                poi(
                    STANDARD_ENTRANCE_POI_ID,
                    "P-STANDARD-ENTRANCE",
                    "普通入口",
                    "entrance",
                    STANDARD_ENTRANCE_NODE_ID,
                    100,
                    false
                ),
                poi(
                    DESTINATION_POI_ID,
                    "P-DESTINATION",
                    "无障碍目的地",
                    "clinic",
                    DESTINATION_NODE_ID,
                    300,
                    true
                )
            )
        );

        AdminValidationResponse validation = validator.validateForPublish(
            workspace(graph)
        );

        assertThat(validation.passed()).isFalse();
        assertThat(validation.errors())
            .extracting(AdminValidationResponse.Issue::code)
            .contains("NO_ACCESSIBLE_ENTRANCE_POI");
    }

    private DraftGraphPayload graph(
        List<DraftGraphPayload.Node> nodes,
        List<DraftGraphPayload.Edge> edges,
        List<DraftGraphPayload.Poi> pois
    ) {
        return new DraftGraphPayload(
            nodes,
            edges,
            pois,
            List.of(),
            List.of(),
            List.of()
        );
    }

    private AdminWorkspaceResponse workspace(DraftGraphPayload graph) {
        return new AdminWorkspaceResponse(
            new AdminWorkspaceResponse.Building(
                BUILDING_ID,
                "TEST",
                "测试楼栋"
            ),
            new AdminWorkspaceResponse.Release(
                RELEASE_ID,
                "DRAFT-TEST",
                "draft",
                0,
                null,
                null,
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
                        MAP_REVISION_ID,
                        1,
                        "/maps/1f.png",
                        1000,
                        1000
                    )
                )
            ),
            graph
        );
    }

    private DraftGraphPayload.Node node(UUID id, String code, double x) {
        return new DraftGraphPayload.Node(
            id,
            code,
            FLOOR_ID,
            x,
            100,
            "poi_access",
            true
        );
    }

    private DraftGraphPayload.Edge edge(
        UUID id,
        String code,
        UUID fromNodeId,
        UUID toNodeId,
        boolean accessible
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
            "walk",
            "public",
            accessible,
            true
        );
    }

    private DraftGraphPayload.Poi poi(
        UUID id,
        String code,
        String name,
        String category,
        UUID nodeId,
        double x,
        boolean accessible
    ) {
        return new DraftGraphPayload.Poi(
            id,
            code,
            name,
            category,
            FLOOR_ID,
            nodeId,
            x,
            100,
            "public",
            accessible,
            true,
            List.of()
        );
    }

    private static UUID id(long value) {
        return new UUID(0, value);
    }
}
