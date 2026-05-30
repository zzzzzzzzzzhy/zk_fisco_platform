/**
 * 本地 Hardhat 节点一键部署脚本
 * 用法: npx hardhat run scripts/deploy-local.js --network localhost
 *
 * 会依次部署:
 *   1. MockWEEToken           (本地测试代币)
 *   2. ForumTokenExtension    (论坛激励合约)
 *   3. ContentShareRegistry   (内容存证合约)
 *   4. MockRiscZeroVerifier   (ZK 证明 Mock 验证器，仅本地用)
 *   5. RewardGovernor         (链上奖励参数治理合约)
 *
 * 部署完成后自动写入 deployments/local.json 和 frontend/.env.local
 */

const { ethers } = require("hardhat");
const fs = require("fs");
const path = require("path");

async function main() {
  console.log("=== 本地合约部署 ===");
  const [deployer] = await ethers.getSigners();
  console.log("部署账户:", deployer.address);
  console.log("账户余额:", ethers.formatEther(await ethers.provider.getBalance(deployer.address)), "ETH");

  // 1. 部署 MockWEEToken
  console.log("\n[1/3] 部署 MockWEEToken...");
  const MockWEE = await ethers.getContractFactory("MockWEEToken");
  const mockWEE = await MockWEE.deploy();
  await mockWEE.waitForDeployment();
  const weeAddress = await mockWEE.getAddress();
  console.log("✅ MockWEEToken:", weeAddress);

  // 2. 部署 ForumTokenExtension
  console.log("\n[2/3] 部署 ForumTokenExtension...");
  const ForumToken = await ethers.getContractFactory("ForumTokenExtension");
  const forumToken = await ForumToken.deploy(deployer.address, weeAddress);
  await forumToken.waitForDeployment();
  const forumAddress = await forumToken.getAddress();
  console.log("✅ ForumTokenExtension:", forumAddress);

  // 给合约转入 500,000 WEE 作为奖励池
  console.log("   向合约注入 500,000 WEE 奖励池...");
  const fundAmount = ethers.parseEther("500000");
  await mockWEE.transfer(forumAddress, fundAmount);
  console.log("✅ 已注入 500,000 WEE");

  // 3. 部署 ContentShareRegistry
  console.log("\n[3/5] 部署 ContentShareRegistry...");
  const ContentShare = await ethers.getContractFactory("ContentShareRegistry");
  const contentShare = await ContentShare.deploy(deployer.address);
  await contentShare.waitForDeployment();
  const contentShareAddress = await contentShare.getAddress();
  console.log("✅ ContentShareRegistry:", contentShareAddress);

  // 4. 部署 MockRiscZeroVerifier（ZK Rollup 本地测试用）
  console.log("\n[4/5] 部署 MockRiscZeroVerifier...");
  const MockVerifier = await ethers.getContractFactory("MockRiscZeroVerifier");
  const mockVerifier = await MockVerifier.deploy();
  await mockVerifier.waitForDeployment();
  const mockVerifierAddress = await mockVerifier.getAddress();
  console.log("✅ MockRiscZeroVerifier:", mockVerifierAddress);

  // 5. 部署 RewardGovernor（治理合约，votingPeriod=60s 便于本地测试）
  console.log("\n[5/5] 部署 RewardGovernor...");
  const RewardGovernor = await ethers.getContractFactory("RewardGovernor");
  // votingPeriod=60 秒（本地测试友好），生产环境用默认 2 天
  const rewardGovernor = await RewardGovernor.deploy(deployer.address, forumAddress, 60);
  await rewardGovernor.waitForDeployment();
  const governorAddress = await rewardGovernor.getAddress();
  console.log("✅ RewardGovernor:", governorAddress);

  // 把治理合约设为 ForumTokenExtension 的 governance
  console.log("   设置 ForumTokenExtension.governance = RewardGovernor...");
  await forumToken.setGovernance(governorAddress);
  console.log("✅ 治理合约已绑定");

  // 注册 deployer 为投票人
  await rewardGovernor.addVoter(deployer.address);
  console.log("✅ 部署者已注册为投票人:", deployer.address);

  // 保存部署结果
  const deployment = {
    network: "localhost",
    chainId: 31337,
    deployer: deployer.address,
    timestamp: new Date().toISOString(),
    contracts: {
      weeToken: weeAddress,
      forumTokenExtension: forumAddress,
      contentShareRegistry: contentShareAddress,
      mockRiscZeroVerifier: mockVerifierAddress,
      rewardGovernor: governorAddress,
    }
  };

  const deploymentsDir = path.join(__dirname, "../deployments");
  fs.mkdirSync(deploymentsDir, { recursive: true });
  fs.writeFileSync(
    path.join(deploymentsDir, "local.json"),
    JSON.stringify(deployment, null, 2)
  );
  console.log("\n📄 部署信息已保存到 deployments/local.json");

  // 生成前端 .env.local 文件
  const frontendEnv = `# 本地 Hardhat 节点合约地址（由 deploy-local.js 自动生成）
VUE_APP_ENV=local
VUE_APP_WEE_TOKEN_ADDRESS=${weeAddress}
VUE_APP_FORUM_TOKEN_ADDRESS=${forumAddress}
VUE_APP_CONTENT_SHARE_ADDRESS=${contentShareAddress}
VUE_APP_REWARD_GOVERNOR_ADDRESS=${governorAddress}
`;
  const frontendEnvPath = path.join(__dirname, "../../frontend/.env.local");
  fs.writeFileSync(frontendEnvPath, frontendEnv);
  console.log("📄 前端 .env.local 已写入:", frontendEnvPath);

  // 生成后端环境变量片段
  const backendEnv = `# 本地 Hardhat 节点区块链配置（由 deploy-local.js 自动生成）
POLYGON_RPC_URL=http://127.0.0.1:8545
POLYGON_CHAIN_ID=31337
BLOCKCHAIN_WEE_TOKEN_ADDRESS=${weeAddress}
BLOCKCHAIN_FORUM_TOKEN_EXTENSION_ADDRESS=${forumAddress}
BLOCKCHAIN_CONTENT_SHARE_REGISTRY_ADDRESS=${contentShareAddress}
BLOCKCHAIN_ROLLUP_REGISTRY_ADDRESS=${mockVerifierAddress}
BLOCKCHAIN_REWARD_GOVERNOR_ADDRESS=${governorAddress}
# Hardhat 默认账户 #0 私钥（仅用于本地测试）
BLOCKCHAIN_ADMIN_PRIVATE_KEY=0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80
`;
  const backendEnvPath = path.join(__dirname, "../../.env.backend.local");
  fs.writeFileSync(backendEnvPath, backendEnv);
  console.log("📄 后端环境变量已写入: .env.backend.local");

  console.log("\n=== 部署完成 ===");
  console.log("WEE Token:             ", weeAddress);
  console.log("ForumTokenExtension:   ", forumAddress);
  console.log("ContentShareRegistry:  ", contentShareAddress);
  console.log("\n提示: 启动前端时 VUE_APP_ENV=local 自动使用本地链配置");
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
