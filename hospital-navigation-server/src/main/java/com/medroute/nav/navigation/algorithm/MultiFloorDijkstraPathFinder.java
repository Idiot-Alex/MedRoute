package com.medroute.nav.navigation.algorithm;

import com.medroute.nav.navigation.model.GraphArc;
import com.medroute.nav.navigation.model.NavigationGraph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.UUID;
import java.util.function.Predicate;

public final class MultiFloorDijkstraPathFinder {
    private static final Comparator<QueueEntry> QUEUE_ORDER =
        Comparator.comparing(QueueEntry::cost)
            .thenComparing(entry -> entry.nodeId().toString());

    public RoutePath findPath(
        UUID startNodeId,
        UUID endNodeId,
        NavigationGraph graph,
        Predicate<GraphArc> allowed
    ) {
        if (!graph.nodes().containsKey(startNodeId) || !graph.nodes().containsKey(endNodeId)) {
            throw new IllegalArgumentException("Route endpoints must exist in the graph");
        }

        Map<UUID, RouteCost> best = new HashMap<>();
        Map<UUID, GraphArc> previous = new HashMap<>();
        PriorityQueue<QueueEntry> queue = new PriorityQueue<>(QUEUE_ORDER);

        best.put(startNodeId, RouteCost.ZERO);
        queue.add(new QueueEntry(startNodeId, RouteCost.ZERO));

        while (!queue.isEmpty()) {
            QueueEntry current = queue.poll();
            if (!current.cost().equals(best.get(current.nodeId()))) {
                continue;
            }
            if (current.nodeId().equals(endNodeId)) {
                return reconstruct(previous, startNodeId, endNodeId, current.cost());
            }

            graph.outgoingFrom(current.nodeId()).stream()
                .filter(allowed)
                .sorted(
                    Comparator.comparing((GraphArc arc) -> arc.toNodeId().toString())
                        .thenComparing(arc -> arc.elementId().toString())
                )
                .forEach(arc -> relax(current, arc, best, previous, queue));
        }

        throw new RouteUnreachableException(startNodeId, endNodeId);
    }

    private void relax(
        QueueEntry current,
        GraphArc arc,
        Map<UUID, RouteCost> best,
        Map<UUID, GraphArc> previous,
        PriorityQueue<QueueEntry> queue
    ) {
        RouteCost candidate = current.cost().plus(arc.timeSeconds(), arc.distanceMeters());
        RouteCost currentBest = best.get(arc.toNodeId());
        if (currentBest == null || candidate.compareTo(currentBest) < 0) {
            best.put(arc.toNodeId(), candidate);
            previous.put(arc.toNodeId(), arc);
            queue.add(new QueueEntry(arc.toNodeId(), candidate));
        }
    }

    private RoutePath reconstruct(
        Map<UUID, GraphArc> previous,
        UUID startNodeId,
        UUID endNodeId,
        RouteCost cost
    ) {
        List<GraphArc> reversedArcs = new ArrayList<>();
        UUID cursor = endNodeId;
        while (!cursor.equals(startNodeId)) {
            GraphArc arc = previous.get(cursor);
            if (arc == null) {
                throw new RouteUnreachableException(startNodeId, endNodeId);
            }
            reversedArcs.add(arc);
            cursor = arc.fromNodeId();
        }

        List<GraphArc> arcs = new ArrayList<>(reversedArcs.size());
        for (int index = reversedArcs.size() - 1; index >= 0; index--) {
            arcs.add(reversedArcs.get(index));
        }
        return new RoutePath(startNodeId, endNodeId, arcs, cost);
    }

    private record QueueEntry(UUID nodeId, RouteCost cost) {
    }

    public record RoutePath(
        UUID startNodeId,
        UUID endNodeId,
        List<GraphArc> arcs,
        RouteCost cost
    ) {
        public RoutePath {
            arcs = List.copyOf(arcs);
        }
    }
}
