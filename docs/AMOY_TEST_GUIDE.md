# Polygon Amoy 测试网快速指南

## 🎯 目标
在 **Amoy 测试网**进行完整的 ZK-Rollup 测试，**零成本**（无真实资金消耗）。

---

## 📋 测试前准备

### 1. 创建测试账户（重要！不要用主网账户）

**步骤：**
1. MetaMask → 创建新账户
2. 账户名称：`Amoy Test Account`
3. **不要**转入真实资金

### 2. 添加 Amoy 测试网络

MetaMask → 设置 → 网络 → 添加网络 → 手动添加：

```
网络名称: Polygon Amoy Testnet
RPC URL: https://rpc-amoy.polygon.technology
链 ID: 80002
货币符号: MATIC
区块浏览器: https://amoy.polygonscan.com
```

### 3. 获取测试代币

**水龙头地址：** https://faucet.polygon.technology/

**步骤：**
1. 确保 MetaMask 切换到 Amoy 网络
2. 选择网络：`Amoy`
3. 输入你的钱包地址
4. 完成人机验证
5. 等待 30 秒，账户收到 0.2-1 MATIC

**备用水龙头：**
- https://faucet.quicknode.com/polygon/amoy
- https://staked-ui.com/

---

## 🚀 一键部署测试环境

### 运行部署脚本

```bash
cd /data/Dapp_Share_Platform/competition-platform

# 运行部署脚本
bash scripts/deploy-to-amoy.sh
```

**脚本会自动完成：**
1. ✅ 编译 Rust ZK Prover
2. ✅ 生成 RISC Zero Image ID
3. ✅ 部署智能合约到 Amoy
4. ✅ 配置后端环境变量
5. ✅ 切换到测试网配置

**预计时间：** 10-15 分钟（主要是编译和证明生成）

---

## ✅ 验证配置

运行配置检查：

```bash
bash scripts/check-amoy-config.sh
```

**期望输出：**
```
✅ 所有检查通过！
🚀 可以启动后端测试
```

---

## 🧪 测试 Rollup 流程

### 1. 启动后端

```bash
cd /data/Dapp_Share_Platform/competition-platform/backend

# 启动 Spring Boot
mvn spring-boot:run

# 查看日志（新终端）
tail -f logs/spring.log | grep -i rollup
```

### 2. 启动前端

```bash
cd /data/Dapp_Share_Platform/competition-platform/frontend

# 启动 Vue.js
npm run serve
```

访问：http://localhost:8081

### 3. 模拟用户操作

**测试场景：**
1. 用户发布内容分享
2. 发表评论
3. 每日签到

**前端签名确认：**
```javascript
// 前端代码会自动签名
await axios.post(`/content-shares/${id}/consent`, {
  signature: web3.eth.personal.sign(...)
})
```

### 4. 等待 Rollup 聚合

**定时任务：**
- 聚合任务：每小时执行（`0 0 * * * ?`）
- 提交任务：每 5 分钟执行（`0 */5 * * * ?`）

**加速测试（可选）：**

修改配置，缩短窗口时间：

```yaml
# backend/src/main/resources/application-amoy.yml
reward:
  rollup:
    window-minutes: 10  # 改为 10 分钟
    cron: "0 */10 * * * ?"  # 每 10 分钟执行
```

重启后端，等待 10 分钟。

### 5. 查看批次生成

**后端日志：**
```bash
tail -f logs/spring.log | grep -i "rollup"
```

**期望输出：**
```
Reward Rollup 完成: eventType=CONTENT_SHARE, batchId=2025010610, count=15, merkleRoot=0xabc...
Rollup proof 生成完成: proofs/rollup/content_share/2025010610.bin
```

### 6. 查看上链状态

**数据库查询：**
```sql
-- 查看批次记录
SELECT * FROM chain_proofs WHERE biz_type = 'CONTENT_SHARE_ROLLUP' ORDER BY id DESC LIMIT 5;

-- 查看事件状态
SELECT * FROM reward_events WHERE status = 2 LIMIT 10;
```

**Polygonscan 验证：**
1. 复制 `tx_hash` 字段
2. 访问：https://amoy.polygonscan.com/
3. 粘贴交易哈希查看详情

**期望结果：**
- ✅ 交易状态：Success
- ✅ Gas Used：~250,000
- ✅ 合约方法：`submitBatch()`

---

## 📊 性能对比测试

### 单次交易成本（主网 vs 测试网）

| 操作 | 主网 Gas | Amoy 测试网 Gas | 成本（主网） | 成本（测试网） |
|------|----------|----------------|-------------|--------------|
| 单次内容分享 | ~50,000 | ~50,000 | ~0.0005 MATIC | **0 MATIC** |
| **Rollup 批次** | **~250,000** | **~250,000** | **~0.0025 MATIC** | **0 MATIC** |
| **100 次操作聚合** | **5,000,000** | **~250,000** | **~0.05 MATIC** | **0 MATIC** |

**结论：** 测试网完全免费！

---

## 🔧 常见问题

### Q1: 水龙头没有代币了？
**A:**
- 尝试备用水龙头：https://staked-ui.com/
- 在 Polygon Discord 领取：https://discord.gg/polygon
- 等待 24 小时后重试

### Q2: 合约部署失败（insufficient funds）？
**A:**
```bash
# 查询余额
cast balance <YOUR_ADDRESS> --rpc-url https://rpc-amoy.polygon.technology

# 如果为 0，重新领水
curl -X POST https://faucet.polygon.technology/api/claim \
  -H "Content-Type: application/json" \
  -d '{"address":"<YOUR_ADDRESS>","network":"amoy"}'
```

### Q3: ZK 证明生成超时？
**A:**
```bash
# 检查 prover 是否编译成功
RUST_DIR=${RUST_DIR:-/path/to/competition-platform/rust}
ls -lh "$RUST_DIR/host/target/release/rollup-prove"

# 手动测试生成（需要提供 metadata.json）
cd "$RUST_DIR"
RISC0_WORK_DIR=/tmp/risc0-work cargo run --bin rollup-prove --release
```

### Q4: 定时任务没有执行？
**A:**
```bash
# 检查 Spring Scheduling 是否启用
grep "@EnableScheduling" backend/src/main/java/com/wereen/competitionplatform/*

# 检查 Cron 表达式
grep "cron:" backend/src/main/resources/application-amoy.yml
```

### Q5: 批次提交失败（nonce too high）？
**A:**
```bash
# 重置 MetaMask nonce
# MetaMask → 设置 → 高级 → 重置账户

# 或在后端添加 nonce 管理
```

---

## 📈 测试完成后的清理

### 1. 停止后端
```bash
# 查找进程
ps aux | grep spring-boot

# 终止进程
kill <PID>
```

### 2. 恢复主网配置（如果需要）
```bash
cd backend

# 恢复备份
cp .env.backup.YYYYMMDD_HHMMSS .env

# 修改 application.yml
# spring.profiles.active: prod  # 或删除此行
```

### 3. 清理测试数据（可选）
```sql
-- 清空测试数据
TRUNCATE TABLE reward_events;
TRUNCATE TABLE chain_proofs;
```

---

## 🎓 学习资源

### Amoy 测试网文档
- [官方文档](https://docs.polygon.technology/docs/develop/network-details/network/network-amoy/)
- [区块浏览器](https://amoy.polygonscan.com/)
- [水龙头](https://faucet.polygon.technology/)

### RISC Zero 文档
- [官方文档](https://dev.risczero.com/)
- [Groth16 说明](https://dev.risczero.com/api/groth16)

---

## ✨ 成功标准

完成以下步骤即表示测试成功：

1. ✅ 成功部署合约到 Amoy
2. ✅ 用户操作生成 reward_events
3. ✅ 定时任务成功聚合批次
4. ✅ ZK 证明生成成功
5. ✅ 批次成功上链
6. ✅ Polygonscan 验证交易成功
7. ✅ 奖励发放成功

**预计耗时：** 1-2 小时（包含等待聚合）

**预计成本：** 0 MATIC（完全免费）

---

## 🚨 安全提醒

1. ⚠️ **测试账户私钥不要在任何地方公开**
2. ⚠️ **测试网代币无真实价值，不可兑换**
3. ⚠️ **测试网合约地址与主网不同**
4. ⚠️ **测试网数据不迁移到主网**

---

**Happy Testing! 🎉**
