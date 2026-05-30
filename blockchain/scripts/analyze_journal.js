const hre = require("hardhat");

function toU256Bytes(value) {
  const buf = Buffer.alloc(32);
  const bigint = BigInt(value);
  const bigEndian = bigint.toString(16).padStart(64, '0');
  const buf2 = Buffer.from(bigEndian, 'hex');
  buf2.copy(buf, 32 - buf2.length);
  return "0x" + buf.toString('hex');
}

async function main() {
  const batchId = 32026010523;
  const merkleRoot = "0x1ebea49cc6473b6e5c56305bbf5179f6c63d6d6c08d68b339aed05d5ff893640";
  const count = 6;
  const windowStart = 1767655008;
  const windowEnd = 1767666008;

  console.log("=== Guest Code Commit 格式（Rust）===\n");
  console.log("Guest commits:");
  console.log("  batch_word:", toU256Bytes(batchId));
  console.log("  merkle_root:", merkleRoot);
  console.log("  count_word:", toU256Bytes(count));
  console.log("  start_word:", toU256Bytes(windowStart));
  console.log("  end_word:", toU256Bytes(windowEnd));
  console.log();
  
  // 手动计算 RISC Zero 应该 commit 的 journal digest
  const batchWord = toU256Bytes(batchId).slice(2);
  const merkleRootBytes = merkleRoot.slice(2);
  const countWord = toU256Bytes(count).slice(2);
  const startWord = toU256Bytes(windowStart).slice(2);
  const endWord = toU256Bytes(windowEnd).slice(2);
  
  // RISC Zero commit: (batch_word, merkle_root, count_word, start_word, end_word)
  // 这是 tuple，按顺序编码
  const journal = batchWord + merkleRootBytes + countWord + startWord + endWord;
  const rustJournalDigest = hre.ethers.sha256("0x" + journal);
  
  console.log("手动计算 RISC Zero journal digest:");
  console.log("  ", rustJournalDigest);
  console.log();
  
  // Solidity 期望的 digest
  const encoded = hre.ethers.AbiCoder.defaultAbiCoder().encode(
    ["uint256", "bytes32", "uint256", "uint256", "uint256"],
    [batchId, merkleRoot, count, windowStart, windowEnd]
  );
  const solidityDigest = hre.ethers.sha256(encoded);
  
  console.log("Solidity 期望的 digest:");
  console.log("  ", solidityDigest);
  console.log();
  
  console.log("两者匹配?", rustJournalDigest === solidityDigest ? "✅ 是" : "❌ 否");
}

main().catch(console.error);
