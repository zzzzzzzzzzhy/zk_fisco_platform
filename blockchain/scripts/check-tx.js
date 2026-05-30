const hre = require("hardhat");

async function main() {
  const txHash = "0x382a9eab7d1cc500eb603242242138c5b48fc78cecabcf394becf0f8ec143e28";
  const receipt = await hre.ethers.provider.getTransactionReceipt(txHash);
  
  console.log("Transaction Receipt:");
  console.log("Status:", receipt.status === 1 ? "✅ SUCCESS" : "❌ FAILED");
  console.log("Gas Used:", receipt.gasUsed.toString());
  console.log("Block Number:", receipt.blockNumber);
  console.log("Contract Address:", receipt.contractAddress);
  console.log("\nLogs:");
  receipt.logs.forEach((log, i) => {
    console.log(`Log ${i}:`);
    console.log("  Address:", log.address);
    console.log("  Topics:", log.topics);
  });
}

main().catch(console.error);
