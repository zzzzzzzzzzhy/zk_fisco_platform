#!/bin/bash

# 生产模式重启脚本（利用 Docker 缓存，提升效率）
# 使用方式：
#   ./restart-docker.sh          # 正常重启（利用缓存）
#   ./restart-docker.sh --rebuild # 强制重新构建（不使用缓存）

FORCE_REBUILD=false
if [ "$1" == "--rebuild" ] || [ "$1" == "--no-cache" ]; then
    FORCE_REBUILD=true
fi

echo "=== 停止所有服务 ==="
pkill -f spring-boot
pkill -f npm

cd /data/Dapp_Share_Platform/competition-platform/docker

echo "=== 停止 Docker 容器 ==="
# 兼容旧环境里可能残留的 minio 容器：先带 profile down，再正常 up
docker compose --profile minio down

if [ "$FORCE_REBUILD" = true ]; then
    echo "=== 强制重新构建后端镜像（不使用缓存）==="
    docker compose build app --no-cache
    
    echo "=== 强制重新构建前端镜像（不使用缓存）==="
    docker compose build frontend --no-cache
else
    echo "=== 构建后端镜像（利用缓存，只构建变化的部分）==="
    docker compose build app
    
    echo "=== 构建前端镜像（利用缓存，只构建变化的部分）==="
    docker compose build frontend
fi

echo "=== 启动所有服务 ==="
docker compose up -d

echo "=== 查看容器状态 ==="
docker compose ps

echo "=== 查看后端日志（最后50行）==="
docker compose logs app --tail=50

echo ""
echo "✅ Docker 启动完成！"
echo "访问地址: http://localhost:8086"
echo "后端API: http://localhost:8080"
echo ""
echo "查看实时日志："
echo "  docker compose logs -f app      # 后端日志"
echo "  docker compose logs -f frontend # 前端日志"
echo ""
echo "💡 提示："
echo "  - 正常重启（利用缓存）: ./restart-docker.sh"
echo "  - 强制重建（不使用缓存）: ./restart-docker.sh --rebuild"
echo "  - 开发模式（热重载）: ./restart-docker-dev.sh"
