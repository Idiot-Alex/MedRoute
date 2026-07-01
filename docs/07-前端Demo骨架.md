# 前端 Demo 骨架

## 1. 技术路线

第一版推荐：

```text
HTML + CSS + JavaScript + SVG
```

正式版可升级：

```text
Vue / React + MapLibre GL JS
```

## 2. 页面结构

```text
左侧操作区
  - 起点选择
  - 终点选择
  - 开始导航
  - 路线步骤

右侧地图区
  - 楼层图背景
  - 路径网络
  - POI
  - 路线高亮
```

## 3. 最小数据结构

```js
const nodes = [
  { id: "N1", x: 160, y: 420 }
];

const edges = [
  { from: "N1", to: "N2", distance: 10 }
];

const pois = [
  { id: "P1", name: "挂号处", nodeId: "N1", x: 130, y: 390 }
];
```

## 4. 后续接后端

将本地数据替换为接口：

```js
const graph = await fetch("/api/hospitals/1/floors/1/graph").then(r => r.json());

const route = await fetch("/api/routes", {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({
    hospitalId: 1,
    startPoiId: 1,
    endPoiId: 4,
    routeMode: "normal"
  })
}).then(r => r.json());
```

## 5. MapLibre 版本思路

正式版可用 MapLibre 的图层能力：

```text
raster layer      显示楼层图
circle layer      显示 POI
line layer        显示路径网络
line layer        显示规划路线
symbol layer      显示文字
```

但第一版 Demo 不建议先上 MapLibre，直接 SVG 更快。
