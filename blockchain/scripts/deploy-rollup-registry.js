const hre = require("hardhat");

async function main() {
  const [deployer] = await hre.ethers.getSigners();

  const verifier = process.env.ROLLUP_VERIFIER_ADDRESS;
  const imageId = process.env.ROLLUP_IMAGE_ID;

  if (!verifier || !imageId) {
    throw new Error("Missing ROLLUP_VERIFIER_ADDRESS or ROLLUP_IMAGE_ID");
  }

  console.log("Deploying ContentRollupRegistry with:", {
    deployer: deployer.address,
    verifier,
    imageId
  });

  const Registry = await hre.ethers.getContractFactory("ContentRollupRegistry");
  const registry = await Registry.deploy(deployer.address, verifier, imageId);
  const deployTx = registry.deploymentTransaction();
  if (deployTx) {
    console.log("Deploy tx hash:", deployTx.hash);
  }
  await registry.waitForDeployment();

  console.log("ContentRollupRegistry deployed to:", await registry.getAddress());
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
