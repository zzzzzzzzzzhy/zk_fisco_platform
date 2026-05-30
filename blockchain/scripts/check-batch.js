const hre = require("hardhat");

async function main() {
  const registryAddress = "0xdD3975937686728A2412065bABe806F1Ed80b09f";
  const Registry = await hre.ethers.getContractAt("ContentRollupRegistry", registryAddress);
  
  const batchId = 32026010523;
  const batch = await Registry.batches(batchId);
  
  console.log("Batch Details for ID:", batchId);
  console.log("Exists:", batch.exists.toString());
  console.log("Merkle Root:", batch.merkleRoot);
  console.log("Count:", batch.count.toString());
  console.log("Window Start:", batch.windowStart.toString());
  console.log("Window End:", batch.windowEnd.toString());
  console.log("Timestamp:", batch.timestamp.toString());
}

main().catch(console.error);
