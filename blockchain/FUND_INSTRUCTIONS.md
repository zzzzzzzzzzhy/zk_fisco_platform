# 🪙 ForumTokenExtension 合约注资指南（WEE）

## 📋 当前状态
- **WEE代币持有账户**: `0xeFdf04EbFD9dCFAe3886A2d7E04B0bEdbe3E68f2`
- **WEE代币地址**: `0xae51580da0C57714904d0B37E5793bF09f743811`
- **ForumTokenExtension合约**: `0x34Af5079D6393DD42Ef0C74894c3Cf49C6D483eF` (2025-11-20 部署)
- **网络**: Polygon Amoy 测试网 (Chain ID: 80002)

## 🎯 注资目标
向ForumTokenExtension合约转入 **10,000 WEE** 代币，用于：
- 每日签到奖励 (5 WEE/天)
- 发帖奖励 (10 WEE/帖)
- 评论奖励 (2 WEE/评论)
- 连续签到奖励 (30 WEE/7天)

## 🚀 方法一: 使用MetaMask手动转账

### 步骤 1: 添加代币到MetaMask
1. 打开MetaMask钱包
2. 切换到 **Polygon Amoy** 网络
3. 点击"添加代币"
4. 粘贴WEE代币合约地址: `0xae51580da0C57714904d0B37E5793bF09f743811`
5. 代币符号: `WEE`
6. 小数位数: `18`

### 步骤 2: 执行转账
1. 在MetaMask中点击"发送"
2. 接收地址: `0x34Af5079D6393DD42Ef0C74894c3Cf49C6D483eF`
3. 金额: `10000` (WEE代币)
4. 确认交易并支付Gas费

## 🛠️ 方法二: 使用自动化脚本

### 步骤 1: 设置私钥
```bash
# 在项目根目录创建环境变量文件
cd /data/competiton-PL/competition-platform/blockchain
echo "PRIVATE_KEY=your_private_key_here" > .env.local
```

### 步骤 2: 运行注资脚本
```bash
cd /data/competiton-PL/competition-platform/blockchain
node scripts/fund-contract.js
```

## 🔍 验证注资结果

### 方法一: 使用Web3前端
1. 访问 http://localhost:8084
2. 连接MetaMask钱包
3. 查看ForumTokenBalance组件中的代币余额

### 方法二: 使用区块链浏览器
1. 访问 https://amoy.polygonscan.com/
2. 搜索合约地址: `0x34Af5079D6393DD42Ef0C74894c3Cf49C6D483eF`
3. 查看"Token Transfers"标签页
4. 确认收到10,000 WEE代币

## ⚠️ 注意事项

1. **网络确认**: 确保在Polygon Amoy测试网上操作
2. **Gas费用**: 转账需要支付少量MATIC作为Gas费
3. **私钥安全**: 不要在代码中硬编码私钥，使用环境变量
4. **测试环境**: 这是在测试网上的操作，代币没有实际价值

## ✅ 完成后

注资完成后，论坛系统就能正常运行：
- ✅ 用户可以直接签到获得WEE奖励 (无需管理员权限)
- ✅ 管理员可以发放发帖和评论奖励
- ✅ 连续签到7天有额外奖励 (30 MTK)
- ✅ 所有Web3功能都将正常工作

## 🔧 新功能特性

**用户签到功能已更新:**
- 用户可直接调用 `checkin()` 函数进行签到
- 管理员仍可使用 `dailyCheckin(address)` 为用户批量签到
- 支持连续签到奖励计算
- 每日奖励上限限制 (100 MTK)
- 防重复签到保护机制

## 📞 如需帮助

如果遇到问题，请检查：
1. 网络是否正确 (Polygon Amoy测试网)
2. 账户余额是否充足
3. 合约地址是否正确
4. Gas费用是否足够
