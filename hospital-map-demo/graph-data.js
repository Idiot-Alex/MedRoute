window.MED_ROUTE_GRAPH = {
  floor: {
    hospitalId: 1,
    hospitalName: "测试医院",
    buildingId: 1,
    buildingName: "门诊楼",
    floorId: 1,
    floorName: "1F",
    imageWidth: 1000,
    imageHeight: 620,
    version: "demo-v1"
  },
  defaults: {
    startPoiId: "P2",
    endPoiId: "P5",
    routeMode: "normal"
  },
  nodes: [
    { id: "N1", x: 170, y: 300, name: "挂号处门口", type: "normal" },
    { id: "N2", x: 320, y: 320, name: "大厅西侧", type: "normal" },
    { id: "N3", x: 500, y: 320, name: "大厅中心", type: "normal" },
    { id: "N4", x: 650, y: 320, name: "中庭路口", type: "normal" },
    { id: "N5", x: 650, y: 250, name: "检验科门口", type: "normal" },
    { id: "N6", x: 820, y: 320, name: "药房通道", type: "normal" },
    { id: "N7", x: 890, y: 250, name: "药房门口", type: "normal" },
    { id: "N8", x: 755, y: 430, name: "影像科路口", type: "normal" },
    { id: "N9", x: 890, y: 430, name: "影像科门口", type: "normal" },
    { id: "N10", x: 335, y: 390, name: "卫生间门口", type: "normal" },
    { id: "N11", x: 145, y: 390, name: "入口门厅", type: "entrance" },
    { id: "N12", x: 640, y: 390, name: "电梯 A 门口", type: "elevator" }
  ],
  edges: [
    { id: "E1", from: "N11", to: "N1", direction: "both", distance: 6, walkTime: 1, accessible: true, status: "enabled", type: "walk", remark: "入口门厅到挂号处。" },
    { id: "E2", from: "N1", to: "N2", direction: "both", distance: 12, walkTime: 1, accessible: true, status: "enabled", type: "walk", remark: "大厅主通道。" },
    { id: "E3", from: "N2", to: "N3", direction: "both", distance: 14, walkTime: 1, accessible: true, status: "enabled", type: "walk", remark: "大厅中心通道。" },
    { id: "E4", from: "N3", to: "N4", direction: "both", distance: 12, walkTime: 1, accessible: true, status: "enabled", type: "walk", remark: "中庭主通道。" },
    { id: "E5", from: "N4", to: "N5", direction: "both", distance: 8, walkTime: 1, accessible: true, status: "enabled", type: "walk", remark: "检验科入口。" },
    { id: "E6", from: "N4", to: "N6", direction: "both", distance: 13, walkTime: 1, accessible: true, status: "enabled", type: "walk", remark: "药房排队区外侧。" },
    { id: "E7", from: "N6", to: "N7", direction: "both", distance: 8, walkTime: 1, accessible: true, status: "enabled", type: "walk", remark: "药房取药窗口。" },
    { id: "E8", from: "N4", to: "N8", direction: "both", distance: 14, walkTime: 1, accessible: true, status: "enabled", type: "walk", remark: "影像科方向主通道。" },
    { id: "E9", from: "N8", to: "N9", direction: "both", distance: 10, walkTime: 1, accessible: true, status: "enabled", type: "walk", remark: "影像科门口。" },
    { id: "E10", from: "N2", to: "N10", direction: "both", distance: 8, walkTime: 1, accessible: true, status: "enabled", type: "walk", remark: "卫生间入口。" },
    { id: "E11", from: "N3", to: "N12", direction: "both", distance: 12, walkTime: 1, accessible: true, status: "enabled", type: "walk", remark: "电梯厅入口。" },
    { id: "E12", from: "N12", to: "N8", direction: "both", distance: 10, walkTime: 1, accessible: false, status: "enabled", type: "walk", remark: "窄通道，轮椅路线不推荐。" }
  ],
  pois: [
    { id: "P1", name: "入口", category: "entrance", nodeId: "N11", x: 145, y: 414, searchKeywords: ["入口", "大门"] },
    { id: "P2", name: "挂号处", category: "window", nodeId: "N1", x: 170, y: 246, searchKeywords: ["挂号", "取号"] },
    { id: "P3", name: "收费处", category: "window", nodeId: "N2", x: 360, y: 246, searchKeywords: ["收费", "缴费"] },
    { id: "P4", name: "检验科", category: "inspection", nodeId: "N5", x: 650, y: 246, searchKeywords: ["检验", "抽血"] },
    { id: "P5", name: "药房", category: "pharmacy", nodeId: "N7", x: 890, y: 246, searchKeywords: ["药房", "取药"] },
    { id: "P6", name: "影像科", category: "department", nodeId: "N9", x: 890, y: 395, searchKeywords: ["影像", "CT", "放射"] },
    { id: "P7", name: "卫生间", category: "toilet", nodeId: "N10", x: 335, y: 395, searchKeywords: ["厕所", "卫生间"] },
    { id: "P8", name: "电梯 A", category: "elevator", nodeId: "N12", x: 640, y: 395, searchKeywords: ["电梯", "上楼"] }
  ]
};
