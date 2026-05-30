#!/bin/bash
# 快速部署 ZK-Rollup 合约到 Amoy 测试网

set -e

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
PROJECT_ROOT=$(cd "$SCRIPT_DIR/.." && pwd)
BLOCKCHAIN_DIR="$PROJECT_ROOT/blockchain"
RUST_DIR="${RUST_DIR:-$PROJECT_ROOT/rust}"

echo "================================================"
echo "  ZK-Rollup 合约一键部署到 Amoy 测试网"
echo "================================================"
echo ""

# ============================================
# 1. 环境检查
# ============================================
echo -e "${YELLOW}[1/5] 环境检查...${NC}"

cd $BLOCKCHAIN_DIR

# 检查 PRIVATE_KEY
if [ -z "$PRIVATE_KEY" ] && [ -z "$BLOCKCHAIN_ADMIN_PRIVATE_KEY" ]; then
    echo -e "${RED}❌ 未找到私钥配置${NC}"
    echo ""
    echo "请设置环境变量："
    echo "  export PRIVATE_KEY=你的私钥"
    echo "  或在 .env 中配置 BLOCKCHAIN_ADMIN_PRIVATE_KEY"
    exit 1
fi

# 使用项目根目录的 .env
if [ -f "$PROJECT_ROOT/.env" ]; then
    export $(cat "$PROJECT_ROOT/.env" | grep -v '^#' | xargs)
fi

echo -e "${GREEN}✅ 环境配置已加载${NC}"
echo ""

# ============================================
# 2. 编译合约
# ============================================
echo -e "${YELLOW}[2/5] 编译智能合约...${NC}"
npx hardhat compile
echo -e "${GREEN}✅ 合约编译完成${NC}"
echo ""

# ============================================
# 3. 设置环境变量
# ============================================
echo -e "${YELLOW}[3/5] 配置环境变量...${NC}"

# Image ID（优先使用环境变量，其次尝试从 rollup-prove 输出获取）
if [ -z "$ROLLUP_IMAGE_ID" ]; then
    ROLLUP_PROVER_BIN="$RUST_DIR/target/release/rollup-prove"
    if [ ! -x "$ROLLUP_PROVER_BIN" ]; then
        ROLLUP_PROVER_BIN="$RUST_DIR/host/target/release/rollup-prove"
    fi
    if [ -x "$ROLLUP_PROVER_BIN" ]; then
        IMAGE_ID_OUTPUT=$("$ROLLUP_PROVER_BIN" 2>&1 | grep "ROLLUP_IMAGE_ID" | head -n 1 || true)
        if [ -n "$IMAGE_ID_OUTPUT" ]; then
            ROLLUP_IMAGE_ID=$(echo "$IMAGE_ID_OUTPUT" | sed -E 's/.*ROLLUP_IMAGE_ID[:= ]+//')
            ROLLUP_IMAGE_ID=$(echo "$ROLLUP_IMAGE_ID" | tr -d '[:space:]')
        fi
    fi
fi
if [ -z "$ROLLUP_IMAGE_ID" ]; then
    echo -e "${RED}❌ 未获取到 ROLLUP_IMAGE_ID，请先生成或手动设置${NC}"
    echo "  方式 1：export ROLLUP_IMAGE_ID=0x..."
    echo "  方式 2：运行 rollup-prove 获取 Image ID"
    exit 1
fi
export ROLLUP_IMAGE_ID
echo -e "${GREEN}✅ Image ID: $ROLLUP_IMAGE_ID${NC}"
echo ""

ROLLUP_PROVER_CMD="${ROLLUP_PROVER_CMD:-$RUST_DIR/target/release/rollup-prove}"

# ============================================
# 4. 部署合约
# ============================================
echo -e "${YELLOW}[4/5] 部署合约到 Amoy 测试网...${NC}"
echo ""

# 4.1 部署 RISC Zero Verifier
echo "部署 RISC Zero Groth16 Verifier..."
VERIFIER_OUTPUT=$(npx hardhat run scripts/deploy-risc0-verifier.js --network amoy 2>&1)
VERIFIER_ADDRESS=$(echo "$VERIFIER_OUTPUT" | grep "deployed to:" | awk '{print $NF}')
if [ -z "$VERIFIER_ADDRESS" ]; then
    echo -e "${RED}❌ Verifier 部署失败${NC}"
    echo "$VERIFIER_OUTPUT"
    exit 1
fi
echo -e "${GREEN}✅ Verifier: $VERIFIER_ADDRESS${NC}"
echo ""

# 设置 Verifier 地址
export ROLLUP_VERIFIER_ADDRESS=$VERIFIER_ADDRESS

# 4.2 部署 ContentRollupRegistry
echo "部署 ContentRollupRegistry..."
REGISTRY_OUTPUT=$(npx hardhat run scripts/deploy-rollup-registry.js --network amoy 2>&1)
REGISTRY_ADDRESS=$(echo "$REGISTRY_OUTPUT" | grep "deployed to:" | awk '{print $NF}')
if [ -z "$REGISTRY_ADDRESS" ]; then
    echo -e "${RED}❌ Registry 部署失败${NC}"
    echo "$REGISTRY_OUTPUT"
    exit 1
fi
echo -e "${GREEN}✅ Registry: $REGISTRY_ADDRESS${NC}"
echo ""

# 4.3 部署 RollupRewardDistributor
echo "部署 RollupRewardDistributor..."
DISTRIBUTOR_OUTPUT=$(npx hardhat run scripts/deploy-rollup-reward-distributor.js --network amoy 2>&1)
DISTRIBUTOR_ADDRESS=$(echo "$DISTRIBUTOR_OUTPUT" | grep "deployed to:" | awk '{print $NF}')
if [ -z "$DISTRIBUTOR_ADDRESS" ]; then
    echo -e "${RED}❌ Distributor 部署失败${NC}"
    echo "$DISTRIBUTOR_OUTPUT"
    exit 1
fi
echo -e "${GREEN}✅ Distributor: $DISTRIBUTOR_ADDRESS${NC}"
echo ""

# ============================================
# 5. 更新配置
# ============================================
echo -e "${YELLOW}[5/5] 更新配置文件...${NC}"

# 更新 .env.amoy
cat >> "$PROJECT_ROOT/backend/.env.amoy" <<EOF

# 自动生成的 Amoy 合约地址（$(date +%Y-%m-%d\ %H:%M:%S)）
ROLLUP_VERIFIER_ADDRESS=$VERIFIER_ADDRESS
ROLLUP_REGISTRY_ADDRESS=$REGISTRY_ADDRESS
ROLLUP_REWARD_DISTRIBUTOR_ADDRESS=$DISTRIBUTOR_ADDRESS
ROLLUP_IMAGE_ID=$ROLLUP_IMAGE_ID
ROLLUP_PROVER_CMD=$ROLLUP_PROVER_CMD
ROLLUP_PROOF_DIR=proofs/rollup
REWARD_ROLLUP_ENABLED=true
REWARD_ROLLUP_WINDOW_MINUTES=120
EOF

echo -e "${GREEN}✅ 配置已保存到 backend/.env.amoy${NC}"
echo ""

# ============================================
# 完成！
# ============================================
echo "================================================"
echo -e "${GREEN}  ✅ 部署成功！${NC}"
echo "================================================"
echo ""
echo -e "${GREEN}📋 合约地址：${NC}"
echo "   • RISC Zero Verifier:"
echo "     $VERIFIER_ADDRESS"
echo ""
echo "   • ContentRollupRegistry:"
echo "     $REGISTRY_ADDRESS"
echo ""
echo "   • RollupRewardDistributor:"
echo "     $DISTRIBUTOR_ADDRESS"
echo ""
echo "   • Image ID:"
echo "     $ROLLUP_IMAGE_ID"
echo ""
echo -e "${GREEN}📖 下一步：${NC}"
echo "   1. 在 Polygonscan 验证合约（可选）"
echo "      https://amoy.polygonscan.com/address/$VERIFIER_ADDRESS"
echo ""
echo "   2. 切换后端到测试网配置"
echo "      cd $PROJECT_ROOT/backend"
echo "      ln -sf .env.amoy .env"
echo ""
echo "   3. 启动后端"
echo "      mvn spring-boot:run"
echo ""
echo "   4. 查看日志"
echo "      tail -f logs/spring.log | grep -i rollup"
echo ""
echo -e "${YELLOW}⚠️  Gas 消耗预估：${NC}"
echo "   • Verifier 部署: ~2,000,000 gas (~0.1 MATIC)"
echo "   • Registry 部署: ~1,500,000 gas (~0.075 MATIC)"
echo "   • Distributor 部署: ~800,000 gas (~0.04 MATIC)"
echo "   • 总计: ~0.215 MATIC"
echo ""
echo -e "${GREEN}💰 节省效果（启用 Rollup 后）：${NC}"
echo "   • 从: 每笔交易 ~0.0025 MATIC"
echo "   • 到: 每批次 ~0.0125 MATIC (包含 100+ 笔交易)"
echo "   • 节省: 96%+ ✨"
echo ""
