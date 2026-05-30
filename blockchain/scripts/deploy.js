const { ethers } = require("hardhat");
require("dotenv").config();

async function main() {
  console.log("开始部署 ForumTokenExtension 合约...");
  console.log("网络:", hre.network.name);

  try {
    // 获取签名者
    const signers = await ethers.getSigners();
    if (signers.length === 0) {
      throw new Error("没有找到可用的签名者账户");
    }

    const deployer = signers[0];
    console.log("部署账户:", deployer.address);
    const weeTokenAddress = process.env.WEE_TOKEN_ADDRESS || process.env.MTK_TOKEN_ADDRESS || "0x3b90669eB9960d1e65D3A09097a9363Df74783DD";
    console.log("WEE Token 地址:", weeTokenAddress);

    // 计算 EIP-1559 费用（使用链上建议为基准，允许通过环境变量覆盖）
    const feeData = await ethers.provider.getFeeData();
    const envMaxFee = process.env.MAX_FEE_PER_GAS_GWEI ? ethers.parseUnits(process.env.MAX_FEE_PER_GAS_GWEI, "gwei") : null;
    const envPriorityFee = process.env.MAX_PRIORITY_FEE_GWEI ? ethers.parseUnits(process.env.MAX_PRIORITY_FEE_GWEI, "gwei") : null;

    const suggestedMaxFee = feeData.maxFeePerGas || ethers.parseUnits("80", "gwei");
    const suggestedPriority = feeData.maxPriorityFeePerGas || ethers.parseUnits("35", "gwei");

    const maxFeePerGas = envMaxFee || suggestedMaxFee;
    const maxPriorityFeePerGas = envPriorityFee || suggestedPriority;
    console.log("Gas 费用配置(EIP-1559): maxFeePerGas =", maxFeePerGas.toString(), "maxPriorityFeePerGas =", maxPriorityFeePerGas.toString());

    // 获取合约工厂
    const ForumTokenExtension = await ethers.getContractFactory("ForumTokenExtension");

    // 部署合约
    console.log("正在部署 ForumTokenExtension 合约...");
    const forumTokenExtension = await ForumTokenExtension.deploy(
      deployer.address,
      weeTokenAddress,
      {
        maxFeePerGas,
        maxPriorityFeePerGas
      }
    );
    console.log("部署交易提交，txHash:", forumTokenExtension.deploymentTransaction().hash);

    await forumTokenExtension.waitForDeployment();

    const contractAddress = await forumTokenExtension.getAddress();
    console.log("✅ ForumTokenExtension 合约部署成功!");
    console.log("合约地址:", contractAddress);
    console.log("部署交易哈希:", forumTokenExtension.deploymentTransaction().hash);
    const deployTx = forumTokenExtension.deploymentTransaction();
    console.log("区块号:", deployTx?.blockNumber || "N/A");
    console.log("Gas费用:", deployTx?.gasUsed?.toString() || "N/A");

    // 验证部署是否成功
    const owner = await forumTokenExtension.owner();
    console.log("合约所有者:", owner);

    // 检查代币地址
    const mtkTokenAddress = await forumTokenExtension.MTK_TOKEN();
    console.log("代币地址:", mtkTokenAddress);

    // 获取初始奖励配置
    const rewardConfig = await forumTokenExtension.rewardConfig();
    console.log("初始奖励配置:");
    console.log("- 发布帖子奖励:", ethers.formatEther(rewardConfig.postReward), "WEE");
    console.log("- 评论奖励:", ethers.formatEther(rewardConfig.commentReward), "WEE");
    console.log("- 每日签到奖励:", ethers.formatEther(rewardConfig.dailyCheckinReward), "WEE");
    console.log("- 精华帖子奖励:", ethers.formatEther(rewardConfig.featuredPostReward), "WEE");
    console.log("- 连续签到奖励:", ethers.formatEther(rewardConfig.consecutiveBonus), "WEE");

    // 保存部署信息
    const deploymentInfo = {
      network: hre.network.name,
      contractAddress: contractAddress,
      mtkTokenAddress: mtkTokenAddress,
      deployer: owner,
      deployTransaction: forumTokenExtension.deploymentTransaction().hash,
      blockNumber: forumTokenExtension.deploymentTransaction().blockNumber,
      gasUsed: forumTokenExtension.deploymentTransaction().gasUsed?.toString() || "0",
      timestamp: new Date().toISOString(),
      rewardConfig: {
        postReward: ethers.formatEther(rewardConfig.postReward),
        commentReward: ethers.formatEther(rewardConfig.commentReward),
        dailyCheckinReward: ethers.formatEther(rewardConfig.dailyCheckinReward),
        featuredPostReward: ethers.formatEther(rewardConfig.featuredPostReward),
        consecutiveBonus: ethers.formatEther(rewardConfig.consecutiveBonus)
      }
    };

    // 保存到文件
    const fs = require("fs");
    const path = require("path");
    const deploymentDir = path.join(__dirname, "../deployments");

    if (!fs.existsSync(deploymentDir)) {
      fs.mkdirSync(deploymentDir, { recursive: true });
    }

    const deploymentFile = path.join(deploymentDir, `${hre.network.name}.json`);
    fs.writeFileSync(deploymentFile, JSON.stringify(deploymentInfo, null, 2));
    console.log("✅ 部署信息已保存到:", deploymentFile);

    // 更新环境配置文件
    await updateBackendConfig(contractAddress, weeTokenAddress);

    console.log("\n🎉 合约部署完成!");
    console.log("📋 重要信息:");
    console.log("合约地址:", contractAddress);
    console.log("WEE代币地址:", mtkTokenAddress);
    console.log("部署网络:", hre.network.name);
    console.log("下一步: 1. 验证合约 2. 更新应用配置 3. 启动服务");

    return contractAddress;

  } catch (error) {
    console.error("❌ 合约部署失败:", error);
    process.exit(1);
  }
}

/**
 * 更新后端配置文件
 */
async function updateBackendConfig(contractAddress, weeTokenAddress) {
  try {
    const fs = require("fs");
    const path = require("path");

    // 读取应用配置
    const configPath = path.join(__dirname, "../../backend/src/main/resources/application.yml");
    let configContent = fs.readFileSync(configPath, 'utf8');

    // 更新区块链配置
    const blockchainConfig = `
# FISCO BCOS configuration (保留原有配置)
fisco:
  config-file: config.toml
  group-id: 1
  contract-address: a73046bac2ec6b98dad00ebca326ae173e9b60ec
  current-account: 0x752d5728bc74270032dcc8d5b4b5748b6d4b0dff

# Polygon WEE Token configuration (新增)
blockchain:
  polygon:
    rpc-url: https://rpc-amoy.polygon.technology/
    chain-id: 80002
  mtk:
    token-address: ${weeTokenAddress}
  forum-token:
    extension-address: ${contractAddress}
  admin:
    private-key: \${BLOCKCHAIN_ADMIN_PRIVATE_KEY:}`;

    // 查找并替换区块链配置部分
    const fiscoConfigRegex = /# FISCO BCOS configuration[\\s\\S]*?current-account: 0x752d5728bc74270032dcc8d5b4b5748b6d4b0dff/g;

    if (fiscoConfigRegex.test(configContent)) {
      configContent = configContent.replace(fiscoConfigRegex, blockchainConfig.trim());
    } else {
      // 如果没有找到，则追加到文件末尾
      configContent += "\n" + blockchainConfig;
    }

    // 写回配置文件
    fs.writeFileSync(configPath, configContent);
    console.log("✅ 后端配置已更新");

    // 创建环境变量文件
    const envPath = path.join(__dirname, "../../.env");
    if (!fs.existsSync(envPath)) {
    const envContent = `# 区块链配置
BLOCKCHAIN_ADMIN_PRIVATE_KEY=${process.env.PRIVATE_KEY || ""}

# Polygon RPC配置
POLYGON_RPC_URL=https://rpc-amoy.polygon.technology/
POLYGONSCAN_API_KEY=${process.env.POLYGONSCAN_API_KEY || ""}

# WEE代币与论坛扩展配置
WEE_TOKEN_ADDRESS=${weeTokenAddress}
FORUM_TOKEN_EXTENSION_ADDRESS=${contractAddress}
`;
      fs.writeFileSync(envPath, envContent);
      console.log("✅ 环境变量文件已创建");
    }

  } catch (error) {
    console.error("更新配置文件失败:", error.message);
    console.log("请手动更新配置文件");
  }
}

/**
 * 验证合约
 */
async function verifyContract(contractAddress) {
  if (hre.network.name === "hardhat" || hre.network.name === "localhost") {
    console.log("本地网络，跳过合约验证");
    return;
  }

  try {
    console.log("开始验证合约...");
    await hre.run("verify:verify", {
      address: contractAddress,
      constructorArguments: []
    });
    console.log("✅ 合约验证成功");
  } catch (error) {
    console.error("❌ 合约验证失败:", error.message);
  }
}

// 如果直接运行此脚本
if (require.main === module) {
  main()
    .then(() => process.exit(0))
    .catch((error) => {
      console.error(error);
      process.exit(1);
    });
}

module.exports = main;
