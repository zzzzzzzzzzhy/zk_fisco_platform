# 竞赛平台智能合约部署文档

## 合约说明

### EvidenceContract.sol
竞赛平台区块链存证智能合约，用于记录和验证竞赛相关的关键数据。

**支持的业务类型**：
- `SUBMISSION` - 提交作品存证
- `LEADERBOARD` - 榜单快照存证
- `PRIZE_BATCH` - 奖金批次存证
- `EVALUATION` - 评测结果存证
- `WITHDRAW` - 提现申请存证

**核心功能**：
1. 添加存证 - `addEvidence()`
2. 查询存证 - `getEvidenceByHash()`
3. 验证存证 - `verifyEvidence()`
4. 分页查询 - `getEvidenceList()`
5. 业务类型查询 - `getEvidencesByBizType()`
6. 用户存证查询 - `getEvidencesByUploader()`
7. 批量验证 - `batchVerifyEvidence()`

---

## 部署步骤

### 前提条件

1. **FISCO BCOS 节点已部署**
   - 节点版本：2.9.x 或更高
   - 群组：group1 (默认)
   - 确保节点可访问：`127.0.0.1:20200` 和 `127.0.0.1:20201`

2. **控制台工具**
   - 下载FISCO BCOS控制台：https://fisco-bcos-documentation.readthedocs.io/zh_CN/latest/docs/installation.html
   - 配置控制台连接到你的节点

3. **Solidity编译器**
   - 版本：0.4.25 (合约中指定)
   - FISCO BCOS控制台自带编译器

---

### 方法一：使用FISCO BCOS控制台部署

#### 步骤1：启动控制台
```bash
cd ~/fisco/console
bash start.sh
```

#### 步骤2：部署合约
```bash
# 在控制台中执行
[group:1]> deploy EvidenceContract
```

#### 步骤3：记录合约地址
部署成功后，控制台会显示合约地址，类似：
```
contract address: 0xa0f88385434996c25d432464ffca1cedabaab5e0
```

**重要**：将此地址复制并更新到 `application.yml` 配置文件：
```yaml
fisco:
  contract-address: 0xa0f88385434996c25d432464ffca1cedabaab5e0  # 替换为实际地址
```

#### 步骤4：验证部署
```bash
# 调用合约查询函数验证
[group:1]> call EvidenceContract 0xa0f88385434996c25d432464ffca1cedabaab5e0 getEvidenceCount
```

---

### 方法二：使用Java SDK部署（代码部署）

#### 步骤1：编译合约
使用FISCO BCOS控制台编译：
```bash
# 在控制台中编译合约
[group:1]> deploy EvidenceContract
```

这会生成：
- `EvidenceContract.abi` - 合约ABI
- `EvidenceContract.bin` - 合约字节码

#### 步骤2：生成Java包装类
```bash
# 使用控制台工具生成Java合约包装类
cd ~/fisco/console
bash sol2java.sh -p com.wereen.competitionplatform.contracts -s contracts/EvidenceContract.sol
```

生成的 `EvidenceContract.java` 应放置在：
```
backend/src/main/java/com/wereen/competitionplatform/contracts/
```

#### 步骤3：代码部署（可选）
在Java代码中部署合约：
```java
@Service
public class ContractDeployService {
    @Autowired
    private Client fiscoBcosClient;

    @Autowired
    private CryptoKeyPair cryptoKeyPair;

    public String deployContract() throws Exception {
        EvidenceContract contract = EvidenceContract.deploy(
            fiscoBcosClient,
            cryptoKeyPair
        );

        String contractAddress = contract.getContractAddress();
        log.info("合约部署成功，地址：{}", contractAddress);

        return contractAddress;
    }
}
```

---

## 配置说明

### application.yml 配置项

```yaml
# FISCO BCOS configuration
fisco:
  config-file: config.toml                                              # 节点配置文件
  group-id: 1                                                          # 群组ID
  contract-address: 0xa0f88385434996c25d432464ffca1cedabaab5e0         # 合约地址（部署后填写）
  current-account: 0x9d6037dcc8b3253c3f2a295eeac2d9fde804543c          # 当前账户地址
```

### config.toml 配置示例

```toml
[cryptoMaterial]
certPath = "conf"                # 证书路径
useSMCrypto = false              # 是否使用国密

[network]
peers = ["127.0.0.1:20200", "127.0.0.1:20201"]  # 节点列表
defaultGroup = "group1"                          # 默认群组

[threadPool]
threadPoolSize = 16              # 线程池大小
```

---

## 合约调用示例

### 1. 添加存证
```java
// 提交作品存证
blockchainService.saveEvidence(
    "SUBMISSION",                    // 业务类型
    "123",                          // 业务ID（提交ID）
    "e3b0c44298fc1c149afbf4c..."    // 数据哈希（文件SHA256）
);
```

### 2. 查询存证
```java
// 根据哈希查询存证详情
String evidenceJson = blockchainService.getEvidenceByHash(
    "e3b0c44298fc1c149afbf4c..."
);

// 返回JSON:
// {
//   "dataHash": "e3b0c44298fc1c...",
//   "bizType": "SUBMISSION",
//   "bizId": "123",
//   "uploader": "456",
//   "metadata": "{...}",
//   "timestamp": 1698123456
// }
```

### 3. 验证存证
```java
// 验证存证是否存在
boolean exists = blockchainService.verifyEvidence(
    "e3b0c44298fc1c149afbf4c..."
);
```

### 4. 查询用户存证列表
```java
// 查询某用户的所有存证
List<String> hashes = blockchainService.getEvidencesByUploader("456");
```

### 5. 批量验证
```java
// 批量验证多个哈希
List<String> hashes = Arrays.asList("hash1", "hash2", "hash3");
List<Boolean> results = blockchainService.batchVerifyEvidence(hashes);
```

---

## 测试验证

### 1. 单元测试合约功能
```java
@Test
public void testAddEvidence() {
    TransactionReceipt receipt = blockchainService.saveEvidence(
        "TEST",
        "1",
        "test-hash-123"
    );

    assertNotNull(receipt);
    assertEquals("0x0", receipt.getStatus());  // 0表示成功
}

@Test
public void testQueryEvidence() {
    // 先添加存证
    blockchainService.saveEvidence("TEST", "1", "test-hash-123");

    // 查询存证
    String result = blockchainService.getEvidenceByHash("test-hash-123");
    assertNotNull(result);
    assertTrue(result.contains("TEST"));
}
```

### 2. 验证区块链连接
```java
@Test
public void testBlockchainConnection() {
    Long blockNumber = blockchainService.getBlockNumber();
    assertTrue(blockNumber > 0);
    log.info("当前区块高度：{}", blockNumber);
}
```

---

## 常见问题

### Q1：合约部署失败
**原因**：节点连接失败或证书配置错误

**解决方法**：
1. 检查节点是否运行：`ps -ef | grep fisco-bcos`
2. 检查节点端口是否可访问：`telnet 127.0.0.1 20200`
3. 验证证书文件是否存在：`ls -l backend/src/main/resources/conf/`

### Q2：交易上链失败
**错误示例**：`TransactionReceipt status = 0x16 (22)`

**常见原因和解决方法**：
- `0x16` - 权限不足，需要配置账户权限
- `0x17` - Gas不足，检查节点配置
- `0x18` - 合约执行失败，检查参数是否正确

### Q3：查询返回空值
**原因**：合约地址配置错误或数据未上链成功

**解决方法**：
1. 确认 `application.yml` 中的合约地址正确
2. 检查交易回执的status字段（应为0x0）
3. 查看区块链日志：`tail -f ~/fisco/nodes/127.0.0.1/node0/log/log_*.log`

### Q4：如何更新已部署的合约？
**注意**：智能合约部署后无法修改代码

**方案**：
1. 部署新版本合约（得到新地址）
2. 更新 `application.yml` 中的合约地址
3. 旧合约数据仍然保留在链上，可以查询

---

## 安全建议

1. **私钥管理**
   - ❌ 不要在配置文件中明文存储私钥
   - ✅ 使用环境变量或密钥管理服务
   - ✅ 生产环境使用硬件安全模块（HSM）

2. **合约权限**
   - 部署后验证合约owner是否正确
   - 使用 `transferOwnership()` 转移所有权时需谨慎
   - 紧急情况可使用 `emergencyStop()` 暂停合约

3. **数据验证**
   - 上链前验证数据哈希格式
   - 不要上传敏感信息到区块链
   - 元数据应脱敏处理

---

## 监控和维护

### 监控指标
- 合约调用成功率
- 平均上链耗时
- 区块链节点同步状态
- 交易失败统计

### 日志位置
- 应用日志：`backend/logs/`
- 区块链节点日志：`~/fisco/nodes/127.0.0.1/node0/log/`

### 定期检查
- 区块高度增长是否正常
- 磁盘空间是否充足
- 证书是否即将过期

---

## 联系支持

- FISCO BCOS官方文档：https://fisco-bcos-documentation.readthedocs.io/
- GitHub Issues：https://github.com/FISCO-BCOS/FISCO-BCOS
- 微信群：WeBank-Blockchain

---

**最后更新时间**：2025-10-22
**合约版本**：v1.0
**兼容节点版本**：FISCO BCOS 2.9.x+
