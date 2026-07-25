package com.medroute.nav.algorithm;

import com.medroute.nav.model.EdgeDirection;
import com.medroute.nav.model.PathEdge;
import com.medroute.nav.model.PathNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

public class DijkstraPathFinder {
    public PathResult findPath(
        String startNodeId,
        String endNodeId,
        List<PathNode> nodes,
        List<PathEdge> usableEdges
    ) {
        Set<String> nodeIds = new HashSet<>();
        for (PathNode node : nodes) {
            nodeIds.add(node.id());
        }
        if (!nodeIds.contains(startNodeId) || !nodeIds.contains(endNodeId)) {
            return PathResult.notFound();
        }

        Map<String, List<EdgeStep>> adjacency = buildAdjacency(usableEdges, nodeIds);
        PriorityQueue<NodeRecord> open = new PriorityQueue<>(
            Comparator.comparingDouble(NodeRecord::distance)
        );
        Map<String, String> cameFrom = new HashMap<>();
        Map<String, String> edgeFrom = new HashMap<>();
        Map<String, Double> bestDistance = new HashMap<>();

        open.add(new NodeRecord(startNodeId, 0));
        bestDistance.put(startNodeId, 0.0);

        while (!open.isEmpty()) {
            NodeRecord current = open.poll();
            double currentBest = bestDistance.getOrDefault(
                current.nodeId(),
                Double.POSITIVE_INFINITY
            );
            if (current.distance() > currentBest) {
                continue;
            }
            if (current.nodeId().equals(endNodeId)) {
                return reconstruct(
                    startNodeId,
                    endNodeId,
                    cameFrom,
                    edgeFrom,
                    current.distance()
                );
            }

            for (EdgeStep step : adjacency.getOrDefault(current.nodeId(), List.of())) {
                double candidate = current.distance() + step.distance();
                if (candidate < bestDistance.getOrDefault(
                    step.toNodeId(),
                    Double.POSITIVE_INFINITY
                )) {
                    cameFrom.put(step.toNodeId(), current.nodeId());
                    edgeFrom.put(step.toNodeId(), step.edgeId());
                    bestDistance.put(step.toNodeId(), candidate);
                    open.add(new NodeRecord(step.toNodeId(), candidate));
                }
            }
        }

        return PathResult.notFound();
    }

    private Map<String, List<EdgeStep>> buildAdjacency(
        List<PathEdge> edges,
        Set<String> nodeIds
    ) {
        Map<String, List<EdgeStep>> adjacency = new HashMap<>();
        for (PathEdge edge : edges) {
            if (!nodeIds.contains(edge.from()) || !nodeIds.contains(edge.to())) {
                continue;
            }
            if (!Double.isFinite(edge.distance()) || edge.distance() < 0) {
                throw new IllegalArgumentException(
                    "Path edge distance must be finite and non-negative: " + edge.id()
                );
            }
            EdgeDirection direction = edge.direction();
            if (direction == EdgeDirection.FORWARD || direction == EdgeDirection.BOTH) {
                addStep(adjacency, edge.from(), edge.to(), edge.id(), edge.distance());
            }
            if (direction == EdgeDirection.BOTH) {
                addStep(adjacency, edge.to(), edge.from(), edge.id(), edge.distance());
            }
        }
        return adjacency;
    }

    private void addStep(
        Map<String, List<EdgeStep>> adjacency,
        String fromNodeId,
        String toNodeId,
        String edgeId,
        double distance
    ) {
        adjacency.computeIfAbsent(fromNodeId, ignored -> new ArrayList<>())
            .add(new EdgeStep(toNodeId, edgeId, distance));
    }

    private PathResult reconstruct(
        String startNodeId,
        String endNodeId,
        Map<String, String> cameFrom,
        Map<String, String> edgeFrom,
        double distance
    ) {
        List<String> nodeIds = new ArrayList<>();
        List<String> edgeIds = new ArrayList<>();
        String cursor = endNodeId;
        while (cursor != null) {
            nodeIds.add(0, cursor);
            String edgeId = edgeFrom.get(cursor);
            if (edgeId != null) {
                edgeIds.add(0, edgeId);
            }
            cursor = cameFrom.get(cursor);
        }

        if (nodeIds.isEmpty() || !nodeIds.get(0).equals(startNodeId)) {
            return PathResult.notFound();
        }
        return new PathResult(nodeIds, edgeIds, distance);
    }

    private record NodeRecord(String nodeId, double distance) {
    }

    private record EdgeStep(String toNodeId, String edgeId, double distance) {
    }

    public record PathResult(
        List<String> nodeIds,
        List<String> edgeIds,
        double distance
    ) {
        public static PathResult notFound() {
            return new PathResult(List.of(), List.of(), Double.POSITIVE_INFINITY);
        }

        public boolean found() {
            return !nodeIds.isEmpty();
        }
    }
}
