#!/bin/bash

# 开发模式重启脚本（支持热重载，代码修改后自动生效）
# 特点：
# 1. 后端：代码挂载，Spring Boot DevTools 自动重启
# 2. 前端：代码挂载，Vue CLI 开发服务器支持热重载
# 3. 利用 Docker 缓存，只构建一次

echo "=== 开发模式：停止所有服务 ==="
pkill -f spring-boot
pkill -f npm

cd /home/ubuntu/data/Dapp_Share_Platform/competition-platform/docker

echo "=== 停止 Docker 容器 ==="
# 兼容旧环境里可能残留的 minio 容器：先带 profile down
docker compose --profile minio -f docker-compose.yml -f docker-compose.dev.yml down

echo "=== 构建开发模式镜像（首次构建，后续利用缓存）==="
docker compose -f docker-compose.yml -f docker-compose.dev.yml build

echo "=== 启动开发模式服务 ==="
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d

echo "=== 查看容器状态 ==="
docker compose -f docker-compose.yml -f docker-compose.dev.yml ps

echo "=== 查看后端日志（最后50行）==="
docker compose -f docker-compose.yml -f docker-compose.dev.yml logs app --tail=50

echo ""
echo "✅ 开发模式启动完成！"
echo ""
echo "📝 开发模式特点："
echo "  - 后端代码修改后，Spring Boot DevTools 会自动重启（约5-10秒）"
echo "  - 前端代码修改后，Vue CLI 会自动热重载（立即生效）"
echo "  - 无需重新构建镜像，直接修改代码即可"
echo ""
echo "访问地址:"
echo "  前端: http://localhost:8086"
echo "  后端API: http://localhost:8080"
echo ""
echo "查看实时日志："
echo "  docker compose -f docker-compose.yml -f docker-compose.dev.yml logs -f app"
echo "  docker compose -f docker-compose.yml -f docker-compose.dev.yml logs -f frontend"
echo ""
echo "💡 提示："
echo "  - 首次启动可能需要1-2分钟（构建镜像）"
echo "  - 后续代码修改无需重启，自动生效"
echo "  - 如果修改了依赖（pom.xml/package.json），需要重新构建："
echo "    docker compose -f docker-compose.yml -f docker-compose.dev.yml build"
