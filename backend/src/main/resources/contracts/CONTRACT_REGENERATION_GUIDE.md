# Java合约包装类重新生成指南

## 当前状态说明

⚠️ **重要提示**：`EvidenceContract.java` 中的以下方法当前使用的是**临时占位实现**：

- `getEvidencesByUploader()` - 返回空列表
- `getEvidencesByBizType()` - 返回空列表
- `getEvidenceCount()` - 返回0
- `getEvidenceList()` - 返回空列表
- `batchVerifyEvidence()` - 返回空列表
- `updateEvidenceMetadata()` - 抛出UnsupportedOperationException异常

这些方法**必须在合约部署后重新生成**才能正常工作。

---

## 为什么需要重新生成？

1. Solidity合约已更新（`EvidenceContract.sol`），添加了新的业务字段和方法
2. Java包装类需要与实际部署的合约ABI完全匹配
3. 当前的占位实现只是为了让代码能够编译通过

---

## 重新生成步骤

### 方法一：使用FISCO BCOS控制台工具（推荐）

#### 步骤1：部署合约

```bash
# 启动控制台
cd ~/fisco/console
bash start.sh

# 部署合约
[group:1]> deploy EvidenceContract

# 记录合约地址
# 输出示例: contract address: 0xa0f88385434996c25d432464ffca1cedabaab5e0
```

#### 步骤2：生成Java包装类

```bash
# 在控制台目录执行
cd ~/fisco/console

# 使用sol2java.sh工具生成Java包装类
bash sol2java.sh \
  -p com.wereen.competitionplatform.contracts \
  -s /path/to/EvidenceContract.sol \
  -o /path/to/output

# 参数说明:
# -p: Java包名
# -s: Solidity合约源文件路径
# -o: 输出目录
```

#### 步骤3：替换旧的包装类

```bash
# 复制生成的EvidenceContract.java到项目目录
cp ~/fisco/console/contracts/sdk/java/com/wereen/competitionplatform/contracts/EvidenceContract.java \
   E:/JavaProject/competition-platform/competition-platform/backend/src/main/java/com/wereen/competitionplatform/contracts/
```

#### 步骤4：更新配置文件

```yaml
# 更新 application.yml
fisco:
  contract-address: 0xa0f88385434996c25d432464ffca1cedabaab5e0  # 替换为实际部署的合约地址
```

#### 步骤5：重新编译项目

```bash
cd backend
mvn clean compile
```

---

### 方法二：使用在线工具

如果没有本地控制台环境，可以使用FISCO BCOS的在线工具：

1. 访问：https://remix.ethereum.org/
2. 安装FISCO BCOS插件
3. 编译合约并导出ABI和BIN
4. 使用Java SDK的代码生成工具

---

## 验证重新生成是否成功

### 1. 检查方法签名

生成的`EvidenceContract.java`应包含以下方法：

```java
// 新增的方法应该有完整实现
public List<String> getEvidencesByUploader(String _uploader) throws ContractException
public List<String> getEvidencesByBizType(String _bizType) throws ContractException
public BigInteger getEvidenceCount() throws ContractException
public List<String> getEvidenceList(BigInteger _offset, BigInteger _limit) throws ContractException
public List<Boolean> batchVerifyEvidence(List<String> _fileHashes) throws ContractException
public TransactionReceipt updateEvidenceMetadata(String _dataHash, String _metadata)
```

### 2. 运行单元测试

```java
@Test
public void testGetEvidenceCount() throws Exception {
    // 应该返回实际数量，而不是0
    BigInteger count = contract.getEvidenceCount();
    assertNotNull(count);
}

@Test
public void testGetEvidencesByUploader() throws Exception {
    // 应该返回实际列表，而不是空列表
    List<String> evidences = contract.getEvidencesByUploader("user123");
    assertNotNull(evidences);
}
```

### 3. 检查日志输出

重新生成后，调用这些方法时应该看到实际的区块链交互日志：

```
INFO  BlockchainService - 查询用户存证: uploader=user123
INFO  BlockchainService - 查询用户存证成功: uploader=user123, count=5
```

---

## 常见问题

### Q1: 生成的代码与现有代码不兼容怎么办？

**A**: 备份当前的`EvidenceContract.java`，对比差异后手动合并。主要关注：
- 导入的包是否一致
- 方法签名是否匹配
- 类型转换是否正确

### Q2: 如何确认ABI是否正确？

**A**: 检查生成的`ABI_ARRAY`字段，确保包含所有新方法的定义：

```java
// 应该包含 getEvidencesByBizType、batchVerifyEvidence 等方法
public static final String[] ABI_ARRAY = {
    "[{...\"name\":\"getEvidencesByBizType\"...},{...\"name\":\"batchVerifyEvidence\"...}]"
};
```

### Q3: 重新生成后编译失败怎么办？

**A**: 常见原因：
1. FISCO BCOS SDK版本不匹配 - 检查pom.xml中的SDK版本
2. 缺少依赖 - 确保所有FISCO BCOS依赖都已添加
3. 包名不匹配 - 确认`-p`参数指定的包名正确

---

## 临时解决方案（如果暂时无法重新生成）

如果由于环境限制暂时无法重新生成，可以：

1. **继续使用占位实现**：代码可以编译和运行，但查询功能无效
2. **在BlockchainService中添加降级逻辑**：
   ```java
   public List<String> getEvidencesByUploader(String uploader) {
       try {
           return contract.getEvidencesByUploader(uploader);
       } catch (UnsupportedOperationException e) {
           log.warn("合约包装类尚未重新生成，返回空列表");
           return Collections.emptyList();
       }
   }
   ```

3. **使用数据库查询代替**：临时从`chain_proofs`表查询数据

---

## 检查清单

在合约部署和Java包装类重新生成后，请检查：

- [ ] 合约已成功部署到FISCO BCOS节点
- [ ] 合约地址已更新到`application.yml`
- [ ] Java包装类已通过sol2java工具重新生成
- [ ] 旧的`EvidenceContract.java`已被替换
- [ ] 项目编译成功（`mvn clean compile`）
- [ ] 单元测试通过
- [ ] 所有新方法返回实际数据而非占位值

---

## 相关资源

- FISCO BCOS官方文档：https://fisco-bcos-documentation.readthedocs.io/
- Java SDK文档：https://fisco-bcos-doc.readthedocs.io/zh_CN/latest/docs/sdk/java_sdk/index.html
- sol2java工具说明：https://fisco-bcos-doc.readthedocs.io/zh_CN/latest/docs/sdk/java_sdk/quick_start.html#id6

---

**最后更新**：2025-10-22
**状态**：等待合约部署
**优先级**：高（影响查询功能）
