const hre = require("hardhat");

async function main() {
  const fs = require("fs");
  const proofPath = process.env.ROLLUP_PROOF_FILE || "/tmp/checkin_proof.bin";
  const proofData = fs.readFileSync(proofPath);
  const seal = "0x" + proofData.toString("hex");
  const sealSelector = seal.slice(0, 10);

  const verifierAddress = process.env.ROLLUP_VERIFIER_ADDRESS || "";
  if (!verifierAddress) {
    throw new Error("Missing ROLLUP_VERIFIER_ADDRESS");
  }

  const Verifier = await hre.ethers.getContractAt("RiscZeroGroth16Verifier", verifierAddress);
  const contractSelector = await Verifier.SELECTOR();
  const contractSelectorBytes4 =
    "0x" + contractSelector.slice(2).padStart(8, "0").slice(-8);

  console.log("Seal Selector:", sealSelector);
  console.log("Contract Selector:", contractSelectorBytes4);
  console.log("Match?", sealSelector === contractSelectorBytes4 ? "✅" : "❌");
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
