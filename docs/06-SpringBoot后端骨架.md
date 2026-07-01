# Spring Boot 后端骨架

## 1. 项目结构

```text
hospital-navigation-server/
  src/main/java/com/example/nav/
    controller/
      HospitalController.java
      MapController.java
      PoiController.java
      RouteController.java
    service/
      RouteService.java
      GraphService.java
      PoiService.java
    algorithm/
      AStarPathFinder.java
      Graph.java
      GraphNode.java
      GraphEdge.java
    entity/
      Hospital.java
      Building.java
      Floor.java
      FloorMap.java
      PathNode.java
      PathEdge.java
      Poi.java
    repository/
      HospitalRepository.java
      PathNodeRepository.java
      PathEdgeRepository.java
      PoiRepository.java
    dto/
      RouteRequest.java
      RouteResponse.java
      MapGraphResponse.java
```

## 2. A* 核心思路

```java
public class GraphNode {
    private Long id;
    private double x;
    private double y;
}
```

```java
public class GraphEdge {
    private Long from;
    private Long to;
    private double weight;
}
```

## 3. A* 伪代码

```java
public List<Long> findPath(Long startNodeId, Long endNodeId, Graph graph) {
    PriorityQueue<NodeRecord> open = new PriorityQueue<>();
    Set<Long> closed = new HashSet<>();
    Map<Long, Long> cameFrom = new HashMap<>();
    Map<Long, Double> gScore = new HashMap<>();

    open.add(new NodeRecord(startNodeId, 0));
    gScore.put(startNodeId, 0.0);

    while (!open.isEmpty()) {
        NodeRecord current = open.poll();

        if (current.nodeId.equals(endNodeId)) {
            return reconstruct(cameFrom, current.nodeId);
        }

        closed.add(current.nodeId);

        for (GraphEdge edge : graph.getEdges(current.nodeId)) {
            Long next = edge.getTo();

            if (closed.contains(next)) {
                continue;
            }

            double tentative = gScore.get(current.nodeId) + edge.getWeight();

            if (tentative < gScore.getOrDefault(next, Double.MAX_VALUE)) {
                cameFrom.put(next, current.nodeId);
                gScore.put(next, tentative);

                double f = tentative + heuristic(next, endNodeId, graph);
                open.add(new NodeRecord(next, f));
            }
        }
    }

    return Collections.emptyList();
}
```

## 4. 路线服务流程

```text
RouteController
  ↓
RouteService
  ↓
查询 startPoi / endPoi
  ↓
查询 hospital 全部可用 node / edge
  ↓
构建 Graph
  ↓
执行 A*
  ↓
组装 path
  ↓
生成 steps
  ↓
返回前端
```

## 5. 第一版建议

第一版可以先不接数据库，直接在 Java 里写死一份 nodes、edges、pois，验证接口流程。

然后再接 PostgreSQL。
