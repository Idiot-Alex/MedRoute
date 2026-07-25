package com.medroute.nav.service;

import com.medroute.nav.dto.FloorMapResponse;
import com.medroute.nav.dto.MapGraphResponse;
import com.medroute.nav.model.EdgeDirection;
import com.medroute.nav.model.FloorInfo;
import com.medroute.nav.model.Hospital;
import com.medroute.nav.model.MapGraph;
import com.medroute.nav.model.PathEdge;
import com.medroute.nav.model.PathNode;
import com.medroute.nav.model.Poi;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

@Service
public class DemoGraphService {
    private final MapGraph graph;

    public DemoGraphService() {
        this(buildGraph());
    }

    DemoGraphService(MapGraph graph) {
        this.graph = Objects.requireNonNull(graph, "graph");
    }

    public List<Hospital> hospitals() {
        return List.of(new Hospital(1, "测试医院", "上海"));
    }

    public FloorMapResponse floorMap(long hospitalId, long floorId) {
        ensureDemoScope(hospitalId, floorId);
        FloorInfo floor = graph.floor();
        return new FloorMapResponse(
            floor.floorId(),
            floor.floorName(),
            "/maps/test-hospital/floor-1.png",
            floor.imageWidth(),
            floor.imageHeight()
        );
    }

    public MapGraphResponse floorGraph(long hospitalId, long floorId) {
        ensureDemoScope(hospitalId, floorId);
        return new MapGraphResponse(graph.floor(), graph.nodes(), graph.edges(), graph.pois());
    }

    public List<Poi> pois(long hospitalId, String keyword) {
        ensureDemoHospital(hospitalId);
        if (keyword == null || keyword.isBlank()) {
            return graph.pois();
        }
        String normalized = keyword.trim().toLowerCase(Locale.ROOT);
        return graph.pois().stream()
            .filter(poi -> matchesPoi(poi, normalized))
            .toList();
    }

    public Optional<Poi> poi(String poiId) {
        return graph.pois().stream()
            .filter(poi -> poi.id().equals(poiId))
            .findFirst();
    }

    public Optional<PathNode> node(String nodeId) {
        return graph.nodes().stream()
            .filter(node -> node.id().equals(nodeId))
            .findFirst();
    }

    public Optional<PathEdge> edge(String edgeId) {
        return graph.edges().stream()
            .filter(edge -> edge.id().equals(edgeId))
            .findFirst();
    }

    public List<PathNode> nodes() {
        return graph.nodes();
    }

    public List<PathEdge> edges() {
        return graph.edges();
    }

    public FloorInfo floor() {
        return graph.floor();
    }

    private boolean matchesPoi(Poi poi, String normalized) {
        if (poi.name().toLowerCase(Locale.ROOT).contains(normalized)) {
            return true;
        }
        if (poi.category() != null && poi.category().toLowerCase(Locale.ROOT).contains(normalized)) {
            return true;
        }
        return poi.searchKeywords().stream()
            .anyMatch(keyword -> keyword.toLowerCase(Locale.ROOT).contains(normalized));
    }

    private void ensureDemoScope(long hospitalId, long floorId) {
        ensureDemoHospital(hospitalId);
        if (floorId != graph.floor().floorId()) {
            throw new IllegalArgumentException("Unknown floorId: " + floorId);
        }
    }

    private void ensureDemoHospital(long hospitalId) {
        if (hospitalId != graph.floor().hospitalId()) {
            throw new IllegalArgumentException("Unknown hospitalId: " + hospitalId);
        }
    }

    private static MapGraph buildGraph() {
        FloorInfo floor = new FloorInfo(
            1,
            "测试医院",
            1,
            "门诊楼",
            1,
            "1F",
            1000,
            620,
            "demo-v1"
        );

        List<PathNode> nodes = List.of(
            new PathNode("N1", 170, 300, "挂号处门口", "normal"),
            new PathNode("N2", 320, 320, "大厅西侧", "normal"),
            new PathNode("N3", 500, 320, "大厅中心", "normal"),
            new PathNode("N4", 650, 320, "中庭路口", "normal"),
            new PathNode("N5", 650, 250, "检验科门口", "normal"),
            new PathNode("N6", 820, 320, "药房通道", "normal"),
            new PathNode("N7", 890, 250, "药房门口", "normal"),
            new PathNode("N8", 755, 430, "影像科路口", "normal"),
            new PathNode("N9", 890, 430, "影像科门口", "normal"),
            new PathNode("N10", 335, 390, "卫生间门口", "normal"),
            new PathNode("N11", 145, 390, "入口门厅", "entrance"),
            new PathNode("N12", 640, 390, "电梯 A 门口", "elevator")
        );

        List<PathEdge> edges = List.of(
            new PathEdge("E1", "N11", "N1", EdgeDirection.BOTH, 6, 1, true, "enabled", "walk", "入口门厅到挂号处。"),
            new PathEdge("E2", "N1", "N2", EdgeDirection.BOTH, 12, 1, true, "enabled", "walk", "大厅主通道。"),
            new PathEdge("E3", "N2", "N3", EdgeDirection.BOTH, 14, 1, true, "enabled", "walk", "大厅中心通道。"),
            new PathEdge("E4", "N3", "N4", EdgeDirection.BOTH, 12, 1, true, "enabled", "walk", "中庭主通道。"),
            new PathEdge("E5", "N4", "N5", EdgeDirection.BOTH, 8, 1, true, "enabled", "walk", "检验科入口。"),
            new PathEdge("E6", "N4", "N6", EdgeDirection.BOTH, 13, 1, true, "enabled", "walk", "药房排队区外侧。"),
            new PathEdge("E7", "N6", "N7", EdgeDirection.BOTH, 8, 1, true, "enabled", "walk", "药房取药窗口。"),
            new PathEdge("E8", "N4", "N8", EdgeDirection.BOTH, 14, 1, true, "enabled", "walk", "影像科方向主通道。"),
            new PathEdge("E9", "N8", "N9", EdgeDirection.BOTH, 10, 1, true, "enabled", "walk", "影像科门口。"),
            new PathEdge("E10", "N2", "N10", EdgeDirection.BOTH, 8, 1, true, "enabled", "walk", "卫生间入口。"),
            new PathEdge("E11", "N3", "N12", EdgeDirection.BOTH, 12, 1, true, "enabled", "walk", "电梯厅入口。"),
            new PathEdge("E12", "N12", "N8", EdgeDirection.BOTH, 10, 1, false, "enabled", "walk", "窄通道，轮椅路线不推荐。")
        );

        List<Poi> pois = List.of(
            new Poi("P1", "入口", "entrance", "N11", 145, 414, List.of("入口", "大门")),
            new Poi("P2", "挂号处", "window", "N1", 170, 246, List.of("挂号", "取号")),
            new Poi("P3", "收费处", "window", "N2", 360, 246, List.of("收费", "缴费")),
            new Poi("P4", "检验科", "inspection", "N5", 650, 246, List.of("检验", "抽血")),
            new Poi("P5", "药房", "pharmacy", "N7", 890, 246, List.of("药房", "取药")),
            new Poi("P6", "影像科", "department", "N9", 890, 395, List.of("影像", "CT", "放射")),
            new Poi("P7", "卫生间", "toilet", "N10", 335, 395, List.of("厕所", "卫生间")),
            new Poi("P8", "电梯 A", "elevator", "N12", 640, 395, List.of("电梯", "上楼"))
        );

        return new MapGraph(floor, nodes, edges, pois);
    }
}
