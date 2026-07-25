# Hospital Navigation Server

MedRoute 的 Spring Boot 后端。当前已完成“阶段 1：内存多楼层路线核心”，
用于在接入 PostgreSQL 之前验证正式路线契约、跨层建模和路径算法。

当前能力：

- 三层楼栋内存发布图，使用固定 UUID 和 `releaseId`。
- 显式建模电梯、楼梯、停靠点和跨层边，不根据设施编号推测停靠楼层。
- 以 `timeSeconds` 为主成本、`distanceMeters` 为次级排序的 Dijkstra。
- 支持 `normal` 和 `accessible`；未授权 `staff` 返回 `403`，
  尚未定义成本规则的 `less_elevator` 返回 `400`。
- 路线服务和测试 fixture 支持进程内临时关闭边或连接器；当前尚未提供运营管理 HTTP 入口。
- 返回按楼层拆分的 `segments`、实际跨层动作 `transitions` 和文字 `steps`。
- 正式路线接口响应携带 `X-Request-Id`；已映射的客户端错误和未预期服务端错误
  使用统一的 `error` 对象。

数据仍是进程内 fixture，不是数据库实现；发布、回滚、维护端和持久化运营状态
属于下一阶段。

## API

正式路线接口：

```http
POST /api/routes
Content-Type: application/json
X-Request-Id: req-local-demo
```

可直接使用当前 fixture 中的 ID：

```json
{
  "buildingId": "00000000-0000-0000-0000-000000000100",
  "expectedReleaseId": "00000000-0000-0000-0000-000000000200",
  "startPoiId": "00000000-0000-0000-0000-000000003001",
  "endPoiId": "00000000-0000-0000-0000-000000003004",
  "routeMode": "accessible"
}
```

该请求会从 `1F` 入口经仅停靠 `1F / 3F` 的 A 电梯到达 `3F` 药房。
响应摘要为 75 米、125 秒，并包含两个楼层段和一个跨层动作。

以下单层数字 ID 接口暂时保留，供现有静态 Demo 兼容使用，不属于新的正式
多楼层契约：

- `GET /api/hospitals`
- `GET /api/hospitals/{hospitalId}/floors/{floorId}/map`
- `GET /api/hospitals/{hospitalId}/floors/{floorId}/graph`
- `GET /api/hospitals/{hospitalId}/pois?keyword=药房`

正式契约以
[`docs/05-接口设计.md`](../docs/05-接口设计.md)
为准。

## 环境

需要 JDK 17 和 Maven 3.8 或更高版本：

```bash
java -version
mvn -version
```

macOS 上如果 Maven 选中了其他 JDK，可在本次 shell 显式指定：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
```

## 测试

```bash
mvn test
```

当前 33 个 JUnit 5 测试覆盖：

- 时间优先、距离次级排序、长整型成本累计和单向边。
- 普通/无障碍跨层路线、电梯停靠限制和连接器关闭后的备选路线。
- 发布版本冲突、POI 版本归属、不可达路线。
- 决策点文字步骤、正式 JSON 响应、错误码和 `X-Request-Id`。
- 保留的单层兼容路线服务。

## 运行

```bash
mvn spring-boot:run
```

默认监听 `http://localhost:8080`。
