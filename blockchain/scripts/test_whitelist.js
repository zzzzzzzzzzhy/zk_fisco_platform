const hre = require("hardhat");

async function main() {
  const fs = require("fs");
  const path = require("path");
  const rustDir = process.env.RUST_DIR || path.resolve(__dirname, "../../rust");

  // 读取 whitelist seal
  const proofData = fs.readFileSync(path.join(rustDir, "host", "whitelist_seal.bin"));
  const proof = "0x" + proofData.toString("hex");

  // 读取 Image ID
  const imageIdArray = "[1376172094, 4182868983, 2678587968, 1008640372, 758401279, 3312111971, 3025778735, 2185735313]";
  const imageId = "0x" + imageIdArray.replace(/[\[\]\s,]/g, '').padStart(64, '0').slice(-64);

  // 读取 journal digest
  const journalDigest = fs.readFileSync(
    path.join(rustDir, "host", "whitelist_journal_digest.txt"),
    "utf8"
  ).trim();

  console.log("=== 测试 Whitelist Seal ===\n");
  console.log("Proof 长度:", proof.length, "chars (", proof.length/2 - 1, "bytes)");
  console.log("Image ID:", imageId);
  console.log("Journal Digest:", journalDigest);
  console.log();

  const verifierAddr = "0x59de2C69DBFCc0155730d7F8F0a2E3ee558dF15A";
  const Verifier = await hre.ethers.getContractAt("RiscZeroGroth16Verifier", verifierAddr);

  try {
    await Verifier.verify.staticCall(proof, imageId, journalDigest);
    console.log("✅ Whitelist seal 验证成功!");
    console.log("\n这说明合约本身是工作的！");
  } catch (error) {
    console.log("❌ Whitelist seal 验证失败:", error.message);
    if (error.errorName) {
      console.log("   Error:", error.errorName);
    }
    console.log("\n这说明合约配置或证明生成有问题！");
  }
}

main().catch(console.error);
