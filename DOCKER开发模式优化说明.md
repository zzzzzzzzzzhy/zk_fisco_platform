# Docker 开发模式优化说明

## 📊 当前问题分析

### 之前的低效方式：
1. **每次修改代码都要**：
   ```bash
   # 后端
   cd backend && mvn clean package -DskipTests
   cd ../docker && docker compose build app --no-cache
   docker compose up -d
   
   # 前端
   cd docker && docker compose build frontend --no-cache
   docker compose up -d
   ```

2. **问题**：
   - ❌ 使用 `--no-cache` 完全禁用缓存，每次都重新构建
   - ❌ 后端需要先在宿主机构建 JAR，再构建镜像（双重构建）
   - ❌ 前端每次都要重新安装依赖和构建
   - ❌ 代码修改后需要手动重启，无法热重载
   - ❌ 构建时间：后端 2-5 分钟，前端 1-3 分钟

## ✅ 优化后的方案

### 方案一：生产模式（利用 Docker 缓存）

**特点**：移除 `--no-cache`，利用 Docker 缓存，只构建变化的部分

**使用方式**：
```bash
cd /data/Dapp_Share_Platform/competition-platform/docker
./restart-docker.sh          # 正常重启（利用缓存，快速）
./restart-docker.sh --rebuild # 强制重建（不使用缓存）
```

**效率提升**：
- ✅ 首次构建：2-5 分钟（正常）
- ✅ 代码修改后：10-30 秒（只重新构建变化的层）
- ✅ 依赖未变化时：5-10 秒（几乎只复制文件）

### 方案二：开发模式（代码挂载 + 热重载）⭐ 推荐

**特点**：
- 代码挂载到容器，修改代码后自动生效
- 后端：Spring Boot DevTools 自动重启（5-10秒）
- 前端：Vue CLI 开发服务器热重载（立即生效）
- 无需重新构建镜像

**使用方式**：
```bash
cd /data/Dapp_Share_Platform/competition-platform/docker
./restart-docker-dev.sh
```

**效率提升**：
- ✅ 首次启动：1-2 分钟（构建镜像）
- ✅ 代码修改后：**无需重启**，自动生效
- ✅ 后端重启：5-10 秒（DevTools 自动重启）
- ✅ 前端更新：**立即生效**（热重载）

## 🚀 快速开始

### 1. 启用后端热重载（可选，但推荐）

如果后端还没有 Spring Boot DevTools，需要添加：

```xml
<!-- backend/pom.xml -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-devtools</artifactId>
    <scope>runtime</scope>
    <optional>true</optional>
</dependency>
```

然后重新构建：
```bash
cd backend
mvn clean package -DskipTests
```

### 2. 启动开发模式

```bash
cd /data/Dapp_Share_Platform/competition-platform/docker
chmod +x restart-docker-dev.sh
./restart-docker-dev.sh
```

### 3. 开发流程

1. **修改代码**（后端或前端）
2. **保存文件**
3. **等待自动生效**：
   - 前端：浏览器自动刷新（1-2秒）
   - 后端：查看日志，等待自动重启（5-10秒）

### 4. 查看日志

```bash
# 实时查看后端日志
docker compose -f docker-compose.yml -f docker-compose.dev.yml logs -f app

# 实时查看前端日志
docker compose -f docker-compose.yml -f docker-compose.dev.yml logs -f frontend
```

## 📋 使用场景对比

| 场景 | 推荐方案 | 命令 |
|------|---------|------|
| **日常开发** | 开发模式 | `./restart-docker-dev.sh` |
| **测试生产构建** | 生产模式 | `./restart-docker.sh` |
| **依赖更新后** | 生产模式 + 重建 | `./restart-docker.sh --rebuild` |
| **首次部署** | 生产模式 | `./restart-docker.sh` |

## 🔧 文件说明

### 新增文件

1. **`docker/docker-compose.dev.yml`**
   - 开发模式配置
   - 代码挂载、热重载配置

2. **`backend/Dockerfile.dev`**
   - 后端开发模式 Dockerfile
   - 支持代码挂载和 DevTools

3. **`frontend/Dockerfile.dev`**
   - 前端开发模式 Dockerfile
   - 使用 Vue CLI 开发服务器

4. **`restart-docker-dev.sh`**
   - 开发模式快速启动脚本

### 修改文件

1. **`restart-docker.sh`**
   - 移除了默认的 `--no-cache`
   - 添加了 `--rebuild` 选项用于强制重建
   - 利用 Docker 缓存提升效率

## 💡 最佳实践

### 1. 日常开发
```bash
# 启动开发模式（首次）
./restart-docker-dev.sh

# 之后修改代码，自动生效，无需重启
```

### 2. 修改依赖后
```bash
# 如果修改了 pom.xml 或 package.json
docker compose -f docker-compose.yml -f docker-compose.dev.yml build
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d
```

### 3. 测试生产构建
```bash
# 切换到生产模式测试
./restart-docker.sh
```

## ⚠️ 注意事项

1. **开发模式 vs 生产模式**
   - 开发模式：代码挂载，性能略低，但支持热重载
   - 生产模式：代码打包进镜像，性能最优，但需要重新构建

2. **网络模式**
   - 当前使用 `network_mode: host`，可以直接访问宿主机服务
   - 开发模式也保持这个配置

3. **数据持久化**
   - MySQL 和 Redis 数据通过 volumes 持久化
   - 开发模式不影响数据

4. **首次启动**
   - 开发模式首次启动需要构建镜像，可能需要 1-2 分钟
   - 后续代码修改无需重启

## 📈 效率对比

### 之前的方式（每次修改代码）
```
修改代码 → 构建 JAR (1-2分钟) → 构建镜像 (1-2分钟) → 重启容器 (10秒)
总计：2-4 分钟
```

### 优化后的生产模式（利用缓存）
```
修改代码 → 构建镜像 (10-30秒，利用缓存) → 重启容器 (10秒)
总计：20-40 秒
```

### 优化后的开发模式（热重载）⭐
```
修改代码 → 保存 → 自动生效
总计：0-10 秒（无需手动操作）
```

## 🎯 总结

- **开发阶段**：使用 `./restart-docker-dev.sh`，享受热重载
- **测试阶段**：使用 `./restart-docker.sh`，测试生产构建
- **部署阶段**：使用 `./restart-docker.sh --rebuild`，确保最新代码

效率提升：**从每次 2-4 分钟 → 开发模式几乎实时生效** 🚀

