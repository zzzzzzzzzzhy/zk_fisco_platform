# ✅ ZK-Rollup 已启用！节省 96% Gas

## 🎉 配置完成摘要

### 已部署的合约（Amoy 测试网）

| 合约 | 地址 | Gas 消耗 |
|------|------|----------|
| **RISC Zero Verifier** | [0xe2a7...5f5F9](https://amoy.polygonscan.com/address/0xe2a7013219e85a5df4E81998c985e7bd8875f5F9) | ~2M gas |
| **ContentRollupRegistry** | [0x6951...C79B](https://amoy.polygonscan.com/address/0x695150dC64243Faea165BC2086DcADC9DB65C79B) | ~1.5M gas |
| **RollupRewardDistributor** | [0x5152...da20](https://amoy.polygonscan.com/address/0x515256F9C04B7Da75B97480389b6375935DBda20) | ~0.8M gas |

**Image ID**: `0x06a4c68ab8cc1f68c73a8209c50eb5d7e576a19340e8242c3d2e2dc1921365b9`

---

## 💰 Gas 节省效果

### 之前（每笔交易上链）
```
每次内容分享/评论/签到 = 1 笔链上交易
Gas = 50,000 × 50 Gwei = 0.0025 MATIC

假设每分钟 1 笔交易：
每天 = 1,440 笔 × 0.0025 MATIC = 3.6 MATIC
30 MATIC 只能使用 ≈ 8 天 ❌
```

### 现在（ZK-Rollup 聚合）
```
每 2 小时 = 1 笔 Rollup 批次交易
Gas = 250,000 × 50 Gwei = 0.0125 MATIC

每天 = 12 笔 × 0.0125 MATIC = 0.15 MATIC
30 MATIC 可使用 ≈ 200 天 ✅

节省 96% Gas！
```

---

## 🚀 启动后端

### 方式 1：直接启动
```bash
cd /data/Dapp_Share_Platform/competition-platform/backend

# 确保 .env 链接到 .env.amoy
ls -la .env  # 应该显示: .env -> .env.amoy

# 启动后端
mvn spring-boot:run
```

### 方式 2：后台运行
```bash
nohup mvn spring-boot:run > logs/spring.log 2>&1 &

# 查看日志
tail -f logs/spring.log | grep -i rollup
```

---

## 📊 Rollup 工作流程

### 自动化流程（无需手动干预）

```
1. 用户操作（前端签名）
   ↓
   内容分享/评论/签到 → POST /consent
   ↓
2. 事件记录（reward_events 表）
   status = 0 (待打包)
   ↓
3. 定时聚合（每小时）
   RewardEventRollupTask.rollupHourly()
   ↓
   构建 Merkle Tree
   生成 batchId, journalDigest
   调用 Rust Prover 生成 ZK 证明
   ↓
4. 链上提交（每 5 分钟检查）
   RewardRollupSubmitTask.submitPending()
   ↓
   调用 ContentRollupRegistry.submitBatch()
   验证 ZK 证明（Groth16）
   ↓
5. 批量发放（每 10 分钟）
   RewardDistributionService.distributeBatch()
   ↓
   调用 RollupRewardDistributor.distributeBatch()
   转账 WEE Token 到用户钱包
   ↓
6. 用户领取（前端可选）
   查询批次状态 → 显示"已上链，可领取"
```

---

## 🔍 验证 Rollup 是否运行

### 查看日志
```bash
# 查看聚合任务
tail -f logs/spring.log | grep "Reward Rollup"

# 查看证明生成
tail -f logs/spring.log | grep "Rollup proof"

# 查看上链提交
tail -f logs/spring.log | grep "submitBatch"
```

### 期望输出
```
✅ 成功运行时会看到：
[INFO] Reward Rollup 完成: eventType=CONTENT_SHARE, batchId=2025010610, count=15, merkleRoot=0x...
[INFO] Rollup proof 生成完成: proofs/rollup/content_share/2025010610.bin
[INFO] 批次提交成功: txHash=0x..., gasUsed=247832
```

### 数据库验证
```sql
-- 查看批次记录
SELECT
    id,
    biz_type,
    biz_id AS batch_id,
    data_hash AS merkle_root,
    status,
    created_at
FROM chain_proofs
WHERE biz_type LIKE '%ROLLUP%'
ORDER BY id DESC
LIMIT 5;

-- 查看事件状态
SELECT
    event_type,
    COUNT(*) AS count,
    status
FROM reward_events
GROUP BY event_type, status;
```

### Polygonscan 验证
1. 访问：https://amoy.polygonscan.com/address/0x695150dC64243Faea165BC2086DcADC9DB65C79B
2. 查看 **Transactions** 标签
3. 应该能看到 `submitBatch` 交易

---

## 🧪 快速测试

### 创建测试数据
```sql
-- 插入测试事件
INSERT INTO reward_events (user_id, event_type, biz_id, signature, payload, status, created_at, updated_at)
VALUES
(1, 'CONTENT_SHARE', 'test-001', '0x123', '{"test": true}', 0, NOW(), NOW()),
(2, 'CONTENT_SHARE', 'test-002', '0x456', '{"test": true}', 0, NOW(), NOW()),
(3, 'COMMENT', 'test-003', '0x789', '{"test": true}', 0, NOW(), NOW());
```

### 手动触发聚合（可选）
```bash
# 修改配置，缩短时间窗口
# backend/.env.amoy
REWARD_ROLLUP_WINDOW_MINUTES=10  # 改为 10 分钟

# 重启后端
mvn spring-boot:run
```

等待 10 分钟，查看日志。

---

## ⚙️ 高级配置（可选）

### 调整聚合频率
```yaml
# backend/src/main/resources/application-amoy.yml
reward:
  rollup:
    window-minutes: 60  # 改为 1 小时
    cron: "0 0 * * * ?"  # 每小时执行
    submit-cron: "0 */10 * * * ?"  # 每 10 分钟检查提交
```

### 调整 Gas 价格
```yaml
blockchain:
  polygon:
    gas-price-gwei: 100  # 提高到 100 Gwei（加快确认）
```

### 启用调试日志
```yaml
logging:
  level:
    com.wereen.competitionplatform.service.RewardEventRollupService: DEBUG
    com.wereen.competitionplatform.service.RewardProofGeneratorService: DEBUG
```

---

## 📈 性能监控

### Gas 消耗统计
```sql
-- 查看批次 Gas 使用情况
SELECT
    biz_id AS batch_id,
    metadata->>"$.count" AS event_count,
    metadata->>"$.gasUsed" AS gas_used,
    created_at
FROM chain_proofs
WHERE biz_type LIKE '%ROLLUP%'
ORDER BY id DESC
LIMIT 10;
```

### 计算节省率
```
节省率 = (原成本 - 新成本) / 原成本 × 100%

例如：
原成本：1000 笔 × 0.0025 MATIC = 2.5 MATIC
新成本：1 批次 × 0.0125 MATIC = 0.0125 MATIC
节省率 = (2.5 - 0.0125) / 2.5 × 100% = 99.5%
```

---

## ❓ 常见问题

### Q1: 批次没有生成？
**A:**
- 检查定时任务是否启用：`grep "@EnableScheduling" backend/src/main/java/`
- 查看日志：`tail -f logs/spring.log | grep "rollup"`
- 检查配置：`cat backend/.env | grep ROLLUP`

### Q2: ZK 证明生成失败？
**A:**
- 检查 Prover：`RUST_DIR=${RUST_DIR:-/path/to/competition-platform/rust} && ls -lh "$RUST_DIR/target/release/rollup-prove"`
- 手动测试：`RUST_DIR=${RUST_DIR:-/path/to/competition-platform/rust} && cd "$RUST_DIR" && cargo run --bin rollup-prove --release`
- 查看错误：`tail -f logs/spring.log | grep "proof"`

### Q3: 上链提交失败？
**A:**
- 检查余额：在 MetaMask 查看 0xeFdf04EbFD9Dfae3886A2d7E04B0bEdbe3E68f2
- 领取测试币：https://faucet.polygon.technology/
- 检查 Gas：`tail -f logs/spring.log | grep "gas"`

### Q4: 想回退到旧模式？
**A:**
```bash
cd backend
cp .env.backup.YYYYMMDD .env
# 或注释掉 ROLLUP 相关配置
```

---

## 🎯 下一步优化

1. **监控告警**
   - 添加 Prometheus + Grafana
   - 监控批次生成延迟
   - Gas 消耗告警

2. **优化证明生成**
   - 使用 Bonsai 云服务（减少本地计算）
   - 并行证明生成
   - 证明缓存

3. **前端展示**
   - 显示批次状态
   - 实时 Gas 节省统计
   - 用户奖励领取页面

4. **安全加固**
   - 多签治理（批次提交需要多签验证）
   - 链下验签（防止伪造签名）
   - 应急暂停机制

---

## 📞 支持

- **RISC Zero 文档**: https://dev.risczero.com/
- **Polygon Amoy 文档**: https://docs.polygon.technology/develop/network-details/network/network-amoy/
- **项目 Issue**: /data/Dapp_Share_Platform/competition-platform/docs/

---

**恭喜！你的平台现在使用 ZK-Rollup 技术，Gas 消耗降低 96%！** 🎉
