# MedRoute 新版前端

该目录是 Vue 3、TypeScript、Tailwind CSS 和 OpenLayers 前端工作区。新版与
`hospital-map-demo` 并行运行，按功能迁移，不影响现有验收入口。

## 目录

```text
apps/map-admin-web/       电脑端楼层图维护
apps/navigation-web/      移动端路线查看
packages/api-client/      后端 API 客户端和演示数据
packages/map-core/        领域类型、图操作、坐标和 OpenLayers 图层
public/                   两个应用共享的本地测试底图
```

## 安装和启动

需要 Node.js 20 或更高版本和 pnpm 10。

```bash
pnpm install
```

维护端：

```bash
pnpm dev:admin
```

导航端：

```bash
pnpm dev:navigation
```

默认后端为 `http://127.0.0.1:8080`，也可以通过查询参数指定：

```text
http://127.0.0.1:5173/?api=http://127.0.0.1:8080
http://127.0.0.1:5174/?api=http://127.0.0.1:8080
```

后端未启动时使用本地演示数据：

```text
http://127.0.0.1:5173/?demo=1
http://127.0.0.1:5174/?demo=1
```

演示模式不会写数据库，所有标注只在当前页面内生效。

## 验证

```bash
pnpm test
pnpm typecheck
pnpm build
```

维护端验收 `1200 x 720` 和 `1440 x 900`；导航端验收 `360 x 800`、
`390 x 844` 和 `430 x 932`。

## 数据约束

- 后端 `DraftGraph` 和路线响应是唯一业务数据源。
- 数据库存储左上角原点、Y 轴向下的楼层图片像素坐标。
- OpenLayers 使用 Y 轴向上的地图坐标，只能通过 `map-core` 边界转换。
- 路径几何由起止节点实时生成，不独立持久化折线坐标。
- 导航端不加载草稿，也不在浏览器计算正式路线。
