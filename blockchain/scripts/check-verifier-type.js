const hre = require("hardhat");

async function main() {
  const verifierAddress = "0xe2a7013219e85a5df4E81998c985e7bd8875f5F9";
  const provider = hre.ethers.provider;
  
  console.log("Checking contract at:", verifierAddress);
  
  // Get the bytecode
  const code = await provider.getCode(verifierAddress);
  console.log("Bytecode length:", code.length, "bytes");
  
  // Try to get the contract's function selectors
  // By checking what functions are available
  
  // Common verifier function signatures
  const functions = {
    "verify(bytes,bytes32,bytes32)": hre.ethers.id("verify(bytes,bytes32,bytes32)").slice(0, 10),
    "verifyProof(uint256[2],uint256[2][2],uint256[2],uint256[5])": hre.ethers.id("verifyProof(uint256[2],uint256[2][2],uint256[2],uint256[5])").slice(0, 10),
  };
  
  console.log("\nLooking for verify functions...");
  for (const [name, selector] of Object.entries(functions)) {
    if (code.toLowerCase().includes(selector.slice(2))) {
      console.log(`✅ Found: ${name} -> ${selector}`);
    }
  }
}

main().catch(console.error);
