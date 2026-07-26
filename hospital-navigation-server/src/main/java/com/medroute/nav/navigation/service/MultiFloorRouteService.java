package com.medroute.nav.navigation.service;

import com.medroute.nav.dto.NavigationRouteRequest;
import com.medroute.nav.dto.NavigationRouteResponse;
import com.medroute.nav.model.RouteMode;
import com.medroute.nav.navigation.algorithm.MultiFloorDijkstraPathFinder;
import com.medroute.nav.navigation.algorithm.MultiFloorDijkstraPathFinder.RoutePath;
import com.medroute.nav.navigation.model.ConnectorSnapshot;
import com.medroute.nav.navigation.model.FloorSnapshot;
import com.medroute.nav.navigation.model.GraphArc;
import com.medroute.nav.navigation.model.GraphNode;
import com.medroute.nav.navigation.model.NavigationGraph;
import com.medroute.nav.navigation.model.PoiSnapshot;
import com.medroute.nav.navigation.repository.PublishedGraphRepository;
import com.medroute.nav.navigation.service.OperationStatusProvider.OperationStatusSnapshot;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

@Service
public class MultiFloorRouteService {
    private final PublishedGraphRepository graphRepository;
    private final OperationStatusProvider operationStatusProvider;
    private final Clock clock;
    private final MultiFloorDijkstraPathFinder pathFinder =
        new MultiFloorDijkstraPathFinder();

    public MultiFloorRouteService(
        PublishedGraphRepository graphRepository,
        OperationStatusProvider operationStatusProvider,
        Clock clock
    ) {
        this.graphRepository = graphRepository;
        this.operationStatusProvider = operationStatusProvider;
        this.clock = clock;
    }

    public NavigationRouteResponse calculateRoute(NavigationRouteRequest request) {
        validate(request);
        Instant calculatedAt = clock.instant();
        NavigationGraph graph = graphRepository
            .active(request.buildingId())
            .graph();

        if (
            request.expectedReleaseId() != null
                && !request.expectedReleaseId().equals(graph.releaseId())
        ) {
            throw new ReleaseMismatchException(
                request.expectedReleaseId(),
                graph.releaseId()
            );
        }

        PoiSnapshot startPoi = requirePoi(graph, request.startPoiId(), "startPoiId");
        PoiSnapshot endPoi = requirePoi(graph, request.endPoiId(), "endPoiId");
        requireNode(graph, startPoi.nodeId());
        requireNode(graph, endPoi.nodeId());
        if (startPoi.id().equals(endPoi.id())) {
            throw new IllegalArgumentException(
                "startPoiId and endPoiId must be different"
            );
        }

        OperationStatusSnapshot operationStatus = operationStatusProvider.status(
            request.buildingId(),
            graph.releaseId(),
            calculatedAt
        );
        Predicate<GraphArc> allowed = PublicRouteArcPolicy.allowed(
            graph,
            request.routeMode(),
            operationStatus.closedElementIds(),
            operationStatus.closedConnectorIds()
        );
        RoutePath path = pathFinder.findPath(
            startPoi.nodeId(),
            endPoi.nodeId(),
            graph,
            allowed
        );

        return assemble(
            graph,
            request.routeMode(),
            calculatedAt,
            startPoi,
            endPoi,
            path
        );
    }

    private NavigationRouteResponse assemble(
        NavigationGraph graph,
        RouteMode routeMode,
        Instant calculatedAt,
        PoiSnapshot startPoi,
        PoiSnapshot endPoi,
        RoutePath path
    ) {
        List<NavigationRouteResponse.RouteSegment> segments = new ArrayList<>();
        List<NavigationRouteResponse.RouteTransition> transitions =
            new ArrayList<>();

        GraphNode currentNode = requireNode(graph, path.startNodeId());
        SegmentAccumulator currentSegment = new SegmentAccumulator(
            currentNode.floorId(),
            point(currentNode)
        );

        for (GraphArc arc : path.arcs()) {
            GraphNode fromNode = requireNode(graph, arc.fromNodeId());
            GraphNode toNode = requireNode(graph, arc.toNodeId());
            if (arc.vertical()) {
                int segmentSequence = addSegment(graph, segments, currentSegment);
                transitions.add(
                    transition(
                        graph,
                        transitions.size() + 1,
                        segmentSequence,
                        arc,
                        fromNode,
                        toNode
                    )
                );
                currentSegment = new SegmentAccumulator(
                    toNode.floorId(),
                    point(toNode)
                );
            } else {
                if (
                    !fromNode.floorId().equals(toNode.floorId())
                        || !currentSegment.floorId().equals(toNode.floorId())
                ) {
                    throw new IllegalStateException(
                        "Horizontal edge crosses floor boundary: " + arc.code()
                    );
                }
                currentSegment.add(arc, point(toNode));
            }
            currentNode = toNode;
        }
        addSegment(graph, segments, currentSegment);

        return new NavigationRouteResponse(
            graph.releaseId(),
            graph.buildingId(),
            routeMode,
            calculatedAt,
            poiRef(graph, startPoi),
            poiRef(graph, endPoi),
            new NavigationRouteResponse.RouteSummary(
                path.cost().distanceMeters(),
                path.cost().timeSeconds()
            ),
            List.copyOf(segments),
            List.copyOf(transitions),
            steps(graph, startPoi, endPoi, path, transitions),
            List.of()
        );
    }

    private int addSegment(
        NavigationGraph graph,
        List<NavigationRouteResponse.RouteSegment> segments,
        SegmentAccumulator accumulator
    ) {
        FloorSnapshot floor = requireFloor(graph, accumulator.floorId());
        int sequence = segments.size() + 1;
        segments.add(
            new NavigationRouteResponse.RouteSegment(
                sequence,
                floor.id(),
                floor.code(),
                floor.mapRevisionId(),
                accumulator.distanceMeters(),
                accumulator.estimatedSeconds(),
                List.copyOf(accumulator.points())
            )
        );
        return sequence;
    }

    private NavigationRouteResponse.RouteTransition transition(
        NavigationGraph graph,
        int sequence,
        int afterSegmentSequence,
        GraphArc arc,
        GraphNode fromNode,
        GraphNode toNode
    ) {
        ConnectorSnapshot connector = graph.connectors().get(arc.connectorId());
        if (connector == null) {
            throw new IllegalStateException(
                "Vertical edge has no connector: " + arc.code()
            );
        }
        FloorSnapshot fromFloor = requireFloor(graph, fromNode.floorId());
        FloorSnapshot toFloor = requireFloor(graph, toNode.floorId());
        String instruction = connector.type()
            == com.medroute.nav.navigation.model.ArcType.ELEVATOR
            ? "乘 " + connector.name() + "至 " + toFloor.code() + "。"
            : "走 " + connector.name() + "至 " + toFloor.code() + "。";

        return new NavigationRouteResponse.RouteTransition(
            sequence,
            afterSegmentSequence,
            connector.type().apiValue(),
            connector.id(),
            connector.code(),
            connector.name(),
            fromFloor.id(),
            fromFloor.code(),
            toFloor.id(),
            toFloor.code(),
            arc.timeSeconds(),
            instruction
        );
    }

    private List<NavigationRouteResponse.RouteStep> steps(
        NavigationGraph graph,
        PoiSnapshot startPoi,
        PoiSnapshot endPoi,
        RoutePath path,
        List<NavigationRouteResponse.RouteTransition> transitions
    ) {
        List<NavigationRouteResponse.RouteStep> steps = new ArrayList<>();
        steps.add(
            new NavigationRouteResponse.RouteStep(
                1,
                "start",
                startPoi.floorId(),
                "从" + startPoi.name() + "出发，沿通道前行。"
            )
        );

        DirectionVector previousDirection = null;
        boolean justChangedFloor = false;
        int transitionIndex = 0;
        for (GraphArc arc : path.arcs()) {
            GraphNode fromNode = requireNode(graph, arc.fromNodeId());
            GraphNode toNode = requireNode(graph, arc.toNodeId());

            if (arc.vertical()) {
                NavigationRouteResponse.RouteTransition transition =
                    transitions.get(transitionIndex++);
                if (previousDirection != null) {
                    addStep(
                        steps,
                        "approach",
                        transition.fromFloorId(),
                        "前往 " + transition.connectorName() + "。"
                    );
                }
                addStep(
                    steps,
                    "transition",
                    transition.fromFloorId(),
                    transition.instruction()
                );
                previousDirection = null;
                justChangedFloor = true;
                continue;
            }

            FloorSnapshot floor = requireFloor(graph, fromNode.floorId());
            if (justChangedFloor) {
                addStep(
                    steps,
                    "continue",
                    floor.id(),
                    "到达 " + floor.code() + "，沿通道继续前行。"
                );
                justChangedFloor = false;
            }

            DirectionVector currentDirection = DirectionVector.between(
                fromNode,
                toNode
            );
            String turn = turnInstruction(
                floor,
                previousDirection,
                currentDirection
            );
            if (turn != null) {
                addStep(steps, "turn", floor.id(), turn);
            }
            previousDirection = currentDirection;
        }

        addStep(
            steps,
            "arrive",
            endPoi.floorId(),
            "到达" + endPoi.name() + "。"
        );
        return List.copyOf(steps);
    }

    private void addStep(
        List<NavigationRouteResponse.RouteStep> steps,
        String type,
        UUID floorId,
        String instruction
    ) {
        steps.add(
            new NavigationRouteResponse.RouteStep(
                steps.size() + 1,
                type,
                floorId,
                instruction
            )
        );
    }

    private String turnInstruction(
        FloorSnapshot floor,
        DirectionVector previous,
        DirectionVector current
    ) {
        if (previous == null || current.lengthSquared() == 0) {
            return null;
        }
        double cross = previous.dx() * current.dy()
            - previous.dy() * current.dx();
        double dot = previous.dx() * current.dx()
            + previous.dy() * current.dy();
        double angle = Math.toDegrees(Math.atan2(Math.abs(cross), dot));
        if (angle < 30) {
            return null;
        }
        if (angle >= 150) {
            return "在 " + floor.code() + " 通道掉头并继续前行。";
        }
        String direction = cross > 0 ? "向右转" : "向左转";
        return "在 " + floor.code() + " 路口" + direction + "，继续前行。";
    }

    private NavigationRouteResponse.PoiRef poiRef(
        NavigationGraph graph,
        PoiSnapshot poi
    ) {
        FloorSnapshot floor = requireFloor(graph, poi.floorId());
        return new NavigationRouteResponse.PoiRef(
            poi.id(),
            poi.code(),
            poi.name(),
            floor.id(),
            floor.code()
        );
    }

    private NavigationRouteResponse.RoutePoint point(GraphNode node) {
        return new NavigationRouteResponse.RoutePoint(
            node.id(),
            node.x(),
            node.y()
        );
    }

    private PoiSnapshot requirePoi(
        NavigationGraph graph,
        UUID poiId,
        String fieldName
    ) {
        PoiSnapshot poi = graph.pois().get(poiId);
        if (poi == null) {
            throw new PoiNotInReleaseException(fieldName, poiId);
        }
        return poi;
    }

    private GraphNode requireNode(NavigationGraph graph, UUID nodeId) {
        GraphNode node = graph.nodes().get(nodeId);
        if (node == null) {
            throw new IllegalStateException("Missing graph node: " + nodeId);
        }
        return node;
    }

    private FloorSnapshot requireFloor(NavigationGraph graph, UUID floorId) {
        FloorSnapshot floor = graph.floors().get(floorId);
        if (floor == null) {
            throw new IllegalStateException("Missing graph floor: " + floorId);
        }
        return floor;
    }

    private void validate(NavigationRouteRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Route request is required");
        }
        if (request.buildingId() == null) {
            throw new IllegalArgumentException("buildingId is required");
        }
        if (request.startPoiId() == null) {
            throw new IllegalArgumentException("startPoiId is required");
        }
        if (request.endPoiId() == null) {
            throw new IllegalArgumentException("endPoiId is required");
        }
        if (request.routeMode() == null) {
            throw new IllegalArgumentException("routeMode is required");
        }
        if (request.routeMode() == RouteMode.STAFF) {
            throw new ForbiddenRouteModeException(request.routeMode().apiValue());
        }
        if (!request.routeMode().supported()) {
            throw new IllegalArgumentException(
                "Unsupported routeMode: " + request.routeMode().apiValue()
            );
        }
    }

    private static final class SegmentAccumulator {
        private final UUID floorId;
        private final List<NavigationRouteResponse.RoutePoint> points =
            new ArrayList<>();
        private BigDecimal distanceMeters = BigDecimal.ZERO;
        private long estimatedSeconds;

        private SegmentAccumulator(
            UUID floorId,
            NavigationRouteResponse.RoutePoint firstPoint
        ) {
            this.floorId = floorId;
            this.points.add(firstPoint);
        }

        private void add(
            GraphArc arc,
            NavigationRouteResponse.RoutePoint nextPoint
        ) {
            distanceMeters = distanceMeters.add(arc.distanceMeters());
            estimatedSeconds = Math.addExact(estimatedSeconds, arc.timeSeconds());
            points.add(nextPoint);
        }

        private UUID floorId() {
            return floorId;
        }

        private List<NavigationRouteResponse.RoutePoint> points() {
            return points;
        }

        private BigDecimal distanceMeters() {
            return distanceMeters;
        }

        private long estimatedSeconds() {
            return estimatedSeconds;
        }
    }

    private record DirectionVector(double dx, double dy) {
        private static DirectionVector between(
            GraphNode fromNode,
            GraphNode toNode
        ) {
            return new DirectionVector(
                toNode.x() - fromNode.x(),
                toNode.y() - fromNode.y()
            );
        }

        private double lengthSquared() {
            return dx * dx + dy * dy;
        }
    }
}
