const hre = require("hardhat");

async function main() {
  const batchId = 32026010523;
  const merkleRoot = "0x1ebea49cc6473b6e5c56305bbf5179f6c63d6d6c08d68b339aed05d5ff893640";
  const count = 6;
  const windowStart = 1767655008;
  const windowEnd = 1767666008;

  // 方法1: solidityPackedSha256 (旧方法 - 错误)
  const digest1 = hre.ethers.solidityPackedSha256(
    ["uint256", "bytes32", "uint256", "uint256", "uint256"],
    [batchId, merkleRoot, count, windowStart, windowEnd]
  );
  console.log("方法1 (solidityPackedSha256 - 错误):");
  console.log(digest1);
  console.log();

  // 方法2: abi.encode + sha256 (正确方法)
  const encoded = hre.ethers.AbiCoder.defaultAbiCoder().encode(
    ["uint256", "bytes32", "uint256", "uint256", "uint256"],
    [batchId, merkleRoot, count, windowStart, windowEnd]
  );
  const digest2 = hre.ethers.sha256(encoded);
  console.log("方法2 (abi.encode + sha256 - 正确):");
  console.log(digest2);
  console.log();

  console.log("合约使用: sha256(abi.encode(batchId, merkleRoot, count, windowStart, windowEnd))");
  console.log("RISC Zero guest commits: (batch_word, merkle_root, count_word, start_word, end_word)");
  console.log("两者应该匹配！");
}

main().catch(console.error);
