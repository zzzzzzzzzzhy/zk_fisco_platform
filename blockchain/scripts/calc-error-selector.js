const hre = require("hardhat");

async function main() {
  const errors = [
    "VerificationFailed()",
    "SelectorMismatch(bytes4,bytes4)",
    "InvalidProof()",
    "InvalidImageId()",
  ];
  
  const target = "0x439cc0cd";
  
  console.log("=== 计算错误 selectors ===\n");
  
  for (const errorSig of errors) {
    const selector = hre.ethers.id(errorSig).slice(0, 10);
    const match = selector === target ? "✅ 匹配!" : "";
    console.log(`${errorSig}:`);
    console.log(`  ${selector} ${match}`);
  }
  
  console.log(`\n实际错误: ${target}`);
}

main().catch(console.error);
