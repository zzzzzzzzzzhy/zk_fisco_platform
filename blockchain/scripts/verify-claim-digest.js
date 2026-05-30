const hre = require("hardhat");

async function main() {
  const imageId = "0x06a4c68ab8cc1f68c73a8209c50eb5d7e576a19340e8242c3d2e2dc1921365b9";
  const journalDigest = "0x59327e5dc9d974e2550434888ac54e45c4b2ba74705aedbbc043d87e1e985cf8";
  
  console.log("=== 计算 ReceiptClaim Digest ===\n");
  console.log("Image ID:", imageId);
  console.log("Journal Digest:", journalDigest);
  console.log();
  
  // 根据 IRiscZeroVerifier.sol 的计算方法
  // 1. 计算 Output digest
  const TAG_OUTPUT = hre.ethers.id("risc0.Output");
  const outputDigest = hre.ethers.solidityPackedSha256(
    ["bytes32", "bytes32", "bytes32", "uint16"],
    [
      TAG_OUTPUT,
      journalDigest,
      "0x0000000000000000000000000000000000000000000000000000000000000000", // assumptionsDigest
      0x0200 // uint16(2) << 8
    ]
  );
  console.log("Output Digest:", outputDigest);
  
  // 2. 计算 ReceiptClaim digest  
  const TAG_CLAIM = hre.ethers.id("risc0.ReceiptClaim");
  const SYSTEM_STATE_ZERO = "0xa3acc27117418996340b84e5a90f3ef4c49d22c79e44aad822ec9c313e1eb8e2";
  const claimDigest = hre.ethers.solidityPackedSha256(
    ["bytes32", "bytes32", "bytes32", "bytes32", "bytes32", "uint32", "uint32", "uint16"],
    [
      TAG_CLAIM,
      "0x0000000000000000000000000000000000000000000000000000000000000000", // input
      SYSTEM_STATE_ZERO, // preStateDigest
      SYSTEM_STATE_ZERO, // postStateDigest
      outputDigest,
      0x00000000, // system exit code (Halted = 0)
      0x00000000, // user exit code (0)
      0x0400 // down.length = 4
    ]
  );
  
  console.log("ReceiptClaim Digest:", claimDigest);
  console.log();
  
  // 从合约获取期望的 digest
  const verifierAddr = "0x59de2C69DBFCc0155730d7F8F0a2E3ee558dF15A";
  const Verifier = await hre.ethers.getContractAt("RiscZeroGroth16Verifier", verifierAddr);
  
  // 使用静态调用测试验证
  const fs = require("fs");
  const proofData = fs.readFileSync("/tmp/check_image.bin");
  const seal = "0x" + proofData.toString("hex");
  
  console.log("=== 测试验证 ===");
  console.log("Seal 长度:", seal.length, "chars");
  
  try {
    await Verifier.verify.staticCall(seal, imageId, journalDigest);
    console.log("✅ 验证成功!");
  } catch (error) {
    console.log("❌ 验证失败:", error.message);
    
    // 检查是否是 SelectorMismatch
    if (error.message.includes("SelectorMismatch")) {
      console.log("   错误类型: SelectorMismatch");
    } else if (error.message.includes("VerificationFailed")) {
      console.log("   错误类型: VerificationFailed");
      console.log("   这意味着 Groth16 证明本身的数学验证失败了");
    }
  }
}

main().catch(console.error);
