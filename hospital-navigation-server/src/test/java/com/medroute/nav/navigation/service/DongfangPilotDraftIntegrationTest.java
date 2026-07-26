package com.medroute.nav.navigation.service;

import com.medroute.nav.dto.AdminRouteRegressionCaseResponse;
import com.medroute.nav.dto.AdminValidationResponse;
import com.medroute.nav.dto.AdminWorkspaceResponse;
import com.medroute.nav.dto.RouteRegressionCaseRequest;
import com.medroute.nav.model.RouteMode;
import com.medroute.nav.navigation.algorithm.MultiFloorDijkstraPathFinder;
import com.medroute.nav.navigation.model.ArcType;
import com.medroute.nav.navigation.model.ConnectorSnapshot;
import com.medroute.nav.navigation.model.NavigationGraph;
import com.medroute.nav.navigation.model.PoiSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

import static com.medroute.nav.navigation.service.InMemoryPublishedGraphService.BUILDING_ID;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@Rollback
class DongfangPilotDraftIntegrationTest {
    private static final UUID PILOT_RELEASE_ID = UUID.fromString(
        "00000000-0000-0000-0000-000000000300"
    );

    @Autowired
    private MapAuthoringService authoringService;

    @Autowired
    private DraftNavigationGraphFactory graphFactory;

    @Autowired
    private RouteRegressionService regressionService;

    @Test
    void validatesTheExpandedThreeFloorPilotDraft() {
        AdminWorkspaceResponse workspace = authoringService.workspace(
            PILOT_RELEASE_ID
        );

        assertThat(workspace.release().code())
            .isEqualTo("PILOT-DFSRMYY-001");
        assertThat(workspace.release().status()).isEqualTo("draft");
        assertThat(workspace.floors()).hasSize(3);
        assertThat(workspace.graph().nodes()).hasSize(59);
        assertThat(workspace.graph().edges()).hasSize(58);
        assertThat(workspace.graph().pois()).hasSize(30);
        assertThat(workspace.graph().connectors()).hasSize(3);
        assertThat(workspace.graph().connectorStops()).hasSize(8);
        assertThat(workspace.graph().verticalLinks()).hasSize(5);

        NavigationGraph graph = graphFactory.create(workspace);
        PoiSnapshot entrance = poi(graph, "P-ENTRANCE");
        assertThat(poi(graph, "P-BLOOD-DRAW-2F").searchKeywords())
            .contains("chouxuezhan", "cxz");
        assertThat(connector(graph, "ELEV-A").floorIds()).hasSize(2);
        assertThat(connector(graph, "ELEV-B").floorIds()).hasSize(3);

        MultiFloorDijkstraPathFinder pathFinder =
            new MultiFloorDijkstraPathFinder();
        for (PoiSnapshot destination : graph.pois().values()) {
            if (destination.id().equals(entrance.id())) {
                continue;
            }
            var normal = pathFinder.findPath(
                entrance.nodeId(),
                destination.nodeId(),
                graph,
                PublicRouteArcPolicy.allowed(
                    graph,
                    RouteMode.NORMAL,
                    Set.of(),
                    Set.of()
                )
            );
            assertThat(normal.endNodeId())
                .as("normal route to %s", destination.name())
                .isEqualTo(destination.nodeId());

            if (!destination.accessible()) {
                continue;
            }
            var accessible = pathFinder.findPath(
                entrance.nodeId(),
                destination.nodeId(),
                graph,
                PublicRouteArcPolicy.allowed(
                    graph,
                    RouteMode.ACCESSIBLE,
                    Set.of(),
                    Set.of()
                )
            );
            assertThat(accessible.arcs())
                .as("accessible route to %s", destination.name())
                .noneMatch(arc -> arc.type() == ArcType.STAIRS);
        }

        enablePilotRegressionCases();
        AdminValidationResponse validation = authoringService.validate(
            PILOT_RELEASE_ID,
            0,
            "pilot-acceptance-test"
        );

        assertThat(validation.passed()).isTrue();
        assertThat(validation.errors()).isEmpty();
        assertThat(validation.warnings()).isEmpty();
        assertThat(validation.routeRegressions()).hasSize(12);
        assertThat(validation.routeRegressions())
            .allMatch(result -> result.passed());
        assertThat(validation.routeRegressions())
            .filteredOn(result ->
                result.caseCode().equals(
                    "PILOT-ENTRANCE-TO-PEDIATRICS-ACCESSIBLE"
                )
            )
            .singleElement()
            .satisfies(result ->
                assertThat(result.connectorCodes())
                    .containsExactly("ELEV-B")
            );
    }

    private void enablePilotRegressionCases() {
        var cases = regressionService.list(BUILDING_ID).items().stream()
            .filter(item -> item.code().startsWith("PILOT-"))
            .toList();
        assertThat(cases).hasSize(8);
        assertThat(cases).noneMatch(AdminRouteRegressionCaseResponse::enabled);

        for (AdminRouteRegressionCaseResponse item : cases) {
            regressionService.update(
                item.id(),
                new RouteRegressionCaseRequest(
                    item.code(),
                    item.name(),
                    item.startPoiCode(),
                    item.endPoiCode(),
                    item.routeMode(),
                    item.critical(),
                    true,
                    item.maxDistanceMeters(),
                    item.maxEstimatedSeconds()
                ),
                "pilot-acceptance-test"
            );
        }
    }

    private PoiSnapshot poi(NavigationGraph graph, String code) {
        return graph.pois().values().stream()
            .filter(item -> item.code().equals(code))
            .findFirst()
            .orElseThrow();
    }

    private ConnectorSnapshot connector(
        NavigationGraph graph,
        String code
    ) {
        return graph.connectors().values().stream()
            .filter(item -> item.code().equals(code))
            .findFirst()
            .orElseThrow();
    }
}
