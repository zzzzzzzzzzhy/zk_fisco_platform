// 智能合约配置文件
export const CONTRACT_CONFIG = {
  // 本地 Hardhat 节点配置（npx hardhat node）
  // 合约地址由 blockchain/scripts/deploy-local.js 部署后自动写入 frontend/.env.local
  local: {
    mtkTokenAddress: process.env.VUE_APP_WEE_TOKEN_ADDRESS || '',
    tokenSymbol: 'WEE',
    tokenName: 'WEE Token (Local)',
    forumTokenAddress: process.env.VUE_APP_FORUM_TOKEN_ADDRESS || '',
    contentShareAddress: process.env.VUE_APP_CONTENT_SHARE_ADDRESS || '',
    rewardGovernorAddress: '',
    chainId: 31337,
    chainName: 'Hardhat Local',
    rpcUrls: ['http://127.0.0.1:8545'],
    blockExplorerUrls: [],
    nativeCurrency: {
      name: 'ETH',
      symbol: 'ETH',
      decimals: 18
    }
  },

  // 开发环境配置（Polygon Amoy 测试网）
  development: {
    mtkTokenAddress: process.env.VUE_APP_WEE_TOKEN_ADDRESS || '',
    tokenSymbol: process.env.VUE_APP_TOKEN_SYMBOL || 'WEE',
    tokenName: process.env.VUE_APP_TOKEN_NAME || 'WEE Token',
    forumTokenAddress: process.env.VUE_APP_FORUM_TOKEN_ADDRESS || '',
    contentShareAddress: process.env.VUE_APP_CONTENT_SHARE_ADDRESS || '',
    rewardGovernorAddress: process.env.VUE_APP_REWARD_GOVERNOR_ADDRESS || '',
    chainId: 80002,
    chainName: 'Polygon Amoy Testnet',
    rpcUrls: [
      'https://rpc-amoy.polygon.technology',
      'https://polygon-amoy.drpc.org'
    ],
    blockExplorerUrls: ['https://amoy.polygonscan.com/'],
    nativeCurrency: {
      name: 'MATIC',
      symbol: 'MATIC',
      decimals: 18
    }
  },

  // 生产环境配置（Polygon 主网）
  production: {
    mtkTokenAddress: process.env.VUE_APP_WEE_TOKEN_ADDRESS || '',
    tokenSymbol: process.env.VUE_APP_TOKEN_SYMBOL || 'WEE',
    tokenName: process.env.VUE_APP_TOKEN_NAME || 'WEE Ecosystem Token',
    forumTokenAddress: process.env.VUE_APP_FORUM_TOKEN_ADDRESS || '',
    contentShareAddress: process.env.VUE_APP_CONTENT_SHARE_ADDRESS || '',
    rewardGovernorAddress: process.env.VUE_APP_REWARD_GOVERNOR_ADDRESS || '',
    chainId: 137,
    chainName: 'Polygon Mainnet',
    rpcUrls: [process.env.VUE_APP_POLYGON_RPC_URL || 'https://polygon-rpc.com'],
    blockExplorerUrls: ['https://polygonscan.com/'],
    nativeCurrency: {
      name: 'MATIC',
      symbol: 'MATIC',
      decimals: 18
    }
  }
}

// 获取当前环境配置
// 通过 VUE_APP_ENV 控制：local | development | production
export function getCurrentConfig() {
  const env = process.env.VUE_APP_ENV || 'local'
  return CONTRACT_CONFIG[env] || CONTRACT_CONFIG.local
}

// 验证合约地址
export function validateContractAddress(address) {
  if (!address || typeof address !== 'string') {
    return false
  }
  return /^0x[a-fA-F0-9]{40}$/.test(address)
}

export default getCurrentConfig()
