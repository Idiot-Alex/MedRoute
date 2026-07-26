import tailwindcss from "@tailwindcss/vite";
import vue from "@vitejs/plugin-vue";
import { defineConfig } from "vite";

export default defineConfig({
  plugins: [
    vue(),
    tailwindcss(),
    {
      name: "deny-admin-api",
      configureServer(server) {
        server.middlewares.use("/api/admin", (_request, response) => {
          response.statusCode = 404;
          response.setHeader("Content-Type", "application/json");
          response.setHeader("Cache-Control", "no-store");
          response.end(
            JSON.stringify({
              error: {
                code: "RESOURCE_NOT_FOUND",
                message: "请求的资源不存在。",
              },
            }),
          );
        });
      },
    },
  ],
  publicDir: "../../public",
  server: {
    host: "0.0.0.0",
    port: 5174,
    strictPort: false,
    proxy: {
      "/api/buildings": {
        target: "http://127.0.0.1:8080",
        changeOrigin: true,
      },
      "/api/map-images": {
        target: "http://127.0.0.1:8080",
        changeOrigin: true,
      },
      "/api/routes": {
        target: "http://127.0.0.1:8080",
        changeOrigin: true,
      },
    },
  },
});
