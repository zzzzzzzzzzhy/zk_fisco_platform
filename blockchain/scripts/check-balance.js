const { ethers } = require('ethers');

// 配置
const RPC_URL = 'https://rpc-amoy.polygon.technology/';

// 合约地址 (使用校验和格式)
const FORUM_TOKEN_ADDRESS = '0xFd6396cA4aAcd6081EC15EA88D1bFFfde7334634';
const MTK_TOKEN_ADDRESS = '0x3b90669eB9960d1e65D3A09097a9363Df74783DD';
const MTK_HOLDER_ADDRESS = '0xefdf04ebfd9dcfae3886a2d7e04b0bedbe3e68f2';

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
  },
  {
    "constant": true,
    "inputs": [],
    "name": "symbol",
    "outputs": [{"name": "", "type": "string"}],
    "payable": false,
    "stateMutability": "view",
    "type": "function"
  }
];

async function checkBalances() {
  try {
    console.log('🔍 检查MTK代币余额...\n');

    // 连接到Polygon Amoy测试网
    const provider = new ethers.JsonRpcProvider(RPC_URL);

    // 使用正确的地址校验和格式
    const checksumHolderAddress = ethers.getAddress(MTK_HOLDER_ADDRESS);
    const checksumContractAddress = ethers.getAddress(FORUM_TOKEN_ADDRESS);
    const checksumTokenAddress = ethers.getAddress(MTK_TOKEN_ADDRESS);

    const mtkToken = new ethers.Contract(checksumTokenAddress, MTK_ABI, provider);

    // 获取代币信息
    const symbol = await mtkToken.symbol();
    const decimals = await mtkToken.decimals();

    console.log('📊 代币信息:');
    console.log('   符号:', symbol);
    console.log('   小数位数:', decimals);
    console.log('');

    // 检查持有者余额
    const holderBalance = await mtkToken.balanceOf(checksumHolderAddress);
    const holderBalanceFormatted = ethers.formatUnits(holderBalance, decimals);

    console.log('💰 MTK持有者余额 (', checksumHolderAddress, '):');
    console.log('   ', holderBalanceFormatted, 'MTK');
    console.log('   原始值:', holderBalance.toString());
    console.log('');

    // 检查合约余额
    const contractBalance = await mtkToken.balanceOf(checksumContractAddress);
    const contractBalanceFormatted = ethers.formatUnits(contractBalance, decimals);

    console.log('🏦 ForumTokenExtension合约余额 (', checksumContractAddress, '):');
    console.log('   ', contractBalanceFormatted, 'MTK');
    console.log('   原始值:', contractBalance.toString());
    console.log('');

    // 计算建议注资金额
    const suggestedAmount = 10000; // 10,000 MTK
    const availableAmount = parseFloat(holderBalanceFormatted);

    console.log('💡 建议分析:');
    if (availableAmount >= suggestedAmount) {
      console.log('   ✅ 余额充足，可以注资', suggestedAmount, 'MTK');
      console.log('   📤 注资后合约余额:', (parseFloat(contractBalanceFormatted) + suggestedAmount).toFixed(4), 'MTK');
    } else {
      console.log('   ❌ 余额不足，需要', suggestedAmount, 'MTK');
      console.log('   💰 当前余额:', holderBalanceFormatted, 'MTK');
      console.log('   📉 还需要:', (suggestedAmount - availableAmount).toFixed(4), 'MTK');
    }

    console.log('\n🎯 注资目标: 将', suggestedAmount, 'MTK转入合约以支持奖励分发');
    console.log('📋 每日预估消耗: ~', (5 * 100).toFixed(0), 'MTK (假设100个用户每天签到)');

  } catch (error) {
    console.error('❌ 检查余额失败:', error.message);
  }
}

// 运行余额检查
checkBalances();
