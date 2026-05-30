# 区块链上链功能实现总结

本文档总结了竞赛平台所有已实现的区块链上链功能。

---

## 📊 上链功能完成情况

| 业务类型 | 功能名称 | 实现状态 | 触发时机 | 实现位置 |
|---------|---------|---------|---------|---------|
| **SUBMISSION** | 提交作品上链 | ✅ 已完成 | 提交记录创建后 | `SubmissionService.java:74-145` |
| **LEADERBOARD** | 榜单冻结上链 | ✅ 已完成 | 手动冻结榜单时 | `LeaderboardService.java:51-124` |
| **EVALUATION** | 评测结果上链 | ✅ 已完成 | 评测完成后 | `EvaluateConsumer.java:74-143` |
| **PRIZE_BATCH** | 奖金批次上链 | ✅ 已完成 | 奖金入账完成后 | `PrizeBookkeepingConsumer.java:100-176` |
| **WITHDRAW** | 提现申请上链 | ✅ 已完成 | 提现状态变更时 | `WithdrawService.java:228-315` |

---

## 1️⃣ 提交作品上链（SUBMISSION）

### 业务描述
用户提交竞赛作品后，将文件哈希上链，确保提交记录不可篡改。

### 上链内容
- **数据哈希**: 提交文件的 SHA-256 哈希值
- **业务ID**: 提交记录ID
- **上传者**: 用户ID
- **元数据**:
  ```json
  {
    "submissionId": 123,
    "userId": 456,
    "competitionId": 789,
    "hashAlgorithm": "SHA256",
    "timestamp": "2025-10-23T10:30:00"
  }
  ```

### 处理流程
1. 用户创建提交记录
2. 文件预检通过后，计算文件哈希（SHA-256）
3. **异步上链**（@Async）：调用 `BlockchainService.saveEvidence()`
4. 保存链上存证到 `chain_proofs` 表
5. 更新 `submissions` 表的链上状态（txHash, blockHeight, blockTime）

### 关键代码
```java
// SubmissionService.java:101-105
TransactionReceipt receipt = blockchainService.saveEvidence(
    "SUBMISSION",
    String.valueOf(submission.getId()),
    submission.getFileHash()
);
```

### 数据库字段
- `submissions.file_hash` - 文件哈希
- `submissions.chain_status` - 上链状态 (0-未上链 1-上链中 2-已上链 3-失败)
- `submissions.chain_tx_hash` - 交易哈希
- `submissions.block_height` - 区块高度
- `submissions.block_time` - 区块时间

---

## 2️⃣ 榜单冻结上链（LEADERBOARD）

### 业务描述
竞赛管理员冻结榜单后，将榜单快照的 Merkle Root 上链，防止榜单数据被篡改。

### 上链内容
- **数据哈希**: 榜单数据的 Merkle Root
- **业务ID**: 竞赛ID
- **上传者**: 冻结操作人ID
- **元数据**:
  ```json
  {
    "competitionId": 789,
    "snapshotId": "snapshot-789-1698123456000",
    "recordCount": 100,
    "frozenAt": "2025-10-23T15:00:00"
  }
  ```

### 处理流程
1. 管理员触发榜单冻结
2. 获取当前榜单数据（前10名）
3. 计算榜单数据的 **Merkle Root**
4. 序列化榜单数据为 JSON
5. **同步上链**：调用 `BlockchainService.freezeLeaderboard()`
6. 保存榜单快照到 `leaderboards` 表（包含链上信息）

### 关键代码
```java
// LeaderboardService.java:69-73
List<String> dataForMerkle = leaderboardData.stream()
    .map(entry -> String.format("%d|%s|%s|%d",
        entry.getRank(), entry.getUsername(), entry.getScore(), entry.getUserId()))
    .collect(Collectors.toList());
String merkleRoot = MerkleTreeUtil.calculateMerkleRoot(dataForMerkle);

// LeaderboardService.java:98-102
TransactionReceipt receipt = blockchainService.freezeLeaderboard(
    String.valueOf(competitionId),
    merkleRoot,
    metadata
);
```

### 验证机制
```java
// LeaderboardService.java:159-163
List<String> dataForMerkle = leaderboardData.stream()
    .map(entry -> String.format("%d|%s|%s|%d",
        entry.getRank(), entry.getUsername(), entry.getScore(), entry.getUserId()))
    .collect(Collectors.toList());
String calculatedRoot = MerkleTreeUtil.calculateMerkleRoot(dataForMerkle);

boolean valid = calculatedRoot.equals(frozenLeaderboard.getMerkleRoot());
```

### 数据库字段
- `leaderboards.merkle_root` - Merkle Root
- `leaderboards.frozen` - 是否已冻结 (1-是)
- `leaderboards.chain_tx_hash` - 交易哈希
- `leaderboards.block_height` - 区块高度
- `leaderboards.block_time` - 区块时间

---

## 3️⃣ 评测结果上链（EVALUATION）

### 业务描述
评测引擎完成评测后，将评测结果上链，确保得分和排名不可篡改。

### 上链内容
- **数据哈希**: 评测结果哈希（包含 evaluationId, submissionId, score, rank, resourceUsage）
- **业务ID**: 评测记录ID
- **上传者**: 系统标识
- **元数据**:
  ```json
  {
    "evaluationId": 101,
    "submissionId": 123,
    "competitionId": 789,
    "userId": 456,
    "score": "85.50",
    "rank": 10,
    "timestamp": "2025-10-23T16:00:00"
  }
  ```

### 处理流程
1. 评测任务从 Redis Stream 消费
2. 执行评测（TODO: Docker 评测引擎）
3. 创建评测记录到 `evaluations` 表
4. 计算评测结果哈希（SHA-256）
5. **同步上链**：调用 `BlockchainService.saveEvidence()`
6. 保存链上存证到 `chain_proofs` 表
7. 更新 `evaluations` 表的链上状态

### 关键代码
```java
// EvaluateConsumer.java:77-85
String resultHash = calculateEvaluationHash(
    evaluation.getId(),
    submissionId,
    competitionId,
    userId,
    score,
    rank,
    resourceUsage
);

// EvaluateConsumer.java:105-109
TransactionReceipt receipt = blockchainService.saveEvidence(
    "EVALUATION",
    String.valueOf(evaluation.getId()),
    resultHash
);
```

### 哈希计算
```java
// EvaluateConsumer.java:161-162
String data = String.format("%d|%d|%d|%d|%s|%d|%s",
    evaluationId, submissionId, competitionId, userId, score.toString(), rank, resourceUsage);
// 使用 SHA-256 计算哈希
```

### 数据库字段
- `evaluations.result_hash` - 评测结果哈希
- `evaluations.chain_status` - 上链状态
- `evaluations.chain_tx_hash` - 交易哈希
- `evaluations.block_height` - 区块高度
- `evaluations.block_time` - 区块时间

---

## 4️⃣ 奖金批次上链（PRIZE_BATCH）

### 业务描述
奖金批次入账完成后，将获奖者列表的 Merkle Root 上链，确保奖金分配公开透明。

### 上链内容
- **数据哈希**: 获奖者列表的 Merkle Root
- **业务ID**: 奖金批次号
- **上传者**: 系统标识
- **元数据**:
  ```json
  {
    "batchNo": "BATCH-2025-001",
    "competitionId": 789,
    "totalAmount": 1000000,
    "winnersCount": 10,
    "successCount": 10,
    "failedCount": 0,
    "timestamp": "2025-10-23T17:00:00"
  }
  ```

### 处理流程
1. 奖金入账任务从 Redis Stream 消费
2. 逐个处理奖金入账（调用 `WalletService.increaseBalance()`）
3. 查询批次的所有获奖明细
4. 计算获奖者列表的 **Merkle Root**
5. **同步上链**：调用 `BlockchainService.recordPrizeBatch()`
6. 保存链上存证到 `chain_proofs` 表
7. 更新 `prize_batches` 表的链上状态和批次状态（ACCOUNTED）

### 关键代码
```java
// PrizeBookkeepingConsumer.java:110-116
List<String> winnerData = allItems.stream()
    .map(item -> String.format("%d|%d|%d|%s",
        item.getUserId(),
        item.getRank(),
        item.getAmount(),
        item.getStatus()))
    .collect(Collectors.toList());

String winnersRoot = MerkleTreeUtil.calculateMerkleRoot(winnerData);

// PrizeBookkeepingConsumer.java:137-143
TransactionReceipt receipt = blockchainService.recordPrizeBatch(
    batch.getBatchNo(),
    winnersRoot,
    batch.getTotalAmount(),
    "CNY",
    metadata
);
```

### 数据库字段
- `prize_batches.merkle_root` - 获奖者列表 Merkle Root
- `prize_batches.chain_tx_hash` - 交易哈希
- `prize_batches.block_height` - 区块高度
- `prize_batches.block_time` - 区块时间
- `prize_batches.status` - 批次状态 (ACCOUNTED-已入账)

---

## 5️⃣ 提现申请上链（WITHDRAW）

### 业务描述
提现申请的全生命周期状态变更上链，确保提现流程透明可追溯。

### 上链内容
- **数据哈希**: 提现申请哈希（包含 requestId, userId, amount, method, status, actionType）
- **业务ID**: 提现申请ID + 操作类型（支持同一申请的多次上链）
- **上传者**: 用户ID
- **元数据**:
  ```json
  {
    "requestId": 202,
    "userId": 456,
    "amount": 500000,
    "method": "ALIPAY",
    "status": "PAID",
    "actionType": "PAID",
    "provider": "stripe",
    "providerTxId": "ch_1234567890",
    "timestamp": "2025-10-23T18:00:00"
  }
  ```

### 上链时机
提现申请的以下状态变更会触发上链：
1. **APPLIED** - 申请提交时
2. **APPROVED** - 审核通过时
3. **REJECTED** - 审核拒绝时
4. **PAID** - 支付完成时（关键操作）
5. **FAILED** - 支付失败时

### 处理流程
```
创建提现申请 → 冻结余额 → 上链(APPLIED)
       ↓
审核提现申请 → 审核通过/拒绝
       ↓                    ↓
   上链(APPROVED)        上链(REJECTED) + 解冻余额
       ↓
标记已支付 → 扣除冻结金额 → 上链(PAID)
       ↓
（或）支付失败 → 解冻余额 → 上链(FAILED)
```

### 关键代码
```java
// WithdrawService.java:96
uploadWithdrawToBlockchainAsync(request.getId(), "APPLIED");

// WithdrawService.java:118
uploadWithdrawToBlockchainAsync(requestId, "APPROVED");

// WithdrawService.java:173
uploadWithdrawToBlockchainAsync(requestId, "PAID");

// WithdrawService.java:266-270
TransactionReceipt receipt = blockchainService.saveEvidence(
    "WITHDRAW",
    String.valueOf(request.getId()) + ":" + actionType, // bizId 包含操作类型
    requestHash
);
```

### 哈希计算
```java
// WithdrawService.java:322-323
String data = String.format("%d|%d|%d|%s|%s|%s|%s",
    requestId, userId, amount, method, status, actionType, LocalDateTime.now().toString());
// 使用 SHA-256 计算哈希
```

### 数据库字段
- `withdraw_requests.request_hash` - 提现申请哈希
- `withdraw_requests.chain_status` - 上链状态
- `withdraw_requests.chain_tx_hash` - 最新交易哈希
- `withdraw_requests.block_height` - 区块高度
- `withdraw_requests.block_time` - 区块时间

---

## 🔧 区块链服务（BlockchainService）

### 核心方法

#### 1. 通用存证方法
```java
public TransactionReceipt saveEvidence(String bizType, String bizId, String dataHash)
```
- 所有业务类型的基础上链方法
- 调用智能合约的 `addEvidence()` 函数
- 返回交易回执

#### 2. 榜单冻结方法
```java
public TransactionReceipt freezeLeaderboard(String competitionId, String merkleRoot, String uri)
```
- 内部调用 `saveEvidence("LEADERBOARD", competitionId, merkleRoot)`

#### 3. 奖金批次方法
```java
public TransactionReceipt recordPrizeBatch(String batchNo, String winnersRoot, Long amount, String currency, String uri)
```
- 内部调用 `saveEvidence("PRIZE_BATCH", batchNo, winnersRoot)`

#### 4. 查询方法（需要合约部署后重新生成 Java 包装类）
- `getEvidenceByHash()` - 查询存证详情 ✅
- `verifyEvidence()` - 验证存证是否存在 ✅
- `getEvidencesByUploader()` - 查询用户所有存证 ⚠️ 占位实现
- `getEvidencesByBizType()` - 按业务类型查询 ⚠️ 占位实现
- `getEvidenceCount()` - 获取存证总数 ⚠️ 占位实现
- `getEvidenceList()` - 分页查询存证 ⚠️ 占位实现
- `batchVerifyEvidence()` - 批量验证 ⚠️ 占位实现
- `updateEvidenceMetadata()` - 更新元数据 ⚠️ 占位实现

---

## 📦 链上存证记录（chain_proofs 表）

所有上链操作都会在 `chain_proofs` 表中创建存证记录：

| 字段 | 说明 | 示例 |
|-----|------|------|
| `biz_type` | 业务类型 | SUBMISSION/LEADERBOARD/PRIZE_BATCH/EVALUATION/WITHDRAW |
| `biz_id` | 业务ID | 提交ID/竞赛ID/批次ID/评测ID/提现申请ID |
| `data_hash` | 数据哈希 | 文件哈希/Merkle Root/结果哈希 |
| `tx_hash` | 交易哈希 | 0x1234...abcd |
| `block_height` | 区块高度 | 12345 |
| `block_time` | 区块时间 | 2025-10-23 18:00:00 |
| `metadata` | 元数据 | JSON格式的详细信息 |
| `status` | 状态 | 2-已上链 |

---

## 🔐 防篡改机制

### 1. 文件哈希验证
- 提交作品：计算文件 SHA-256 哈希
- 评测结果：计算结果数据哈希
- 提现申请：计算申请数据哈希

### 2. Merkle Tree 验证
- 榜单冻结：榜单数据 Merkle Root
- 奖金批次：获奖者列表 Merkle Root

### 3. 链上时间戳
- 所有上链操作都记录 `block.timestamp`
- 不可回溯修改

### 4. 状态追踪
- 提现申请：记录完整的状态变更链
- 每次状态变更都单独上链

---

## 🚀 部署清单

### 合约部署前
- [x] 5个业务类型的上链功能已实现
- [x] BlockchainService 已完善
- [x] 智能合约已更新（支持5种业务类型）
- [x] 合约文档已更新

### 合约部署后（必做）
1. 部署 `EvidenceContract.sol` 到 FISCO BCOS 节点
2. 使用 `sol2java.sh` 工具重新生成 `EvidenceContract.java`
3. 替换旧的 Java 包装类
4. 更新 `application.yml` 中的合约地址
5. 测试所有查询方法

详细步骤见：`CONTRACT_REGENERATION_GUIDE.md`

---

## 📊 上链数据统计

### 上链频率估算
- **SUBMISSION**: 每次提交 1次上链（高频）
- **EVALUATION**: 每次评测 1次上链（高频）
- **LEADERBOARD**: 每个竞赛 1次上链（低频）
- **PRIZE_BATCH**: 每个竞赛 1次上链（低频）
- **WITHDRAW**: 每次提现 1-5次上链（中频，取决于状态变更次数）

### Gas 成本优化
- 所有上链操作使用 **异步处理**（@Async）
- 避免阻塞用户操作
- 上链失败不影响业务主流程（除关键操作外）

---

## ✅ 测试验证

### 单元测试清单
- [ ] 提交作品上链测试
- [ ] 榜单冻结上链测试
- [ ] 评测结果上链测试
- [ ] 奖金批次上链测试
- [ ] 提现申请上链测试（多状态）

### 集成测试清单
- [ ] 端到端提交流程测试（预检 → 上链 → 评测 → 上链）
- [ ] 榜单冻结 → Merkle Root 验证测试
- [ ] 奖金批次 → 入账 → 上链完整流程测试
- [ ] 提现申请 → 审核 → 支付 → 多次上链测试

### 查询测试清单（部署后）
- [ ] 根据哈希查询存证
- [ ] 验证存证是否存在
- [ ] 查询用户所有存证
- [ ] 按业务类型查询存证
- [ ] 分页查询存证列表
- [ ] 批量验证存证

---

**最后更新**：2025-10-23
**状态**：✅ 所有上链功能已实现，等待合约部署
**下一步**：部署智能合约并重新生成 Java 包装类
