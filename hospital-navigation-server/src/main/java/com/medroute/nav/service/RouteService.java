package com.medroute.nav.service;

import com.medroute.nav.algorithm.AStarPathFinder;
import com.medroute.nav.dto.PathPoint;
import com.medroute.nav.dto.PoiSummary;
import com.medroute.nav.dto.RouteRequest;
import com.medroute.nav.dto.RouteResponse;
import com.medroute.nav.model.PathEdge;
import com.medroute.nav.model.PathNode;
import com.medroute.nav.model.Poi;
import com.medroute.nav.model.RouteMode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class RouteService {
    private final DemoGraphService graphService;
    private final AStarPathFinder pathFinder = new AStarPathFinder();

    public RouteService(DemoGraphService graphService) {
        this.graphService = graphService;
    }

    public RouteResponse route(RouteRequest request) {
        if (request.hospitalId() != graphService.floor().hospitalId()) {
            throw new IllegalArgumentException("Unknown hospitalId: " + request.hospitalId());
        }
        RouteMode routeMode = RouteMode.from(request.routeMode());
        Poi startPoi = graphService.poi(request.startPoiId())
            .orElseThrow(() -> new IllegalArgumentException("Unknown startPoiId: " + request.startPoiId()));
        Poi endPoi = graphService.poi(request.endPoiId())
            .orElseThrow(() -> new IllegalArgumentException("Unknown endPoiId: " + request.endPoiId()));

        if (startPoi.id().equals(endPoi.id())) {
            throw new IllegalArgumentException("startPoiId and endPoiId must be different");
        }

        List<PathEdge> usableEdges = graphService.edges().stream()
            .filter(edge -> usable(edge, routeMode))
            .toList();

        AStarPathFinder.PathResult result = pathFinder.findPath(
            startPoi.nodeId(),
            endPoi.nodeId(),
            graphService.nodes(),
            usableEdges
        );
        if (!result.found()) {
            throw new IllegalArgumentException("No route found for " + startPoi.id() + " -> " + endPoi.id());
        }

        List<PathPoint> path = result.nodeIds().stream()
            .map(this::pathPoint)
            .toList();
        List<String> steps = buildSteps(startPoi, endPoi, result);
        int estimatedTime = result.edgeIds().stream()
            .map(graphService::edge)
            .flatMap(java.util.Optional::stream)
            .mapToInt(edge -> Math.max(1, edge.walkTime()) * 60)
            .sum();

        return new RouteResponse(
            request.hospitalId(),
            routeMode.apiValue(),
            new PoiSummary(startPoi.id(), startPoi.name()),
            new PoiSummary(endPoi.id(), endPoi.name()),
            result.distance(),
            estimatedTime,
            path,
            steps
        );
    }

    private boolean usable(PathEdge edge, RouteMode routeMode) {
        if (!edge.enabled()) {
            return false;
        }
        if (routeMode == RouteMode.ACCESSIBLE && !edge.accessible()) {
            return false;
        }
        return routeMode != RouteMode.STAFF || "staff".equals(edge.type()) || "walk".equals(edge.type());
    }

    private PathPoint pathPoint(String nodeId) {
        PathNode node = graphService.node(nodeId)
            .orElseThrow(() -> new IllegalStateException("Missing path node: " + nodeId));
        return new PathPoint(node.id(), graphService.floor().floorId(), node.x(), node.y());
    }

    private List<String> buildSteps(Poi startPoi, Poi endPoi, AStarPathFinder.PathResult result) {
        List<String> steps = new ArrayList<>();
        steps.add("从" + startPoi.name() + "出发，进入" + nodeName(result.nodeIds().get(0)) + "。");

        for (int index = 0; index < result.edgeIds().size(); index++) {
            PathEdge edge = graphService.edge(result.edgeIds().get(index))
                .orElseThrow(() -> new IllegalStateException("Missing path edge: " + result.edgeIds().get(index)));
            String fromName = nodeName(result.nodeIds().get(index));
            String toName = nodeName(result.nodeIds().get(index + 1));
            String remark = edge.remark() == null || edge.remark().isBlank() ? "" : " " + edge.remark();
            steps.add("从" + fromName + "前往" + toName + "，约 " + trimDistance(edge.distance()) + " 米。" + remark);
        }

        steps.add("到达" + endPoi.name() + "附近，导航结束。");
        return steps;
    }

    private String nodeName(String nodeId) {
        return graphService.node(nodeId)
            .map(PathNode::name)
            .orElse(nodeId);
    }

    private String trimDistance(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        return String.format("%.1f", value);
    }
}
