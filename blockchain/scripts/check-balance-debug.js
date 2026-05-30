const { ethers } = require('ethers');

// 配置
const PRIVATE_KEY = process.env.PRIVATE_KEY;
const RPC_URL = 'https://rpc-amoy.polygon.technology/';
const FORUM_TOKEN_ADDRESS = '0xFd6396cA4aAcd6081EC15EA88D1bFFfde7334634';

// ForumTokenExtension 合约 ABI
const FORUM_TOKEN_ABI = [
  {
    "inputs": [{"internalType": "address", "name": "user", "type": "address"}],
    "name": "getUserTokenBalance",
    "outputs": [{"internalType": "uint256", "name": "", "type": "uint256"}],
    "stateMutability": "view",
    "type": "function"
  },
  {
    "inputs": [{"internalType": "address", "name": "user", "type": "address"}],
    "name": "getUserRewardInfo",
    "outputs": [
      {"internalType": "uint256", "name": "lastCheckinTime", "type": "uint256"},
      {"internalType": "uint256", "name": "consecutiveDays", "type": "uint256"},
      {"internalType": "uint256", "name": "dailyRewardAmount", "type": "uint256"},
      {"internalType": "uint256", "name": "totalRewarded", "type": "uint256"}
    ],
    "stateMutability": "view",
    "type": "function"
  }
];

async function checkUserBalance() {
  try {
    console.log('🔍 检查用户MTK代币余额...\n');

    // 连接到Polygon Amoy测试网
    const provider = new ethers.JsonRpcProvider(RPC_URL);
    const wallet = new ethers.Wallet(PRIVATE_KEY, provider);
    const forumToken = new ethers.Contract(FORUM_TOKEN_ADDRESS, FORUM_TOKEN_ABI, provider);

    // 测试几个可能的用户地址
    const testAddresses = [
      '0xda3680d36411c6a72d21d9a2809deaff2c6e2a6e', // 用户1地址
      wallet.address, // 管理员地址
      '0x1234567890123456789012345678901234567890' // 随机地址测试
    ];

    console.log('📋 管理员地址:', wallet.address);
    console.log('🏦 合约地址:', FORUM_TOKEN_ADDRESS);
    console.log('');

    for (const address of testAddresses) {
      try {
        const balance = await forumToken.getUserTokenBalance(address);
        const balanceFormatted = ethers.formatUnits(balance, 18);

        console.log(`💰 地址 ${address}:`);
        console.log(`   余额: ${balanceFormatted} MTK`);

        if (balance > 0) {
          // 获取详细奖励信息
          const rewardInfo = await forumToken.getUserRewardInfo(address);
          console.log(`   上次签到: ${rewardInfo.lastCheckinTime.toString() === '0' ? '从未签到' : new Date(parseInt(rewardInfo.lastCheckinTime.toString()) * 1000).toLocaleString()}`);
          console.log(`   连续天数: ${rewardInfo.consecutiveDays.toString()}`);
          console.log(`   累计奖励: ${ethers.formatUnits(rewardInfo.totalRewarded, 18)} MTK`);
        }
        console.log('');
      } catch (error) {
        console.log(`❌ 查询地址 ${address} 失败: ${error.message.substring(0, 100)}...`);
        console.log('');
      }
    }

    // 检查最新的内容分享奖励交易
    console.log('🔍 检查最近的奖励交易...');

    // 检查管理员地址的最近交易
    const latestBlock = await provider.getBlockNumber();
    console.log(`📦 当前区块: ${latestBlock}`);

    // 查找最近的相关交易
    for (let i = 0; i < 10; i++) {
      try {
        const block = await provider.getBlock(latestBlock - i, true);
        if (block && block.transactions) {
          for (const tx of block.transactions) {
            if (tx.to && tx.to.toLowerCase() === FORUM_TOKEN_ADDRESS.toLowerCase()) {
              const receipt = await provider.getTransactionReceipt(tx.hash);
              if (receipt && receipt.status === 1) {
                console.log(`✅ 找到成功交易: ${tx.hash}`);
                console.log(`   区块: ${receipt.blockNumber}`);
                console.log(`   Gas Used: ${receipt.gasUsed.toString()}`);
              }
            }
          }
        }
      } catch (error) {
        // 忽略错误，继续检查
      }
    }

  } catch (error) {
    console.error('❌ 检查失败:', error.message);
  }
}

// 运行检查
checkUserBalance();