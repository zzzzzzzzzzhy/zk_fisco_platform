const hre = require("hardhat");

async function main() {
  const fs = require("fs");
  
  // 新部署的合约地址
  const registryAddress = "0x0042E3f232c92E446971d851194D52CdCf920eEA";
  
  // 读取新生成的证明
  const proofData = fs.readFileSync("/tmp/new_proof.bin");
  const proof = "0x" + proofData.toString("hex");
  
  // 批次参数（与之前相同）
  const batchId = 32026010523;
  const merkleRoot = "0x1ebea49cc6473b6e5c56305bbf5179f6c63d6d6c08d68b339aed05d5ff893640";
  const count = 6;
  const windowStart = 1767655008;
  const windowEnd = 1767666008;
  
  // 计算 journal digest
  const encoded = hre.ethers.AbiCoder.defaultAbiCoder().encode(
    ["uint256", "bytes32", "uint256", "uint256", "uint256"],
    [batchId, merkleRoot, count, windowStart, windowEnd]
  );
  const journalDigest = hre.ethers.sha256(encoded);
  
  console.log("=== 提交批次到新合约 ===\n");
  console.log("Registry:", registryAddress);
  console.log("Batch ID:", batchId);
  console.log("Merkle Root:", merkleRoot);
  console.log("Journal Digest:", journalDigest);
  console.log("证明长度:", proof.length, "chars (", proof.length/2 - 1, "bytes)");
  console.log();
  
  const Registry = await hre.ethers.getContractAt("ContentRollupRegistry", registryAddress);
  
  try {
    console.log("发送交易...");
    const tx = await Registry.submitBatch(
      proof,
      journalDigest,
      batchId,
      merkleRoot,
      count,
      windowStart,
      windowEnd,
      { gasLimit: 500000 }
    );
    
    console.log("交易哈希:", tx.hash);
    console.log("等待确认...");
    
    const receipt = await tx.wait();
    
    console.log();
    console.log("🎉 批次提交成功!");
    console.log("Gas Used:", receipt.gasUsed.toString());
    console.log("区块号:", receipt.blockNumber);
    console.log();
    console.log("查看交易:");
    console.log(`https://amoy.polygonscan.com/tx/${tx.hash}`);
  } catch (error) {
    console.error("\n❌ 交易失败:");
    console.error(error.message);
    if (error.receipt) {
      console.error("Gas Used:", error.receipt.gasUsed.toString());
    }
  }
}

main().catch(console.error);
