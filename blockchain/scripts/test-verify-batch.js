const hre = require("hardhat");

async function main() {
  const registryAddress = "0x695150dC64243Faea165BC2086DcADC9DB65C79B";
  const Registry = await hre.ethers.getContractAt("ContentRollupRegistry", registryAddress);
  
  const fs = require("fs");
  const proofData = fs.readFileSync("/tmp/fresh_proof.bin");
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
  
  console.log("Testing submitBatch call...");
  console.log("Batch ID:", batchId);
  console.log("Merkle Root:", merkleRoot);
  console.log("Journal Digest:", journalDigest);
  console.log();
  
  try {
    // Try to call submitBatch as a static call to see revert reason
    const tx = await Registry.submitBatch.staticCall(
      proof,
      journalDigest,
      batchId,
      merkleRoot,
      count,
      windowStart,
      windowEnd,
      { gasLimit: 500000 }
    );
    console.log("✅ Success (should not reach here)");
  } catch (error) {
    console.log("❌ Reverted with error:");
    console.log("Error name:", error.name);
    console.log("Error message:", error.message);
    
    if (error.errorName) {
      console.log("Contract error name:", error.errorName);
      console.log("Error args:", error.errorArgs);
    }
    
    if (error.data) {
      console.log("Error data:", error.data);
    }
  }
}

main().catch(console.error);
