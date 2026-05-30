const hre = require("hardhat");

async function main() {
  const targetSelector = "0x439cc0cd";
  
  // Try common error signatures
  const signatures = [
    "InvalidProof()",
    "InvalidImageId()",
    "InvalidJournalDigest()", 
    "ProofVerificationFailed()",
    "VerifyFailed()",
    "InvalidSeal()",
    "InvalidInput()",
    "DoesNotExist()"
  ];
  
  console.log("Looking for error with selector:", targetSelector);
  console.log();
  
  for (const sig of signatures) {
    const selector = hre.ethers.id(sig).slice(0, 10);
    console.log(`${sig}: ${selector}`);
    if (selector === targetSelector) {
      console.log("  ✅ MATCH!");
    }
  }
  
  // Also check the RISC Zero control ID error
  console.log("\nRISC Zero specific errors:");
  const controlIdSig = "ControlIdInvalid()";
  const selector = hre.ethers.id(controlIdSig).slice(0, 10);
  console.log(`${controlIdSig}: ${selector}`);
}

main().catch(console.error);
