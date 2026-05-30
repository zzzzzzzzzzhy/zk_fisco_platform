const hre = require("hardhat");

async function main() {
  const [deployer] = await hre.ethers.getSigners();

  const token = process.env.WEE_TOKEN_ADDRESS;
  const registry = process.env.ROLLUP_REGISTRY_ADDRESS;

  if (!token || !registry) {
    throw new Error("Missing WEE_TOKEN_ADDRESS or ROLLUP_REGISTRY_ADDRESS");
  }

  console.log("Deploying RollupRewardDistributor with:", {
    deployer: deployer.address,
    token,
    registry
  });

  const Distributor = await hre.ethers.getContractFactory("RollupRewardDistributor");
  const distributor = await Distributor.deploy(deployer.address, token, registry);
  await distributor.waitForDeployment();

  console.log("RollupRewardDistributor deployed to:", await distributor.getAddress());
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
