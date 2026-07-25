package com.medroute.nav.algorithm;

import com.medroute.nav.model.EdgeDirection;
import com.medroute.nav.model.PathEdge;
import com.medroute.nav.model.PathNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DijkstraPathFinderTest {
    private final DijkstraPathFinder pathFinder = new DijkstraPathFinder();

    @Test
    void findsTheShortestRouteWithoutUsingImageCoordinates() {
        List<PathNode> nodes = List.of(
            node("S", 0, 0),
            node("A", 10_000, 10_000),
            node("T", 1, 1)
        );
        List<PathEdge> edges = List.of(
            edge("DIRECT", "S", "T", EdgeDirection.FORWARD, 5),
            edge("LEG-1", "S", "A", EdgeDirection.FORWARD, 1),
            edge("LEG-2", "A", "T", EdgeDirection.FORWARD, 1)
        );

        DijkstraPathFinder.PathResult result = pathFinder.findPath("S", "T", nodes, edges);

        assertTrue(result.found());
        assertEquals(List.of("S", "A", "T"), result.nodeIds());
        assertEquals(List.of("LEG-1", "LEG-2"), result.edgeIds());
        assertEquals(2, result.distance());
    }

    @Test
    void respectsForwardEdgeDirection() {
        List<PathNode> nodes = List.of(node("A", 0, 0), node("B", 1, 1));
        List<PathEdge> edges = List.of(
            edge("ONE-WAY", "A", "B", EdgeDirection.FORWARD, 1)
        );

        assertTrue(pathFinder.findPath("A", "B", nodes, edges).found());
        assertFalse(pathFinder.findPath("B", "A", nodes, edges).found());
    }

    @Test
    void keepsBidirectionalEdgesCompatibleWithTheExistingDemo() {
        List<PathNode> nodes = List.of(node("A", 0, 0), node("B", 1, 1));
        PathEdge legacyStyleEdge = new PathEdge(
            "TWO-WAY",
            "A",
            "B",
            1,
            1,
            true,
            "enabled",
            "walk",
            ""
        );

        assertEquals(EdgeDirection.BOTH, legacyStyleEdge.direction());
        assertTrue(pathFinder.findPath("A", "B", nodes, List.of(legacyStyleEdge)).found());
        assertTrue(pathFinder.findPath("B", "A", nodes, List.of(legacyStyleEdge)).found());
    }

    @Test
    void reportsUnreachableDestinations() {
        List<PathNode> nodes = List.of(node("A", 0, 0), node("B", 1, 1));

        DijkstraPathFinder.PathResult result = pathFinder.findPath(
            "A",
            "B",
            nodes,
            List.of()
        );

        assertFalse(result.found());
        assertTrue(Double.isInfinite(result.distance()));
        assertEquals(List.of(), result.nodeIds());
        assertEquals(List.of(), result.edgeIds());
    }

    @Test
    void ignoresEdgesWhoseEndpointsAreMissingFromTheGraph() {
        List<PathNode> nodes = List.of(node("S", 0, 0), node("T", 1, 1));
        List<PathEdge> edges = List.of(
            edge("DIRECT", "S", "T", EdgeDirection.FORWARD, 10),
            edge("DANGLING-1", "S", "MISSING", EdgeDirection.FORWARD, 1),
            edge("DANGLING-2", "MISSING", "T", EdgeDirection.FORWARD, 1)
        );

        DijkstraPathFinder.PathResult result = pathFinder.findPath("S", "T", nodes, edges);

        assertTrue(result.found());
        assertEquals(List.of("S", "T"), result.nodeIds());
        assertEquals(List.of("DIRECT"), result.edgeIds());
        assertEquals(10, result.distance());
    }

    private PathNode node(String id, double x, double y) {
        return new PathNode(id, x, y, id, "normal");
    }

    private PathEdge edge(
        String id,
        String from,
        String to,
        EdgeDirection direction,
        double distance
    ) {
        return new PathEdge(
            id,
            from,
            to,
            direction,
            distance,
            1,
            true,
            "enabled",
            "walk",
            ""
        );
    }
}
