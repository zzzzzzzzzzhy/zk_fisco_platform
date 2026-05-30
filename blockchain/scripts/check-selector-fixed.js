const hre = require("hardhat");

async function main() {
  const fs = require("fs");
  
  // 读取 seal
  const proofData = fs.readFileSync("/tmp/check_image.bin");
  const seal = "0x" + proofData.toString("hex");
  
  // 提取 selector（前 4 字节）
  const sealSelector = seal.slice(0, 10);
  console.log("Seal Selector:", sealSelector);
  
  // 从合约获取期望的 selector
  const verifierAddr = "0x59de2C69DBFCc0155730d7F8F0a2E3ee558dF15A";
  const Verifier = await hre.ethers.getContractAt("RiscZeroGroth16Verifier", verifierAddr);
  
  const contractSelector = await Verifier.SELECTOR();
  // Contract 返回的是 uint256，需要转换为 bytes4
  const contractSelectorBytes4 = "0x" + contractSelector.slice(2).padStart(8, '0').slice(-8);
  console.log("Contract Selector (raw):", contractSelector);
  console.log("Contract Selector (bytes4):", contractSelectorBytes4);
  
  console.log("\n匹配?", sealSelector === contractSelectorBytes4 ? "✅ 是" : "❌ 否");
}

main().catch(console.error);
