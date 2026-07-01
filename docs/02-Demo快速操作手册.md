# Demo 快速操作手册

## 目标

按照本手册，你可以先做出一个最小可用 Demo：

```text
一张医院楼层图
几个关键点
一套路网
点击起点和终点
自动高亮路线
```

这个 Demo 不依赖后端、不依赖数据库、不依赖地图服务，只用一个 HTML 文件即可跑起来。

## 一、准备楼层图

准备一张医院某一层楼的示意图，格式建议：

```text
PNG / JPG
```

假设文件名：

```text
floor-1.png
```

建议先找一张简单图，包含：

- 挂号处
- 收费处
- 药房
- 检验科
- 电梯
- 卫生间

如果没有真实医院图，可以先自己画一张简单的矩形示意图。

## 二、创建 Demo 目录

```bash
mkdir hospital-map-demo
cd hospital-map-demo
```

目录结构：

```text
hospital-map-demo/
  index.html
  floor-1.png
```

## 三、定义地图坐标

前端 Demo 使用图片像素坐标。

例如图片宽高是：

```text
1000 x 600
```

那么地图左上角是：

```text
x=0, y=0
```

右下角是：

```text
x=1000, y=600
```

POI 和路径节点都使用这个坐标。

## 四、设计路径节点

示例：

```text
N1：挂号处附近
N2：大厅中间
N3：收费处附近
N4：走廊拐角
N5：检验科门口
N6：药房门口
```

节点示例：

```json
[
  { "id": "N1", "x": 160, "y": 420 },
  { "id": "N2", "x": 320, "y": 420 },
  { "id": "N3", "x": 480, "y": 420 },
  { "id": "N4", "x": 650, "y": 420 },
  { "id": "N5", "x": 650, "y": 260 },
  { "id": "N6", "x": 820, "y": 420 }
]
```

## 五、设计路径边

路径边就是节点之间能走的路。

示例：

```json
[
  { "from": "N1", "to": "N2", "distance": 10 },
  { "from": "N2", "to": "N3", "distance": 10 },
  { "from": "N3", "to": "N4", "distance": 12 },
  { "from": "N4", "to": "N5", "distance": 15 },
  { "from": "N4", "to": "N6", "distance": 12 }
]
```

## 六、设计 POI

POI 绑定到路径节点。

```json
[
  { "id": "P1", "name": "挂号处", "nodeId": "N1", "x": 130, "y": 390 },
  { "id": "P2", "name": "收费处", "nodeId": "N3", "x": 460, "y": 390 },
  { "id": "P3", "name": "检验科", "nodeId": "N5", "x": 630, "y": 230 },
  { "id": "P4", "name": "药房", "nodeId": "N6", "x": 800, "y": 390 }
]
```

## 七、创建 index.html

复制下面代码到 `index.html`。

> 如果你还没有楼层图，可以先把 img 标签隐藏，直接看节点和路线效果。

```html
<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8" />
  <title>医院室内导航 Demo</title>
  <style>
    body {
      font-family: Arial, "Microsoft YaHei", sans-serif;
      margin: 0;
      display: flex;
      height: 100vh;
    }
    .sidebar {
      width: 280px;
      padding: 16px;
      border-right: 1px solid #ddd;
      box-sizing: border-box;
    }
    .map-wrap {
      flex: 1;
      overflow: auto;
      background: #f5f5f5;
      display: flex;
      justify-content: center;
      align-items: center;
    }
    .map {
      position: relative;
      width: 1000px;
      height: 600px;
      background: #fff;
      border: 1px solid #ccc;
    }
    .map img {
      position: absolute;
      left: 0;
      top: 0;
      width: 1000px;
      height: 600px;
      object-fit: contain;
      opacity: 0.45;
    }
    svg {
      position: absolute;
      left: 0;
      top: 0;
    }
    .poi {
      position: absolute;
      transform: translate(-50%, -100%);
      background: #1677ff;
      color: #fff;
      padding: 4px 8px;
      border-radius: 4px;
      font-size: 13px;
      cursor: pointer;
      white-space: nowrap;
    }
    .poi.selected-start {
      background: #52c41a;
    }
    .poi.selected-end {
      background: #f5222d;
    }
    button, select {
      width: 100%;
      margin: 8px 0;
      padding: 8px;
    }
    .steps {
      margin-top: 16px;
      font-size: 14px;
      line-height: 1.8;
    }
  </style>
</head>
<body>
  <div class="sidebar">
    <h2>医院室内导航 Demo</h2>

    <label>起点</label>
    <select id="startSelect"></select>

    <label>终点</label>
    <select id="endSelect"></select>

    <button onclick="route()">开始导航</button>

    <div class="steps" id="steps"></div>
  </div>

  <div class="map-wrap">
    <div class="map" id="map">
      <img src="./floor-1.png" onerror="this.style.display='none'" />

      <svg id="svg" width="1000" height="600"></svg>
    </div>
  </div>

<script>
const nodes = [
  { id: "N1", x: 160, y: 420 },
  { id: "N2", x: 320, y: 420 },
  { id: "N3", x: 480, y: 420 },
  { id: "N4", x: 650, y: 420 },
  { id: "N5", x: 650, y: 260 },
  { id: "N6", x: 820, y: 420 }
];

const edges = [
  { from: "N1", to: "N2", distance: 10 },
  { from: "N2", to: "N3", distance: 10 },
  { from: "N3", to: "N4", distance: 12 },
  { from: "N4", to: "N5", distance: 15 },
  { from: "N4", to: "N6", distance: 12 }
];

const pois = [
  { id: "P1", name: "挂号处", nodeId: "N1", x: 130, y: 390 },
  { id: "P2", name: "收费处", nodeId: "N3", x: 460, y: 390 },
  { id: "P3", name: "检验科", nodeId: "N5", x: 630, y: 230 },
  { id: "P4", name: "药房", nodeId: "N6", x: 800, y: 390 }
];

const nodeMap = Object.fromEntries(nodes.map(n => [n.id, n]));
const poiMap = Object.fromEntries(pois.map(p => [p.id, p]));

function init() {
  const startSelect = document.getElementById("startSelect");
  const endSelect = document.getElementById("endSelect");

  pois.forEach(p => {
    startSelect.innerHTML += `<option value="${p.id}">${p.name}</option>`;
    endSelect.innerHTML += `<option value="${p.id}">${p.name}</option>`;
  });

  if (pois.length > 1) {
    endSelect.value = pois[1].id;
  }

  drawBase();
  drawPois();
}

function drawBase() {
  const svg = document.getElementById("svg");
  svg.innerHTML = "";

  edges.forEach(e => {
    const a = nodeMap[e.from];
    const b = nodeMap[e.to];

    svg.innerHTML += `
      <line x1="${a.x}" y1="${a.y}" x2="${b.x}" y2="${b.y}"
            stroke="#999" stroke-width="4" stroke-dasharray="6 4" />
    `;
  });

  nodes.forEach(n => {
    svg.innerHTML += `
      <circle cx="${n.x}" cy="${n.y}" r="5" fill="#333" />
    `;
  });
}

function drawPois() {
  const map = document.getElementById("map");

  document.querySelectorAll(".poi").forEach(el => el.remove());

  pois.forEach(p => {
    const div = document.createElement("div");
    div.className = "poi";
    div.style.left = p.x + "px";
    div.style.top = p.y + "px";
    div.innerText = p.name;
    div.onclick = () => {
      document.getElementById("endSelect").value = p.id;
    };
    map.appendChild(div);
  });
}

function buildGraph() {
  const graph = {};
  nodes.forEach(n => graph[n.id] = []);

  edges.forEach(e => {
    graph[e.from].push({ to: e.to, weight: e.distance });
    graph[e.to].push({ to: e.from, weight: e.distance });
  });

  return graph;
}

function heuristic(aId, bId) {
  const a = nodeMap[aId];
  const b = nodeMap[bId];
  return Math.hypot(a.x - b.x, a.y - b.y);
}

function astar(start, end) {
  const graph = buildGraph();
  const open = new Set([start]);
  const cameFrom = {};

  const gScore = {};
  const fScore = {};
  nodes.forEach(n => {
    gScore[n.id] = Infinity;
    fScore[n.id] = Infinity;
  });

  gScore[start] = 0;
  fScore[start] = heuristic(start, end);

  while (open.size > 0) {
    let current = [...open].reduce((best, id) =>
      fScore[id] < fScore[best] ? id : best
    );

    if (current === end) {
      return reconstruct(cameFrom, current);
    }

    open.delete(current);

    graph[current].forEach(edge => {
      const tentative = gScore[current] + edge.weight;
      if (tentative < gScore[edge.to]) {
        cameFrom[edge.to] = current;
        gScore[edge.to] = tentative;
        fScore[edge.to] = tentative + heuristic(edge.to, end);
        open.add(edge.to);
      }
    });
  }

  return [];
}

function reconstruct(cameFrom, current) {
  const path = [current];
  while (cameFrom[current]) {
    current = cameFrom[current];
    path.unshift(current);
  }
  return path;
}

function route() {
  drawBase();

  const startPoi = poiMap[document.getElementById("startSelect").value];
  const endPoi = poiMap[document.getElementById("endSelect").value];

  if (startPoi.id === endPoi.id) {
    alert("起点和终点不能相同");
    return;
  }

  const path = astar(startPoi.nodeId, endPoi.nodeId);
  drawRoute(path);
  showSteps(startPoi, endPoi, path);
}

function drawRoute(path) {
  const svg = document.getElementById("svg");

  for (let i = 0; i < path.length - 1; i++) {
    const a = nodeMap[path[i]];
    const b = nodeMap[path[i + 1]];

    svg.innerHTML += `
      <line x1="${a.x}" y1="${a.y}" x2="${b.x}" y2="${b.y}"
            stroke="#ff4d4f" stroke-width="8" stroke-linecap="round" />
    `;
  }
}

function showSteps(startPoi, endPoi, path) {
  const steps = document.getElementById("steps");

  let html = `<h3>路线步骤</h3>`;
  html += `<p>从 <b>${startPoi.name}</b> 前往 <b>${endPoi.name}</b></p>`;
  html += `<ol>`;
  html += `<li>从 ${startPoi.name} 出发，进入主通道。</li>`;

  for (let i = 1; i < path.length - 1; i++) {
    html += `<li>沿通道前进，经过节点 ${path[i]}。</li>`;
  }

  html += `<li>到达 ${endPoi.name}。</li>`;
  html += `</ol>`;

  steps.innerHTML = html;
}

init();
</script>
</body>
</html>
```

## 八、运行 Demo

直接双击打开：

```text
index.html
```

如果浏览器因为本地图片限制导致图片不显示，可以用本地服务启动：

```bash
python -m http.server 8080
```

然后访问：

```text
http://localhost:8080
```

## 九、如何替换成真实楼层图

1. 把真实楼层图复制到 Demo 目录。
2. 命名为：

```text
floor-1.png
```

3. 打开图片查看宽高。
4. 修改 `.map`、`svg`、`img` 的宽高。
5. 修改 nodes 的 x/y 坐标。
6. 修改 pois 的 x/y 坐标。
7. 修改 edges 连接关系。

## 十、如何确定坐标

简单方法：

用浏览器临时加点击取点代码。

在 `init();` 前面加入：

```js
document.getElementById("map").addEventListener("click", (e) => {
  const rect = e.currentTarget.getBoundingClientRect();
  console.log({
    x: Math.round(e.clientX - rect.left),
    y: Math.round(e.clientY - rect.top)
  });
});
```

打开控制台，点击地图，就能看到坐标。

## 十一、Demo 完成标准

完成后应该能实现：

- 地图能显示
- POI 能显示
- 起点终点能选择
- 点击开始导航能高亮路线
- 左侧能出现路线步骤

做到这里，第一版 Demo 就成功了。
