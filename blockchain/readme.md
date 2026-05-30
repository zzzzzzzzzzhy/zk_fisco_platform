# 🏗️ 竞赛平台区块链合约

## 📋 合约信息

- **网络**: Polygon Amoy 测试网 (Chain ID: 80002)
- **合约地址**: `0xFd6396cA4aAcd6081EC15EA88D1bFFfde7334634` （2025-11-18 部署）
  - **WEE代币地址**: `0xae51580da0C57714904d0B37E5793bF09f743811`
- **部署者地址**: `0xeFdf04EbFD9DcFae3886A2d7E04B0bEdbe3E68f2`
- **部署交易哈希**: `0x590382838db2843a7471c65403092c668dce6381176a38c32e3761c7b4042461`

## 💰 合约状态

- **合约余额**: 待注资（请参考 FUND_INSTRUCTIONS.md）
- **MTK代币精度**: 18位小数
- **部署区块**: 28935491
- **状态**: ✅ 正常运行

## 🎯 合约功能

### ForumTokenExtension 合约

基于OpenZeppelin标准开发，集成了论坛代币奖励系统。

#### 主要功能：
1. **用户签到** `checkin()` - 用户每日签到获得MTK奖励
2. **管理员奖励** `dailyCheckin(address)` - 管理员批量处理用户签到
3. **帖子奖励** `rewardPost(address, string)` - 发布帖子获得奖励
4. **评论奖励** `rewardComment(address, string)` - 发布评论获得奖励
5. **精华帖子** `rewardFeaturedPost(address, string)` - 精华帖子额外奖励
6. **内容分享奖励** `rewardContentShare(address,string,bool)` - 根据图片/视频发放 5/10 MTK
7. **批量奖励** `batchRewardPosts()` - 批量处理多个奖励

#### 奖励配置：
- **每日签到**: 5 MTK
- **发布帖子**: 10 MTK
- **发布评论**: 2 MTK
- **精华帖子**: 50 MTK
- **内容分享（图片）**: 5 MTK
- **内容分享（视频）**: 10 MTK
- **连续7天签到**: 额外30 MTK
- **每日奖励上限**: 100 MTK

## 🔧 技术特性

### 安全机制
- ✅ 重入攻击防护 (ReentrancyGuard)
- ✅ 权限控制 (Ownable + 授权铸造者)
- ✅ 防重复奖励 (内容ID去重)
- ✅ 每日奖励上限保护
- ✅ 余额检查机制

### 权限设计
- **用户权限**: 可直接调用 `checkin()` 进行签到
- **管理员权限**: 可调用所有奖励分发函数
- **所有者权限**: 可更新配置、授权铸造者、紧急提取

## 🚀 部署记录

### 最新部署 (2025-11-12)
- **版本**: v2.0 (修复用户签到权限)
- **改进**: 添加用户直接签到功能，分离用户和管理员权限
- **状态**: ✅ 部署成功并注资

### 历史版本
- **v1.0**: 初始版本，仅支持管理员调用签到功能

## 📖 使用指南

### 用户签到
```solidity
// 用户直接调用
function checkin() external nonReentrant
```

### 管理员操作
```solidity
// 授权铸造者
function authorizeMinter(address minter, bool authorized) external onlyOwner

// 批量奖励
function batchRewardPosts(
    address[] calldata users,
    string[] calldata contentIds,
    RewardType rewardType
) external onlyAuthorizedMinter
```

## 🔍 验证方法

### 1. 区块链浏览器
访问 https://amoy.polygonscan.com/address/0xFd6396cA4aAcd6081EC15EA88D1bFFfde7334634

### 2. 余额查询
```bash
# 检查合约余额
node scripts/check-balance.js

# 测试合约功能
node scripts/test-contract.js
```

### 3. API集成
后端已配置区块链集成，API地址: `http://localhost:8080/api`

## 📝 开发说明

### 环境变量
```bash
BLOCKCHAIN_ADMIN_PRIVATE_KEY=your_private_key_here
```

### 合约文件位置
- **合约源码**: `contracts/ForumTokenExtension.sol`
- **部署脚本**: `scripts/deploy.js`
- **测试脚本**: `scripts/test-contract.js`
- **注资脚本**: `scripts/fund-contract.js`

## ⚠️ 注意事项

1. **测试环境**: 当前部署在Polygon Amoy测试网
2. **Gas费用**: 用户操作需要支付少量MATIC作为Gas费
3. **私钥安全**: 生产环境请妥善管理私钥
4. **合约升级**: 升级合约需要重新部署和注资

## 🛠️ 故障排除

### 常见问题
1. **交易失败**: 检查账户MATIC余额是否充足
2. **权限错误**: 确认调用者权限和合约配置
3. **余额不足**: 合约MTK余额不足以支付奖励

### 联系支持
如需技术支持，请检查部署日志或联系开发团队。

---
**最后更新**: 2025-11-12
**文档版本**: v2.0
