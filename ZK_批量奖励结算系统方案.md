# ZK 批量奖励结算系统方案（降本增效）

目标：将高频奖励（发贴、评论、签到等）从“每次上链”改为“链下记账 + ZK 批量结算 + 链上领取”，显著降低 Gas 与链上拥堵。

## 1. 背景与痛点

现状：每次奖励触发都会调用链上合约（`rewardPost` / `checkin` 等）。

问题：
- **Gas 成本高**：高频动作直接变成高频交易。
- **TPS 瓶颈**：链上并发受限。
- **RPC 压力大**：集中时段容易拥堵。

## 2. 方案核心思路

**链下记账，ZK 证明，链上批量结算**（Rollup 风格）：

1. 用户行为写入“待结算池”（DB/Redis）。
2. 定时批处理（例如 24h）生成 ZK 证明。
3. 链上只提交 **1 笔交易**，记录本批次的 Merkle Root。
4. 用户用 Merkle Proof 领取奖励。

## 3. 系统架构概览

### 3.1 数据流

```
用户签到/评论/发帖
      |
      v
后端收集（写入待结算池）
      |
      v
ZK Prover 批处理（校验签名+计算奖励）
      |
      v
链上提交：proof + merkle root
      |
      v
用户凭 merkle proof 领取奖励
```

### 3.2 关键组成

- **RewardPool（待结算池）**：存储高频奖励事件（含用户签名）。
- **ZK Prover**：验证签名、计算奖励、输出 Merkle Root + Proof。
- **Settlement 合约**：验证 ZK 证明，记录批次 Merkle Root。
- **Claim 流程**：用户自助领取。

## 4. 业务流程设计

### 4.1 用户行为记录（链下）

行为发生时：
- 后端不再直接上链。
- 记录事件到数据库（或 Redis 后异步落库）。

建议字段：
- `event_id`
- `user_address`
- `event_type`（checkin/comment/post）
- `event_payload`
- `event_time`
- `user_signature`
- `status`（pending/settled/invalid）

### 4.2 批处理结算（链下）

触发方式：
- 定时任务（例如每天 02:00）
- 或按数量阈值（例如 1 万条触发）

批处理逻辑：
1. 拉取 `pending` 事件。
2. 验证用户签名。
3. 计算奖励金额。
4. 生成 Merkle Tree（叶子: `user_address + amount`）。
5. 生成 ZK Proof（证明签名校验 + 规则计算 + root 正确）。
6. 生成批次记录（batchId）。

### 4.3 链上结算

后端提交 1 笔交易：
- `batchId`
- `merkleRoot`
- `proof`

合约验证：
1. 验证 proof。
2. 记录 `batchId -> merkleRoot`。

### 4.4 用户领取

用户领取时：
1. 前端获取 Merkle Proof（服务端提供查询接口）。
2. 调用合约 `claim(batchId, amount, proof)`。
3. 合约验证 Merkle Proof 并转账。

## 5. 合约接口草案

```solidity
function submitBatch(
  uint256 batchId,
  bytes32 merkleRoot,
  bytes calldata proof
) external;

function claim(
  uint256 batchId,
  address user,
  uint256 amount,
  bytes32[] calldata merkleProof
) external;
```

状态维护：
- `batchRoots[batchId]`
- `claimed[batchId][user]`

## 6. ZK Guest 逻辑设计

输入：
- 批次事件列表
- 用户签名
- 奖励规则参数（可固定）

输出：
- Merkle Root
- 公共输入哈希（可选）

证明内容：
- 签名有效
- 奖励金额符合规则
- Root 是所有用户奖励的正确承诺

## 7. 与现有系统的对接点

建议新增模块：

- `RewardEvent` 表（代替直接上链）
- `RewardBatch` 表（记录批次状态、root、proof）
- `RewardClaim` 表（记录用户领取状态）

后端入口：
- 替换 `rewardPost / checkin` 为 `createRewardEvent`。
- 新增 `batchSettle` 任务。
- 新增 `getMerkleProof(user, batchId)` 接口。

## 8. 分阶段实施（建议）

### Phase 1: 无 ZK 的批量结算
- 先实现“链下记账 + 批量 Merkle Root + 用户领取”
- 先不上 ZK，只是链上记录 root

### Phase 2: ZK 加入
- 在批处理环节生成 ZK proof
- 合约增加 `submitBatch(proof)` 验证逻辑

### Phase 3: 全面优化
- 支持多类型奖励合并批次
- 引入惩罚/撤销机制

## 9. 风险与约束

- **ZK 成本**：Proof 生成需要算力与时间，建议先做小批次验证。
- **数据一致性**：批次切分与事件去重要严谨。
- **用户体验**：领取需要用户主动触发，可在前端提示。

## 10. 预期收益

- Gas 降低 99%（1 万笔 -> 1 笔）
- RPC 压力下降
- 可审计性增强（proof + root）

---

如果确认实施，我可以继续输出：
- 数据库表结构草案
- 后端接口清单
- ZK Guest 输入输出格式
- 合约最小实现版本
