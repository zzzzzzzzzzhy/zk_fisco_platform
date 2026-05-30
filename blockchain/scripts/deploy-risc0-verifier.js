const hre = require("hardhat");

async function main() {
  const [deployer] = await hre.ethers.getSigners();

  const controlRoot = "0xa54dc85ac99f851c92d7c96d7318af41dbe7c0194edfcc37eb4d422a998c1f56";
  const bn254ControlId = "0x04446e66d300eb7fb45c9726bb53c793dda407a62e9601618bb43c5c14657ac0";

  console.log("Deploying RiscZeroGroth16Verifier with:", {
    deployer: deployer.address,
    controlRoot,
    bn254ControlId
  });

  const Verifier = await hre.ethers.getContractFactory("RiscZeroGroth16Verifier");
  const verifier = await Verifier.deploy(controlRoot, bn254ControlId);
  await verifier.waitForDeployment();

  console.log("RiscZeroGroth16Verifier deployed to:", await verifier.getAddress());
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
