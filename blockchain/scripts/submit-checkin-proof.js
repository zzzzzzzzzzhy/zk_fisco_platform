const hre = require("hardhat");

async function main() {
  const fs = require("fs");

  const metadataPath = process.env.ROLLUP_METADATA || "/tmp/rollup_metadata.json";
  const proofPath = process.env.ROLLUP_PROOF_FILE || "/tmp/checkin_proof.bin";
  const registryAddress =
    process.env.ROLLUP_REGISTRY_ADDRESS || process.env.ROLLUP_REGISTRY || "";

  if (!registryAddress) {
    throw new Error("Missing ROLLUP_REGISTRY_ADDRESS");
  }

  const metadata = JSON.parse(fs.readFileSync(metadataPath, "utf8"));
  const proofData = fs.readFileSync(proofPath);
  const proof = "0x" + proofData.toString("hex");

  const batchId = Number(metadata.batchId);
  const merkleRoot = metadata.merkleRoot;
  const count = Number(metadata.count);
  const windowStart = Number(metadata.windowStartEpoch);
  const windowEnd = Number(metadata.windowEndEpoch);

  let journalDigest = process.env.ROLLUP_JOURNAL_DIGEST || "";
  if (!journalDigest) {
    const encoded = hre.ethers.AbiCoder.defaultAbiCoder().encode(
      ["uint256", "bytes32", "uint256", "uint256", "uint256"],
      [batchId, merkleRoot, count, windowStart, windowEnd]
    );
    journalDigest = hre.ethers.sha256(encoded);
  }

  console.log("提交批次到 Amoy 测试网...");
  console.log("Registry:", registryAddress);
  console.log("Batch ID:", batchId);
  console.log("Merkle Root:", merkleRoot);
  console.log("Journal Digest:", journalDigest);
  console.log("Proof bytes:", proofData.length);
  console.log();

  const Registry = await hre.ethers.getContractAt("ContentRollupRegistry", registryAddress);
  const tx = await Registry.submitBatch(
    proof,
    journalDigest,
    batchId,
    merkleRoot,
    count,
    windowStart,
    windowEnd,
    { gasLimit: 600000 }
  );

  console.log("交易哈希:", tx.hash);
  console.log("等待确认...");
  const receipt = await tx.wait();
  console.log("✅ 批次提交成功!");
  console.log("Gas Used:", receipt.gasUsed.toString());
  console.log("区块号:", receipt.blockNumber);
  console.log(`https://amoy.polygonscan.com/tx/${tx.hash}`);
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
