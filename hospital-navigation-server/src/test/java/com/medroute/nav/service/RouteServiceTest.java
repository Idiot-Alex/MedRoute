package com.medroute.nav.service;

import com.medroute.nav.dto.RouteRequest;
import com.medroute.nav.dto.RouteResponse;
import com.medroute.nav.model.EdgeDirection;
import com.medroute.nav.model.FloorInfo;
import com.medroute.nav.model.MapGraph;
import com.medroute.nav.model.PathEdge;
import com.medroute.nav.model.PathNode;
import com.medroute.nav.model.Poi;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteServiceTest {
    @Test
    void accessibleRouteAvoidsNonAccessibleEdges() {
        RouteService service = new RouteService(new DemoGraphService());

        RouteResponse normal = service.route(new RouteRequest(1, "P1", "P6", "normal"));
        RouteResponse accessible = service.route(
            new RouteRequest(1, "P1", "P6", "accessible")
        );

        assertEquals(64, normal.distance());
        assertEquals(68, accessible.distance());
        assertTrue(nodeIds(normal).contains("N12"));
        assertFalse(nodeIds(accessible).contains("N12"));
    }

    @Test
    void disabledEdgesAreIgnored() {
        RouteService service = serviceFor(
            List.of(node("N1"), node("N2"), node("N3")),
            List.of(
                edge("DISABLED", "N1", "N3", 1, true, "disabled", "walk"),
                edge("E1", "N1", "N2", 5, true, "enabled", "walk"),
                edge("E2", "N2", "N3", 5, true, "enabled", "walk")
            )
        );

        RouteResponse response = service.route(
            new RouteRequest(1, "START", "END", "normal")
        );

        assertEquals(10, response.distance());
        assertEquals(List.of("N1", "N2", "N3"), nodeIds(response));
    }

    @Test
    void normalRoutesNeverUseStaffOnlyEdges() {
        RouteService service = serviceFor(
            List.of(node("N1"), node("N2"), node("N3")),
            List.of(
                edge("STAFF", "N1", "N3", 1, true, "enabled", "staff"),
                edge("E1", "N1", "N2", 5, true, "enabled", "walk"),
                edge("E2", "N2", "N3", 5, true, "enabled", "walk")
            )
        );

        RouteResponse response = service.route(
            new RouteRequest(1, "START", "END", "normal")
        );

        assertEquals(10, response.distance());
        assertEquals(List.of("N1", "N2", "N3"), nodeIds(response));
    }

    @Test
    void rejectsUnknownAndUnimplementedRouteModes() {
        RouteService service = new RouteService(new DemoGraphService());

        IllegalArgumentException unknown = assertThrows(
            IllegalArgumentException.class,
            () -> service.route(new RouteRequest(1, "P1", "P6", "fastest"))
        );
        IllegalArgumentException unimplemented = assertThrows(
            IllegalArgumentException.class,
            () -> service.route(new RouteRequest(1, "P1", "P6", "less_elevator"))
        );

        assertTrue(unknown.getMessage().contains("Unknown routeMode"));
        assertTrue(unimplemented.getMessage().contains("Unsupported routeMode"));
    }

    private RouteService serviceFor(List<PathNode> nodes, List<PathEdge> edges) {
        FloorInfo floor = new FloorInfo(
            1,
            "测试医院",
            1,
            "门诊楼",
            1,
            "1F",
            1000,
            620,
            "test"
        );
        List<Poi> pois = List.of(
            new Poi("START", "起点", "test", "N1", 0, 0, List.of()),
            new Poi("END", "终点", "test", "N3", 0, 0, List.of())
        );
        return new RouteService(new DemoGraphService(new MapGraph(floor, nodes, edges, pois)));
    }

    private List<String> nodeIds(RouteResponse response) {
        return response.path().stream().map(point -> point.nodeId()).toList();
    }

    private PathNode node(String id) {
        return new PathNode(id, 0, 0, id, "normal");
    }

    private PathEdge edge(
        String id,
        String from,
        String to,
        double distance,
        boolean accessible,
        String status,
        String type
    ) {
        return new PathEdge(
            id,
            from,
            to,
            EdgeDirection.BOTH,
            distance,
            1,
            accessible,
            status,
            type,
            ""
        );
    }
}
