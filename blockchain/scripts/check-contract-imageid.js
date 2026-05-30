const hre = require("hardhat");

async function main() {
  const registryAddress = "0x695150dC64243Faea165BC2086DcADC9DB65C79B";
  const Registry = await hre.ethers.getContractAt("ContentRollupRegistry", registryAddress);
  
  const contractImageId = await Registry.imageId();
  const envImageId = "0x06a4c68ab8cc1f68c73a8209c50eb5d7e576a19340e8242c3d2e2dc1921365b9";
  
  console.log("Contract Image ID:");
  console.log(contractImageId);
  console.log();
  console.log("Environment Image ID (used for proof generation):");
  console.log(envImageId);
  console.log();
  console.log("Match:", contractImageId.toLowerCase() === envImageId.toLowerCase() ? "✅ YES" : "❌ NO");
  
  const verifierAddress = await Registry.verifier();
  console.log("\nVerifier Address:", verifierAddress);
}

main().catch(console.error);
