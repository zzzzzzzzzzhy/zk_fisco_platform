# 公榜（Public Leaderboard）与私榜（Private Leaderboard）详解

## 📋 核心概念

### 什么是公榜和私榜？

**公榜（Public Leaderboard / A榜）**:
- 竞赛期间实时可见的排行榜
- 基于**公开测试集**的评测结果
- 参赛者可以随时查看自己和他人的排名
- 用于指导参赛者优化模型

**私榜（Private Leaderboard / B榜）**:
- 竞赛结束后才公布的最终排行榜
- 基于**隐藏测试集**的评测结果
- 竞赛期间参赛者**看不到**私榜排名
- 作为**最终评奖依据**

---

## 🎯 为什么需要公榜和私榜？

### 问题场景

如果只有一个榜单（公榜），会出现以下问题：

#### 1. 过拟合（Overfitting）问题

**场景**：某参赛者提交100次，每次微调模型参数
```
提交1: 公榜得分 85.2%
提交2: 公榜得分 85.5%  ← 参数调整方向1
提交3: 公榜得分 85.1%
提交4: 公榜得分 86.0%  ← 参数调整方向2
...
提交100: 公榜得分 92.3% ✓ 看起来很高
```

**问题**：
- 参赛者通过大量试错，找到了"最适合公开测试集"的参数
- 但这个模型在**真实数据**上可能表现很差
- 这叫做**对测试集过拟合**

#### 2. 作弊风险

**场景**：参赛者通过反复提交，逆向推断测试集答案
```
已知：测试集有1000个样本
策略：
- 提交全预测为A → 得分30% → 推断300个样本答案是A
- 提交全预测为B → 得分40% → 推断400个样本答案是B
- 提交全预测为C → 得分30% → 推断300个样本答案是C
```

**结果**：通过"探测"，参赛者可能猜出部分测试集的真实答案

#### 3. 运气成分

**场景**：两个参赛者的模型能力相近
```
参赛者A:
  - 公榜: 90.2% (恰好模型对公开测试集表现好)
  - 真实能力: 85%

参赛者B:
  - 公榜: 88.5% (模型对公开测试集表现一般)
  - 真实能力: 87%
```

**问题**：仅凭公榜，参赛者A排名更高，但实际能力可能不如参赛者B

---

## 🔍 公榜与私榜的运作机制

### 测试集划分

```
完整测试集（Test Set）
    ├── 公开测试集（Public Test Set，30%）
    │   ├─ 用于实时评测，生成公榜
    │   └─ 参赛者可见排名变化
    │
    └── 隐藏测试集（Private Test Set，70%）
        ├─ 用于最终评测，生成私榜
        ├─ 竞赛期间不公布结果
        └─ 竞赛结束后作为最终排名依据
```

### 时间线流程

```
┌─────────────────────────────────────────────────────────┐
│  竞赛期间（例如：2025-10-01 ~ 2025-11-01）               │
├─────────────────────────────────────────────────────────┤
│  用户提交作品                                            │
│      ↓                                                   │
│  评测引擎运行                                            │
│      ├─→ 公开测试集（30%）→ 生成公榜得分（实时可见）    │
│      └─→ 隐藏测试集（70%）→ 生成私榜得分（不可见）❌    │
│                                                          │
│  参赛者只能看到公榜排名，根据公榜调整策略                 │
└─────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────┐
│  竞赛结束时（2025-11-01 23:59:59）                       │
├─────────────────────────────────────────────────────────┤
│  1. 公榜冻结（最后一次提交的公榜排名）                    │
│  2. 私榜公布（最后一次提交的私榜排名）                    │
│  3. 以私榜排名为准，决定最终获奖者                        │
└─────────────────────────────────────────────────────────┘
```

### 典型案例

#### 案例1：公榜第一，私榜掉榜

**参赛者Alice**:
```
竞赛期间:
  - 提交次数: 150次（大量试错）
  - 公榜排名: 第1名（得分 95.8%）
  - 策略: 针对公开测试集优化参数

竞赛结束:
  - 私榜排名: 第15名（得分 82.3%）❌
  - 原因: 模型过拟合公开测试集，泛化能力差
```

**结果**：Alice虽然公榜第一，但因为私榜排名低，最终未获奖

#### 案例2：公榜中游，私榜逆袭

**参赛者Bob**:
```
竞赛期间:
  - 提交次数: 10次（注重模型本质）
  - 公榜排名: 第12名（得分 89.2%）
  - 策略: 本地交叉验证，避免过拟合

竞赛结束:
  - 私榜排名: 第2名（得分 91.5%）✓
  - 原因: 模型泛化能力强，适应性好
```

**结果**：Bob公榜排名一般，但私榜表现优异，最终获得亚军

---

## 📊 公榜与私榜对比表

| 维度 | 公榜（Public Leaderboard） | 私榜（Private Leaderboard） |
|-----|---------------------------|----------------------------|
| **测试数据** | 公开测试集（30%） | 隐藏测试集（70%） |
| **可见性** | 竞赛期间实时可见 ✓ | 竞赛结束后才公布 ❌ |
| **作用** | 指导参赛者优化方向 | 最终评奖依据 |
| **排名依据** | 公开测试集得分 | 隐藏测试集得分 |
| **更新频率** | 每次提交后立即更新 | 竞赛结束后一次性更新 |
| **防作弊** | 容易被过拟合 | 难以被过拟合 |
| **真实性** | 可能不反映真实能力 | 更接近真实能力 |

---

## 🏆 实际案例：Kaggle 竞赛

### Kaggle 的公私榜机制

以 **Kaggle Titanic 竞赛**为例：

```
测试集总样本数: 418
    ├── 公开测试集: 约 40% (167个样本)
    │   └── 用于生成公榜，参赛者可见
    │
    └── 隐藏测试集: 约 60% (251个样本)
        └── 用于生成私榜，竞赛结束后公布
```

**参赛者提交流程**:
1. 训练模型（基于训练集）
2. 对测试集进行预测
3. 提交预测结果（CSV文件）
4. Kaggle 评测系统：
   - 计算公开测试集得分 → 更新公榜 ✓ 立即可见
   - 计算隐藏测试集得分 → 更新私榜 ❌ 暂不可见

**竞赛结束**:
- 公布私榜排名
- 以私榜排名为准颁奖
- 经常出现"公榜前10，私榜掉到50名外"的情况

### 真实震荡案例

**2019年某Kaggle竞赛**:
```
公榜 Top 10:
1. TeamA    - 0.9823
2. TeamB    - 0.9801
3. TeamC    - 0.9785
...

私榜 Top 10（竞赛结束后）:
1. TeamX    - 0.9612  ← 公榜排名第45
2. TeamC    - 0.9598  ← 公榜排名第3（稳定）
3. TeamY    - 0.9587  ← 公榜排名第32
...
45. TeamA   - 0.9123  ← 公榜第1，私榜惨跌！
```

**分析**:
- TeamA 过度针对公开测试集优化，私榜暴跌
- TeamX 注重模型泛化，公榜中游但私榜夺冠
- TeamC 排名稳定，说明模型健壮

---

## 🛠️ 技术实现要点

### 数据库设计

```sql
-- competitions 表增加字段
ALTER TABLE competitions
ADD COLUMN use_ab_leaderboard TINYINT DEFAULT 0 COMMENT '是否启用公私榜 (0-否 1-是)';

ALTER TABLE competitions
ADD COLUMN public_test_ratio DECIMAL(3,2) DEFAULT 0.30 COMMENT '公开测试集比例';

ALTER TABLE competitions
ADD COLUMN private_leaderboard_publish_time DATETIME COMMENT '私榜公布时间（通常=竞赛结束时间）';

-- evaluations 表增加字段
ALTER TABLE evaluations
ADD COLUMN leaderboard_type VARCHAR(10) DEFAULT 'PUBLIC' COMMENT '榜单类型 (PUBLIC-公榜 PRIVATE-私榜)';

ALTER TABLE evaluations
ADD COLUMN public_score DECIMAL(10,4) COMMENT '公榜得分（公开测试集）';

ALTER TABLE evaluations
ADD COLUMN private_score DECIMAL(10,4) COMMENT '私榜得分（隐藏测试集）';

ALTER TABLE evaluations
ADD COLUMN public_rank INT COMMENT '公榜排名';

ALTER TABLE evaluations
ADD COLUMN private_rank INT COMMENT '私榜排名';
```

### 评测流程设计

```java
public void evaluateSubmission(Long submissionId) {
    Submission submission = getSubmissionById(submissionId);
    Competition competition = getCompetitionById(submission.getCompetitionId());

    // 1. 加载测试集
    TestDataset testDataset = loadTestDataset(competition.getDatasetPath());

    // 2. 划分公开测试集和隐藏测试集
    double publicRatio = competition.getPublicTestRatio(); // 0.3
    TestDataset publicTestSet = testDataset.split(0, publicRatio);      // 前30%
    TestDataset privateTestSet = testDataset.split(publicRatio, 1.0);   // 后70%

    // 3. 运行评测
    double publicScore = evaluate(submission, publicTestSet);   // 公榜得分
    double privateScore = evaluate(submission, privateTestSet); // 私榜得分

    // 4. 保存评测结果
    Evaluation evaluation = new Evaluation();
    evaluation.setPublicScore(new BigDecimal(publicScore));
    evaluation.setPrivateScore(new BigDecimal(privateScore));

    // 5. 更新排名
    updatePublicRank(competition.getId());   // 更新公榜排名
    updatePrivateRank(competition.getId());  // 更新私榜排名（不公开）

    evaluationMapper.insert(evaluation);
}
```

### 榜单查询接口

```java
/**
 * 查询榜单
 * @param type PUBLIC-公榜, PRIVATE-私榜
 */
@GetMapping("/{competitionId}")
public Result<List<LeaderboardEntry>> getLeaderboard(
        @PathVariable Long competitionId,
        @RequestParam(defaultValue = "PUBLIC") String type) {

    Competition competition = getCompetitionById(competitionId);

    // 私榜只有在竞赛结束后才能查看
    if ("PRIVATE".equals(type)) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime publishTime = competition.getPrivateLeaderboardPublishTime();

        if (now.isBefore(publishTime)) {
            throw new BusinessException("私榜尚未公布，请等待竞赛结束");
        }
    }

    // 查询对应类型的榜单
    List<LeaderboardEntry> leaderboard = leaderboardService.getLeaderboard(
        competitionId, type);

    return Result.success(leaderboard);
}
```

---

## 📈 最佳实践建议

### 对于平台方

1. **测试集划分比例**
   - 公开测试集：20%-40%（常见30%）
   - 隐藏测试集：60%-80%（常见70%）
   - 确保隐藏测试集样本量足够大

2. **提交次数限制**
   - 每日提交次数：5-10次
   - 总提交次数：50-100次
   - 防止暴力试错

3. **私榜公布时机**
   - 方案A：竞赛结束立即公布
   - 方案B：竞赛结束后1-3天公布（预留审核时间）

4. **防止数据泄露**
   - 测试集严格保密
   - 不公布测试集标签
   - 评测在服务器端进行

### 对于参赛者

1. **避免过拟合公榜**
   - 不要盲目追求公榜排名
   - 注重本地交叉验证（K-Fold CV）
   - 选择泛化能力强的模型

2. **合理利用提交次数**
   - 每次提交前先本地验证
   - 不要频繁微调参数提交
   - 保留1-2次最后冲刺机会

3. **关注模型鲁棒性**
   - 测试集可能与训练集分布不同
   - 使用集成学习提高稳定性
   - 避免过度特征工程

---

## ⚖️ 公私榜的争议

### 支持方观点

✅ **优点**:
1. 防止过拟合测试集
2. 考察模型真实泛化能力
3. 减少作弊和运气成分
4. 更接近真实业务场景

### 反对方观点

❌ **缺点**:
1. 参赛者体验差（看不到真实排名）
2. 可能打击参赛积极性
3. 私榜震荡大，"黑天鹅"事件多
4. 增加平台技术复杂度

### 折中方案

部分平台采用**三榜机制**：
- **公榜**：30%测试集，实时可见
- **准私榜**：30%测试集，最后一周公布
- **私榜**：40%测试集，竞赛结束公布

---

## 📚 经典案例学习

### Case 1: Netflix Prize（2006-2009）

```
奖金: 100万美元
任务: 预测用户电影评分

机制:
- 公开测试集: 最近评分（28万条）
- 隐藏测试集: 未来评分（280万条）
- 要求: 私榜成绩比Netflix自己的算法提升10%

结果:
- 2009年，BellKor's Pragmatic Chaos团队夺冠
- 公榜和私榜排名基本一致（因为隐藏测试集足够大）
```

### Case 2: 天池新冠疫情预测（2020）

```
任务: 预测各地区新冠确诊人数

机制:
- 公榜: 前7天数据
- 私榜: 后7天数据
- 特点: 时间序列预测，私榜数据是"未来数据"

震荡:
- 公榜前10的队伍，私榜只有3支进入前10
- 原因: 疫情走势突变，模型过拟合历史趋势
```

---

## 🎯 总结

### 核心区别

| 维度 | 公榜 | 私榜 |
|-----|------|------|
| **本质** | 指导性榜单 | 决定性榜单 |
| **数据** | 公开测试集（小部分） | 隐藏测试集（大部分） |
| **可见性** | 竞赛期间可见 | 竞赛结束后公布 |
| **作用** | 帮助调优 | 评奖依据 |
| **风险** | 容易过拟合 | 真实能力体现 |

### 关键要点

1. **公榜是指南针，私榜是考官**
   - 公榜告诉你方向对不对
   - 私榜评判你能力强不强

2. **不要迷信公榜排名**
   - 公榜第一不代表最终获奖
   - 注重模型本质，而非排名数字

3. **平台应合理设计比例**
   - 公开测试集不宜太小（防止运气成分）
   - 隐藏测试集不宜太小（确保评价准确）

4. **技术实现要确保数据安全**
   - 隐藏测试集绝对保密
   - 评测服务器端执行
   - 防止数据泄露

---

**参考资料**:
- Kaggle: https://www.kaggle.com/competitions
- 天池大赛: https://tianchi.aliyun.com/competition
- Netflix Prize: https://www.netflixprize.com/

**最后更新**: 2025-10-23
