const { ethers } = require('ethers');

// 配置
const PRIVATE_KEY = process.env.PRIVATE_KEY;
const RPC_URL = 'https://rpc-amoy.polygon.technology/';

// 合约地址
const FORUM_TOKEN_ADDRESS = '0xFd6396cA4aAcd6081EC15EA88D1bFFfde7334634';

// ForumTokenExtension 合约 ABI (添加内容分享奖励函数)
const FORUM_TOKEN_ABI = [
  {
    "inputs": [{"internalType": "address", "name": "user", "type": "address"}],
    "name": "getUserTokenBalance",
    "outputs": [{"internalType": "uint256", "name": "", "type": "uint256"}],
    "stateMutability": "view",
    "type": "function"
  },
  {
    "inputs": [],
    "name": "rewardConfig",
    "outputs": [
      {"internalType": "uint256", "name": "postReward", "type": "uint256"},
      {"internalType": "uint256", "name": "commentReward", "type": "uint256"},
      {"internalType": "uint256", "name": "dailyCheckinReward", "type": "uint256"},
      {"internalType": "uint256", "name": "featuredPostReward", "type": "uint256"},
      {"internalType": "uint256", "name": "consecutiveBonus", "type": "uint256"},
      {"internalType": "uint256", "name": "contentImageReward", "type": "uint256"},
      {"internalType": "uint256", "name": "contentVideoReward", "type": "uint256"}
    ],
    "stateMutability": "view",
    "type": "function"
  },
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
    "inputs": [{"internalType": "address", "name": "minter", "type": "address"}, {"internalType": "bool", "name": "authorized", "type": "bool"}],
    "name": "authorizeMinter",
    "outputs": [],
    "stateMutability": "nonpayable",
    "type": "function"
  }
];

async function testContentShareReward() {
  try {
    console.log('🧪 测试内容分享奖励功能...\n');

    // 连接到Polygon Amoy测试网
    const provider = new ethers.JsonRpcProvider(RPC_URL);
    const wallet = new ethers.Wallet(PRIVATE_KEY, provider);

    const checksumContractAddress = ethers.getAddress(FORUM_TOKEN_ADDRESS);
    const forumToken = new ethers.Contract(checksumContractAddress, FORUM_TOKEN_ABI, wallet);

    console.log('📋 测试账户:', wallet.address);
    console.log('🏦 合约地址:', checksumContractAddress);
    console.log('');

    // 1. 检查当前余额
    const beforeBalance = await forumToken.getUserTokenBalance(wallet.address);
    console.log('💰 奖励前余额:', ethers.formatUnits(beforeBalance, 18), 'MTK');
    console.log('');

    // 2. 检查奖励配置
    const rewardConfig = await forumToken.rewardConfig();
    console.log('🎯 奖励配置:');
    console.log('   图片分享奖励:', ethers.formatUnits(rewardConfig.contentImageReward, 18), 'MTK');
    console.log('   视频分享奖励:', ethers.formatUnits(rewardConfig.contentVideoReward, 18), 'MTK');
    console.log('');

    // 3. 确保自己是授权的铸造者
    console.log('🔐 授权铸造者...');
    try {
      await forumToken.authorizeMinter(wallet.address, true);
      console.log('   ✅ 铸造者授权成功');
    } catch (error) {
      console.log('   ⚠️  授权可能已存在或失败:', error.message.substring(0, 50) + '...');
    }
    console.log('');

    // 4. 测试图片分享奖励
    console.log('📸 测试图片分享奖励...');
    try {
      const imageTx = await forumToken.rewardContentShare(
        wallet.address,
        'share_image_123',
        false  // isVideo = false
      );
      console.log('   📤 图片奖励交易哈希:', imageTx.hash);
      console.log('   ⏳ 等待交易确认...');
      const imageReceipt = await imageTx.wait();
      console.log('   ✅ 图片奖励成功！');
      console.log('   📊 Gas使用:', imageReceipt.gasUsed.toString());
    } catch (error) {
      console.log('   ❌ 图片奖励失败:', error.message);
    }
    console.log('');

    // 5. 检查图片奖励后余额
    const afterImageBalance = await forumToken.getUserTokenBalance(wallet.address);
    console.log('💰 图片奖励后余额:', ethers.formatUnits(afterImageBalance, 18), 'MTK');
    const imageReward = afterImageBalance - beforeBalance;
    console.log('🎁 实际图片奖励:', ethers.formatUnits(imageReward, 18), 'MTK');
    console.log('');

    // 6. 测试视频分享奖励
    console.log('🎬 测试视频分享奖励...');
    try {
      const videoTx = await forumToken.rewardContentShare(
        wallet.address,
        'share_video_456',
        true   // isVideo = true
      );
      console.log('   📤 视频奖励交易哈希:', videoTx.hash);
      console.log('   ⏳ 等待交易确认...');
      const videoReceipt = await videoTx.wait();
      console.log('   ✅ 视频奖励成功！');
      console.log('   📊 Gas使用:', videoReceipt.gasUsed.toString());
    } catch (error) {
      console.log('   ❌ 视频奖励失败:', error.message);
    }
    console.log('');

    // 7. 检查视频奖励后余额
    const afterVideoBalance = await forumToken.getUserTokenBalance(wallet.address);
    console.log('💰 视频奖励后余额:', ethers.formatUnits(afterVideoBalance, 18), 'MTK');
    const videoReward = afterVideoBalance - afterImageBalance;
    console.log('🎁 实际视频奖励:', ethers.formatUnits(videoReward, 18), 'MTK');
    console.log('');

    console.log('🎉 内容分享奖励测试完成！');

  } catch (error) {
    console.error('❌ 测试失败:', error.message);
  }
}

// 运行测试
testContentShareReward();