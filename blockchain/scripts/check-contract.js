const hre = require("hardhat");

async function main() {
  const provider = hre.ethers.provider;
  const registryAddress = "0xdD3975937686728A2412065bABe806F1Ed80b09f";
  
  console.log("Checking contract at:", registryAddress);
  console.log();
  
  // Check if contract exists
  const code = await provider.getCode(registryAddress);
  console.log("Contract bytecode length:", code.length);
  console.log("Has bytecode:", code !== "0x");
  
  if (code === "0x") {
    console.log("\n❌ No contract deployed at this address!");
    return;
  }
  
  // Try to get transaction receipt to see what happened
  const txHash = "0x382a9eab7d1cc500eb603242242138c5b48fc78cecabcf394becf0f8ec143e28";
  const receipt = await provider.getTransactionReceipt(txHash);
  
  console.log("\nTransaction Receipt:");
  console.log("Status:", receipt.status === 1 ? "SUCCESS" : "FAILED");
  console.log("Gas Used:", receipt.gasUsed.toString());
  console.log("To Address:", receipt.to);
  console.log("Contract Address:", receipt.contractAddress);
  console.log("Logs Count:", receipt.logs.length);
  
  if (receipt.logs.length > 0) {
    console.log("\nEvent Logs:");
    receipt.logs.forEach((log, i) => {
      console.log(`\nLog ${i}:`);
      console.log("  Address:", log.address);
      console.log("  Block:", log.blockNumber);
      if (log.topics.length > 0) {
        console.log("  Event Signature:", log.topics[0]);
      }
    });
  }
}

main().catch(console.error);
