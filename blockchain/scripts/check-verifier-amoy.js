const hre = require("hardhat");

async function main() {
  const fs = require("fs");
  const proofPath = process.env.ROLLUP_PROOF_FILE || "/tmp/checkin_proof.bin";
  const proofData = fs.readFileSync(proofPath);
  const proof = "0x" + proofData.toString("hex");

  const verifierAddress = process.env.ROLLUP_VERIFIER_ADDRESS || "";
  if (!verifierAddress) {
    throw new Error("Missing ROLLUP_VERIFIER_ADDRESS");
  }

  const imageId = process.env.ROLLUP_IMAGE_ID || "";
  const journalDigest = process.env.ROLLUP_JOURNAL_DIGEST || "";
  if (!imageId || !journalDigest) {
    throw new Error("Missing ROLLUP_IMAGE_ID or ROLLUP_JOURNAL_DIGEST");
  }

  const Verifier = await hre.ethers.getContractAt(
    "contracts/risc0/IRiscZeroVerifier.sol:IRiscZeroVerifier",
    verifierAddress
  );

  try {
    await Verifier.verify.staticCall(proof, imageId, journalDigest);
    console.log("✅ Verifier.verify 成功");
  } catch (error) {
    console.log("❌ Verifier.verify 失败");
    console.log("Message:", error.message);
    if (error.errorName) {
      console.log("Error Name:", error.errorName);
    }
    if (error.errorArgs) {
      console.log("Error Args:", error.errorArgs);
    }
    if (error.data) {
      console.log("Error Data:", error.data);
      console.log("Selector:", error.data.slice(0, 10));
    }
  }
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
