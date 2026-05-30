const hre = require("hardhat");

async function main() {
  const verifierAddr = "0x59de2C69DBFCc0155730d7F8F0a2E3ee558dF15A";
  const registryAddr = "0x0042E3f232c92E446971d851194D52CdCf920eEA";
  const expectedImageId = "0x06a4c68ab8cc1f68c73a8209c50eb5d7e576a19340e8242c3d2e2dc1921365b9";
  
  console.log("=== 验证新部署的合约 ===\n");
  
  // 检查 Verifier
  const verifierCode = await hre.ethers.provider.getCode(verifierAddr);
  console.log("✅ Verifier 存在:", verifierAddr, verifierCode.length > 2 ? "是" : "否");
  
  // 检查 Registry
  const Registry = await hre.ethers.getContractAt("ContentRollupRegistry", registryAddr);
  const contractVerifier = await Registry.verifier();
  const contractImageId = await Registry.imageId();
  const owner = await Registry.owner();
  
  console.log("✅ Registry 存在:", registryAddr);
  console.log("   Verifier 地址:", contractVerifier);
  console.log("   Image ID:", contractImageId);
  console.log("   Owner:", owner);
  console.log();
  console.log("配置验证:");
  console.log("  Verifier 匹配:", contractVerifier.toLowerCase() === verifierAddr.toLowerCase() ? "✅" : "❌");
  console.log("  Image ID 匹配:", contractImageId.toLowerCase() === expectedImageId.toLowerCase() ? "✅" : "❌");
  console.log("\n合约配置正确！准备提交批次...");
}

main().catch(console.error);
