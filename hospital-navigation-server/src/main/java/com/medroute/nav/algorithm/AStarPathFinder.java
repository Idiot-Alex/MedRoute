package com.medroute.nav.algorithm;

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

public class AStarPathFinder {
    public PathResult findPath(
        String startNodeId,
        String endNodeId,
        List<PathNode> nodes,
        List<PathEdge> usableEdges
    ) {
        Map<String, PathNode> nodeById = new HashMap<>();
        for (PathNode node : nodes) {
            nodeById.put(node.id(), node);
        }

        Map<String, List<EdgeStep>> adjacency = buildAdjacency(usableEdges);
        PriorityQueue<NodeRecord> open = new PriorityQueue<>(Comparator.comparingDouble(NodeRecord::score));
        Set<String> closed = new HashSet<>();
        Map<String, String> cameFrom = new HashMap<>();
        Map<String, String> edgeFrom = new HashMap<>();
        Map<String, Double> gScore = new HashMap<>();

        open.add(new NodeRecord(startNodeId, 0));
        gScore.put(startNodeId, 0.0);

        while (!open.isEmpty()) {
            NodeRecord current = open.poll();
            if (closed.contains(current.nodeId())) {
                continue;
            }
            if (current.nodeId().equals(endNodeId)) {
                return reconstruct(startNodeId, endNodeId, cameFrom, edgeFrom, gScore.get(endNodeId));
            }

            closed.add(current.nodeId());
            for (EdgeStep step : adjacency.getOrDefault(current.nodeId(), List.of())) {
                if (closed.contains(step.toNodeId())) {
                    continue;
                }

                double candidate = gScore.getOrDefault(current.nodeId(), Double.MAX_VALUE) + step.distance();
                if (candidate < gScore.getOrDefault(step.toNodeId(), Double.MAX_VALUE)) {
                    cameFrom.put(step.toNodeId(), current.nodeId());
                    edgeFrom.put(step.toNodeId(), step.edgeId());
                    gScore.put(step.toNodeId(), candidate);

                    double score = candidate + heuristic(step.toNodeId(), endNodeId, nodeById);
                    open.add(new NodeRecord(step.toNodeId(), score));
                }
            }
        }

        return new PathResult(List.of(), List.of(), Double.POSITIVE_INFINITY);
    }

    private Map<String, List<EdgeStep>> buildAdjacency(List<PathEdge> edges) {
        Map<String, List<EdgeStep>> adjacency = new HashMap<>();
        for (PathEdge edge : edges) {
            adjacency.computeIfAbsent(edge.from(), ignored -> new ArrayList<>())
                .add(new EdgeStep(edge.to(), edge.id(), edge.distance()));
            adjacency.computeIfAbsent(edge.to(), ignored -> new ArrayList<>())
                .add(new EdgeStep(edge.from(), edge.id(), edge.distance()));
        }
        return adjacency;
    }

    private double heuristic(String fromNodeId, String toNodeId, Map<String, PathNode> nodeById) {
        PathNode from = nodeById.get(fromNodeId);
        PathNode to = nodeById.get(toNodeId);
        if (from == null || to == null) {
            return 0;
        }
        return Math.hypot(from.x() - to.x(), from.y() - to.y()) / 10.0;
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
            return new PathResult(List.of(), List.of(), Double.POSITIVE_INFINITY);
        }
        return new PathResult(nodeIds, edgeIds, distance);
    }

    private record NodeRecord(String nodeId, double score) {
    }

    private record EdgeStep(String toNodeId, String edgeId, double distance) {
    }

    public record PathResult(
        List<String> nodeIds,
        List<String> edgeIds,
        double distance
    ) {
        public boolean found() {
            return !nodeIds.isEmpty();
        }
    }
}
