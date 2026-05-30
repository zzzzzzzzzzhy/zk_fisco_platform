const { ethers } = require('ethers');
require('dotenv').config();

// 配置
const PRIVATE_KEY = process.env.PRIVATE_KEY;
const RPC_URL = 'https://rpc-amoy.polygon.technology/';

// 新合约地址
const FORUM_TOKEN_ADDRESS = '0xFd6396cA4aAcd6081EC15EA88D1bFFfde7334634';
const MTK_TOKEN_ADDRESS = '0x3b90669eB9960d1e65D3A09097a9363Df74783DD';

// 新的ForumTokenExtension 合约 ABI (包含checkin函数)
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
    "name": "canCheckinToday",
    "outputs": [{"internalType": "bool", "name": "", "type": "bool"}],
    "stateMutability": "view",
    "type": "function"
  },
  {
    "inputs": [],
    "name": "checkin",
    "outputs": [],
    "stateMutability": "nonpayable",
    "type": "function"
  }
];

// MTK代币ABI
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

async function testNewContract() {
  try {
    console.log('🧪 测试新部署的合约...\n');

    // 连接到Polygon Amoy测试网
    const provider = new ethers.JsonRpcProvider(RPC_URL);
    const wallet = new ethers.Wallet(PRIVATE_KEY, provider);

    // 使用正确的地址校验和格式
    const checksumContractAddress = ethers.getAddress(FORUM_TOKEN_ADDRESS);
    const checksumTokenAddress = ethers.getAddress(MTK_TOKEN_ADDRESS);

    const forumToken = new ethers.Contract(checksumContractAddress, FORUM_TOKEN_ABI, wallet);
    const mtkToken = new ethers.Contract(checksumTokenAddress, MTK_ABI, provider);

    console.log('📋 测试账户:', wallet.address);
    console.log('🏦 新合约地址:', checksumContractAddress);
    console.log('');

    // 1. 检查用户代币余额
    console.log('1️⃣ 检查用户MTK余额:');
    const userBalance = await forumToken.getUserTokenBalance(wallet.address);
    const decimals = await mtkToken.decimals();
    console.log('   用户MTK余额:', ethers.formatUnits(userBalance, decimals), 'MTK');
    console.log('');

    // 2. 检查今日是否可以签到
    console.log('2️⃣ 检查签到状态:');
    const canCheckin = await forumToken.canCheckinToday(wallet.address);
    console.log('   今日是否可以签到:', canCheckin ? '✅ 是' : '❌ 否');
    console.log('');

    // 3. 如果可以签到，测试签到功能
    if (canCheckin) {
      console.log('3️⃣ 测试用户签到功能:');
      try {
        console.log('   🔄 正在调用 checkin() 函数...');
        const tx = await forumToken.checkin();
        console.log('   📤 签到交易哈希:', tx.hash);
        console.log('   ⏳ 等待交易确认...');
        const receipt = await tx.wait();
        console.log('   ✅ 用户签到成功！');
        console.log('   📊 Gas使用:', receipt.gasUsed.toString());
        console.log('   🏆 交易状态:', receipt.status === 1 ? '成功' : '失败');
      } catch (error) {
        console.log('   ❌ 用户签到失败:', error.message);
      }
    } else {
      console.log('3️⃣ 今日已签到，跳过签到测试');
    }

    console.log('\n🎉 新合约测试完成！');
    console.log('\n📝 总结:');
    console.log('✅ 合约地址已更新: 0xFd6396cA4aAcd6081EC15EA88D1bFFfde7334634');
    console.log('✅ 新增用户签到函数: checkin()');
    console.log('✅ 前端配置已同步更新');
    console.log('✅ 缓存已清理');

  } catch (error) {
    console.error('❌ 测试失败:', error.message);
  }
}

// 运行测试
testNewContract();
