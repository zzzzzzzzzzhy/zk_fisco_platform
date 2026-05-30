require("@nomicfoundation/hardhat-toolbox");
require("dotenv").config();
const { getDecryptedPrivateKey } = require("./utils/decrypt");

// 获取私钥：优先使用加密私钥，如果没有则使用明文私钥
const {
  PRIVATE_KEY,
  ENCRYPTED_PRIVATE_KEY,
  DECRYPTION_PASSWORD,
  POLYGONSCAN_API_KEY = "",
  POLYGON_GAS_PRICE_GWEI,
} = process.env;

let privateKey = PRIVATE_KEY;

if (ENCRYPTED_PRIVATE_KEY) {
  // 使用加密私钥
  try {
    if (!DECRYPTION_PASSWORD) {
      console.error('错误：使用加密私钥时必须提供 DECRYPTION_PASSWORD 环境变量');
      process.exit(1);
    }
    privateKey = getDecryptedPrivateKey(DECRYPTION_PASSWORD);
    console.log('✓ 成功解密私钥');
  } catch (error) {
    console.error('私钥解密失败:', error.message);
    console.error('请检查 DECRYPTION_PASSWORD 环境变量是否正确');
    process.exit(1);
  }
} else if (PRIVATE_KEY) {
  console.warn('⚠️  警告：正在使用明文私钥，建议使用加密私钥提高安全性');
}

const normalizedPrivateKey = privateKey ? (privateKey.startsWith("0x") ? privateKey : "0x" + privateKey) : null;

/** @type import('hardhat/config').HardhatUserConfig */
module.exports = {
  solidity: {
    version: "0.8.24",
    settings: {
      optimizer: {
        enabled: true,
        runs: 200,
      },
      evmVersion: "cancun",
    },
  },
  networks: {
    hardhat: {
      chainId: 31337,
      accounts: {
        count: 10,
        accountsBalance: "10000000000000000000000"
      }
    },
    localhost: {
      url: "http://127.0.0.1:8545",
      chainId: 31337
    },
    amoy: {
      url: "https://rpc-amoy.polygon.technology/",
      accounts: normalizedPrivateKey ? [normalizedPrivateKey] : [],
      chainId: 80002,
      gas: 6000000,
      gasPrice: 30000000000
    },
    polygon: {
      url: process.env.POLYGON_RPC_URL || "https://polygon-rpc.com",
      accounts: normalizedPrivateKey ? [normalizedPrivateKey] : [],
      chainId: 137,
      gasPrice: POLYGON_GAS_PRICE_GWEI ? Number(POLYGON_GAS_PRICE_GWEI) * 1e9 : undefined
    }
  },
  etherscan: {
    apiKey: {
      polygon: POLYGONSCAN_API_KEY,
      polygonAmoy: POLYGONSCAN_API_KEY
    }
  },
  paths: {
    sources: "./contracts",
    tests: "./test",
    cache: "./cache",
    artifacts: "./artifacts"
  }
};
