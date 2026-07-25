package com.medroute.nav.navigation.algorithm;

import com.medroute.nav.navigation.model.AccessScope;
import com.medroute.nav.navigation.model.ArcType;
import com.medroute.nav.navigation.model.FloorSnapshot;
import com.medroute.nav.navigation.model.GraphArc;
import com.medroute.nav.navigation.model.GraphNode;
import com.medroute.nav.navigation.model.NavigationGraph;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MultiFloorDijkstraPathFinderTest {
    private static final UUID FLOOR_ID = uuid(1);
    private static final UUID START = uuid(10);
    private static final UUID FAST = uuid(11);
    private static final UUID SHORT = uuid(12);
    private static final UUID END = uuid(13);

    private final MultiFloorDijkstraPathFinder pathFinder =
        new MultiFloorDijkstraPathFinder();

    @Test
    void prefersShorterTimeEvenWhenTheRouteIsLonger() {
        GraphArc fastFirst = arc(101, "FAST-1", START, FAST, 5, 100);
        GraphArc fastSecond = arc(102, "FAST-2", FAST, END, 5, 100);
        GraphArc shortFirst = arc(103, "SHORT-1", START, SHORT, 8, 1);
        GraphArc shortSecond = arc(104, "SHORT-2", SHORT, END, 8, 1);

        MultiFloorDijkstraPathFinder.RoutePath result = pathFinder.findPath(
            START,
            END,
            graph(
                Map.of(
                    START, List.of(fastFirst, shortFirst),
                    FAST, List.of(fastSecond),
                    SHORT, List.of(shortSecond)
                )
            ),
            ignored -> true
        );

        assertEquals(List.of("FAST-1", "FAST-2"), codes(result));
        assertEquals(10, result.cost().timeSeconds());
        assertEquals(new BigDecimal("200"), result.cost().distanceMeters());
    }

    @Test
    void usesDistanceOnlyWhenTimeIsEqual() {
        GraphArc longerFirst = arc(201, "LONGER-1", START, FAST, 5, 20);
        GraphArc longerSecond = arc(202, "LONGER-2", FAST, END, 5, 20);
        GraphArc shorterFirst = arc(203, "CLOSER-1", START, SHORT, 5, 2);
        GraphArc shorterSecond = arc(204, "CLOSER-2", SHORT, END, 5, 3);

        MultiFloorDijkstraPathFinder.RoutePath result = pathFinder.findPath(
            START,
            END,
            graph(
                Map.of(
                    START, List.of(longerFirst, shorterFirst),
                    FAST, List.of(longerSecond),
                    SHORT, List.of(shorterSecond)
                )
            ),
            ignored -> true
        );

        assertEquals(List.of("CLOSER-1", "CLOSER-2"), codes(result));
        assertEquals(10, result.cost().timeSeconds());
        assertEquals(new BigDecimal("5"), result.cost().distanceMeters());
    }

    @Test
    void keepsAccumulatedSecondsInALong() {
        GraphArc first = arc(
            301,
            "LARGE-1",
            START,
            FAST,
            Integer.MAX_VALUE,
            1
        );
        GraphArc second = arc(
            302,
            "LARGE-2",
            FAST,
            END,
            Integer.MAX_VALUE,
            1
        );

        MultiFloorDijkstraPathFinder.RoutePath result = pathFinder.findPath(
            START,
            END,
            graph(Map.of(START, List.of(first), FAST, List.of(second))),
            ignored -> true
        );

        assertEquals(4_294_967_294L, result.cost().timeSeconds());
    }

    @Test
    void doesNotInventAReverseArc() {
        GraphArc oneWay = arc(401, "ONE-WAY", START, END, 10, 10);
        NavigationGraph graph = graph(Map.of(START, List.of(oneWay)));

        pathFinder.findPath(START, END, graph, ignored -> true);

        assertThrows(
            RouteUnreachableException.class,
            () -> pathFinder.findPath(END, START, graph, ignored -> true)
        );
    }

    private NavigationGraph graph(Map<UUID, List<GraphArc>> outgoing) {
        FloorSnapshot floor = new FloorSnapshot(
            FLOOR_ID,
            "1F",
            "1F",
            1,
            uuid(2),
            1,
            "/maps/test.png",
            1000,
            620
        );
        Map<UUID, GraphNode> nodes = Map.of(
            START, node(START, "START"),
            FAST, node(FAST, "FAST"),
            SHORT, node(SHORT, "SHORT"),
            END, node(END, "END")
        );
        return new NavigationGraph(
            uuid(3),
            uuid(4),
            Map.of(FLOOR_ID, floor),
            nodes,
            outgoing,
            Map.of(),
            Map.of()
        );
    }

    private GraphNode node(UUID id, String code) {
        return new GraphNode(id, code, FLOOR_ID, 0, 0, "normal");
    }

    private GraphArc arc(
        long id,
        String code,
        UUID from,
        UUID to,
        int seconds,
        long meters
    ) {
        return new GraphArc(
            uuid(id),
            code,
            from,
            to,
            seconds,
            BigDecimal.valueOf(meters),
            ArcType.WALK,
            AccessScope.PUBLIC,
            true,
            null,
            true
        );
    }

    private List<String> codes(MultiFloorDijkstraPathFinder.RoutePath path) {
        return path.arcs().stream().map(GraphArc::code).toList();
    }

    private static UUID uuid(long value) {
        return UUID.fromString(
            "00000000-0000-0000-0000-" + String.format("%012d", value)
        );
    }
}
