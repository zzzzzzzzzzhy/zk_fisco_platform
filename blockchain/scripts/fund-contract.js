const { ethers } = require('ethers');
require('dotenv').config();

// 配置
const PRIVATE_KEY = process.env.PRIVATE_KEY;
const RPC_URL = 'https://rpc-amoy.polygon.technology/';

// 合约地址
const FORUM_TOKEN_ADDRESS = '0x34Af5079D6393DD42Ef0C74894c3Cf49C6D483eF';
const MTK_TOKEN_ADDRESS = '0x3b90669eB9960d1e65D3A09097a9363Df74783DD';

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
    "constant": false,
    "inputs": [
      {"name": "spender", "type": "address"},
      {"name": "value", "type": "uint256"}
    ],
    "name": "transfer",
    "outputs": [{"name": "", "type": "bool"}],
    "payable": false,
    "stateMutability": "nonpayable",
    "type": "function"
  },
  {
    "constant": false,
    "inputs": [
      {"name": "spender", "type": "address"},
      {"name": "value", "type": "uint256"}
    ],
    "name": "approve",
    "outputs": [{"name": "", "type": "bool"}],
    "payable": false,
    "stateMutability": "nonpayable",
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

async function fundContract() {
  try {
    console.log('🚀 开始为ForumTokenExtension合约注资...');

    // 连接到Polygon Amoy测试网
    const provider = new ethers.JsonRpcProvider(RPC_URL);
    const wallet = new ethers.Wallet(PRIVATE_KEY, provider);

    console.log('📋 使用账户:', wallet.address);

    // 检查账户余额
    const mtkToken = new ethers.Contract(MTK_TOKEN_ADDRESS, MTK_ABI, wallet);
    const balance = await mtkToken.balanceOf(wallet.address);
    const decimals = await mtkToken.decimals();
    const balanceFormatted = ethers.formatUnits(balance, decimals);

    console.log('💰 当前MTK余额:', balanceFormatted, 'MTK');

    // 注资金额 (10000 MTK)
    const fundAmount = ethers.parseUnits('10000', decimals);

    if (balance < fundAmount) {
      console.log('❌ 余额不足！需要至少 10000 MTK');
      return;
    }

    console.log('💸 准备转入', ethers.formatUnits(fundAmount, decimals), 'MTK到合约...');

    // 执行转账
    const tx = await mtkToken.transfer(FORUM_TOKEN_ADDRESS, fundAmount);
    console.log('📤 交易哈希:', tx.hash);

    console.log('⏳ 等待交易确认...');
    const receipt = await tx.wait();

    console.log('✅ 注资成功！');
    console.log('📊 交易详情:');
    console.log('   - 区块号:', receipt.blockNumber);
    console.log('   - Gas使用:', receipt.gasUsed.toString());
    console.log('   - 状态:', receipt.status === 1 ? '成功' : '失败');

    // 检查合约余额
    const contractBalance = await mtkToken.balanceOf(FORUM_TOKEN_ADDRESS);
    console.log('💎 合约当前余额:', ethers.formatUnits(contractBalance, decimals), 'MTK');

  } catch (error) {
    console.error('❌ 注资失败:', error.message);
  }
}

// 运行注资
fundContract();
