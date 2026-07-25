# MedRoute 医院室内路线引导

MedRoute 是面向医院楼栋的关键点到关键点室内路线引导系统，不做实时定位。
当前最小可用版本支持每层独立底图、节点/路径/POI 标注、电梯和楼梯停靠关系、
跨层路线、草稿校验发布、历史版回滚及临时设施封闭。

## 技术结构

```text
hospital-map-demo/             静态导航页和地图维护后台
hospital-navigation-server/    Spring Boot 模块化单体
PostgreSQL 16                  地图、底图、发布版本和运营状态
Flyway                         自动建表和数据库版本迁移
```

正式路线由后端计算。地图维护只修改草稿，用户导航只读取当前启用的发布版本。

## 快速启动

准备 JDK 17、Docker Desktop 和 Python 3。在仓库根目录启动数据库：

```bash
docker compose up -d postgres
```

启动后端：

```bash
cd hospital-navigation-server
./mvnw spring-boot:run
```

另开终端，在仓库根目录启动静态页面：

```bash
python3 -m http.server 4173
```

打开：

- 地图维护后台：<http://127.0.0.1:4173/hospital-map-demo/admin.html>
- 多楼层导航：<http://127.0.0.1:4173/hospital-map-demo/multifloor.html>
- 单层原型：<http://127.0.0.1:4173/hospital-map-demo/index.html>

IDEA 可以打开仓库根目录，也可以只打开 `hospital-navigation-server`。运行
`HospitalNavigationApplication` 前，确认 Project SDK 为 JDK 17 且 PostgreSQL
容器已经启动。

## 验证

```bash
cd hospital-navigation-server
./mvnw test
```

完整的维护、发布、回滚、备份流程见
[`docs/10-地图维护与发布操作手册.md`](docs/10-地图维护与发布操作手册.md)。

## 当前边界

- 第一份测试数据是一栋三层门急诊楼，底图来自公开医院页面，仅用于开发验证。
- 当前维护端未接入正式登录和权限系统，只适合本机或受控内网验收。
- 生产部署前必须增加身份认证、楼栋数据权限、HTTPS、反向代理和定时备份。
