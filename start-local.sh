#!/usr/bin/env bash
# ============================================================
# 本地一键启动脚本
# 依赖: Java 17+, Maven, Node.js 16+, MySQL 8, Redis
# ============================================================
set -e

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
BLOCKCHAIN_DIR="$ROOT_DIR/blockchain"
BACKEND_DIR="$ROOT_DIR/backend"
FRONTEND_DIR="$ROOT_DIR/frontend"
LOG_DIR="$ROOT_DIR/logs"

mkdir -p "$LOG_DIR"

# ---- 颜色输出 ----
GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'
info()  { echo -e "${GREEN}[INFO]${NC} $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC} $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*"; }

# ---- 检查依赖 ----
info "检查依赖..."
command -v java  >/dev/null || { error "未找到 java，请安装 JDK 17+"; exit 1; }
command -v mvn   >/dev/null || { error "未找到 maven"; exit 1; }
command -v node  >/dev/null || { error "未找到 node"; exit 1; }
command -v mysql >/dev/null || { error "未找到 mysql 客户端"; exit 1; }
command -v redis-cli >/dev/null || { error "未找到 redis-cli"; exit 1; }

# ---- 加载环境变量 ----
if [ -f "$ROOT_DIR/.env" ]; then
  info "加载 .env 文件..."
  set -a; source "$ROOT_DIR/.env"; set +a
fi

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-competition_platform}"
DB_USERNAME="${DB_USERNAME:-root}"
DB_PASSWORD="${DB_PASSWORD:-}"
REDIS_HOST="${REDIS_HOST:-127.0.0.1}"
REDIS_PORT="${REDIS_PORT:-6379}"
REDIS_PASSWORD="${REDIS_PASSWORD:-}"

# ---- 检查 MySQL ----
info "检查 MySQL 连接 ($DB_HOST:$DB_PORT)..."
if ! mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USERNAME" ${DB_PASSWORD:+-p"$DB_PASSWORD"} \
     -e "SELECT 1" "$DB_NAME" >/dev/null 2>&1; then
  error "无法连接 MySQL ($DB_HOST:$DB_PORT/$DB_NAME)，请先启动 MySQL 并创建数据库"
  echo "  参考命令: mysqld --user=mysql --port=$DB_PORT --daemonize"
  echo "  建库命令: mysql -u root -e \"CREATE DATABASE IF NOT EXISTS $DB_NAME\""
  exit 1
fi
info "MySQL 连接正常 ✅"

# ---- 初始化数据库表 ----
SQL_DIR="$BACKEND_DIR/src/main/resources/sql"
if mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USERNAME" ${DB_PASSWORD:+-p"$DB_PASSWORD"} \
     "$DB_NAME" -e "SHOW TABLES LIKE 'users'" 2>/dev/null | grep -q users; then
  info "数据库表已存在，跳过初始化"
else
  info "初始化数据库表..."
  mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USERNAME" ${DB_PASSWORD:+-p"$DB_PASSWORD"} \
    "$DB_NAME" < "$SQL_DIR/schema.sql"
  mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USERNAME" ${DB_PASSWORD:+-p"$DB_PASSWORD"} \
    "$DB_NAME" < "$SQL_DIR/governance_tables.sql"
  for f in V2_add_leaderboard_features V3_add_prize_disbursement_features V4_add_content_share_discussions V5_add_reward_events; do
    sed 's/USE `competition-platform`;//g' "$SQL_DIR/migrations/$f.sql" | \
      mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USERNAME" ${DB_PASSWORD:+-p"$DB_PASSWORD"} \
      "$DB_NAME" --force 2>/dev/null || true
  done
  info "数据库初始化完成 ✅"
fi

# ---- 检查 Redis ----
info "检查 Redis 连接 ($REDIS_HOST:$REDIS_PORT)..."
REDIS_PING=$(redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" ${REDIS_PASSWORD:+-a "$REDIS_PASSWORD"} ping 2>/dev/null)
if [ "$REDIS_PING" != "PONG" ]; then
  error "无法连接 Redis，请先启动: redis-server --daemonize yes ${REDIS_PASSWORD:+--requirepass $REDIS_PASSWORD}"
  exit 1
fi
info "Redis 连接正常 ✅"

# ---- 安装 blockchain 依赖 ----
if [ ! -d "$BLOCKCHAIN_DIR/node_modules" ]; then
  info "安装 blockchain 依赖..."
  cd "$BLOCKCHAIN_DIR" && npm install --prefer-offline
fi

# ---- 启动 Hardhat 节点 ----
info "启动 Hardhat 本地节点 (端口 8545)..."
if lsof -i :8545 >/dev/null 2>&1; then
  warn "端口 8545 已被占用，跳过启动 Hardhat"
else
  cd "$BLOCKCHAIN_DIR"
  nohup npx hardhat node > "$LOG_DIR/hardhat.log" 2>&1 &
  HARDHAT_PID=$!
  echo $HARDHAT_PID > "$LOG_DIR/hardhat.pid"
  info "Hardhat 节点 PID: $HARDHAT_PID，等待启动..."
  sleep 5
fi

# ---- 编译并部署合约 ----
if [ -f "$BLOCKCHAIN_DIR/deployments/local.json" ]; then
  info "检测到已有本地部署，跳过重新部署（删除 blockchain/deployments/local.json 可强制重新部署）"
else
  info "编译合约..."
  cd "$BLOCKCHAIN_DIR" && npx hardhat compile
  info "部署合约到本地节点..."
  npx hardhat run scripts/deploy-local.js --network localhost
fi

# ---- 读取合约地址写入后端环境变量 ----
if [ -f "$BLOCKCHAIN_DIR/deployments/local.json" ]; then
  WEE_ADDR=$(node -e "const d=require('$BLOCKCHAIN_DIR/deployments/local.json'); console.log(d.contracts.weeToken)")
  FORUM_ADDR=$(node -e "const d=require('$BLOCKCHAIN_DIR/deployments/local.json'); console.log(d.contracts.forumTokenExtension)")
  CONTENT_ADDR=$(node -e "const d=require('$BLOCKCHAIN_DIR/deployments/local.json'); console.log(d.contracts.contentShareRegistry)")
  export BLOCKCHAIN_WEE_TOKEN_ADDRESS="$WEE_ADDR"
  export BLOCKCHAIN_FORUM_TOKEN_EXTENSION_ADDRESS="$FORUM_ADDR"
  export BLOCKCHAIN_CONTENT_SHARE_REGISTRY_ADDRESS="$CONTENT_ADDR"
  export BLOCKCHAIN_ADMIN_PRIVATE_KEY="0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80"
  info "合约地址已加载:
    WEE Token:           $WEE_ADDR
    ForumTokenExtension: $FORUM_ADDR
    ContentShareRegistry:$CONTENT_ADDR"
fi

# ---- 编译后端 ----
if [ ! -f "$BACKEND_DIR/target/competition-platform.jar" ]; then
  info "编译后端..."
  cd "$BACKEND_DIR"
  JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}" \
    mvn clean package -DskipTests -q
fi

# ---- 启动后端 ----
info "启动后端 (端口 8080)..."
cd "$BACKEND_DIR"
export DB_URL="jdbc:mysql://${DB_HOST}:${DB_PORT}/${DB_NAME}?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true"
export SPRING_REDIS_HOST="$REDIS_HOST"
export SPRING_REDIS_PORT="$REDIS_PORT"
export SPRING_REDIS_PASSWORD="$REDIS_PASSWORD"
export POLYGON_RPC_URL="http://127.0.0.1:8545"
export POLYGON_CHAIN_ID="31337"

nohup java -jar target/competition-platform.jar > "$LOG_DIR/backend.log" 2>&1 &
BACKEND_PID=$!
echo $BACKEND_PID > "$LOG_DIR/backend.pid"
info "后端 PID: $BACKEND_PID，等待启动..."

# 等待后端就绪
for i in $(seq 1 30); do
  sleep 3
  if curl -s http://localhost:8080/api/competitions >/dev/null 2>&1; then
    info "后端启动成功 ✅"
    break
  fi
  if [ $i -eq 30 ]; then
    warn "后端 30 次检查超时，请查看 logs/backend.log"
  fi
done

# ---- 安装前端依赖 ----
if [ ! -d "$FRONTEND_DIR/node_modules" ]; then
  info "安装前端依赖..."
  cd "$FRONTEND_DIR" && npm install --prefer-offline
fi

# ---- 启动前端 ----
info "启动前端开发服务器 (端口 8084)..."
cd "$FRONTEND_DIR"
nohup npm run serve > "$LOG_DIR/frontend.log" 2>&1 &
FRONTEND_PID=$!
echo $FRONTEND_PID > "$LOG_DIR/frontend.pid"
info "前端 PID: $FRONTEND_PID"

# 等待前端就绪
for i in $(seq 1 20); do
  sleep 3
  if grep -q "App running at" "$LOG_DIR/frontend.log" 2>/dev/null; then
    info "前端启动成功 ✅"
    break
  fi
done

echo ""
echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}  竞赛平台本地环境启动完成！${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""
echo "  前端页面:  http://localhost:8084"
echo "  后端 API:  http://localhost:8080/api"
echo "  Hardhat:   http://127.0.0.1:8545  (chainId: 31337)"
echo ""
echo "  日志目录:  $LOG_DIR/"
echo "  停止所有:  bash stop-local.sh"
echo ""
info "MetaMask 网络配置:"
echo "  名称: Hardhat Local"
echo "  RPC:  http://127.0.0.1:8545"
echo "  链ID: 31337"
echo "  货币: ETH"
