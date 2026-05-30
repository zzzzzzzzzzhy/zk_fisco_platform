#!/bin/bash
# Amoy 测试网配置检查脚本

echo "================================================"
echo "  Polygon Amoy 测试网配置检查"
echo "================================================"
echo ""

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
PROJECT_ROOT=$(cd "$SCRIPT_DIR/.." && pwd)
BACKEND_DIR="$PROJECT_ROOT/backend"
RUST_DIR="${RUST_DIR:-$PROJECT_ROOT/rust}"

# 读取配置
if [ -f "$BACKEND_DIR/.env" ]; then
    source "$BACKEND_DIR/.env"
else
    echo -e "${RED}❌ 找不到 .env 文件${NC}"
    exit 1
fi

ISSUES_FOUND=0

echo -e "${YELLOW}检查项目：${NC}"
echo ""

# 1. RPC 配置
echo "1. RPC 配置"
if [[ "$POLYGON_RPC_URL" == *"amoy"* ]] || [[ "$POLYGON_RPC_URL" == *"80002"* ]]; then
    echo -e "   ${GREEN}✅ RPC URL: $POLYGON_RPC_URL${NC}"
else
    echo -e "   ${RED}❌ RPC URL 可能指向主网: $POLYGON_RPC_URL${NC}"
    echo -e "   ${YELLOW}   应为: https://rpc-amoy.polygon.technology${NC}"
    ISSUES_FOUND=$((ISSUES_FOUND + 1))
fi
echo ""

# 2. Chain ID
echo "2. Chain ID 配置"
CHAIN_ID=$(grep "chain-id:" "$BACKEND_DIR/src/main/resources/application.yml" | awk '{print $2}')
if [ "$CHAIN_ID" = "80002" ]; then
    echo -e "   ${GREEN}✅ Chain ID: $CHAIN_ID (Amoy 测试网)${NC}"
else
    echo -e "   ${RED}❌ Chain ID: $CHAIN_ID (不是 Amoy 测试网)${NC}"
    echo -e "   ${YELLOW}   应为: 80002${NC}"
    ISSUES_FOUND=$((ISSUES_FOUND + 1))
fi
echo ""

# 3. 合约地址
echo "3. 合约地址配置"
if [ -z "$ROLLUP_REGISTRY_ADDRESS" ]; then
    echo -e "   ${YELLOW}⚠️  ROLLUP_REGISTRY_ADDRESS 未配置（需要部署）${NC}"
    ISSUES_FOUND=$((ISSUES_FOUND + 1))
else
    echo -e "   ${GREEN}✅ Registry: $ROLLUP_REGISTRY_ADDRESS${NC}"
fi

if [ -z "$ROLLUP_REWARD_DISTRIBUTOR_ADDRESS" ]; then
    echo -e "   ${YELLOW}⚠️  ROLLUP_REWARD_DISTRIBUTOR_ADDRESS 未配置（需要部署）${NC}"
    ISSUES_FOUND=$((ISSUES_FOUND + 1))
else
    echo -e "   ${GREEN}✅ Distributor: $ROLLUP_REWARD_DISTRIBUTOR_ADDRESS${NC}"
fi

if [ -z "$ROLLUP_IMAGE_ID" ]; then
    echo -e "   ${YELLOW}⚠️  ROLLUP_IMAGE_ID 未配置（需要生成）${NC}"
    ISSUES_FOUND=$((ISSUES_FOUND + 1))
else
    echo -e "   ${GREEN}✅ Image ID: $ROLLUP_IMAGE_ID${NC}"
fi
echo ""

# 4. Prover 配置
echo "4. ZK Prover 配置"
PROVER_CMD="$RUST_DIR/host/target/release/rollup-prove"
if [ -f "$PROVER_CMD" ]; then
    echo -e "   ${GREEN}✅ Prover 二进制存在: $PROVER_CMD${NC}"
else
    echo -e "   ${RED}❌ Prover 二进制不存在: $PROVER_CMD${NC}"
    echo -e "   ${YELLOW}   需要运行: cd $RUST_DIR && cargo build --release${NC}"
    ISSUES_FOUND=$((ISSUES_FOUND + 1))
fi

if [ -n "$ROLLUP_PROVER_CMD" ]; then
    echo -e "   ${GREEN}✅ Prover 命令已配置: $ROLLUP_PROVER_CMD${NC}"
else
    echo -e "   ${YELLOW}⚠️  Prover 命令未配置${NC}"
    ISSUES_FOUND=$((ISSUES_FOUND + 1))
fi
echo ""

# 5. 私钥配置
echo "5. 管理员私钥配置"
if [ -z "$BLOCKCHAIN_ADMIN_PRIVATE_KEY" ]; then
    echo -e "   ${RED}❌ BLOCKCHAIN_ADMIN_PRIVATE_KEY 未配置${NC}"
    ISSUES_FOUND=$((ISSUES_FOUND + 1))
else
    KEY_LENGTH=${#BLOCKCHAIN_ADMIN_PRIVATE_KEY}
    if [ "$KEY_LENGTH" -eq 64 ]; then
        echo -e "   ${GREEN}✅ 私钥已配置（长度正确）${NC}"
    else
        echo -e "   ${YELLOW}⚠️  私钥长度异常: $KEY_LENGTH 字符（应为 64）${NC}"
        ISSUES_FOUND=$((ISSUES_FOUND + 1))
    fi
fi
echo ""

# 6. Rollup 配置
echo "6. Rollup 功能配置"
if [ "$REWARD_ROLLUP_ENABLED" = "true" ]; then
    echo -e "   ${GREEN}✅ Rollup 已启用${NC}"
else
    echo -e "   ${YELLOW}⚠️  Rollup 未启用${NC}"
fi

if [ -n "$REWARD_ROLLUP_WINDOW_MINUTES" ]; then
    echo -e "   ${GREEN}✅ 时间窗口: $REWARD_ROLLUP_WINDOW_MINUTES 分钟${NC}"
else
    echo -e "   ${YELLOW}⚠️  时间窗口未配置${NC}"
fi
echo ""

# 总结
echo "================================================"
if [ $ISSUES_FOUND -eq 0 ]; then
    echo -e "${GREEN}  ✅ 所有检查通过！${NC}"
    echo "================================================"
    echo ""
    echo "🚀 可以启动后端测试："
    echo "   cd $BACKEND_DIR"
    echo "   mvn spring-boot:run"
    echo ""
else
    echo -e "${RED}  发现 $ISSUES_FOUND 个问题${NC}"
    echo "================================================"
    echo ""
    echo "📖 解决方案："
    echo "   运行部署脚本: bash scripts/deploy-to-amoy.sh"
    echo ""
fi
