#!/usr/bin/env bash
# 停止本地所有服务
ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
LOG_DIR="$ROOT_DIR/logs"

stop_pid() {
  local name=$1 pid_file="$LOG_DIR/$2.pid"
  if [ -f "$pid_file" ]; then
    PID=$(cat "$pid_file")
    if kill -0 "$PID" 2>/dev/null; then
      kill "$PID" && echo "已停止 $name (PID $PID)"
    fi
    rm -f "$pid_file"
  fi
}

stop_pid "前端" frontend
stop_pid "后端" backend
stop_pid "Hardhat" hardhat
echo "所有服务已停止"
