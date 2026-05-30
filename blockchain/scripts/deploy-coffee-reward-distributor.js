const hre = require("hardhat");

async function main() {
  const [deployer] = await hre.ethers.getSigners();
  const tokenAddress = process.env.COFFEE_REWARD_TOKEN || "0xae51580da0C57714904d0B37E5793bF09f743811";

  if (!tokenAddress || tokenAddress === "0x") {
    throw new Error("Missing COFFEE_REWARD_TOKEN");
  }

  console.log("Deploying CoffeeWeeklyRewardDistributor with:");
  console.log(" - Deployer:", deployer.address);
  console.log(" - Reward token:", tokenAddress);

  const Distributor = await hre.ethers.getContractFactory("CoffeeWeeklyRewardDistributor");
  const distributor = await Distributor.deploy(deployer.address, tokenAddress);
  await distributor.waitForDeployment();

  const address = await distributor.getAddress();
  console.log("CoffeeWeeklyRewardDistributor deployed to:", address);
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
