#!/bin/bash

echo "=== Docker 容器状态 ==="
cd /data/Dapp_Share_Platform/competition-platform/docker
docker compose ps

echo ""
echo "=== 后端日志（最后30行）==="
docker compose logs app --tail=30 2>/dev/null || echo "后端容器未运行"

echo ""
echo "=== 前端日志（最后10行）==="
docker compose logs frontend --tail=10 2>/dev/null || echo "前端容器未运行"

echo ""
echo "=== 可用的命令 ==="
echo "  docker compose logs -f app      # 实时查看后端日志"
echo "  docker compose logs -f frontend # 实时查看前端日志"
echo "  docker compose restart app      # 重启后端"
echo "  docker compose restart frontend # 重启前端"

