package com.medroute.nav.algorithm;

import com.medroute.nav.model.PathEdge;
import com.medroute.nav.model.PathNode;

import java.util.List;

public class AStarPathFinderSmoke {
    public static void main(String[] args) {
        List<PathNode> nodes = List.of(
            new PathNode("N1", 170, 300, "挂号处门口", "normal"),
            new PathNode("N2", 320, 320, "大厅西侧", "normal"),
            new PathNode("N3", 500, 320, "大厅中心", "normal"),
            new PathNode("N4", 650, 320, "中庭路口", "normal"),
            new PathNode("N8", 755, 430, "影像科路口", "normal"),
            new PathNode("N9", 890, 430, "影像科门口", "normal"),
            new PathNode("N11", 145, 390, "入口门厅", "entrance"),
            new PathNode("N12", 640, 390, "电梯 A 门口", "elevator")
        );
        List<PathEdge> normalEdges = List.of(
            edge("E1", "N11", "N1", 6, true),
            edge("E2", "N1", "N2", 12, true),
            edge("E3", "N2", "N3", 14, true),
            edge("E4", "N3", "N4", 12, true),
            edge("E8", "N4", "N8", 14, true),
            edge("E9", "N8", "N9", 10, true),
            edge("E11", "N3", "N12", 12, true),
            edge("E12", "N12", "N8", 10, false)
        );
        List<PathEdge> accessibleEdges = normalEdges.stream()
            .filter(PathEdge::accessible)
            .toList();

        AStarPathFinder finder = new AStarPathFinder();
        AStarPathFinder.PathResult normal = finder.findPath("N11", "N9", nodes, normalEdges);
        AStarPathFinder.PathResult accessible = finder.findPath("N11", "N9", nodes, accessibleEdges);

        if (!normal.found() || !accessible.found()) {
            throw new IllegalStateException("Expected both routes to be found");
        }
        if (normal.edgeIds().equals(accessible.edgeIds())) {
            throw new IllegalStateException("Expected accessible route to avoid the non-accessible edge");
        }

        System.out.println("normal=" + normal.edgeIds() + ", distance=" + normal.distance());
        System.out.println("accessible=" + accessible.edgeIds() + ", distance=" + accessible.distance());
    }

    private static PathEdge edge(String id, String from, String to, double distance, boolean accessible) {
        return new PathEdge(id, from, to, distance, 1, accessible, "enabled", "walk", "");
    }
}
