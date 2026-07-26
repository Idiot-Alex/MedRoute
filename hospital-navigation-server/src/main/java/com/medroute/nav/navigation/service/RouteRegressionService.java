package com.medroute.nav.navigation.service;

import com.medroute.nav.dto.AdminRouteRegressionCaseListResponse;
import com.medroute.nav.dto.AdminRouteRegressionCaseResponse;
import com.medroute.nav.dto.AdminRouteRegressionResult;
import com.medroute.nav.dto.AdminWorkspaceResponse;
import com.medroute.nav.dto.RouteRegressionCaseRequest;
import com.medroute.nav.model.RouteMode;
import com.medroute.nav.navigation.algorithm.MultiFloorDijkstraPathFinder;
import com.medroute.nav.navigation.algorithm.MultiFloorDijkstraPathFinder.RoutePath;
import com.medroute.nav.navigation.algorithm.RouteUnreachableException;
import com.medroute.nav.navigation.model.ArcType;
import com.medroute.nav.navigation.model.ConnectorSnapshot;
import com.medroute.nav.navigation.model.GraphArc;
import com.medroute.nav.navigation.model.NavigationGraph;
import com.medroute.nav.navigation.model.PoiSnapshot;
import com.medroute.nav.navigation.repository.JdbcRouteRegressionCaseRepository;
import com.medroute.nav.navigation.repository.JdbcRouteRegressionCaseRepository.ConfigurationSnapshot;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

@Service
public class RouteRegressionService {
    private final JdbcRouteRegressionCaseRepository repository;
    private final DraftNavigationGraphFactory graphFactory;
    private final MultiFloorDijkstraPathFinder pathFinder =
        new MultiFloorDijkstraPathFinder();

    public RouteRegressionService(
        JdbcRouteRegressionCaseRepository repository,
        DraftNavigationGraphFactory graphFactory
    ) {
        this.repository = repository;
        this.graphFactory = graphFactory;
    }

    public AdminRouteRegressionCaseListResponse list(UUID buildingId) {
        return new AdminRouteRegressionCaseListResponse(
            repository.list(buildingId)
        );
    }

    public AdminRouteRegressionCaseResponse create(
        UUID buildingId,
        RouteRegressionCaseRequest request,
        String actor
    ) {
        return repository.create(
            buildingId,
            normalize(request),
            actor(actor)
        );
    }

    public AdminRouteRegressionCaseResponse update(
        UUID caseId,
        RouteRegressionCaseRequest request,
        String actor
    ) {
        return repository.update(
            caseId,
            normalize(request),
            actor(actor)
        );
    }

    public void delete(UUID caseId) {
        repository.delete(caseId);
    }

    public RouteRegressionRun run(
        AdminWorkspaceResponse workspace
    ) {
        ConfigurationSnapshot configuration =
            repository.configurationForValidation(
                workspace.building().id()
            );
        List<AdminRouteRegressionCaseResponse> cases =
            configuration.cases();
        if (cases.isEmpty()) {
            return new RouteRegressionRun(
                configuration.revision(),
                List.of()
            );
        }

        NavigationGraph graph = graphFactory.create(workspace);
        Map<String, PoiSnapshot> poiByCode = new LinkedHashMap<>();
        for (PoiSnapshot poi : graph.pois().values()) {
            poiByCode.put(poi.code(), poi);
        }

        List<AdminRouteRegressionResult> results = new ArrayList<>();
        for (AdminRouteRegressionCaseResponse regressionCase : cases) {
            results.add(run(regressionCase, graph, poiByCode));
        }
        return new RouteRegressionRun(
            configuration.revision(),
            results
        );
    }

    private AdminRouteRegressionResult run(
        AdminRouteRegressionCaseResponse regressionCase,
        NavigationGraph graph,
        Map<String, PoiSnapshot> poiByCode
    ) {
        PoiSnapshot start = poiByCode.get(regressionCase.startPoiCode());
        PoiSnapshot end = poiByCode.get(regressionCase.endPoiCode());
        if (start == null || end == null) {
            String missing = start == null
                ? regressionCase.startPoiCode()
                : regressionCase.endPoiCode();
            return failure(
                regressionCase,
                start,
                end,
                "POI_NOT_FOUND",
                "当前草稿中找不到已启用的公共 POI：" + missing + "。"
            );
        }
        if (
            !graph.nodes().containsKey(start.nodeId())
                || !graph.nodes().containsKey(end.nodeId())
        ) {
            return failure(
                regressionCase,
                start,
                end,
                "ENDPOINT_NODE_DISABLED",
                "回归路线的起点或终点绑定了停用节点。"
            );
        }
        if (
            regressionCase.routeMode() == RouteMode.ACCESSIBLE
                && (!start.accessible() || !end.accessible())
        ) {
            return failure(
                regressionCase,
                start,
                end,
                "ENDPOINT_NOT_ACCESSIBLE",
                "无障碍回归路线的起点和终点都必须标记为无障碍。"
            );
        }

        Predicate<GraphArc> allowed = PublicRouteArcPolicy.allowed(
            graph,
            regressionCase.routeMode(),
            Set.of(),
            Set.of()
        );
        RoutePath path;
        try {
            path = pathFinder.findPath(
                start.nodeId(),
                end.nodeId(),
                graph,
                allowed
            );
        } catch (RouteUnreachableException error) {
            return failure(
                regressionCase,
                start,
                end,
                "ROUTE_UNREACHABLE",
                "当前草稿在指定路线模式下不可达。"
            );
        }

        List<String> violations = limits(regressionCase, path);
        if (
            regressionCase.routeMode() == RouteMode.ACCESSIBLE
                && path.arcs().stream().anyMatch(
                    arc -> arc.type() == ArcType.STAIRS
                )
        ) {
            violations.add("无障碍路线错误地经过了楼梯");
        }
        List<String> connectorCodes = connectorCodes(graph, path);
        if (!violations.isEmpty()) {
            return new AdminRouteRegressionResult(
                regressionCase.id(),
                regressionCase.code(),
                regressionCase.name(),
                regressionCase.routeMode(),
                regressionCase.critical(),
                regressionCase.startPoiCode(),
                start.name(),
                regressionCase.endPoiCode(),
                end.name(),
                false,
                "LIMIT_EXCEEDED",
                path.cost().distanceMeters(),
                path.cost().timeSeconds(),
                connectorCodes,
                String.join("；", violations) + "。"
            );
        }

        return new AdminRouteRegressionResult(
            regressionCase.id(),
            regressionCase.code(),
            regressionCase.name(),
            regressionCase.routeMode(),
            regressionCase.critical(),
            regressionCase.startPoiCode(),
            start.name(),
            regressionCase.endPoiCode(),
            end.name(),
            true,
            "PASSED",
            path.cost().distanceMeters(),
            path.cost().timeSeconds(),
            connectorCodes,
            "路线可达并符合配置限制。"
        );
    }

    private List<String> limits(
        AdminRouteRegressionCaseResponse regressionCase,
        RoutePath path
    ) {
        List<String> violations = new ArrayList<>();
        Integer maxSeconds = regressionCase.maxEstimatedSeconds();
        if (
            maxSeconds != null
                && path.cost().timeSeconds() > maxSeconds
        ) {
            violations.add(
                "预计耗时 " + path.cost().timeSeconds()
                    + " 秒超过上限 " + maxSeconds + " 秒"
            );
        }
        BigDecimal maxDistance = regressionCase.maxDistanceMeters();
        if (
            maxDistance != null
                && path.cost().distanceMeters().compareTo(maxDistance) > 0
        ) {
            violations.add(
                "距离 " + path.cost().distanceMeters().stripTrailingZeros()
                    .toPlainString()
                    + " 米超过上限 "
                    + maxDistance.stripTrailingZeros().toPlainString()
                    + " 米"
            );
        }
        return violations;
    }

    private List<String> connectorCodes(
        NavigationGraph graph,
        RoutePath path
    ) {
        Set<String> codes = new LinkedHashSet<>();
        for (GraphArc arc : path.arcs()) {
            if (arc.connectorId() == null) {
                continue;
            }
            ConnectorSnapshot connector = graph.connectors().get(
                arc.connectorId()
            );
            if (connector != null) {
                codes.add(connector.code());
            }
        }
        return List.copyOf(codes);
    }

    private AdminRouteRegressionResult failure(
        AdminRouteRegressionCaseResponse regressionCase,
        PoiSnapshot start,
        PoiSnapshot end,
        String resultCode,
        String message
    ) {
        return new AdminRouteRegressionResult(
            regressionCase.id(),
            regressionCase.code(),
            regressionCase.name(),
            regressionCase.routeMode(),
            regressionCase.critical(),
            regressionCase.startPoiCode(),
            start == null ? null : start.name(),
            regressionCase.endPoiCode(),
            end == null ? null : end.name(),
            false,
            resultCode,
            null,
            null,
            List.of(),
            message
        );
    }

    private RouteRegressionCaseRequest normalize(
        RouteRegressionCaseRequest request
    ) {
        if (request == null) {
            throw new IllegalArgumentException(
                "route regression case request is required"
            );
        }
        String code = text(request.code(), "code", 80);
        String name = text(request.name(), "name", 120);
        String startPoiCode = text(
            request.startPoiCode(),
            "startPoiCode",
            80
        );
        String endPoiCode = text(
            request.endPoiCode(),
            "endPoiCode",
            80
        );
        if (startPoiCode.equals(endPoiCode)) {
            throw new IllegalArgumentException(
                "startPoiCode and endPoiCode must be different"
            );
        }
        RouteMode routeMode = request.routeMode() == null
            ? RouteMode.NORMAL
            : request.routeMode();
        if (!routeMode.supported()) {
            throw new IllegalArgumentException(
                "routeMode must be normal or accessible"
            );
        }
        if (
            request.maxDistanceMeters() != null
                && request.maxDistanceMeters().signum() <= 0
        ) {
            throw new IllegalArgumentException(
                "maxDistanceMeters must be positive"
            );
        }
        if (
            request.maxEstimatedSeconds() != null
                && request.maxEstimatedSeconds() <= 0
        ) {
            throw new IllegalArgumentException(
                "maxEstimatedSeconds must be positive"
            );
        }
        return new RouteRegressionCaseRequest(
            code,
            name,
            startPoiCode,
            endPoiCode,
            routeMode,
            request.criticalOrDefault(),
            request.enabledOrDefault(),
            request.maxDistanceMeters(),
            request.maxEstimatedSeconds()
        );
    }

    private String actor(String value) {
        if (value == null || value.isBlank()) {
            return "local-admin";
        }
        return text(value, "admin user", 100);
    }

    private String text(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(
                field + " must not exceed " + maxLength + " characters"
            );
        }
        return normalized;
    }

    public record RouteRegressionRun(
        long configurationRevision,
        List<AdminRouteRegressionResult> results
    ) {
        public RouteRegressionRun {
            results = List.copyOf(results);
        }
    }
}
