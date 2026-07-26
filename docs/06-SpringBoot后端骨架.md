# Spring Boot 后端骨架

## 1. 适用范围与技术结论

本文定义 MedRoute 第一版 Spring Boot 后端的可实施结构，并与
`04-数据库设计.md`、`05-接口设计.md` 和 `09-多楼层导航实施方案.md`
保持一致。

> 当前实现状态（2026-07-26）：阶段 1–3 的最小可用范围已完成。
> 正式路线读取 JDBC 发布图，Flyway、草稿乐观锁、校验、发布、回滚和持久化
> 运营封闭已落地；Spring Security、正式数据范围授权和 Bean Validation
> 留待生产化阶段。

第一版技术结论：

- 部署一个 Spring Boot 模块化单体，不拆微服务。
- 使用 PostgreSQL + Flyway；暂不依赖 PostGIS。
- 使用 Dijkstra，以 `time_seconds` 为主成本。
- 路线计算范围是一个 `building_map_release`，不按整个医院加载图。
- 已发布图不可变；草稿编辑、发布切换和临时运营覆盖分别处理。
- 前端只展示后端返回的 `segments`、`transitions` 和 `steps`，不自行计算路线。
- 不引入消息队列或 Redis。需要缓存时先使用进程内、按 `releaseId` 键控的不可变图缓存。

当前内存三层图仅作为测试 fixture，正式数据源为 PostgreSQL。

## 2. 推荐依赖

```text
spring-boot-starter-web
spring-boot-starter-validation
spring-boot-starter-security
spring-boot-starter-jdbc
flyway-core
postgresql
spring-boot-starter-test
spring-security-test
```

路线图读取推荐使用 Spring JDBC/JdbcClient，一次按 `release_id` 批量加载节点、边、POI、垂直设施、停靠点和跨层边，避免 ORM 的 N+1 查询。普通目录维护若采用其他持久化方式，不得改变数据库和 API 契约。

## 3. 模块化单体结构

沿用当前基础包 `com.medroute.nav`，按领域能力拆包：

```text
hospital-navigation-server/
  pom.xml
  src/main/java/com/medroute/nav/
    HospitalNavigationApplication.java

    catalog/
      domain/
        Hospital.java
        Campus.java
        Building.java
        Floor.java
        FloorMapRevision.java
        BuildingMapRelease.java
      application/
        CatalogQueryService.java
        NavigationContextService.java
      infrastructure/
        JdbcCatalogRepository.java

    authoring/
      domain/
        DraftRelease.java
        ReleaseValidationResult.java
      application/
        DraftReleaseService.java
        GraphAuthoringService.java
        ReleaseValidationService.java
        ReleasePublicationService.java
      infrastructure/
        JdbcDraftRepository.java
        JdbcPublicationRepository.java

    navigation/
      domain/
        GraphNode.java
        GraphArc.java
        NavigationGraph.java
        RouteCost.java
        RoutePath.java
        RouteMode.java
      algorithm/
        DijkstraPathFinder.java
      application/
        RouteApplicationService.java
        PublishedGraphLoader.java
        RoutePolicy.java
        RouteSegmentAssembler.java
        InstructionGenerator.java
      infrastructure/
        JdbcPublishedGraphRepository.java
        PublishedGraphCache.java

    operations/
      domain/
        OperationOverride.java
      application/
        OperationOverrideService.java
        EffectiveOverrideQuery.java
      infrastructure/
        JdbcOperationOverrideRepository.java

    api/
      navigation/
        HospitalController.java
        BuildingController.java
        PoiController.java
        GraphController.java
        RouteController.java
        dto/
      admin/
        DraftReleaseController.java
        MapAuthoringController.java
        PublicationController.java
        OperationOverrideController.java
        dto/
      error/
        ApiError.java
        ApiExceptionHandler.java

    security/
      SecurityConfiguration.java
      BuildingScopeAuthorizer.java
      CurrentPrincipal.java

    shared/
      clock/
        SystemClockConfiguration.java
      id/
        PublicId.java
      transaction/
        AfterCommitActions.java

  src/main/resources/
    application.yml
    db/migration/
      V1__create_catalog_tables.sql
      V2__create_release_tables.sql
      V3__create_graph_tables.sql
      V4__create_vertical_connector_tables.sql
      V5__create_operation_and_audit_tables.sql
      V6__create_indexes.sql

  src/test/java/com/medroute/nav/
    navigation/
    authoring/
    operations/
    api/
```

包边界规则：

- `navigation` 不直接调用 `authoring` 的草稿仓储。
- `authoring` 负责草稿和发布动作，不负责用户路线计算。
- `operations` 只管理临时覆盖，不更新发布图元素。
- `api` 只做协议转换、参数校验和权限入口，不放路线算法。
- 跨模块调用通过 `application` 层接口，不能绕过接口访问其他模块的 JDBC Repository。

## 4. 发布图领域模型

数据库行不应直接作为算法对象。图加载器将发布数据转换为不可变内存图：

```java
public record GraphNode(
    UUID id,
    UUID floorId,
    double x,
    double y
) {}

public record GraphArc(
    UUID elementId,
    UUID fromNodeId,
    UUID toNodeId,
    int timeSeconds,
    BigDecimal distanceMeters,
    ArcType type,
    AccessScope accessScope,
    boolean accessible,
    UUID connectorId
) {}

public record NavigationGraph(
    UUID releaseId,
    UUID buildingId,
    Map<UUID, GraphNode> nodes,
    Map<UUID, List<GraphArc>> outgoing,
    Map<UUID, PoiSnapshot> pois,
    Map<UUID, ConnectorSnapshot> connectors
) {}
```

`elementId` 对楼层内边是 `path_edge.public_id`，对跨层边是
`vertical_link.public_id`。`connectorId` 只在跨层边上有值。

### 4.1 方向装配

- `direction = forward`：只生成 `from → to` 一条 `GraphArc`。
- `direction = both`：生成 `from → to` 和 `to → from` 两条 `GraphArc`。
- `path_edge` 的两个端点必须在同一楼层。
- `vertical_link` 通过两个 `connector_stop.node_id` 生成跨层弧。
- 不能根据楼层顺序或相同电梯编号自动生成跨层弧。

### 4.2 图加载范围

`PublishedGraphLoader.load(UUID releaseId)` 接收 API 公开 UUID，仓储先将它解析为
`building_map_release.public_id` 对应的内部主键，再一次只加载：

```text
building_map_release.public_id = releaseId
release_floor_map
path_node
path_edge
poi
vertical_connector
connector_stop
vertical_link
```

不得继续使用“查询 hospital 全部节点和边”的旧流程。所有 SQL 都必须包含 `release_id` 条件。

## 5. Dijkstra 路线算法

### 5.1 成本规则

路线成本使用非负的词典序二元组：

```text
主排序：累计 timeSeconds
次排序：累计 distanceMeters
```

因此先选择预计耗时最短路线；耗时完全相同时选择距离较短路线。队列最终再以节点 UUID 排序，使相同数据的结果稳定可复现。

第一版不使用图片像素坐标作为启发函数。不同楼层图片没有共同坐标系，且像素距离与秒/米不在同一量纲，无法保证 A* 启发函数可采纳。只有将来建立经过校准的统一米制坐标后，才可以另行评估 A*。

### 5.2 伪代码

```java
public RoutePath findPath(
    UUID startNodeId,
    UUID endNodeId,
    NavigationGraph graph,
    Predicate<GraphArc> allowed
) {
    Map<UUID, RouteCost> best = new HashMap<>();
    Map<UUID, GraphArc> previous = new HashMap<>();
    PriorityQueue<QueueEntry> queue =
        new PriorityQueue<>(QueueEntry.BY_COST_THEN_NODE_ID);

    best.put(startNodeId, RouteCost.ZERO);
    queue.add(new QueueEntry(startNodeId, RouteCost.ZERO));

    while (!queue.isEmpty()) {
        QueueEntry current = queue.poll();

        if (!current.cost().equals(best.get(current.nodeId()))) {
            continue; // 跳过优先队列中的旧记录
        }

        if (current.nodeId().equals(endNodeId)) {
            return reconstruct(previous, startNodeId, endNodeId);
        }

        for (GraphArc arc : graph.outgoingFrom(current.nodeId())) {
            if (!allowed.test(arc)) {
                continue;
            }

            RouteCost candidate = current.cost().plus(
                arc.timeSeconds(),
                arc.distanceMeters()
            );

            if (candidate.isBetterThan(best.get(arc.toNodeId()))) {
                best.put(arc.toNodeId(), candidate);
                previous.put(arc.toNodeId(), arc);
                queue.add(new QueueEntry(arc.toNodeId(), candidate));
            }
        }
    }

    throw new RouteUnreachableException();
}
```

实现约束：

- `timeSeconds` 必须大于零，距离不得为负。
- 累计秒数使用 `long` 并检查溢出。
- 不用空列表表达不可达，抛出领域异常并映射为 `422 ROUTE_UNREACHABLE`。
- 不把数据库实体或可变集合暴露给算法。
- 路径重建保存实际 `GraphArc`，不能只保存节点 ID；否则无法组装方向、跨层动作和距离。

## 6. 路线模式与边过滤

`RoutePolicy` 负责把身份、路线模式、发布图状态和临时覆盖组合成边过滤器：

```java
public interface RoutePolicy {
    Predicate<GraphArc> allowedArcs(
        RouteMode mode,
        PrincipalContext principal,
        Set<UUID> closedElementIds,
        Set<UUID> closedConnectorIds
    );
}
```

规则：

- `normal`：只允许 `accessScope = public`，并排除静态禁用元素和有效的临时关闭。
- `accessible`：在 `normal` 基础上，排除楼梯以及所有 `accessible = false` 的边和设施。
- `staff`：允许 `public` 和 `staff` 元素，但只有 `BuildingScopeAuthorizer` 确认具有 `STAFF_ROUTE_READ` 和楼栋范围后才可使用。
- 客户端传入 `staff` 字符串不构成授权。
- 整个垂直设施被关闭时，它的所有 `vertical_link` 都被排除。

模式逻辑集中在 `RoutePolicy`，不能散落在 Controller、SQL 和算法中形成不同规则。

## 7. 路线服务流程

`RouteApplicationService` 的固定流程：

```text
校验请求和楼栋读取权限
        ↓
捕获 calculatedAt
        ↓
读取楼栋当前启用 releaseId
        ↓
校验 expectedReleaseId（如果提供）
        ↓
在该 releaseId 中解析 startPoi / endPoi
        ↓
加载不可变完整楼栋图
        ↓
查询 calculatedAt 时刻有效的临时覆盖
        ↓
按 routeMode 和身份构造 RoutePolicy
        ↓
Dijkstra 计算最低通行成本路径
        ↓
按 vertical_link 切分楼层 segments
        ↓
组装 transitions、summary 和 steps
        ↓
返回实际 releaseId
```

建议方法签名：

```java
public RouteResponse calculateRoute(
    RouteCommand command,
    PrincipalContext principal
);
```

`RouteCommand` 使用 API 公开 UUID，不使用数据库自增 ID。

### 7.1 一致性边界

路线请求使用一个只读 `REPEATABLE_READ` 事务：

1. 从注入的 `Clock` 捕获一次 `calculatedAt`。
2. 固定当前启用 `releaseId`。
3. 所有 POI、图和覆盖读取使用同一事务快照。
4. 覆盖查询以 `calculatedAt` 判断有效区间。

发布图本身不可变，因此可安全命中按 `releaseId` 缓存的图；临时覆盖不能永久并入该缓存。

如果请求提供 `expectedReleaseId` 且与当前启用版本不同，在加载图前返回
`409 RELEASE_MISMATCH`。

### 7.2 分段与跨层动作

`RouteSegmentAssembler` 根据实际路径弧组装响应：

- 连续 `path_edge` 聚合为同一楼层 `segment`。
- 每条实际使用的 `vertical_link` 产生 `transition`。
- 同一设施连续的多条跨层边可以合并为一个从起始层到目标层的 transition，但不能虚构未实际经过的连接。
- `segment.mapRevisionId` 来自该发布版本的 `release_floor_map`。
- `summary.estimatedSeconds` 等于所有弧耗时之和。
- `summary.distanceMeters` 等于所有弧配置距离之和，不从像素坐标推算。

### 7.3 文字步骤

`InstructionGenerator` 按决策点生成：

- 起步。
- 明显转向或通道变化。
- 进入电梯、楼梯或扶梯。
- 跨层抵达后的继续方向。
- 最终到达。

连续直行边应合并。不得直接输出“经过节点 N3”作为用户指引。

## 8. 草稿、校验、发布和回滚

### 8.1 草稿修改

所有草稿写操作：

1. 确认 `status = draft`。
2. 校验 `If-Match` 等于 `content_revision`。
3. 在一个事务中修改元素并递增 `content_revision`。
4. 返回新的 ETag。

对 `published` 或 `retired` 版本写入时抛出 `ReleaseImmutableException`，映射为 `409 RELEASE_IMMUTABLE`。

### 8.2 发布前校验

`ReleaseValidationService` 至少包含：

```text
FloorMapBindingValidator
CoordinateBoundsValidator
PoiNodeBindingValidator
InFloorEdgeValidator
ConnectorStopValidator
VerticalLinkValidator
DuplicatePublicIdAndCodeValidator
ReachabilityValidator
AccessibleRouteValidator
```

校验结果记录 `release_id + content_revision`。任何草稿修改都会让旧校验失效。

### 8.3 发布事务

`ReleasePublicationService.publish` 使用一个写事务：

1. `SELECT ... FOR UPDATE` 锁定楼栋。
2. 检查草稿状态和 `If-Match`。
3. 检查同一 `content_revision` 的最新校验已通过。
4. 取消旧版本 `is_active`。
5. 将草稿设为 `published + is_active`，填写发布人和时间。
6. 写入 `building_release_event`。
7. 提交后再失效当前版本指针和 navigation-context 缓存。

数据库的部分唯一索引负责兜底“一栋楼只有一个启用版本”。缓存失效不能早于事务提交。

### 8.4 回滚

回滚锁定同一楼栋，在事务中把当前版本停用并重新启用目标已发布版本。它不修改或复制历史图，并写入 `building_release_event(event_type = rollback)`。

旧版本上的运营覆盖不会自动应用到新启用版本；运营人员必须显式确认和重新绑定。

## 9. 临时运营覆盖

`OperationOverrideService` 负责创建和撤销覆盖：

- 创建时只允许目标属于楼栋当前启用版本。
- 保存 `release_id` 和目标元素内部外键。
- 不提供物理删除；恢复通行写入 `revoked_at/revoked_by`。
- 已生效覆盖查询使用服务器 `Clock`，便于测试固定时间。
- 发布切换后，旧 `release_id` 的覆盖不参与新路线。

`EffectiveOverrideQuery` 返回：

```java
public record EffectiveClosures(
    Set<UUID> closedPathEdgeIds,
    Set<UUID> closedVerticalLinkIds,
    Set<UUID> closedConnectorIds
) {}
```

这里的 UUID 是目标图元素 `public_id`，与 `NavigationGraph` 的元素 ID 对齐。

## 10. API 与异常映射

Controller 只接收 DTO，使用 Jakarta Validation：

```java
public record RouteRequest(
    @NotNull UUID buildingId,
    UUID expectedReleaseId,
    @NotNull UUID startPoiId,
    @NotNull UUID endPoiId,
    @NotNull RouteMode routeMode
) {}
```

`ApiExceptionHandler` 统一映射：

| 领域异常 | HTTP / 错误码 |
|---|---|
| `ResourceNotFoundException` | `404 RESOURCE_NOT_FOUND` |
| `NoPublishedReleaseException` | `404 MAP_RELEASE_NOT_PUBLISHED` |
| `ReleaseMismatchException` | `409 RELEASE_MISMATCH` |
| `DraftChangedException` | `409 DRAFT_CHANGED` |
| `ReleaseImmutableException` | `409 RELEASE_IMMUTABLE` |
| `RouteUnreachableException` | `422 ROUTE_UNREACHABLE` |
| `ReleaseValidationException` | `422 VALIDATION_FAILED` |
| `AccessDeniedException` | `403 FORBIDDEN` |

任何异常响应都符合 `05-接口设计.md` 的标准错误体，并携带 `X-Request-Id`。不得在响应中暴露 SQL、堆栈、内部表名或数据库主键。

## 11. 鉴权边界

最小权限：

```text
NAVIGATION_READ
STAFF_ROUTE_READ
MAP_EDITOR
MAP_PUBLISHER
OPERATION_OPERATOR
```

控制要求：

- `/api/admin/**` 必须认证。
- 草稿编辑同时要求 `MAP_EDITOR` 和目标楼栋数据范围。
- 发布/回滚要求 `MAP_PUBLISHER` 和目标楼栋数据范围。
- 临时覆盖要求 `OPERATION_OPERATOR` 和目标楼栋数据范围。
- `staff` 路线要求 `STAFF_ROUTE_READ` 和目标楼栋数据范围。
- 公共导航是否匿名由部署配置决定，但只读已发布版本。
- Repository 查询不能代替授权；授权在 application service 入口再次校验，避免内部调用绕过 Controller。

建议通过 `BuildingScopeAuthorizer` 统一判断医院、院区和楼栋范围，不在各 Controller 中复制权限逻辑。

## 12. 缓存边界

允许的第一版缓存：

```text
key: releaseId
value: immutable NavigationGraph
```

规则：

- 已发布图不可变，因此按 `releaseId` 缓存不需要原地更新。
- 当前启用版本指针可以短缓存，发布提交后失效。
- 临时覆盖不永久并入 `NavigationGraph`。
- 草稿不进入用户导航缓存。
- 单实例阶段可使用简单进程内缓存；多实例部署前再设计发布通知或共享缓存。

不应为了缓存提前引入 Redis。

## 13. 测试结构与最低用例

### 13.1 算法单元测试

```text
findsLowestTimeRoute
usesDistanceAsTieBreaker
respectsForwardOnlyEdge
returnsUnreachableWhenDisconnected
doesNotUseClosedEdge
accessibleModeRejectsStairs
accessibleModeRejectsInaccessibleEdge
handlesLargeCostsWithoutOverflow
```

### 13.2 多楼层路线 fixture

固定使用：

- `1F / 2F / 3F`。
- A 电梯只停 `1F / 3F`。
- B 电梯停 `1F / 2F / 3F`。
- 楼梯连接相邻楼层。
- 一条单向边。
- 一条不可无障碍边。

必须验证：

- 路线不会让 A 电梯在 2F 停靠。
- 普通路线可以选择楼梯，无障碍路线不能。
- 关闭 A 电梯后选择 B 电梯或返回不可达。
- 响应包含正确 `releaseId`、楼层 `segments` 和 `transitions`。
- 汇总时间等于所有弧耗时之和。

### 13.3 服务与事务测试

```text
rejectsPoiFromAnotherRelease
rejectsExpectedReleaseMismatch
usesSingleReleaseDuringConcurrentPublish
publishesOnlyValidatedContentRevision
keepsOneActiveReleasePerBuilding
rollbackDoesNotMutatePublishedGraph
expiredOverrideDoesNotCloseEdge
newReleaseDoesNotInheritOldOverride
```

### 13.4 API 契约测试

固定验证：

- UUID 字段始终是字符串。
- 字段单位使用 `Meters`、`Seconds` 后缀。
- 不可达返回 `422` 标准错误，而非空 `200`。
- 无权限 staff 路线返回 `403`。
- 路线响应不再返回旧版扁平 `path`。
- 维护端草稿写入要求 `If-Match`。

### 13.5 持久化测试

使用真实 PostgreSQL 测试环境执行 Flyway，验证：

- 空库可一次迁移完成。
- 普通边不能跨发布版本或跨楼层绑定节点。
- 垂直连接两端必须属于同一设施。
- 同一楼栋不能存在两个启用版本。
- 发布和回滚事务失败时不会留下半切换状态。

## 14. 分步替换当前骨架

### 阶段 1：内存多楼层核心

**状态：已完成**

- 为正式路线接口新增三层 fixture，并保留单层兼容数据。
- 用 `DijkstraPathFinder` 替换 `AStarPathFinder`。
- 实现方向、无障碍、垂直设施、停靠点和跨层边。
- 返回 `releaseId + segments + transitions + steps`。
- 完成算法与路线服务自动化测试。

### 阶段 2：PostgreSQL 持久化

**状态：已完成**

- 添加 Flyway 和 PostgreSQL 配置。
- 实现 `JdbcPublishedGraphRepository`。
- 用数据库读取替换正式路线的 `InMemoryPublishedGraphService`，内存 fixture
  保留给测试；旧 `DemoGraphService` 随兼容接口另行退役。
- 实现发布、回滚与覆盖事务。

### 阶段 3：维护端

**状态：已完成最小可用版本**

- 实现草稿复制和乐观锁。
- 实现图元素维护和发布前校验。
- 实现发布、回滚和临时运营覆盖。

真实 PostgreSQL 当前发布版本已有可重复 API 冒烟和带校验和的备份恢复脚本。
下一步优先完成真实文件上传、定时备份/监控和医院设备现场走查；统一登录与
多医院/楼栋数据权限在功能稳定并
移植宿主项目时通过适配器接入，不需要为当前运维规模拆分微服务或引入额外基础设施。
