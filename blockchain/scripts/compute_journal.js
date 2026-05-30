const hre = require("hardhat");

function toU256Bytes(value) {
  // Convert to 32-byte big-endian array (matching Rust's to_u256_bytes)
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

  console.log("Rust guest commitment (matching to_u256_bytes):");
  console.log("batch_word:", toU256Bytes(batchId));
  console.log("merkle_root:", merkleRoot);
  console.log("count_word:", toU256Bytes(count));
  console.log("start_word:", toU256Bytes(windowStart));
  console.log("end_word:", toU256Bytes(windowEnd));
  console.log();

  // Compute journal digest as SHA256 of the tuple
  // RISC Zero commits: (batch_word, merkle_root, count_word, start_word, end_word)
  // This is encoded as 5 consecutive 32-byte values
  
  const batchWord = toU256Bytes(batchId).slice(2);
  const merkleRootBytes = merkleRoot.slice(2);
  const countWord = toU256Bytes(count).slice(2);
  const startWord = toU256Bytes(windowStart).slice(2);
  const endWord = toU256Bytes(windowEnd).slice(2);
  
  const journal = batchWord + merkleRootBytes + countWord + startWord + endWord;
  const journalDigest = hre.ethers.sha256("0x" + journal);
  
  console.log("Journal digest (computed from tuple):");
  console.log(journalDigest);
  console.log();

  // Also compute using Solidity's abi.encode
  const encoded = hre.ethers.AbiCoder.defaultAbiCoder().encode(
    ["uint256", "bytes32", "uint256", "uint256", "uint256"],
    [batchId, merkleRoot, count, windowStart, windowEnd]
  );
  const solidityDigest = hre.ethers.sha256(encoded);
  
  console.log("Solidity digest (sha256(abi.encode(...))):");
  console.log(solidityDigest);
  console.log();

  console.log("Match:", journalDigest === solidityDigest ? "✅ YES" : "❌ NO");
}

main().catch(console.error);
