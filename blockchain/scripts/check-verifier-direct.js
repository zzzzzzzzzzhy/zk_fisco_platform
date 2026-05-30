const hre = require("hardhat");

async function main() {
  const provider = hre.ethers.provider;
  
  // 读取我们刚才生成的证明
  const fs = require("fs");
  const proofData = fs.readFileSync("/tmp/new_proof.bin");
  const proof = "0x" + proofData.toString("hex");
  
  // 调用 verifier 合约直接测试
  const verifierAddr = "0x59de2C69DBFCc0155730d7F8F0a2E3ee558dF15A";
  const Verifier = await hre.ethers.getContractAt("contracts/risc0/IRiscZeroVerifier.sol:IRiscZeroVerifier", verifierAddr);
  
  const imageId = "0x06a4c68ab8cc1f68c73a8209c50eb5d7e576a19340e8242c3d2e2dc1921365b9";
  const journalDigest = "0x59327e5dc9d974e2550434888ac54e45c4b2ba74705aedbbc043d87e1e985cf8";
  
  console.log("=== 直接测试 Verifier ===\n");
  console.log("Proof length:", proof.length, "chars");
  console.log("Image ID:", imageId);
  console.log("Journal Digest:", journalDigest);
  console.log();
  
  try {
    const result = await Verifier.verify.staticCall(proof, imageId, journalDigest);
    console.log("✅ Verify 成功:", result);
  } catch (error) {
    console.log("❌ Verify 失败:");
    console.log("  Error:", error.message);
    if (error.errorName) {
      console.log("  Error Name:", error.errorName);
    }
    if (error.errorArgs) {
      console.log("  Error Args:", error.errorArgs);
    }
    if (error.data) {
      console.log("  Error Data:", error.data);
      
      // 尝试解析为自定义错误
      const selector = error.data.slice(0, 10);
      console.log("  Selector:", selector);
      
      // 计算常见错误的 selector
      const errors = {
        "InvalidProof()": hre.ethers.id("InvalidProof()").slice(0, 10),
        "InvalidImageId()": hre.ethers.id("InvalidImageId()").slice(0, 10),
      };
      
      console.log("\n已知错误 selectors:");
      for (const [name, sel] of Object.entries(errors)) {
        console.log(`  ${name}: ${sel}`);
        if (sel === selector) {
          console.log(`    ✅ 匹配!`);
        }
      }
    }
  }
}

main().catch(console.error);
