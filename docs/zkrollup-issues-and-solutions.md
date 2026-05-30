# ZK-Rollup 问题排查与解决方案

**日期**: 2026-01-06
**状态**: 🔴 Groth16 验证失败 - 需要重新生成合约

---

## 📋 问题描述

### 核心问题
ZK-Rollup 批次提交时，Groth16 证明验证失败：
- **错误码**: `0x439cc0cd` → `VerificationFailed()`
- **Gas 消耗**: 261,522 (交易失败但仍消耗 gas)
- **影响**: 无法将 rollup 批次提交到链上

### 错误表现
```solidity
// 合约调用
verifier.verify(seal, imageId, journalDigest);

// 返回
Error: VerificationFailed()
```

---

## 🔍 已检查项（✅ 全部通过）

### 1. 合约配置 ✅
- **RiscZeroGroth16Verifier**: `0x59de2C69DBFCc0155730d7F8F0a2E3ee558dF15A` (新部署)
- **ContentRollupRegistry**: `0x0042E3f232c92E446971d851194D52CdCf920eEA` (新部署)
- **Image ID**: `0x06a4c68ab8cc1f68c73a8209c50eb5d7e576a19340e8242c3d2e2dc1921365b9`

### 2. Image ID 匹配 ✅
```
Prover Image ID:   0x06a4c68ab8cc1f68c73a8209c50eb5d7e576a19340e8242c3d2e2dc1921365b9
Contract Image ID: 0x06a4c68ab8cc1f68c73a8209c50eb5d7e576a19340e8242c3d2e2dc1921365b9
✅ 匹配
```

### 3. Journal Digest 计算 ✅
```javascript
// Guest commit 格式
(batch_word, merkle_root, count_word, start_word, end_word)

// JavaScript 计算
const digest = sha256(abi.encode(
    batchId, merkleRoot, count, windowStart, windowEnd
));

// Rust guest 计算
env::commit(&(batch_word, merkle_root, count_word, start_word, end_word));

✅ 两者完全匹配: 0x59327e5dc9d974e2550434888ac54e45c4b2ba74705aedbbc043d87e1e985cf8
```

### 4. Selector 匹配 ✅
```
Seal Selector:    0x73c457ba
Contract Selector: 0x73c457ba
✅ 匹配
```

Selector 是基于以下参数计算的：
- Control Root
- BN254 Control ID
- Verifier Key Digest

### 5. 编码方式 ✅
- **encode_seal**: 260 bytes (正确)
- **bincode**: 457 bytes (不用于合约)
- 使用 `risc0-ethereum-contracts::encode_seal` ✅

### 6. 测试数据 ✅
- 使用真实 CHECKIN 事件（6条）
- Merkle Root: `0x1ebea49cc6473b6e5c56305bbf5179f6c63d6d6c08d68b339aed05d5ff893640`
- Batch ID: `32026010523`
- Window: 1767655008 → 1767666008

---

## ❌ 失败的解决方案

### 尝试 1: 重新部署合约
```bash
# 部署新的 Verifier 和 Registry
npx hardhat run scripts/deploy-risc0-verifier.js --network amoy
npx hardhat run scripts/deploy-rollup-registry.js --network amoy

# 结果: ❌ 还是 VerificationFailed()
```

### 尝试 2: 更改编码方式
```rust
// 尝试 bincode 序列化
let groth16 = snark_receipt.inner.groth16().unwrap();
let seal = bincode::serialize(groth16).unwrap(); // ❌ 457 bytes

// 尝试 encode_seal
let seal = encode_seal(&snark_receipt).unwrap(); // ❌ 260 bytes

// 结果: ❌ 两种方式都失败
```

### 尝试 3: 测试其他证明
```bash
# 测试 merkle_verify 证明
❌ VerificationFailed()

# 测试 whitelist_prove 证明
❌ VerificationFailed()

# 结论: 不是 rollup 特有问题，而是整个 Groth16 设置有问题
```

---

## 🎯 根本原因分析

### 最可能的原因
**Groth16Verifier.sol 不是从当前版本的 prover 正确生成的**

验证逻辑：
```solidity
// RiscZeroGroth16Verifier.sol:159-183
function _verifyIntegrity(bytes calldata seal, bytes32 claimDigest) internal view {
    // 1. Selector 检查 ✅ 通过
    if (SELECTOR != bytes4(seal[:4])) {
        revert SelectorMismatch(...);
    }

    // 2. Groth16 数学验证 ❌ 失败
    bool verified = this.verifyProof(
        decodedSeal.a,
        decodedSeal.b,
        decodedSeal.c,
        [CONTROL_ROOT_0, CONTROL_ROOT_1, claim0, claim1, BN254_CONTROL_ID]
    );

    if (!verified) {
        revert VerificationFailed(); // ← 我们在这里
    }
}
```

### 可能的子原因
1. **Groth16 Verifying Key 不匹配**
   - 合约中的 IC0-IC5 值与 prover 的 proving key 不一致
   - Groth16Verifier.sol 需要从当前 RISC Zero 版本重新生成

2. **RISC Zero 版本不匹配**
   - Prover: `risc0-zkvm = "3.0"`
   - Contracts: 可能来自旧版本或不同分支

3. **Control Root/ID 配置问题**
   - 虽然 selector 匹配，但实际参数可能仍有差异

4. **批次 ID 不一致（已定位）**
   - 元数据里的 `batchId` 使用了 `yyyyMMddHH`（短 ID），而链上提交使用完整 ID：
     `typeId * 10_000_000_000 + yyyyMMddHH`
   - prover 生成证明时读取元数据里的 `batchId`，导致 journalDigest 与合约侧不一致
   - 结果：Groth16 验证失败（claimDigest 不匹配）

5. **Journal Digest 计算方式错误（已定位）**
   - Guest 使用 `env::commit` 输出 `([u8;32], [u8;32], [u8;32], [u8;32], [u8;32])`
   - RISC0 Journal 序列化会将每个 `u8` 扩展为 little-endian `u32`（每字节占 4 字节）
   - 实际 Journal 长度为 640 bytes（5 * 32 * 4）
   - 后端用 `abi.encode(...)` 计算得到的 digest 与实际不一致
   - 结果：验证失败（VerificationFailed）

6. **Image ID 取值错误（已定位）**
   - `methods` 中的 `ROLLUP_BATCH_ID` 转成 hex 后不是实际的 `preState` digest
   - 真实的 imageId 应以 Receipt 的 `pre` digest 为准（`ROLLUP_CLAIM_PRE`）
   - 结果：链上 `ReceiptClaim.ok(imageId, journalDigest)` 与证明不一致

---

## ✅ 解决方案

### 方案 0: 修复 batchId 不一致（优先）
已在后端修复：元数据中存储完整 `batchId`，并新增 `batchIdText` 用于展示。

涉及文件：
- `backend/src/main/java/com/wereen/competitionplatform/service/RewardEventRollupService.java`
- `backend/src/main/java/com/wereen/competitionplatform/service/RewardRollupSubmitService.java`

操作建议：
1. 删除旧证明文件（旧 batchId 生成的 proof 无效）
2. 重新生成 proof 并提交

### 方案 0.5: 修复 Journal Digest 计算
后端需用 RISC0 Journal 的真实序列化规则计算 digest：
- 每个 32-byte 字段按 byte 展开，每个 byte 用 `u32` little-endian 表示
- 最终 `sha256(journal_bytes)` 作为 journalDigest

已修复文件：
- `backend/src/main/java/com/wereen/competitionplatform/service/RewardEventRollupService.java`

### 方案 0.6: 修复 Image ID
用 Receipt 的 `pre` digest 作为 imageId（而不是 `ROLLUP_BATCH_ID` 的 hex）：
- `rollup-prove` 输出 `ROLLUP_CLAIM_PRE`/`ROLLUP_IMAGE_ID`，部署合约用该值
- 需要用新 imageId 重新部署 `ContentRollupRegistry`

### 方案 1: 重新生成 Groth16 合约（推荐）

#### 步骤 1: 安装 RISC Zero 工具
```bash
RUST_DIR=${RUST_DIR:-/path/to/competition-platform/rust}
cd "$RUST_DIR"
cargo install risc0-zkvm
cargo install risc0-build
```

#### 步骤 2: 生成 Groth16 合约
```bash
# 方法 A: 使用 xtask
cargo xtask bootstrap-groth16

# 方法 B: 使用 risc0-build
cargo risc0-build

# 生成的文件在:
# - target/generated/risc0/groth16/ControlID.sol
# - target/generated/risc0/groth16/Groth16Verifier.sol
# - target/generated/risc0/groth16/RiscZeroGroth16Verifier.sol
```

#### 步骤 3: 更新合约文件
```bash
# 复制到项目
cp target/generated/risc0/groth16/*.sol \
   /data/Dapp_Share_Platform/competition-platform/blockchain/contracts/risc0/groth16/
```

#### 步骤 4: 重新部署
```bash
cd /data/Dapp_Share_Platform/competition-platform/blockchain

# 部署 Verifier
npx hardhat run scripts/deploy-risc0-verifier.js --network amoy

# 部署 Registry
npx hardhat run scripts/deploy-rollup-registry.js --network amoy

# 更新配置
# 编辑 backend/.env.amoy
```

---

### 方案 2: 使用官方预生成的合约

#### 从 RISC Zero 官方仓库获取
```bash
# 克隆 RISC Zero 仓库（如果还没有）
git clone https://github.com/risc0/risc0.git
cd risc0
git checkout release-3.0

# 复制 Groth16 合约
cp risc0-ethereum/contracts/groth16/*.sol \
   /data/Dapp_Share_Platform/competition-platform/blockchain/contracts/risc0/groth16/
```

#### 版本对应关系
```
risc0-zkvm: 3.0.x
risc0-ethereum-contracts: 3.0.1
Contracts: release-3.0 branch
```

---

### 方案 3: 暂时使用 STARK 证明（临时方案）

**适用场景**: 快速验证整个 rollup 流程是否工作（gas 会高很多）

#### 修改 rollup_prove.rs
```rust
// 不压缩到 Groth16，直接使用 STARK
let receipt = prover.prove(env, ROLLUP_BATCH_ELF).unwrap().receipt;
let seal = receipt.journal.bytes; // STARK 证明（很大）
```

#### 修改合约接口
```solidity
// 接受 STARK receipt
function submitSTARK(
    bytes calldata starkProof,
    bytes32 imageId,
    ...
) external onlyOwner {
    // 直接验证 STARK
    verifier.verifySTARK(starkProof, ...);
}
```

**缺点**:
- Gas 费用高（可能几百万 vs 26万）
- 证明体积大（几百 KB vs 260 bytes）

---

## 📊 当前配置信息

### RISC Zero 版本
```toml
# $RUST_DIR/host/Cargo.toml
[dependencies]
risc0-zkvm = { version = "3.0", features = ["prove"] }
risc0-ethereum-contracts = "3.0.1"
```

### RPC 配置
```javascript
// /data/Dapp_Share_Platform/competition-platform/blockchain/hardhat.config.js
amoy: {
  url: "https://rpc-amoy.polygon.technology/",
  chainId: 80002,
  gas: 6000000,
  gasPrice: 30000000000 // 30 gwei
}
```

**AMOY RPC**: 官方公共 RPC
- URL: https://rpc-amoy.polygon.technology/
- 优点: 免费，无需申请
- 缺点: 可能有速率限制

**备用 RPC**:
```javascript
// Infura/Alchemy（推荐用于生产）
url: process.env.AMOY_RPC_URL || "https://rpc-amoy.polygon.technology/"

// 其他公共 RPC
// - https://amoy.blockpi.network/v1/rpc/public
// - https://polygon-amoy.blockpi.network/v1/rpc/public
```

### 部署的合约（测试网）

#### 主网配置
```
Verifier: 0x2F2C744Cca5503157ea7CDE08368372567dCBa39
Registry: 0xdD3975937686728A2412065bABe806F1Ed80b09f
Network: Polygon Mainnet (Chain ID: 137)
```

#### 测试网配置（Amoy）
```
Verifier: 0x59de2C69DBFCc0155730d7F8F0a2E3ee558dF15A ⭐ 新部署
Registry: 0x0042E3f232c92E446971d851194D52CdCf920eEA ⭐ 新部署
Network: Polygon Amoy (Chain ID: 80002)

Control Root: 0xa54dc85ac99f851c92d7c96d7318af41dbe7c0194edfcc37eb4d422a998c1f56
BN254 Control ID: 0x04446e66d300eb7fb45c9726bb53c793dda407a62e9601618bb43c5c14657ac0
```

---

## 🛠️ 调试工具

### 检查 Selector
```bash
cd /data/Dapp_Share_Platform/competition-platform/blockchain
npx hardhat run scripts/check-selector-fixed.js --network amoy
```

### 验证证明
```bash
npx hardhat run scripts/check-verifier-direct.js --network amoy
```

### 测试 Digest 计算
```bash
npx hardhat run scripts/compute_journal.js --network amoy
```

---

## 📝 下一步行动

### 立即执行（按优先级）
1. ⭐ **重新生成 Groth16 合约** (方案 1)
   ```bash
   RUST_DIR=${RUST_DIR:-/path/to/competition-platform/rust}
   cd "$RUST_DIR"
   cargo xtask bootstrap-groth16
   ```

2. 更新合约文件到项目

3. 重新部署到 Amoy 测试网

4. 用 rollup_prove 生成新证明并测试

5. 如果成功，部署到主网

### 如果方案 1 失败
6. 使用官方预生成合约 (方案 2)

7. 或临时使用 STARK 证明验证流程 (方案 3)

---

## 📚 相关文件

### 核心文件
```
Rust Prover:
  $RUST_DIR/host/src/rollup_prove.rs
  $RUST_DIR/target/release/rollup-prove
  $RUST_DIR/methods/guest/src/rollup.rs

Solidity 合约:
  /data/Dapp_Share_Platform/competition-platform/blockchain/contracts/
    - ContentRollupRegistry.sol
    - contracts/risc0/groth16/RiscZeroGroth16Verifier.sol
    - contracts/risc0/groth16/ControlID.sol
    - contracts/risc0/IRiscZeroVerifier.sol

配置:
  /data/Dapp_Share_Platform/competition-platform/backend/.env.amoy
  /data/Dapp_Share_Platform/competition-platform/blockchain/hardhat.config.js
```

### 日志和数据
```
测试证明:
  /tmp/check_image.bin (rollup 证明)
  /tmp/combined_metadata.json (测试数据)
  $RUST_DIR/host/snark_seal.bin (merkle 证明)
  $RUST_DIR/host/whitelist_seal.bin (whitelist 证明)
```

---

## 💡 经验教训

### ✅ 做对的事情
1. 使用 `encode_seal` 而不是 `bincode` 序列化
2. 检查 Selector 匹配（这是关键的第一步验证）
3. 从多个角度验证（digest、image ID、selector）
4. 测试其他证明类型（merkle、whitelist）以排除 rollup 特有问题

### ❌ 遇到的坑
1. **Groth16Verifier.sol 版本问题**
   - 不能使用旧版本或不同分支的合约
   - 必须与 prover 版本完全匹配

2. **编码方式混淆**
   - `bincode::serialize(groth16)` → 457 bytes → 用于 Rust 内部
   - `encode_seal(&snark_receipt)` → 260 bytes → 用于 Solidity

3. **主网和测试网配置混淆**
   - 确保使用正确的 `.env.amoy` 配置
   - 不要混用主网和测试网合约地址

---

## 🔗 参考资料

### RISC Zero 文档
- Groth16 on Ethereum: https://dev.risczero.com/api/risc0-ethereum/ethereum
- Contract Guide: https://dev.risczero.com/api/risc0-ethereum/ethereum
- GitHub Issues: https://github.com/risc0/risc0/issues

### RISC Zero 版本
- Current Release: https://github.com/risc0/risc0/releases
- Version 3.0 API: https://dev.risczero.com/api/

---

## 历史问题（已归档）

### 早期实现问题
- 证明生成依赖外部 prover 命令与二进制文件，未配置时自动跳过
- proof 文件缺失或生成失败会直接跳过提交
- 链上提交与奖励发放依赖合约地址、管理员私钥、RPC 等配置
- Polygon 上链 gas 配置偏低，容易导致交易 pending/失败

### 历史解决方案（Phase 1-4）
已记录在早期版本文档中，当前版本已解决大部分问题。

---

**更新日期**: 2026-01-06
**维护者**: Claude Code Assistant
**状态**: 🔴 等待 Groth16 合约重新生成
