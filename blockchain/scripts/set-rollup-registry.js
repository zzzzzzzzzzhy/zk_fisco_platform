const hre = require("hardhat");

async function main() {
  const distributorAddress = process.env.ROLLUP_REWARD_DISTRIBUTOR_ADDRESS;
  const registryAddress = process.env.ROLLUP_REGISTRY_ADDRESS;

  if (!distributorAddress || !registryAddress) {
    throw new Error("Missing ROLLUP_REWARD_DISTRIBUTOR_ADDRESS or ROLLUP_REGISTRY_ADDRESS");
  }

  const distributor = await hre.ethers.getContractAt(
    "RollupRewardDistributor",
    distributorAddress
  );

  console.log("Updating rollup registry:", {
    distributor: distributorAddress,
    registry: registryAddress
  });

  const tx = await distributor.setRegistry(registryAddress);
  const receipt = await tx.wait();

  console.log("Registry updated, txHash:", receipt.hash);
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
