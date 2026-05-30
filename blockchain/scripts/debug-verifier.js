const hre = require("hardhat");

async function main() {
  const verifierAddress = "0xe2a7013219e85a5df4E81998c985e7bd8875f5F9";
  const registryAddress = "0x695150dC64243Faea165BC2086DcADC9DB65C79B";
  
  // Get contract instances
  const Registry = await hre.ethers.getContractAt("ContentRollupRegistry", registryAddress);
  const contractImageId = await Registry.imageId();
  
  console.log("Registry Contract:", registryAddress);
  console.log("Verifier Contract:", verifierAddress);
  console.log("Contract Image ID:", contractImageId);
  console.log();
  
  // Try calling verify directly to see what error we get
  const fs = require("fs");
  const proofData = fs.readFileSync("/tmp/fresh_proof.bin");
  const proof = "0x" + proofData.toString("hex");
  
  const journalDigest = "0x59327e5dc9d974e2550434888ac54e45c4b2ba74705aedbbc043d87e1e985cf8";
  
  const Verifier = await hre.ethers.getContractAt("contracts/risc0/IRiscZeroVerifier.sol:IRiscZeroVerifier", verifierAddress);
  
  console.log("Testing verifier.verify() directly...");
  console.log("Proof length:", proof.length, "chars (", proof.length/2 - 1, "bytes)");
  console.log("Journal Digest:", journalDigest);
  console.log("Image ID:", contractImageId);
  console.log();
  
  try {
    const result = await Verifier.verify.staticCall(proof, contractImageId, journalDigest);
    console.log("✅ Verify succeeded (should not reach here)");
  } catch (error) {
    console.log("❌ Verify failed as expected:");
    console.log("Error:", error.message);
    
    // Try to get more detailed error info
    if (error.data) {
      console.log("Error data:", error.data);
    }
  }
}

main().catch(console.error);
