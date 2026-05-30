const hre = require("hardhat");

async function main() {
  const [deployer] = await hre.ethers.getSigners();
  const fs = require("fs");

  // 读取证明文件
  const proofData = fs.readFileSync("/tmp/real_checkin_proof.bin");
  const proof = "0x" + proofData.toString("hex");

  // 批次参数（真实值）
  const batchId = 32026010523;
  const merkleRoot = "0x1ebea49cc6473b6e5c56305bbf5179f6c63d6d6c08d68b339aed05d5ff893640";
  const count = 6;
  const windowStart = 1767655008;
  const windowEnd = 1767666008;

  // 计算 journal digest
  const journalDigest = hre.ethers.solidityPackedSha256(
    ["uint256", "bytes32", "uint256", "uint256", "uint256"],
    [batchId, merkleRoot, count, windowStart, windowEnd]
  );

  console.log("提交批次到 Amoy 测试网...");
  console.log("Batch ID:", batchId);
  console.log("Merkle Root:", merkleRoot);
  console.log("Journal Digest:", journalDigest);
  console.log("证明长度:", proof.length, "bytes");
  console.log();

  const registryAddress = process.env.ROLLUP_REGISTRY_ADDRESS || "0x695150dC64243Faea165BC2086DcADC9DB65C79B";
  const Registry = await hre.ethers.getContractAt("ContentRollupRegistry", registryAddress);

  console.log("合约地址:", registryAddress);
  console.log("发送交易...");

  try {
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
    console.log("✅ 批次提交成功!");
    console.log("Gas Used:", receipt.gasUsed.toString());
    console.log("区块号:", receipt.blockNumber);
    console.log();
    console.log("查看交易:");
    console.log(`https://amoy.polygonscan.com/tx/${tx.hash}`);
  } catch (error) {
    console.error("❌ 交易失败:");
    console.error(error.message);
    if (error.receipt) {
      console.error("Gas Used:", error.receipt.gasUsed.toString());
    }
  }
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
