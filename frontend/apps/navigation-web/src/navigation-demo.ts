import {
  demoNavigationContext,
  demoNavigationPois,
  demoRoute,
} from "@medroute/api-client/demo";

export const navigationDemoProvider = {
  context: demoNavigationContext,
  pois: demoNavigationPois,
  route: demoRoute,
  notice: "演示数据 · 未经现场核验",
};
