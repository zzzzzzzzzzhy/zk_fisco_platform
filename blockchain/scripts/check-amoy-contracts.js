const hre = require("hardhat");

async function main() {
  // 测试网配置
  const amoyVerifier = "0xe2a7013219e85a5df4E81998c985e7bd8875f5F9";
  const amoyRegistry = "0x695150dC64243Faea165BC2086DcADC9DB65C79B";
  const amoyDistributor = "0x515256F9C04B7Da75B97480389b6375935DBda20";
  
  const provider = hre.ethers.provider;
  
  console.log("=== Amoy 测试网合约检查 ===\n");
  
  // 检查 Verifier
  const verifierCode = await provider.getCode(amoyVerifier);
  console.log("Verifier:", amoyVerifier);
  console.log("  合约存在:", verifierCode.length > 2);
  console.log("  Bytecode长度:", verifierCode.length);
  
  // 检查 Registry
  const registryCode = await provider.getCode(amoyRegistry);
  console.log("\nRegistry:", amoyRegistry);
  console.log("  合约存在:", registryCode.length > 2);
  console.log("  Bytecode长度:", registryCode.length);
  
  if (registryCode.length > 2) {
    const Registry = await hre.ethers.getContractAt("ContentRollupRegistry", amoyRegistry);
    try {
      const contractVerifier = await Registry.verifier();
      const contractImageId = await Registry.imageId();
      console.log("  Verifier地址:", contractVerifier);
      console.log("  Image ID:", contractImageId);
      console.log("  Image ID匹配?", contractImageId === "0x06a4c68ab8cc1f68c73a8209c50eb5d7e576a19340e8242c3d2e2dc1921365b9");
    } catch (e) {
      console.log("  无法读取合约信息:", e.message);
    }
  }
  
  // 检查 Distributor
  const distCode = await provider.getCode(amoyDistributor);
  console.log("\nDistributor:", amoyDistributor);
  console.log("  合约存在:", distCode.length > 2);
  console.log("  Bytecode长度:", distCode.length);
}

main().catch(console.error);
