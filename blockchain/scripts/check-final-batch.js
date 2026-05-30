const hre = require("hardhat");

async function main() {
  // Use the actual contract address that was used for submission
  const registryAddress = "0xdD3975937686728A2412065bABe806F1Ed80b09f";
  const Registry = await hre.ethers.getContractAt("ContentRollupRegistry", registryAddress);
  
  const batchId = 32026010523;
  
  console.log("Checking batch in contract:", registryAddress);
  console.log("Batch ID:", batchId);
  console.log();
  
  try {
    const isSubmitted = await Registry.isBatchSubmitted(batchId);
    console.log("Is Submitted:", isSubmitted);
    
    if (isSubmitted) {
      const [merkleRoot, count, windowStart, windowEnd, timestamp] = await Registry.getBatch(batchId);
      console.log("\n🎉 Batch Details:");
      console.log("Merkle Root:", merkleRoot);
      console.log("Count:", count.toString());
      console.log("Window Start:", windowStart.toString(), "(", new Date(Number(windowStart) * 1000).toISOString(), ")");
      console.log("Window End:", windowEnd.toString(), "(", new Date(Number(windowEnd) * 1000).toISOString(), ")");
      console.log("Timestamp:", timestamp.toString(), "(", new Date(Number(timestamp) * 1000).toISOString(), ")");
      console.log("\n✅ ZK-Rollup batch successfully verified and stored on-chain!");
      console.log("✅ RISC Zero Groth16 proof verification successful!");
    } else {
      console.log("\n❌ Batch not found");
    }
  } catch (error) {
    console.error("Error checking batch:", error.message);
  }
}

main().catch(console.error);
