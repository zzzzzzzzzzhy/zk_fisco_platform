#!/bin/bash
# Polygon Amoy 测试网一键部署脚本
# ⚠️  仅用于测试，不要在主网使用！

set -e

echo "================================================"
echo "  Polygon Amoy 测试网部署脚本"
echo "================================================"
echo ""

# 颜色输出
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# 项目根目录
SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
PROJECT_ROOT=$(cd "$SCRIPT_DIR/.." && pwd)
BACKEND_DIR="$PROJECT_ROOT/backend"
BLOCKCHAIN_DIR="$PROJECT_ROOT/blockchain"
RUST_DIR="${RUST_DIR:-$PROJECT_ROOT/rust}"

# ============================================
# 1. 环境检查
# ============================================
echo -e "${YELLOW}[1/7] 环境检查...${NC}"

# 检查 Node.js
if ! command -v node &> /dev/null; then
    echo -e "${RED}❌ Node.js 未安装${NC}"
    exit 1
fi
echo -e "${GREEN}✅ Node.js: $(node -v)${NC}"

# 检查 Rust
if ! command -v cargo &> /dev/null; then
    echo -e "${RED}❌ Rust 未安装${NC}"
    exit 1
fi
echo -e "${GREEN}✅ Rust: $(rustc --version)${NC}"

# 检查 .env 配置
if [ ! -f "$BACKEND_DIR/.env.amoy" ]; then
    echo -e "${RED}❌ 找不到 .env.amoy 配置文件${NC}"
    exit 1
fi
echo -e "${GREEN}✅ 配置文件存在${NC}"

# 读取私钥
source "$BACKEND_DIR/.env.amoy"
if [ -z "$BLOCKCHAIN_ADMIN_PRIVATE_KEY" ] || [ "$BLOCKCHAIN_ADMIN_PRIVATE_KEY" = "你的测试账户私钥" ]; then
    echo -e "${RED}❌ 请先配置 BLOCKCHAIN_ADMIN_PRIVATE_KEY${NC}"
    echo ""
    echo "📖 获取步骤："
    echo "   1. 安装 MetaMask"
    echo "   2. 添加 Amoy 测试网络"
    echo "   3. 创建新账户（不要使用主网账户！）"
    echo "   4. 导出私钥：MetaMask → 账户详情 → 导出私钥"
    echo "   5. 填入 .env.amoy 的 BLOCKCHAIN_ADMIN_PRIVATE_KEY"
    echo ""
    exit 1
fi
echo -e "${GREEN}✅ 私钥已配置（已隐藏）${NC}"

echo ""

# ============================================
# 2. 编译 Rust Prover
# ============================================
echo -e "${YELLOW}[2/7] 编译 Rust ZK Prover...${NC}"
cd "$RUST_DIR"
cargo build --release --bin rollup-prove
echo -e "${GREEN}✅ Prover 编译完成${NC}"
echo ""

# ============================================
# 3. 生成 Image ID
# ============================================
echo -e "${YELLOW}[3/7] 生成 RISC Zero Image ID...${NC}"
IMAGE_ID_OUTPUT=$(cargo run --bin rollup-prove --release 2>&1 | grep "ROLLUP_IMAGE_ID" || true)
if [ -z "$IMAGE_ID_OUTPUT" ]; then
    echo -e "${YELLOW}⚠️  无法自动获取 Image ID，需要手动配置${NC}"
    echo "   运行: cd $RUST_DIR && cargo run --bin rollup-prove --release"
else
    IMAGE_ID=$(echo "$IMAGE_ID_OUTPUT" | cut -d'=' -f2)
    echo -e "${GREEN}✅ Image ID: $IMAGE_ID${NC}"
fi
echo ""

# ============================================
# 4. 部署智能合约到 Amoy
# ============================================
echo -e "${YELLOW}[4/7] 部署智能合约到 Amoy 测试网...${NC}"
cd "$BLOCKCHAIN_DIR"

# 安装依赖（如果需要）
if [ ! -d "node_modules" ]; then
    echo "安装依赖..."
    npm install
fi

# 编译合约
echo "编译合约..."
npx hardhat compile

# 部署 RISC Zero Verifier
echo "部署 RISC Zero Verifier..."
VERIFIER_ADDRESS=$(npx hardhat run scripts/deploy-risc0-verifier.js --network amoy | grep "Verifier:" | cut -d' ' -f2)
echo -e "${GREEN}✅ Verifier: $VERIFIER_ADDRESS${NC}"

# 部署 ContentRollupRegistry
echo "部署 ContentRollupRegistry..."
REGISTRY_ADDRESS=$(npx hardhat run scripts/deploy-rollup-registry.js --network amoy | grep "Registry:" | cut -d' ' -f2)
echo -e "${GREEN}✅ Registry: $REGISTRY_ADDRESS${NC}"

# 部署 RollupRewardDistributor
echo "部署 RollupRewardDistributor..."
DISTRIBUTOR_ADDRESS=$(npx hardhat run scripts/deploy-rollup-reward-distributor.js --network amoy | grep "Distributor:" | cut -d' ' -f2)
echo -e "${GREEN}✅ Distributor: $DISTRIBUTOR_ADDRESS${NC}"

echo ""

# ============================================
# 5. 更新 .env 配置
# ============================================
echo -e "${YELLOW}[5/7] 更新配置文件...${NC}"
cat >> "$BACKEND_DIR/.env.amoy" <<EOF

# 自动生成的合约地址（$(date)）
ROLLUP_VERIFIER_ADDRESS=$VERIFIER_ADDRESS
ROLLUP_REGISTRY_ADDRESS=$REGISTRY_ADDRESS
ROLLUP_REWARD_DISTRIBUTOR_ADDRESS=$DISTRIBUTOR_ADDRESS
ROLLUP_IMAGE_ID=$IMAGE_ID
EOF
echo -e "${GREEN}✅ 配置已更新${NC}"
echo ""

# ============================================
# 6. 切换后端配置
# ============================================
echo -e "${YELLOW}[6/7] 切换后端到测试网配置...${NC}"
cd "$BACKEND_DIR"

# 备份当前配置
if [ -f ".env" ]; then
    cp .env .env.backup.$(date +%Y%m%d_%H%M%S)
    echo -e "${GREEN}✅ 已备份当前 .env${NC}"
fi

# 使用测试网配置
ln -sf .env.amoy .env
echo -e "${GREEN}✅ 已切换到测试网配置${NC}"

# 修改启动脚本使用 Amoy profile
if grep -q "spring.profiles.active" src/main/resources/application.yml; then
    sed -i.bak 's/spring.profiles.active:.*/spring.profiles.active: amoy/' src/main/resources/application.yml
else
    echo "spring.profiles.active: amoy" >> src/main/resources/application.yml
fi
echo -e "${GREEN}✅ Spring Profile 已设置为 amoy${NC}"
echo ""

# ============================================
# 7. 获取测试代币
# ============================================
echo -e "${YELLOW}[7/7] 获取测试代币...${NC}"
echo ""
echo -e "${GREEN}📖 获取 Amoy MATIC 步骤：${NC}"
echo "   1. 打开 MetaMask，切换到 Amoy 测试网"
echo "   2. 访问: https://faucet.polygon.technology/"
echo "   3. 选择：Amoy Testnet"
echo "   4. 输入你的钱包地址"
echo "   5. 完成验证后领取 0.2-1 MATIC"
echo "   6. 等待约 30 秒到账"
echo ""
echo -e "${YELLOW}⚠️  注意：测试代币每 24 小时限制领取${NC}"
echo ""

# 显示当前账户余额
echo -e "${YELLOW}查询账户余额...${NC}"
WALLET_ADDRESS=$(echo "0x$(echo $BLOCKCHAIN_ADMIN_PRIVATE_KEY | cut -c1-64)" | xxd -r -p | openssl sha256 -hex | head -c 40) 2>/dev/null || echo "（需要 Web3 工具）"
echo "   账户地址: （请在 MetaMask 中查看）"
echo ""

# ============================================
# 完成！
# ============================================
echo "================================================"
echo -e "${GREEN}  ✅ 部署完成！${NC}"
echo "================================================"
echo ""
echo -e "${GREEN}📋 部署摘要：${NC}"
echo "   • RISC Zero Verifier: $VERIFIER_ADDRESS"
echo "   • ContentRollupRegistry: $REGISTRY_ADDRESS"
echo "   • RollupRewardDistributor: $DISTRIBUTOR_ADDRESS"
echo "   • Image ID: $IMAGE_ID"
echo ""
echo -e "${GREEN}📖 下一步：${NC}"
echo "   1. 获取测试 MATIC: https://faucet.polygon.technology/"
echo "   2. 启动后端: cd backend && mvn spring-boot:run"
echo "   3. 查看日志: tail -f logs/spring.log"
echo "   4. 测试 Rollup: 等待下次定时任务执行（每小时）"
echo ""
echo -e "${YELLOW}⚠️  重要提示：${NC}"
echo "   • 此配置仅供测试，不要用于生产环境"
echo "   • 测试代币无需真实价值"
echo "   • 合约代码与主网一致，仅网络不同"
echo ""
