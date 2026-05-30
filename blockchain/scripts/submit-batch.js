const hre = require("hardhat");

async function main() {
  const [deployer] = await hre.ethers.getSigners();

  // 读取证明文件（纯二进制）
  const fs = require("fs");
  const proofData = fs.readFileSync("/tmp/test_proof.bin");
  // 转换为hex字符串，添加0x前缀
  const proof = "0x" + proofData.toString("hex");

  // 批次参数
  const batchId = 32026010523;
  const merkleRoot = "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef";
  const count = 6;
  const windowStart = 1767654960;
  const windowEnd = 1767669360;

  // 计算 journal digest
  const journalDigest = hre.ethers.solidityPackedSha256(
    ["uint256", "bytes32", "uint256", "uint256", "uint256"],
    [batchId, merkleRoot, count, windowStart, windowEnd]
  );

  console.log("提交批次到 Amoy 测试网...");
  console.log("部署者地址:", deployer.address);
  console.log("Batch ID:", batchId);
  console.log("Merkle Root:", merkleRoot);
  console.log("Journal Digest:", journalDigest);
  console.log();

  // 获取合约地址
  const registryAddress = process.env.ROLLUP_REGISTRY_ADDRESS || "0x695150dC64243Faea165BC2086DcADC9DB65C79B";
  const Registry = await hre.ethers.getContractAt("ContentRollupRegistry", registryAddress);

  console.log("合约地址:", registryAddress);
  console.log();

  // 提交批次
  console.log("发送交易...");
  const tx = await Registry.submitBatch(
    proof, // seal (已经包含0x前缀)
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
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
