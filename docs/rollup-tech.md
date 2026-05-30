# 内容分享平台 Rollup 批次上链（主网）技术说明

## 目标
- 内容分享 / 评论 / 签到：前端只做签名确认，不直接上链
- 后端按时间窗口聚合事件，生成 ZK 证明并批量上链
- 批次上链后发放奖励（前端展示批次状态与“可领取”提示）

## 端到端流程
1. **前端签名确认**
   - 内容分享：`POST /content-shares/{id}/consent`
   - 评论：`POST /forum/posts/{postId}/comments/{commentId}/consent`
   - 签到：`POST /checkin/consent`
   - 作用：证明用户真实操作；签名数据存入 `reward_events`

2. **后端批次聚合**
   - 定时任务按窗口抓取 `reward_events`，构建 Merkle Root
   - 生成 `journalDigest`（批次元数据哈希）
   - 写入 `chain_proof` 记录，等待证明生成与上链

3. **ZK 证明生成（RISC Zero Groth16）**
   - 读取批次元数据与 Merkle Root
   - 生成 Groth16 `seal`（proof.bin）

4. **批次上链与发放**
   - `ContentRollupRegistry` 验证 RISC0 Groth16 证明
   - `RollupRewardDistributor` 发放奖励

5. **前端展示**
   - 通过 `GET /rollup/batches` 显示批次窗口与状态
   - “已上链，可领取”即展示可领取提示

## 链上合约
- `ContentRollupRegistry`：验证 Groth16 证明并记录批次
- `RollupRewardDistributor`：根据批次事件发放奖励
- `Groth16 Verifier`：RISC Zero 官方合约

## 关键配置（主网）
后端：`backend/src/main/resources/application.yml`
- `blockchain.rollup.registry-address`
- `blockchain.rollup.reward-distributor-address`
- `blockchain.rollup.image-id`

后端环境：`backend/.env`
- `ROLLUP_VERIFIER_ADDRESS`
- `ROLLUP_IMAGE_ID`
- `ROLLUP_REGISTRY_ADDRESS`
- `ROLLUP_REWARD_DISTRIBUTOR_ADDRESS`
- `POLYGON_RPC_URL`（主网 RPC）

**主网 RPC 示例**
```
POLYGON_RPC_URL=https://rpc.ankr.com/polygon/eabc8327bb63be01f955e817344bc7c0383409f704b326ff858e81e72e35d6df
```

## 时间窗口与频率（可调）
- 不写死在合约里，全部由后端配置控制
- `reward.rollup.window-hours` 控制窗口长度
- `reward.rollup.cron` / `submit-cron` / `distribute-cron` 控制任务频率

示例（每小时 rollup 一次）：
```
reward:
  rollup:
    window-hours: 1
    cron: "0 0 * * * ?"
    submit-cron: "0 */5 * * * ?"
    distribute-cron: "0 */10 * * * ?"
```

## ZK 证明与 ImageId
- `ROLLUP_IMAGE_ID` 来自 RISC Zero guest 的 `image_id`
- `ROLLUP_VERIFIER_ADDRESS` 为 RISC Zero Groth16 Verifier 合约地址
- 证明文件路径由 `reward.rollup.proof-base-dir` 管理

## 前端要点
- 内容分享 / 评论 / 签到只做签名确认
- 不直接发交易（无 Gas）
- 批次上链状态展示：`GET /rollup/batches`

## 备注
- 批次窗口与任务频率可随时调整（无需改合约）
- 切换回旧合约奖励模式时，仅需调整后端配置和调用路径
