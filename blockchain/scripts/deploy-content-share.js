const hre = require("hardhat");
const fs = require("fs");
const path = require("path");

async function main() {
  const [deployer] = await hre.ethers.getSigners();
  console.log("部署账户:", deployer.address);

  const balance = await hre.ethers.provider.getBalance(deployer.address);
  console.log("账户余额:", hre.ethers.formatEther(balance), "MATIC");

  // 读取链上建议 gas（EIP-1559），允许通过环境变量覆盖
  const feeData = await hre.ethers.provider.getFeeData();
  const maxFeePerGas =
    process.env.MAX_FEE_PER_GAS_GWEI
      ? hre.ethers.parseUnits(process.env.MAX_FEE_PER_GAS_GWEI, "gwei")
      : feeData.maxFeePerGas;
  const maxPriorityFeePerGas =
    process.env.MAX_PRIORITY_FEE_GWEI
      ? hre.ethers.parseUnits(process.env.MAX_PRIORITY_FEE_GWEI, "gwei")
      : feeData.maxPriorityFeePerGas;

  console.log("Gas 配置:", {
    maxFeePerGas: maxFeePerGas?.toString(),
    maxPriorityFeePerGas: maxPriorityFeePerGas?.toString(),
  });

  const ContentShareRegistry = await hre.ethers.getContractFactory("ContentShareRegistry");
  console.log("开始部署 ContentShareRegistry...");
  const contract = await ContentShareRegistry.deploy(deployer.address, {
    maxFeePerGas,
    maxPriorityFeePerGas,
  });
  console.log("部署交易哈希:", contract.deploymentTransaction().hash);

  await contract.waitForDeployment();
  const address = await contract.getAddress();
  console.log("✅ 部署完成，合约地址:", address);

  // 保存部署信息
  const deploymentInfo = {
    network: hre.network.name,
    contract: "ContentShareRegistry",
    address,
    deployer: deployer.address,
    txHash: contract.deploymentTransaction().hash,
    timestamp: new Date().toISOString(),
    gas: {
      maxFeePerGas: maxFeePerGas?.toString(),
      maxPriorityFeePerGas: maxPriorityFeePerGas?.toString(),
    },
  };

  const deploymentsDir = path.join(__dirname, "../deployments");
  if (!fs.existsSync(deploymentsDir)) {
    fs.mkdirSync(deploymentsDir, { recursive: true });
  }
  const file = path.join(deploymentsDir, `${hre.network.name}-content-share-${Date.now()}.json`);
  fs.writeFileSync(file, JSON.stringify(deploymentInfo, null, 2));
  console.log("部署信息已保存:", file);
}

main().catch((error) => {
  console.error("部署失败:", error);
  process.exit(1);
});
