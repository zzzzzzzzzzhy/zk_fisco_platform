const { ethers } = require('ethers');

// 配置
const PRIVATE_KEY = 'b5a5c7ae3324e465ea7439806a0ed17abcdc2a59b048604951b9df08bd9b682c';
const RPC_URL = 'https://rpc-amoy.polygon.technology/';
const FORUM_TOKEN_ADDRESS = '0xFd6396cA4aAcd6081EC15EA88D1bFFfde7334634';
const USER_ADDRESS = '0xda3680d36411c6a72d21d9a2809deaff2c6e2a6e';

// ForumTokenExtension 合约 ABI
const FORUM_TOKEN_ABI = [
  {
    "inputs": [
      {"internalType": "address", "name": "user", "type": "address"},
      {"internalType": "string", "name": "contentId", "type": "string"},
      {"internalType": "bool", "name": "isVideo", "type": "bool"}
    ],
    "name": "rewardContentShare",
    "outputs": [],
    "stateMutability": "nonpayable",
    "type": "function"
  },
  {
    "inputs": [{"internalType": "address", "name": "user", "type": "address"}],
    "name": "getUserTokenBalance",
    "outputs": [{"internalType": "uint256", "name": "", "type": "uint256"}],
    "stateMutability": "view",
    "type": "function"
  }
];

async function testManualReward() {
  try {
    console.log('🧪 手动测试内容分享奖励...\n');

    // 连接到Polygon Amoy测试网
    const provider = new ethers.JsonRpcProvider(RPC_URL);
    const wallet = new ethers.Wallet(PRIVATE_KEY, provider);
    const forumToken = new ethers.Contract(FORUM_TOKEN_ADDRESS, FORUM_TOKEN_ABI, wallet);

    console.log('📋 管理员地址:', wallet.address);
    console.log('👤 目标用户地址:', USER_ADDRESS);
    console.log('🏦 合约地址:', FORUM_TOKEN_ADDRESS);
    console.log('');

    // 1. 检查用户当前余额
    console.log('📊 检查奖励前余额...');
    const beforeBalance = await forumToken.getUserTokenBalance(USER_ADDRESS);
    console.log('奖励前余额:', ethers.formatUnits(beforeBalance, 18), 'MTK');
    console.log('');

    // 2. 检查管理员授权状态（如果需要）
    console.log('🔐 检查管理员授权状态...');
    try {
      // 尝试授权（如果失败可能是已经授权）
      const authTx = await forumToken.authorizeMinter(wallet.address, true);
      console.log('授权交易:', authTx.hash);
      await authTx.wait();
      console.log('✅ 授权成功');
    } catch (error) {
      console.log('⚠️ 授权可能已存在或失败:', error.message.substring(0, 100) + '...');
    }
    console.log('');

    // 3. 执行奖励交易
    console.log('🎁 执行内容分享奖励...');
    const contentId = 'share_test_' + Date.now();
    const isVideo = false;

    console.log('内容ID:', contentId);
    console.log('是否视频:', isVideo);
    console.log('Gas Price:', ethers.formatUnits(await provider.getFeeData().then(f => f.gasPrice), 'gwei'), 'Gwei');

    const rewardTx = await forumToken.rewardContentShare(USER_ADDRESS, contentId, isVideo);
    console.log('📤 交易哈希:', rewardTx.hash);
    console.log('⏳ 等待交易确认...');

    const receipt = await rewardTx.wait();

    if (receipt.status === 1) {
      console.log('✅ 奖励交易成功！');
      console.log('📊 Gas使用:', receipt.gasUsed.toString());
      console.log('📦 区块号:', receipt.blockNumber);

      // 4. 检查奖励后余额
      console.log('');
      console.log('📊 检查奖励后余额...');
      const afterBalance = await forumToken.getUserTokenBalance(USER_ADDRESS);
      console.log('奖励后余额:', ethers.formatUnits(afterBalance, 18), 'MTK');

      const reward = afterBalance - beforeBalance;
      console.log('🎁 实际奖励:', ethers.formatUnits(reward, 18), 'MTK');

    } else {
      console.log('❌ 交易失败');
    }

  } catch (error) {
    console.error('❌ 测试失败:', error.message);

    if (error.message.includes('insufficient funds')) {
      console.log('💡 原因: 余额不足');
    } else if (error.message.includes('gas')) {
      console.log('💡 原因: Gas 相关问题');
    } else if (error.message.includes('nonce')) {
      console.log('💡 原因: Nonce 问题');
    } else if (error.message.includes('underpriced')) {
      console.log('💡 原因: Gas Price 太低');
    }
  }
}

// 运行测试
testManualReward();