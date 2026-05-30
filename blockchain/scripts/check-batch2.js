const hre = require("hardhat");

async function main() {
  const registryAddress = "0xdD3975937686728A2412065bABe806F1Ed80b09f";
  const Registry = await hre.ethers.getContractAt("ContentRollupRegistry", registryAddress);
  
  const batchId = 32026010523;
  
  const isSubmitted = await Registry.isBatchSubmitted(batchId);
  console.log("Batch ID:", batchId);
  console.log("Is Submitted:", isSubmitted);
  
  if (isSubmitted) {
    const [merkleRoot, count, windowStart, windowEnd, timestamp] = await Registry.getBatch(batchId);
    console.log("\nBatch Details:");
    console.log("Merkle Root:", merkleRoot);
    console.log("Count:", count.toString());
    console.log("Window Start:", windowStart.toString());
    console.log("Window End:", windowEnd.toString());
    console.log("Timestamp:", timestamp.toString());
    console.log("\n✅ ZK-Rollup batch successfully stored on-chain!");
  } else {
    console.log("\n❌ Batch not found - transaction may have reverted");
  }
}

main().catch(console.error);
