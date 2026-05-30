const hre = require("hardhat");

async function main() {
  const fs = require("fs");
  const path = require("path");
  const rustDir = process.env.RUST_DIR || path.resolve(__dirname, "../../rust");

  // 读取 merkle_verify 生成的 seal
  const proofData = fs.readFileSync(path.join(rustDir, "host", "snark_seal.bin"));
  const proof = "0x" + proofData.toString("hex");

  // 读取 Image ID
  const imageIdRaw = fs.readFileSync(path.join(rustDir, "host", "image_id.txt"), "utf8").trim();
  // 转换为 32 字节 hex
  const imageIdClean = imageIdRaw.replace(/[\[\]\s,]/g, '');
  const imageId = "0x" + imageIdClean.padStart(64, '0').slice(-64);

  console.log("=== 测试 Merkle Seal (encode_seal 格式) ===\n");
  console.log("Proof 长度:", proof.length, "chars (", proof.length/2 - 1, "bytes)");
  console.log("Image ID:", imageId);

  // merkle_verify 的 journal 是 32 字节的 merkle root
  const root = [0x88, 0xaf, 0xba, 0xe0];
  const journalDigest = "0x" + Buffer.from(root).toString("hex").padEnd(64, '0');

  console.log("Journal Digest:", journalDigest);
  console.log();

  // 测试验证
  const verifierAddr = "0x59de2C69DBFCc0155730d7F8F0a2E3ee558dF15A";
  const Verifier = await hre.ethers.getContractAt("RiscZeroGroth16Verifier", verifierAddr);

  try {
    await Verifier.verify.staticCall(proof, imageId, journalDigest);
    console.log("✅ Merkle seal 验证成功!");
    console.log("\n这意味着 encode_seal 格式是正确的！");
  } catch (error) {
    console.log("❌ Merkle seal 验证失败:", error.message);
    if (error.errorName) {
      console.log("   Error:", error.errorName);
    }
  }
}

main().catch(console.error);
