const hre = require("hardhat");

async function main() {
  const distributorAddress = process.env.COFFEE_REWARD_DISTRIBUTOR;
  const backendUrl = process.env.COFFEE_BACKEND_URL || "http://localhost:9090";
  const epoch = process.env.COFFEE_REWARD_EPOCH;

  if (!distributorAddress) {
    throw new Error("Missing COFFEE_REWARD_DISTRIBUTOR");
  }

  const payload = epoch ? { epoch: Number(epoch) } : {};
  const response = await fetch(`${backendUrl}/v1/reward-snapshot`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });

  if (!response.ok) {
    const text = await response.text();
    throw new Error(`Snapshot request failed: ${response.status} ${text}`);
  }

  const snapshot = await response.json();
  console.log("Snapshot:", snapshot);

  const distributor = await hre.ethers.getContractAt(
    "CoffeeWeeklyRewardDistributor",
    distributorAddress
  );
  const tx = await distributor.setMerkleRoot(snapshot.epoch, snapshot.merkle_root);
  console.log("Submitting root:", tx.hash);
  await tx.wait();
  console.log("Merkle root set for epoch", snapshot.epoch);
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
