const hre = require("hardhat");

async function main() {
  const fs = require("fs");
  const verifierAddr = "0x59de2C69DBFCc0155730d7F8F0a2E3ee558dF15A";
  const registryAddr = "0x0042E3f232c92E446971d851194D52CdCf920eEA";
  
  // 读取新证明
  const proofData = fs.readFileSync("/tmp/bincode_proof.bin");
  const proof = "0x" + proofData.toString("hex");
  
  const batchId = 32026010523;
  const merkleRoot = "0x1ebea49cc6473b6e5c56305bbf5179f6c63d6d6c08d68b339aed05d5ff893640";
  const count = 6;
  const windowStart = 1767655008;
  const windowEnd = 1767666008;
  
  const encoded = hre.ethers.AbiCoder.defaultAbiCoder().encode(
    ["uint256", "bytes32", "uint256", "uint256", "uint256"],
    [batchId, merkleRoot, count, windowStart, windowEnd]
  );
  const journalDigest = hre.ethers.sha256(encoded);
  
  console.log("=== 测试 bincode 编码的证明 ===\n");
  console.log("Proof 长度:", proof.length, "chars (", proof.length/2 - 1, "bytes)");
  console.log("Journal Digest:", journalDigest);
  console.log();
  
  // 1. 先直接测试 verifier
  console.log("1. 测试 Verifier.verify()...");
  const Verifier = await hre.ethers.getContractAt("contracts/risc0/IRiscZeroVerifier.sol:IRiscZeroVerifier", verifierAddr);
  const imageId = "0x06a4c68ab8cc1f68c73a8209c50eb5d7e576a19340e8242c3d2e2dc1921365b9";
  
  try {
    await Verifier.verify.staticCall(proof, imageId, journalDigest);
    console.log("   ✅ Verifier.verify() 成功!");
  } catch (error) {
    console.log("   ❌ Verifier.verify() 失败:", error.message);
    return;
  }
  
  // 2. 测试完整提交
  console.log("\n2. 测试 submitBatch()...");
  const Registry = await hre.ethers.getContractAt("ContentRollupRegistry", registryAddr);
  
  try {
    const tx = await Registry.submitBatch(
      proof, journalDigest, batchId, merkleRoot, count, windowStart, windowEnd,
      { gasLimit: 500000 }
    );
    
    console.log("   交易哈希:", tx.hash);
    const receipt = await tx.wait();
    
    console.log();
    console.log("🎉 批次提交成功!");
    console.log("Gas Used:", receipt.gasUsed.toString());
    console.log("区块号:", receipt.blockNumber);
    console.log("\n查看交易:");
    console.log(`https://amoy.polygonscan.com/tx/${tx.hash}`);
  } catch (error) {
    console.log("   ❌ submitBatch() 失败:", error.message);
    if (error.receipt) {
      console.log("   Gas Used:", error.receipt.gasUsed.toString());
    }
  }
}

main().catch(console.error);
