# 1h Rollup (Rewards) — Polygon Mainnet + RISC Zero

## 1. 目标与范围
- 每 1 小时将奖励事件（内容分享/评论/签到）打包为一次链上提交（测试模式）。
- 链上仅存批次摘要（Merkle Root + count + batchId），不存原始内容。
- 适用：内容分享平台的可验证存证，不追求实时逐条上链。




## 2. 网络与环境
- Network: Polygon Mainnet
- ChainId: 137
- RPC: https://rpc.ankr.com/polygon/eabc8327bb63be01f955e817344bc7c0383409f704b326ff858e81e72e35d6df
- Wallet: 主网账户，需真实 MATIC
  - 运行参数可在 `backend/src/main/resources/application.yml` 中调整

## 3. 批次规则
- 批次标识: batchId = YYYYMMDDHH (建议 UTC+8 固定时间点)
- 时间窗口: [T-1h, T)
- 最小记录字段:
  - userId
  - eventType (CONTENT_SHARE/COMMENT/CHECKIN)
  - bizId
  - signature
  - payload

## 4. 规范化与哈希
- 排序规则: 按事件ID或 createdAt 升序，确保确定性
- 记录哈希:
  leaf = sha256(userId|eventType|bizId|signature|payloadHash|createdAt)
- 批次输出 (public inputs):
  - batchId
  - merkleRoot
  - count

## 5. Guest 程序职责 (Rust / RISC Zero)
- 校验:
  - count > 0
  - createdAt 属于批次窗口
  - shareId 去重
- 构建 Merkle Root
- env::commit(batchId, merkleRoot, count)

## 6. 证明生成流程
1) RISC Zero zkVM 执行 Guest，生成 STARK receipt
2) Groth16 压缩为 SNARK proof (用于链上验证)
3) 输出:
   - snark_proof.bin
   - journal/public_inputs (含 merkleRoot + batchId)

## 7. 合约部署（新增）
- ContentRollupRegistry：验证 Groth16 proof + 记录 batch 摘要
- RollupRewardDistributor：后端统一发放奖励（需先有 batch 上链）
- 部署脚本:
  - blockchain/scripts/deploy-rollup-registry.js
  - blockchain/scripts/deploy-rollup-reward-distributor.js

## 8. 合约设计 (主网)
### 7.1 Verifier 合约
- Groth16 verifier 由 RISC Zero 工具生成并部署

### 7.2 Rollup 合约接口 (建议)
- submitBatch(bytes proof, bytes32 merkleRoot, uint256 batchId, uint256 count)
- 状态:
  - batchId -> merkleRoot
  - batchId -> count
  - batchId -> submitter
  - batchId -> timestamp
- 事件:
  - BatchSubmitted(batchId, merkleRoot, count, submitter)

## 9. 后端批处理流程
1) 定时任务触发 (每小时整点)
2) 拉取奖励事件（内容分享/评论/签到）
3) 生成批次 JSON
4) 生成 proof + journal
5) 调用合约 submitBatch
6) 批次落库 (batchId, merkleRoot, txHash, count)

## 10. 前端/用户验证
- 用户查询奖励事件:
  - 获取 batchId 和 merkleRoot
  - 计算 leaf + Merkle path
  - 验证 leaf 在批次中

## 11. 安全与运维
- 主网不可回滚，需:
  - 白名单提交者或多签
  - 批次去重，防止重复提交
  - 提交前 estimateGas / callStatic
- 记录链上 txHash 与批次元数据

## 12. 参考 JSON Schema (批次输入)
```json
{
  "batchId": "2025010112",
  "windowStart": 1735718400,
  "windowEnd": 1735722000,
  "records": [
    {
      "userId": 2001,
      "eventType": "CONTENT_SHARE",
      "bizId": "share_1001",
      "signature": "0x...",
      "payload": {
        "mediaType": "IMAGE",
        "shareId": 1001
      },
      "createdAt": 1735651300
    }
  ]
}
```

## 13. Rust Guest 模板 (示意)
```rust
#![no_main]
#![no_std]
extern crate alloc;

use alloc::vec::Vec;
use risczero_zkvm::guest::{env, sha};

risczero_zkvm::guest::entry!(main);

pub fn main() {
    let batch_id: u32 = env::read();
    let records: Vec<[u8; 32]> = env::read(); // 已规范化的 leaf 列表

    assert!(!records.is_empty());

    let mut nodes = records;
    while nodes.len() > 1 {
        let mut next = Vec::new();
        let mut i = 0;
        while i < nodes.len() {
            let left = nodes[i];
            let right = if i + 1 < nodes.len() { nodes[i + 1] } else { nodes[i] };
            next.push(sha::hash_pairs(left, right));
            i += 2;
        }
        nodes = next;
    }
    let merkle_root = nodes[0];
    let count = records.len() as u32;

    env::commit(&(batch_id, merkle_root, count));
}
```

## 14. Solidity 接口草案 (示意)
```solidity
function submitBatch(
    bytes calldata proof,
    bytes32 merkleRoot,
    uint256 batchId,
    uint256 count
) external;
```

## 15. 下一步建议
- 确定批次窗口时间规则 (UTC+8 / UTC)
- 选择批次数据落库表结构
- 确认 rollup 合约权限模型 (multisig/owner)
