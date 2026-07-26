# Hospital Navigation Server

MedRoute 的 Spring Boot 后端。当前版本以 PostgreSQL 中的当前发布图作为正式导航
数据源，并提供一套适合少量维护人员使用的地图编修、发布和运营 API。

## 当前能力

- PostgreSQL 16 + Flyway 管理地图目录、路网、发布版本、底图和运营状态。
- 空数据库首次启动自动建表，并初始化三层基础发布版本和公开底图试点草稿。
- 每层独立底图和像素坐标系，底图上传后生成不可变修订版。
- 显式建模电梯、楼梯、停靠点和跨层连接，不推断设施能到达全部楼层。
- 以时间为主成本、距离为次级排序的 Dijkstra。
- 支持 `normal` 和 `accessible` 路线，返回楼层段、跨层动作和文字步骤。
- 草稿复制、`ETag`/`If-Match` 乐观锁、结构校验、发布、回滚和草稿删除。
- 路径、跨层连接或整组设施的定时/立即封闭及撤销。
- 发布和回滚时按稳定公开 ID 迁移仍生效的运营封闭。
- 统一客户端错误响应和 `X-Request-Id`。
- 当前发布 POI 搜索支持人工维护的中文别名、拼音和首字母关键词。
- 使用稳定 POI 业务编码生成固定点导航二维码 PNG。
- 楼栋级关键路线回归配置；草稿校验显示距离、耗时和跨层设施，关键失败阻止发布。
- 东方市人民医院公开底图试点草稿包含 30 个 POI，用于完整建图发布闭环验收。

内存三层图仅保留为算法单元测试 fixture，不再是正式运行时数据源。

## 环境

项目以 Java 17 为编译基线。推荐直接使用 JDK 17；IDEA 的 Project SDK 和
Maven Runner JDK 应保持一致。仓库自带 Maven Wrapper，不要求全局安装 Maven。

后端默认连接：

```text
PostgreSQL: jdbc:postgresql://127.0.0.1:5432/medroute
用户/密码: medroute / medroute
HTTP: http://127.0.0.1:8080
```

连接信息可通过 `MEDROUTE_DB_URL`、`MEDROUTE_DB_USER`、
`MEDROUTE_DB_PASSWORD` 和 `MEDROUTE_DB_POOL_SIZE` 覆盖。HTTP 默认只绑定
`127.0.0.1`；只有在已配置身份认证、受控反向代理和防火墙后，才应通过
`MEDROUTE_SERVER_ADDRESS` 修改监听地址。

## 运行

先在仓库根目录启动数据库：

```bash
docker compose up -d postgres
```

再启动后端：

```bash
cd hospital-navigation-server
./mvnw spring-boot:run
```

Flyway 会在应用启动前校验并执行尚未应用的数据库迁移。

## 导航 API

```http
GET  /api/buildings/{buildingId}/navigation-context
GET  /api/buildings/{buildingId}/pois
POST /api/routes
GET  /api/map-images/{revisionId}
```

可直接使用初始化数据验证跨层路线：

```json
{
  "buildingId": "00000000-0000-0000-0000-000000000100",
  "expectedReleaseId": "00000000-0000-0000-0000-000000000200",
  "startPoiId": "00000000-0000-0000-0000-000000003001",
  "endPoiId": "00000000-0000-0000-0000-000000003004",
  "routeMode": "accessible"
}
```

该请求从 1F 门诊入口经只停靠 1F/3F 的 A 电梯到达 3F 超声医学科，基线摘要
为 75 米、125 秒。

## 维护 API

```http
GET    /api/admin/buildings/{buildingId}/releases
POST   /api/admin/buildings/{buildingId}/releases/drafts
GET    /api/admin/releases/{releaseId}
DELETE /api/admin/releases/{releaseId}
PUT    /api/admin/releases/{releaseId}/workspace
POST   /api/admin/releases/{releaseId}/floors/{floorId}/map
POST   /api/admin/releases/{releaseId}/validate
POST   /api/admin/releases/{releaseId}/publish
POST   /api/admin/releases/{releaseId}/rollback

GET    /api/admin/buildings/{buildingId}/route-regression-cases
POST   /api/admin/buildings/{buildingId}/route-regression-cases
PUT    /api/admin/route-regression-cases/{caseId}
DELETE /api/admin/route-regression-cases/{caseId}

GET    /api/admin/buildings/{buildingId}/operations/closures
POST   /api/admin/buildings/{buildingId}/operations/closures
DELETE /api/admin/operations/closures/{closureId}
POST   /api/admin/navigation-qr-code
```

草稿写入、底图替换、校验、发布和删除使用 `If-Match` 携带当前内容修订号。
维护后台位于 `hospital-map-demo/admin.html`。

## 测试

```bash
./mvnw test
```

Surefire 会为二维码图像测试启用 Java headless 模式，测试过程不依赖桌面窗口服务。

测试环境使用 H2 PostgreSQL 兼容模式和同一组通用 Flyway 迁移，覆盖：

- 路线算法、单向边、无障碍过滤和长整型成本。
- 电梯停靠限制、分楼层路线、版本冲突和不可达错误。
- JDBC 发布图读取、底图上传和坐标缩放。
- 草稿校验、发布、回滚、删除及运营封闭迁移。
- 封闭设施后的备选路线和恢复后的最优路线。
- 固定点二维码生成、PNG 尺寸、地址解码和非法协议拒绝。
- 关键路线执行、普通/无障碍设施选择、配置写入、发布阻断和非关键提醒。
- 公开底图试点草稿全部 POI 的普通/无障碍可达性和 12 条关键路线。

本地验收还应使用 Docker PostgreSQL 运行后端并在浏览器完成一次发布/回滚闭环。
后端运行时，可在仓库根目录执行真实 PostgreSQL API 冒烟：

```bash
MEDROUTE_EXPECTED_POI_COUNT=30 node scripts/pilot-api-smoke.mjs
MEDROUTE_EXPECTED_POI_COUNT=30 node scripts/pilot-api-smoke.mjs --write
MEDROUTE_API_BASE=http://192.168.5.42:5174 \
  MEDROUTE_EXPECTED_POI_COUNT=30 \
  node scripts/pilot-api-smoke.mjs --public-only
```

默认模式只读取当前发布版本，检查上下文/POI/运营版本一致性、全部 POI 普通和
无障碍可达矩阵及二维码 PNG。`--write` 还会自动选择一条当前路线使用的开放
电梯或楼梯，创建即时封闭，验证绕行或明确不可达，再撤销封闭并确认原基线恢复。
该模式保留已撤销的运营审计记录，不会修改地图发布版本。`--public-only` 仅调用
导航公开 API，适合从局域网地址检查手机访问边界，不能与 `--write` 同时使用。

## 安全边界

当前维护 API 通过 `X-Admin-User` 记录操作人，但尚未接入 Spring Security，
该请求头不能视为身份认证。因此后端和维护端默认只允许本机访问；局域网手机通过
导航 Vite 的公开 API 白名单代理进行试点验收。当前宽松 CORS 只适合本机验收。
生产部署前必须增加正式登录、角色和楼栋数据范围、HTTPS、反向代理及受控 CORS。

正式契约和操作步骤见：

- [`docs/05-接口设计.md`](../docs/05-接口设计.md)
- [`docs/10-地图维护与发布操作手册.md`](../docs/10-地图维护与发布操作手册.md)
- [`docs/12-公开底图试点建图与验收清单.md`](../docs/12-公开底图试点建图与验收清单.md)
