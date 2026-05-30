## 竞赛平台 Docker 容器化步骤说明（草稿）

> 本文是给“项目作者自己看”的操作步骤说明，用来一步一步把当前这套系统容器化。  
> 先只做最小可用的方案，后面再逐步扩展。

---

## 阶段 0：前置约定

- 后端：`backend`（Spring Boot 3 + JDK 17，端口 8080）  
- 前端：`frontend`（Vue CLI，开发端口默认 8080，生产构建为静态文件）  
- 依赖服务：MySQL、Redis、MinIO、FISCO（当前 MinIO、FISCO 都**继续跑在宿主机**，通过网络和挂载接入容器）  
- 运行环境：一台安装了 **Docker + Docker Compose** 的 Linux 服务器（或本地 WSL 先练习）。

---

## 当前 Docker 集成现状（2025-11-26 更新）

> 本节描述的是**现在已经落地的实际状态**，方便以后回顾或迁移到服务器。

- **Compose 文件位置**：`docker/docker-compose.yml`  
- **当前正在运行的容器**：
  - **competition-backend（app）**：  
    - 从 `../backend` 使用 `backend/Dockerfile` 构建  
    - 暴露端口：`8080:8080`（仅给内网 / 开发机用，线上可只暴露 Nginx）  
    - 环境变量：
      - MySQL：`SPRING_DATASOURCE_URL=mysql:3306`，账号密码 `zhy / zhy020313`  
      - Redis：主机名 `redis`，数据库 `1`，密码 `zhy020313`  
      - MinIO：`MINIO_ENDPOINT=http://172.26.115.103:9000`（宿主机 MinIO，不在容器中）  
      - FISCO：`FISCO_ENABLED=true`，`FISCO_CONFIG_FILE=/app/config.toml`，`FISCO_GROUP_ID=1` 等  
    - **挂载卷（只读）**：
      - `../backend/src/main/resources/config.toml -> /app/config.toml`（FISCO SDK 配置）  
      - `../fisco/nodes/127.0.0.1/sdk -> /app/fisco-sdk`（Guomi 证书）  
      - `../backend/src/main/resources/account -> /app/account`（Guomi 账户 pem 文件）
  - **competition-mysql（mysql）**：  
    - 镜像：`docker.1ms.run/mysql:8.0`（国内镜像加速）  
    - 端口：`3306:3306`  
    - 初始化数据库：`competition_platform`，用户 `zhy / zhy020313`  
    - 数据卷：`mysql-data:/var/lib/mysql`  
  - **competition-redis（redis）**：  
    - 镜像：`docker.1ms.run/redis:7-alpine`  
    - 端口：`6379:6379`，密码 `zhy020313`  
    - 数据卷：`redis-data:/data`
  - **competition-frontend（frontend）**：  
    - 从 `../frontend` 构建，内部是 `nginx:alpine` + 静态文件  
    - 端口：`80:80`  
    - Nginx 反向代理 `/api` 到 `app:8080`，其余路径直接返回前端静态页面。

- **当前仍在宿主机运行的服务**：
  - **MinIO**：
    - 端口：`9000`（API），`9001`（Console）  
    - 桶策略：`content-share` 已设置为 `public download`，用于前端直接访问图片 / 视频。  
    - 后端通过 `MINIO_ENDPOINT=http://172.26.115.103:9000` 访问。
  - **FISCO BCOS Guomi 链**：
    - 节点端口：`172.26.115.103:20200`、`172.26.115.103:20201`  
    - SDK 配置文件：`backend/src/main/resources/config.toml`（容器内挂载到 `/app/config.toml`）  
    - 当前使用的国密账户：`0x1cadfbc03bd63e8d37e7f7fcd7d88300a4fa755b`。

- **后端构建优化**：
  - `backend/Dockerfile` 使用 `docker.1ms.run/maven:3.9-eclipse-temurin-17` + 阿里云 Maven 镜像：  
    - 在 `/root/.m2/settings.xml` 中配置：
      - `<mirrorOf>*</mirrorOf>` 指向 `https://maven.aliyun.com/repository/public`  
    - 实际效果：后端镜像构建时间从 10+ 分钟降低到约 40 秒。

> 之后如果要把 MinIO / FISCO 也容器化，可以在本节下面再补一小节“计划中的下一步”，目前按上面这套部署即可完整运行。

---

## 阶段 1：容器化后端（最小可用）

1. **在 `backend` 目录新增 `Dockerfile`（多阶段构建）**

   - 第一个阶段使用 `maven:3.9-eclipse-temurin-17`：  
     - `COPY` `pom.xml` 和 `src`  
     - 运行 `mvn -q -DskipTests package` 生成 `target/competition-platform.jar`
   - 第二个阶段使用 `eclipse-temurin:17-jre`：  
     - 只拷贝前一阶段生成的 `competition-platform.jar`  
     - `EXPOSE 8080`  
     - `ENTRYPOINT ["java","-jar","-Dspring.profiles.active=prod","/app/competition-platform.jar"]`

2. **在项目根或 `docker/` 下写一个最小版 `docker-compose.yml`**

   只包含 3 个服务：

   - `app`：从 `../backend` 构建镜像，端口映射 `8080:8080`  
   - `mysql`：`mysql:8.0`，映射 `3306:3306`，用环境变量设置库名、账号密码  
   - `redis`：`redis:7-alpine`，简单设置密码  
   - （MinIO 可以等下一步加）

3. **本地验证**

   ```bash
   cd competition-platform/docker   # 假定 docker-compose.yml 放在这个目录
   docker-compose build app
   docker-compose up -d
   docker-compose ps
   curl http://localhost:8080/api/actuator/health
   ```

   目标：后端通过容器能启动、能连上容器里的 MySQL / Redis。

---

## 阶段 2：把 MinIO 放进 Compose

1. 在 `docker-compose.yml` 里新增 `minio` 服务：

   - 使用官方镜像 `minio/minio:latest`  
   - 端口 `9000:9000`（API）、`9001:9001`（Console）  
   - 命令：`server /data --console-address ":9001"`  
   - 使用一个本地卷 `minio-data:/data` 持久化。

2. 修改 `app` 的环境变量：

   - `MINIO_ENDPOINT=http://minio:9000`  
   - `MINIO_ACCESS_KEY/SECRET_KEY` 按 compose 中的值来。

3. 数据迁移：

   - 新容器里的 MinIO 会是一个全新的实例，需要**重新建桶**（可以让后端在启动时自动创建）。  

---

## 阶段 3：前端打包 + Nginx 提供静态文件

1. 在 `frontend` 目录新增 `Dockerfile`：

   - 构建阶段：`node:18`  
     - `npm ci` 或 `npm install`  
     - `npm run build` 生成 `dist/`
   - 运行阶段：`nginx:alpine`  
     - 拷贝 `dist/` 到 `/usr/share/nginx/html`  
     - 覆盖默认 `nginx.conf`（反向代理 `/api` 到后端容器 `app:8080`，其它静态文件直接本地）。

2. 在 `docker-compose.yml` 里新增 `frontend` 服务：

   - `build: ../frontend`  
   - 端口映射 `80:80`（对外只暴露前端，后端隐藏在内部网络）。

3. 本地访问：

   ```bash
   docker-compose up -d --build
   open http://localhost
   ```

   目标：浏览器访问前端容器 → 通过 Nginx 反向代理访问后端 API，一切功能正常。

---

## 阶段 4：准备线上部署与更新流程

1. **给镜像起固定名字和 tag**：

   - 后端镜像：`yourname/competition-app:latest`  
   - 前端镜像：`yourname/competition-frontend:latest`

2. **在 GitHub 上配一个简单的 Actions 流水线**：

   - 触发条件：`push` 到 `main` 分支  
   - 步骤：`checkout` → `docker build` → `docker push` 到 Docker Hub。

3. **服务器上的更新步骤**（手动版）：

   ```bash
   ssh your-server
   cd /path/to/competition-platform/docker
   git pull
   docker-compose pull      # 拉取最新镜像
   docker-compose up -d     # 滚动更新
   ```

4. 以后要做的：

   - 根据 `Docker部署指南.md` 慢慢加 Prometheus / Grafana 监控  
   - 考虑把 FISCO BCOS 也做成独立容器或单独一台机器。

---

## 后续可以优化的点（不用一次到位）

- 给 `app` 容器单独挂载 `/app/logs`，方便收集日志。  
- 在 Compose 里使用 `.env` 注入敏感配置（数据库密码、JWT Secret 等）。  
- 引入 Traefik 或 Nginx 作为统一入口，支持 HTTPS / 多域名。  
- 用 `docker-compose -f docker-compose.yml -f docker-compose.prod.yml` 区分开发 / 生产配置。

---

> 下一步建议：  
> 按上面“阶段 1”先在项目里新建 `backend/Dockerfile` 和一个精简版 `docker/docker-compose.yml`，我可以直接帮你把这两个文件写好并验证本地能跑通。 


