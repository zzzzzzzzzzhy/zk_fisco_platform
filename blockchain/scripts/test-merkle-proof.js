const hre = require("hardhat");

async function main() {
  const fs = require("fs");
  const path = require("path");
  const rustDir = process.env.RUST_DIR || path.resolve(__dirname, "../../rust");
  
  // 读取 merkle_verify 生成的证明
  const proofData = fs.readFileSync(path.join(rustDir, "snark_proof.bin"));
  const proof = "0x" + proofData.toString("hex");
  
  // 读取 Image ID
  const imageId = fs.readFileSync(path.join(rustDir, "image_id.txt"), "utf8").trim();
  // 转换格式
  const cleanImageId = imageId.replace(/[\[\]\s,]/g, '').replace(/^0x/i, '0x').padStart(66, '0');
  const finalImageId = "0x" + cleanImageId.slice(-64);
  
  console.log("=== 测试 Merkle Verify 证明 ===\n");
  console.log("Proof 长度:", proof.length, "chars");
  console.log("Image ID:", finalImageId);
  console.log();
  
  // merkle_verify 的 journal digest 是 merkle_root 的 SHA256
  // leaf = [0u8; 32], path = [[1u8; 32], [2u8; 32]], bits = [true, false]
  // 计算得到的 root = [88, af, ba, e0, ...]
  const journalDigest = "0x" + Buffer.from([0x88, 0xaf, 0xba, 0xe0]).toString("hex").padEnd(64, '0');
  
  console.log("Journal Digest:", journalDigest);
  console.log();
  
  // 测试验证
  const verifierAddr = "0x59de2C69DBFCc0155730d7F8F0a2E3ee558dF15A";
  const Verifier = await hre.ethers.getContractAt("RiscZeroGroth16Verifier", verifierAddr);
  
  try {
    await Verifier.verify.staticCall(proof, finalImageId, journalDigest);
    console.log("✅ Merkle 证明验证成功!");
  } catch (error) {
    console.log("❌ Merkle 证明验证失败:", error.message);
    
    if (error.errorName) {
      console.log("   Error Name:", error.errorName);
    }
  }
}

main().catch(console.error);
