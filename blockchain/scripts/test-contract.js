const { ethers } = require('ethers');
require('dotenv').config();

// 配置
const PRIVATE_KEY = process.env.PRIVATE_KEY;
const RPC_URL = 'https://rpc-amoy.polygon.technology/';

// 合约地址
const FORUM_TOKEN_ADDRESS = '0x34Af5079D6393DD42Ef0C74894c3Cf49C6D483eF';
const MTK_TOKEN_ADDRESS = '0x3b90669eB9960d1e65D3A09097a9363Df74783DD';

// ForumTokenExtension 合约 ABI (简化版)
const FORUM_TOKEN_ABI = [
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
  },
  {
    "inputs": [{"internalType": "address", "name": "user", "type": "address"}],
    "name": "canCheckinToday",
    "outputs": [{"internalType": "bool", "name": "", "type": "bool"}],
    "stateMutability": "view",
    "type": "function"
  },
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
    "inputs": [{"internalType": "address", "name": "minter", "type": "address"}, {"internalType": "bool", "name": "authorized", "type": "bool"}],
    "name": "authorizeMinter",
    "outputs": [],
    "stateMutability": "nonpayable",
    "type": "function"
  },
  {
    "inputs": [],
    "name": "checkin",
    "outputs": [],
    "stateMutability": "nonpayable",
    "type": "function"
  },
  {
    "inputs": [{"internalType": "address", "name": "user", "type": "address"}],
    "name": "dailyCheckin",
    "outputs": [],
    "stateMutability": "nonpayable",
    "type": "function"
  }
];

// MTK代币ABI (简化版)
const MTK_ABI = [
  {
    "constant": true,
    "inputs": [{"name": "account", "type": "address"}],
    "name": "balanceOf",
    "outputs": [{"name": "", "type": "uint256"}],
    "payable": false,
    "stateMutability": "view",
    "type": "function"
  },
  {
    "constant": true,
    "inputs": [],
    "name": "decimals",
    "outputs": [{"name": "", "type": "uint8"}],
    "payable": false,
    "stateMutability": "view",
    "type": "function"
  }
];

async function testContractFunction() {
  try {
    console.log('🧪 开始测试ForumTokenExtension合约功能...\n');

    // 连接到Polygon Amoy测试网
    const provider = new ethers.JsonRpcProvider(RPC_URL);
    const wallet = new ethers.Wallet(PRIVATE_KEY, provider);

    // 使用正确的地址校验和格式
    const checksumContractAddress = ethers.getAddress(FORUM_TOKEN_ADDRESS);
    const checksumTokenAddress = ethers.getAddress(MTK_TOKEN_ADDRESS);

    const forumToken = new ethers.Contract(checksumContractAddress, FORUM_TOKEN_ABI, wallet);
    const mtkToken = new ethers.Contract(checksumTokenAddress, MTK_ABI, provider);

    console.log('📋 测试账户:', wallet.address);
    console.log('🏦 合约地址:', checksumContractAddress);
    console.log('');

    // 1. 检查合约代币余额
    console.log('1️⃣ 检查合约代币余额:');
    const contractBalance = await mtkToken.balanceOf(checksumContractAddress);
    const decimals = await mtkToken.decimals();
    console.log('   合约MTK余额:', ethers.formatUnits(contractBalance, decimals), 'MTK');
    console.log('');

    // 2. 检查用户代币余额
    console.log('2️⃣ 检查用户代币余额:');
    const userBalance = await forumToken.getUserTokenBalance(wallet.address);
    console.log('   用户MTK余额:', ethers.formatUnits(userBalance, decimals), 'MTK');
    console.log('');

    // 3. 检查奖励配置
    console.log('3️⃣ 检查奖励配置:');
    const rewardConfig = await forumToken.rewardConfig();
    console.log('   发布帖子奖励:', ethers.formatUnits(rewardConfig.postReward, decimals), 'MTK');
    console.log('   发布评论奖励:', ethers.formatUnits(rewardConfig.commentReward, decimals), 'MTK');
    console.log('   每日签到奖励:', ethers.formatUnits(rewardConfig.dailyCheckinReward, decimals), 'MTK');
    console.log('   精华帖子奖励:', ethers.formatUnits(rewardConfig.featuredPostReward, decimals), 'MTK');
    console.log('   连续签到奖励:', ethers.formatUnits(rewardConfig.consecutiveBonus, decimals), 'MTK');
    console.log('   内容分享-图片奖励:', ethers.formatUnits(rewardConfig.contentImageReward, decimals), 'MTK');
    console.log('   内容分享-视频奖励:', ethers.formatUnits(rewardConfig.contentVideoReward, decimals), 'MTK');
    console.log('');

    // 4. 检查用户奖励信息
    console.log('4️⃣ 检查用户奖励信息:');
    const userRewardInfo = await forumToken.getUserRewardInfo(wallet.address);
    console.log('   上次签到时间:', new Date(Number(userRewardInfo.lastCheckinTime) * 1000).toLocaleString());
    console.log('   连续签到天数:', userRewardInfo.consecutiveDays.toString());
    console.log('   今日已获得奖励:', ethers.formatUnits(userRewardInfo.dailyRewardAmount, decimals), 'MTK');
    console.log('   总奖励金额:', ethers.formatUnits(userRewardInfo.totalRewarded, decimals), 'MTK');
    console.log('');

    // 5. 检查今日是否可以签到
    console.log('5️⃣ 检查签到状态:');
    const canCheckin = await forumToken.canCheckinToday(wallet.address);
    console.log('   今日是否可以签到:', canCheckin ? '✅ 是' : '❌ 否');
    console.log('');

    // 6. 授权当前账户为铸造者（如果尚未授权）
    console.log('6️⃣ 检查铸造者授权状态:');
    try {
      // 注意：这个功能可能需要owner权限，如果当前账户不是owner会失败
      await forumToken.authorizeMinter(wallet.address, true);
      console.log('   ✅ 铸造者授权成功');
    } catch (error) {
      console.log('   ⚠️  铸造者授权失败 (可能需要owner权限):', error.message.substring(0, 50) + '...');
    }
    console.log('');

    // 7. 如果可以签到，尝试签到 (使用新的用户签到函数)
    if (canCheckin) {
      console.log('7️⃣ 尝试执行每日签到 (用户直接调用):');
      try {
        const tx = await forumToken.checkin();
        console.log('   📤 签到交易哈希:', tx.hash);
        console.log('   ⏳ 等待交易确认...');
        const receipt = await tx.wait();
        console.log('   ✅ 用户签到成功！');
        console.log('   📊 Gas使用:', receipt.gasUsed.toString());

        // 重新检查用户余额和奖励信息
        const newUserBalance = await forumToken.getUserTokenBalance(wallet.address);
        const newUserRewardInfo = await forumToken.getUserRewardInfo(wallet.address);
        console.log('   💰 签到后余额:', ethers.formatUnits(newUserBalance, decimals), 'MTK');
        console.log('   🏆 总奖励金额:', ethers.formatUnits(newUserRewardInfo.totalRewarded, decimals), 'MTK');
        console.log('   📅 连续签到天数:', newUserRewardInfo.consecutiveDays.toString());
      } catch (error) {
        console.log('   ❌ 用户签到失败:', error.message);
        console.log('   🔍 尝试管理员签到方式...');

        // 如果用户签到失败，尝试管理员签到
        try {
          const tx = await forumToken.dailyCheckin(wallet.address);
          console.log('   📤 管理员签到交易哈希:', tx.hash);
          console.log('   ⏳ 等待交易确认...');
          const receipt = await tx.wait();
          console.log('   ✅ 管理员签到成功！');
          console.log('   📊 Gas使用:', receipt.gasUsed.toString());
        } catch (adminError) {
          console.log('   ❌ 管理员签到也失败:', adminError.message);
        }
      }
    } else {
      console.log('7️⃣ 今日已签到，跳过签到测试');
    }

    console.log('\n🎉 合约功能测试完成！');

  } catch (error) {
    console.error('❌ 测试失败:', error.message);
  }
}

// 运行测试
testContractFunction();
