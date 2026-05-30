# 竞赛平台榜单管理功能梳理报告

本报告全面梳理竞赛平台榜单管理功能的实现情况，包括已实现功能、未实现功能、数据流程、技术架构和改进建议。

---

## 📊 功能实现总览

### ✅ 已实现的功能

| 功能模块 | 功能点 | 实现位置 | 状态 |
|---------|-------|---------|------|
| **榜单查询** | 获取竞赛实时榜单 | `LeaderboardController:29-33` | ✅ 已实现 |
| **榜单冻结** | 冻结榜单快照（管理员） | `LeaderboardController:38-50` | ✅ 已实现 |
| **冻结榜单查询** | 查询已冻结的榜单 | `LeaderboardController:55-88` | ✅ 已实现 |
| **榜单验证** | 验证榜单数据完整性 | `LeaderboardController:93-97` | ✅ 已实现 |
| **Merkle Tree 验证** | 计算和验证 Merkle Root | `LeaderboardService:69-73, 159-168` | ✅ 已实现 |
| **区块链存证** | 榜单快照上链 | `LeaderboardService:98-102` | ✅ 已实现 |
| **快照数据序列化** | 榜单 JSON 序列化存储 | `LeaderboardService:66` | ✅ 已实现 |

### ❌ 未实现的功能

| 功能模块 | 功能点 | 业务重要性 | 技术难度 | 优先级 |
|---------|-------|-----------|---------|-------|
| **榜单自动更新** | 评测完成后自动更新实时榜单 | ⭐⭐⭐⭐⭐ | 低 | P0 |
| **榜单历史版本** | 查询榜单历史快照列表 | ⭐⭐⭐⭐ | 低 | P1 |
| **榜单公示期** | 榜单冻结后公示期机制 | ⭐⭐⭐⭐ | 中 | P1 |
| **榜单异议处理** | 用户对榜单提出异议 | ⭐⭐⭐⭐ | 中 | P1 |
| **榜单查询条件** | 按时间/版本/状态筛选榜单 | ⭐⭐⭐ | 低 | P2 |
| **榜单排名趋势** | 用户排名变化趋势图 | ⭐⭐⭐ | 中 | P2 |
| **榜单对比** | 对比不同时间点的榜单 | ⭐⭐⭐ | 中 | P2 |
| **榜单导出** | 导出榜单为 Excel/CSV | ⭐⭐⭐ | 低 | P2 |
| **榜单统计分析** | 分数分布、排名波动统计 | ⭐⭐⭐ | 中 | P3 |
| **A/B榜切换** | 公榜/私榜切换机制 | ⭐⭐⭐⭐ | 中 | P1 |
| **榜单刷新限流** | 防止频繁查询榜单 | ⭐⭐⭐⭐ | 低 | P1 |
| **榜单缓存** | Redis 缓存榜单数据 | ⭐⭐⭐⭐ | 低 | P1 |
| **榜单解冻** | 管理员解冻已冻结榜单 | ⭐⭐ | 低 | P3 |
| **榜单备注** | 管理员对榜单添加备注 | ⭐⭐ | 低 | P3 |
| **榜单通知** | 排名变化通知用户 | ⭐⭐⭐ | 中 | P2 |

---

## 🏗️ 当前实现架构

### 1. 数据模型

#### Leaderboard 实体（榜单快照）
```java
// backend/src/main/java/com/wereen/competitionplatform/model/entity/Leaderboard.java
public class Leaderboard {
    private Long id;                    // 主键
    private Long competitionId;         // 竞赛ID
    private String snapshotId;          // 快照ID（版本号）
    private String merkleRoot;          // Merkle Root（防篡改）
    private Integer frozen;             // 是否冻结 (0-否 1-是)
    private Long frozenBy;              // 冻结操作人ID
    private LocalDateTime frozenAt;     // 冻结时间
    private String chainTxHash;         // 链上交易哈希
    private Long blockHeight;           // 区块高度
    private LocalDateTime blockTime;    // 区块时间
    private String leaderboardData;     // 榜单数据（JSON格式）
}
```

**数据库表结构**:
```sql
CREATE TABLE `leaderboards` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `competition_id` BIGINT NOT NULL,
    `snapshot_id` VARCHAR(50) NOT NULL,
    `merkle_root` VARCHAR(128),
    `frozen` TINYINT DEFAULT 0,
    `frozen_by` BIGINT,
    `frozen_at` DATETIME,
    `chain_tx_hash` VARCHAR(128),
    `block_height` BIGINT,
    `block_time` DATETIME,
    `leaderboard_data` LONGTEXT,
    -- 标准字段
    KEY `idx_competition_id` (`competition_id`),
    KEY `idx_snapshot_id` (`snapshot_id`),
    KEY `idx_frozen` (`frozen`)
);
```

#### LeaderboardEntry 数据结构（榜单条目）
```java
// LeaderboardService.LeaderboardEntry
public static class LeaderboardEntry {
    private Integer rank;           // 排名
    private Long userId;            // 用户ID
    private String username;        // 用户名
    private BigDecimal score;       // 得分
    private Long submissionId;      // 提交ID
    private LocalDateTime submitTime; // 提交时间
}
```

### 2. 数据流程

#### 当前榜单数据流（已实现）

```
评测完成
    ↓
[evaluations表存储得分和排名]
    ↓
调用 getLeaderboard(competitionId)
    ↓
[生成模拟榜单数据 ⚠️ 硬编码模拟数据]
    ↓
返回榜单列表（前10名）
```

**⚠️ 关键问题**: 当前榜单数据是**硬编码的模拟数据**，并未从 `evaluations` 表查询真实数据！

```java
// LeaderboardService.java:179-203
private List<LeaderboardEntry> generateMockLeaderboard(Long competitionId) {
    // 生成10条模拟数据
    String[] mockUsers = {"AliceAI", "BobML", "Charlie_DL", ...};

    for (int i = 0; i < 10; i++) {
        LeaderboardEntry entry = new LeaderboardEntry();
        entry.setRank(i + 1);
        entry.setUserId((long) (1000 + i));
        entry.setUsername(mockUsers[i]);
        entry.setScore(BigDecimal.valueOf(95.0 - i * 2.5)); // 模拟得分
        // ...
    }
}
```

#### 榜单冻结流程（已实现）

```
管理员触发冻结
    ↓
1. 检查是否已冻结
    ↓
2. 获取榜单数据（当前为模拟数据）
    ↓
3. 序列化榜单数据为JSON
    ↓
4. 计算 Merkle Root
    ├─ rank|username|score|userId
    └─ SHA-256 → Merkle Tree
    ↓
5. 生成快照ID
    └─ snapshot-{competitionId}-{timestamp}
    ↓
6. 上链存证
    ├─ 调用 BlockchainService.freezeLeaderboard()
    ├─ 数据哈希: Merkle Root
    └─ 元数据: competitionId, snapshotId, recordCount, frozenAt
    ↓
7. 保存到 leaderboards 表
    ├─ frozen = 1
    ├─ merkleRoot
    ├─ chainTxHash
    ├─ blockHeight
    ├─ blockTime
    └─ leaderboardData (JSON)
```

#### 榜单验证流程（已实现）

```
调用 verifyLeaderboard(competitionId)
    ↓
1. 查询冻结的榜单快照
    ↓
2. 解析 leaderboardData (JSON → List<LeaderboardEntry>)
    ↓
3. 重新计算 Merkle Root
    └─ 使用相同的数据格式: rank|username|score|userId
    ↓
4. 对比计算的 Merkle Root 和存储的 Merkle Root
    ↓
5. 返回验证结果 (true/false)
```

### 3. API 接口

| 接口路径 | 方法 | 权限 | 功能 | 实现状态 |
|---------|------|------|------|---------|
| `GET /leaderboards/{competitionId}` | GET | 公开 | 获取竞赛实时榜单 | ✅ 已实现（模拟数据） |
| `POST /leaderboards/{competitionId}/freeze` | POST | 管理员 | 冻结榜单 | ✅ 已实现 |
| `GET /leaderboards/{competitionId}/frozen` | GET | 公开 | 获取冻结榜单 | ✅ 已实现 |
| `GET /leaderboards/{competitionId}/verify` | GET | 公开 | 验证榜单完整性 | ✅ 已实现 |
| `GET /leaderboards/{competitionId}/history` | GET | 公开 | 获取榜单历史快照列表 | ❌ 未实现 |
| `GET /leaderboards/{competitionId}/snapshots/{snapshotId}` | GET | 公开 | 获取指定快照 | ❌ 未实现 |
| `POST /leaderboards/{competitionId}/unfreeze` | POST | 管理员 | 解冻榜单 | ❌ 未实现 |
| `GET /leaderboards/user/{userId}/rank-trend` | GET | 用户 | 用户排名趋势 | ❌ 未实现 |
| `GET /leaderboards/{competitionId}/export` | GET | 管理员 | 导出榜单 | ❌ 未实现 |

---

## 🔍 功能缺失详细分析

### 1. 榜单自动更新（P0 - 最高优先级）

**当前问题**:
- 榜单数据是硬编码的模拟数据，不反映真实评测结果
- 评测完成后，榜单不会自动更新
- 没有从 `evaluations` 表查询真实得分和排名

**应实现的逻辑**:
```java
// 应修改 LeaderboardService.getLeaderboard()
public List<LeaderboardEntry> getLeaderboard(Long competitionId) {
    // 从 evaluations 表查询真实数据
    List<Evaluation> evaluations = evaluationMapper.selectList(
        new LambdaQueryWrapper<Evaluation>()
            .eq(Evaluation::getCompetitionId, competitionId)
            .eq(Evaluation::getStatus, 2) // 评测成功
            .orderByAsc(Evaluation::getRank)
            .last("LIMIT 100") // 返回前100名
    );

    // 转换为 LeaderboardEntry
    return evaluations.stream()
        .map(this::convertToLeaderboardEntry)
        .collect(Collectors.toList());
}
```

**关联改动**:
- `EvaluateConsumer.java` 评测完成后需要更新排名
- 排名算法需要实现（按得分降序排列）

---

### 2. A/B 榜切换机制（P1 - 高优先级）

**业务背景**:
很多竞赛平台采用 A/B 榜机制：
- **A榜（公榜）**: 竞赛期间实时更新，用户可见
- **B榜（私榜）**: 使用另一套测试集，竞赛结束后公布，作为最终排名

**缺失内容**:
- 没有 A/B 榜的区分字段
- 没有榜单类型配置
- 没有 B 榜数据集

**应实现的字段**:
```sql
-- competitions 表增加字段
ALTER TABLE competitions ADD COLUMN `use_ab_leaderboard` TINYINT DEFAULT 0 COMMENT '是否启用A/B榜 (0-否 1-是)';
ALTER TABLE competitions ADD COLUMN `b_leaderboard_publish_time` DATETIME COMMENT 'B榜公布时间';

-- evaluations 表增加字段
ALTER TABLE evaluations ADD COLUMN `leaderboard_type` VARCHAR(10) DEFAULT 'A' COMMENT '榜单类型 (A-公榜 B-私榜)';
```

**应实现的接口**:
```
GET /leaderboards/{competitionId}?type=A  // 查询A榜
GET /leaderboards/{competitionId}?type=B  // 查询B榜（需判断是否已公布）
```

---

### 3. 榜单历史版本管理（P1 - 高优先级）

**当前问题**:
- `getFrozenLeaderboard()` 只能查询**最新一次冻结**的榜单
- 无法查询历史快照列表
- 无法查询指定版本的快照

**应实现的功能**:
```java
// 查询榜单历史快照列表
public List<LeaderboardSnapshot> getLeaderboardHistory(Long competitionId) {
    return leaderboardMapper.selectList(
        new LambdaQueryWrapper<Leaderboard>()
            .eq(Leaderboard::getCompetitionId, competitionId)
            .eq(Leaderboard::getFrozen, 1)
            .orderByDesc(Leaderboard::getFrozenAt)
    );
}

// 查询指定快照
public Leaderboard getLeaderboardBySnapshotId(String snapshotId) {
    return leaderboardMapper.selectOne(
        new LambdaQueryWrapper<Leaderboard>()
            .eq(Leaderboard::getSnapshotId, snapshotId)
    );
}
```

---

### 4. 榜单公示期机制（P1 - 高优先级）

**业务背景**:
榜单冻结后应有公示期，允许用户在公示期内提出异议。

**应实现的字段**:
```sql
ALTER TABLE leaderboards ADD COLUMN `publicity_start_time` DATETIME COMMENT '公示开始时间';
ALTER TABLE leaderboards ADD COLUMN `publicity_end_time` DATETIME COMMENT '公示结束时间';
ALTER TABLE leaderboards ADD COLUMN `publicity_status` VARCHAR(20) DEFAULT 'IN_PUBLICITY' COMMENT '公示状态';
```

**应实现的功能**:
- 冻结榜单时自动设置公示期（如7天）
- 公示期内允许提出异议
- 公示期结束后自动确认榜单

---

### 5. 榜单异议处理（P1 - 高优先级）

**业务场景**:
用户对榜单排名有异议时，可以提交申诉。

**应实现的数据表**:
```sql
CREATE TABLE `leaderboard_appeals` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `leaderboard_id` BIGINT NOT NULL COMMENT '榜单快照ID',
    `user_id` BIGINT NOT NULL COMMENT '申诉用户ID',
    `appeal_type` VARCHAR(50) COMMENT '申诉类型',
    `appeal_reason` TEXT COMMENT '申诉理由',
    `status` VARCHAR(20) DEFAULT 'PENDING' COMMENT '处理状态',
    `reviewer_id` BIGINT COMMENT '审核人ID',
    `review_result` TEXT COMMENT '审核结果',
    `reviewed_at` DATETIME COMMENT '审核时间',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY `idx_leaderboard_id` (`leaderboard_id`),
    KEY `idx_user_id` (`user_id`)
);
```

**应实现的接口**:
```
POST /leaderboards/{competitionId}/appeals       // 提交异议
GET /leaderboards/{competitionId}/appeals        // 查询异议列表
PUT /leaderboards/appeals/{appealId}/review      // 审核异议（管理员）
```

---

### 6. 榜单缓存机制（P1 - 高优先级）

**当前问题**:
- 每次查询榜单都生成模拟数据（实际应从数据库查询）
- 没有缓存，高并发时数据库压力大

**应实现的缓存策略**:
```java
// Redis 缓存键设计
String LEADERBOARD_KEY = "leaderboard:{competitionId}:{type}";
String LEADERBOARD_TTL = 300; // 5分钟过期

// 缓存逻辑
public List<LeaderboardEntry> getLeaderboard(Long competitionId) {
    // 1. 尝试从 Redis 获取
    String cacheKey = String.format("leaderboard:%d:A", competitionId);
    String cachedData = redisTemplate.opsForValue().get(cacheKey);

    if (cachedData != null) {
        return objectMapper.readValue(cachedData,
            new TypeReference<List<LeaderboardEntry>>() {});
    }

    // 2. 从数据库查询
    List<LeaderboardEntry> leaderboard = queryFromDatabase(competitionId);

    // 3. 写入 Redis 缓存
    redisTemplate.opsForValue().set(cacheKey,
        objectMapper.writeValueAsString(leaderboard),
        300, TimeUnit.SECONDS);

    return leaderboard;
}
```

**缓存更新策略**:
- 评测完成后删除缓存
- 定时任务刷新缓存
- 榜单冻结时删除缓存

---

### 7. 榜单导出功能（P2 - 中优先级）

**业务场景**:
管理员需要导出榜单为 Excel/CSV 格式，用于存档或分析。

**应实现的功能**:
```java
public void exportLeaderboard(Long competitionId, HttpServletResponse response) {
    // 1. 查询榜单数据
    List<LeaderboardEntry> leaderboard = getLeaderboard(competitionId);

    // 2. 生成 Excel
    Workbook workbook = new XSSFWorkbook();
    Sheet sheet = workbook.createSheet("榜单");

    // 3. 写入表头和数据
    // ...

    // 4. 输出文件
    response.setContentType("application/vnd.ms-excel");
    response.setHeader("Content-Disposition",
        "attachment; filename=leaderboard-" + competitionId + ".xlsx");
    workbook.write(response.getOutputStream());
}
```

---

### 8. 用户排名趋势（P2 - 中优先级）

**业务场景**:
用户想查看自己在竞赛中的排名变化趋势。

**应实现的数据表**:
```sql
CREATE TABLE `leaderboard_history` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `competition_id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `rank` INT NOT NULL,
    `score` DECIMAL(10,4) NOT NULL,
    `snapshot_time` DATETIME NOT NULL,
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY `idx_competition_user` (`competition_id`, `user_id`),
    KEY `idx_snapshot_time` (`snapshot_time`)
);
```

**应实现的接口**:
```
GET /leaderboards/user/{userId}/rank-trend?competitionId=123
```

**返回数据格式**:
```json
{
    "userId": 456,
    "competitionId": 123,
    "trend": [
        {"time": "2025-10-20 10:00:00", "rank": 15, "score": 85.5},
        {"time": "2025-10-21 10:00:00", "rank": 12, "score": 88.2},
        {"time": "2025-10-22 10:00:00", "rank": 10, "score": 90.1}
    ]
}
```

---

### 9. 榜单刷新限流（P1 - 高优先级）

**业务场景**:
防止用户频繁刷新榜单，造成服务器压力。

**应实现的限流策略**:
```java
// 使用 Guava RateLimiter 或 Redis 限流
@GetMapping("/{competitionId}")
@RateLimiter(limit = 10, period = 60) // 每分钟最多10次
public Result<List<LeaderboardEntry>> getLeaderboard(@PathVariable Long competitionId) {
    // ...
}
```

**限流规则**:
- 普通用户：每分钟最多查询 10 次
- VIP用户：每分钟最多查询 30 次
- 管理员：不限制

---

### 10. 榜单统计分析（P3 - 低优先级）

**业务场景**:
提供榜单的统计分析功能，帮助主办方分析竞赛数据。

**应实现的统计指标**:
- 分数分布直方图
- 排名波动分析
- 提交次数统计
- 用户活跃度分析

**应实现的接口**:
```
GET /leaderboards/{competitionId}/statistics
```

---

## 📈 数据来源分析

### 榜单数据的正确来源链路

```
用户提交作品
    ↓
submissions 表（file_hash, user_id, competition_id）
    ↓
评测引擎评测
    ↓
evaluations 表（score, rank, user_id, competition_id）
    ↓
查询榜单（从 evaluations 表聚合）
    ↓
SELECT
    e.rank,
    e.user_id,
    u.username,
    e.score,
    e.submission_id,
    s.created_at as submit_time
FROM evaluations e
JOIN users u ON e.user_id = u.id
JOIN submissions s ON e.submission_id = s.id
WHERE e.competition_id = ?
  AND e.status = 2
ORDER BY e.rank ASC
LIMIT 100
    ↓
返回榜单数据
```

### 当前数据流（错误）

```
查询榜单
    ↓
生成模拟数据 ⚠️
    ↓
返回硬编码的假数据
```

---

## 🔄 榜单更新时机

### 应实现的更新时机

1. **评测完成后**
   - 触发位置: `EvaluateConsumer.onMessage()` 评测成功后
   - 操作:
     - 更新 `evaluations` 表的 rank 字段
     - 删除 Redis 榜单缓存

2. **手动刷新**
   - 管理员触发全量重新计算排名
   - 用于修正排名错误

3. **定时任务**
   - 每小时自动刷新缓存
   - 确保榜单数据最新

---

## 🛡️ 防篡改机制

### 已实现

1. **Merkle Tree 验证**
   - 冻结榜单时计算 Merkle Root
   - 验证时重新计算并对比
   - 任何数据修改都会导致 Merkle Root 不匹配

2. **区块链存证**
   - 榜单快照上链
   - 交易哈希、区块高度不可篡改
   - 提供第三方可验证性

### 可增强

1. **数字签名**
   - 管理员对榜单进行数字签名
   - 验证签名确保操作人身份

2. **审计日志**
   - 记录所有榜单操作
   - 包括冻结、解冻、修改等

---

## 📋 改进建议优先级

### P0 - 必须立即实现

1. **修复榜单数据来源**
   - 从 `evaluations` 表查询真实数据
   - 移除模拟数据生成逻辑
   - 实现排名算法

### P1 - 高优先级（1-2周内）

2. **实现榜单缓存**
   - Redis 缓存机制
   - 缓存过期策略
   - 缓存更新机制

3. **实现 A/B 榜机制**
   - 数据库字段扩展
   - 双榜单查询接口
   - B 榜公布时间控制

4. **榜单历史版本**
   - 历史快照列表查询
   - 指定版本快照查询

5. **榜单公示期**
   - 公示期配置
   - 公示状态管理

6. **榜单刷新限流**
   - 接口限流
   - 分级限流策略

### P2 - 中优先级（1个月内）

7. **榜单异议处理**
   - 异议提交
   - 异议审核
   - 异议处理流程

8. **榜单导出**
   - Excel 导出
   - CSV 导出

9. **用户排名趋势**
   - 排名历史记录
   - 趋势图数据接口

10. **榜单通知**
    - 排名变化通知
    - 榜单冻结通知

### P3 - 低优先级（可选）

11. **榜单统计分析**
    - 分数分布
    - 排名波动分析

12. **榜单解冻**
    - 管理员解冻功能

13. **榜单对比**
    - 不同时间点榜单对比

---

## 🎯 完整功能对照表

| 功能分类 | 功能点 | 实现状态 | 优先级 | 预计工作量 |
|---------|-------|---------|-------|-----------|
| **基础查询** | 实时榜单查询 | ⚠️ 模拟数据 | P0 | 2天 |
| **基础查询** | 冻结榜单查询 | ✅ 已实现 | - | - |
| **基础查询** | 历史版本查询 | ❌ 未实现 | P1 | 1天 |
| **基础查询** | 指定快照查询 | ❌ 未实现 | P1 | 0.5天 |
| **榜单操作** | 榜单冻结 | ✅ 已实现 | - | - |
| **榜单操作** | 榜单解冻 | ❌ 未实现 | P3 | 0.5天 |
| **榜单操作** | 榜单验证 | ✅ 已实现 | - | - |
| **榜单类型** | A榜/B榜切换 | ❌ 未实现 | P1 | 3天 |
| **性能优化** | 榜单缓存 | ❌ 未实现 | P1 | 2天 |
| **性能优化** | 刷新限流 | ❌ 未实现 | P1 | 1天 |
| **公示异议** | 公示期机制 | ❌ 未实现 | P1 | 2天 |
| **公示异议** | 异议提交 | ❌ 未实现 | P2 | 3天 |
| **公示异议** | 异议审核 | ❌ 未实现 | P2 | 2天 |
| **数据导出** | Excel导出 | ❌ 未实现 | P2 | 1天 |
| **数据导出** | CSV导出 | ❌ 未实现 | P2 | 0.5天 |
| **数据分析** | 排名趋势 | ❌ 未实现 | P2 | 3天 |
| **数据分析** | 统计分析 | ❌ 未实现 | P3 | 5天 |
| **数据分析** | 榜单对比 | ❌ 未实现 | P2 | 2天 |
| **通知提醒** | 排名变化通知 | ❌ 未实现 | P2 | 2天 |
| **通知提醒** | 榜单冻结通知 | ❌ 未实现 | P2 | 1天 |

---

## 📊 总结

### 当前状态
- ✅ **已实现**: 4个核心功能（榜单查询、冻结、验证、区块链存证）
- ⚠️ **有缺陷**: 榜单数据来源为模拟数据，未连接真实评测结果
- ❌ **未实现**: 15+ 个重要功能缺失

### 核心问题
1. **数据来源错误**: 榜单数据是硬编码的模拟数据
2. **缺少自动更新**: 评测完成后榜单不会自动更新
3. **缺少缓存机制**: 高并发场景下性能堪忧
4. **缺少 A/B 榜**: 无法支持公榜/私榜切换
5. **缺少公示异议**: 无法处理榜单争议

### 建议开发顺序
1. **Week 1**: 修复数据来源（P0）+ 实现榜单缓存（P1）
2. **Week 2**: A/B 榜机制（P1）+ 历史版本查询（P1）
3. **Week 3**: 公示期机制（P1）+ 刷新限流（P1）
4. **Week 4**: 异议处理（P2）+ 榜单导出（P2）
5. **Week 5+**: 排名趋势（P2）+ 通知功能（P2）+ 统计分析（P3）

### 预计总工作量
- **P0功能**: 2天
- **P1功能**: 12天
- **P2功能**: 15天
- **P3功能**: 7天
- **总计**: 约 36 人天（1.5个月，2人并行开发）

---

**文档版本**: v1.0
**最后更新**: 2025-10-23
**状态**: 待开发
