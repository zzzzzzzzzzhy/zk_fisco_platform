package com.wereen.competitionplatform.consumer;

import com.wereen.competitionplatform.mapper.ChainProofMapper;
import com.wereen.competitionplatform.mapper.CompetitionMapper;
import com.wereen.competitionplatform.mapper.EvaluationMapper;
import com.wereen.competitionplatform.model.entity.ChainProof;
import com.wereen.competitionplatform.model.entity.Competition;
import com.wereen.competitionplatform.model.entity.Evaluation;
import com.wereen.competitionplatform.service.BlockchainService;
import com.wereen.competitionplatform.service.LeaderboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fisco.bcos.sdk.model.TransactionReceipt;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 评测消费者
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EvaluateConsumer implements StreamListener<String, ObjectRecord<String, Map<String, String>>> {

    private final EvaluationMapper evaluationMapper;
    private final BlockchainService blockchainService;
    private final ChainProofMapper chainProofMapper;
    private final CompetitionMapper competitionMapper;
    private final LeaderboardService leaderboardService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onMessage(ObjectRecord<String, Map<String, String>> record) {
        try {
            Map<String, String> taskData = record.getValue();
            Long submissionId = Long.parseLong(taskData.get("submissionId"));
            Long competitionId = Long.parseLong(taskData.get("competitionId"));
            Long userId = Long.parseLong(taskData.get("userId"));

            log.info("处理评测任务: submissionId={}, competitionId={}, userId={}", submissionId, competitionId, userId);

            // TODO: 实现完整的评测逻辑
            // 1. 加载竞赛数据集
            // 2. 加载评测镜像（Docker）
            // 3. 执行评测任务
            // 4. 记录评测结果和日志
            // 5. 更新排名

            // ========== 模拟评测结果（实际应由评测引擎返回）==========
            BigDecimal score = new BigDecimal("85.50"); // 模拟得分
            String resourceUsage = "{\"cpu\":\"50%\",\"memory\":\"512MB\",\"time\":\"30s\"}"; // 模拟资源使用
            String logPath = String.format("evaluation-logs/%d/%d.log", competitionId, submissionId); // 日志路径

            // 获取竞赛配置
            Competition competition = competitionMapper.selectById(competitionId);
            boolean useAbLeaderboard = competition.getUseAbLeaderboard() != null && competition.getUseAbLeaderboard() == 1;
            Double publicTestRatio = competition.getPublicTestRatio() != null ? competition.getPublicTestRatio() : 0.30;

            // 创建评测记录
            Evaluation evaluation = new Evaluation();
            evaluation.setSubmissionId(submissionId);
            evaluation.setCompetitionId(competitionId);
            evaluation.setUserId(userId);
            evaluation.setScore(score);
            evaluation.setLogPath(logPath);
            evaluation.setResourceUsage(resourceUsage);
            evaluation.setStatus(2); // 评测成功
            evaluation.setIsReview(0); // 非复评
            evaluation.setChainStatus(0); // 未上链

            // 处理公私榜得分
            if (useAbLeaderboard) {
                // 启用公私榜：公开测试集占30%，隐藏测试集占70%
                // 实际场景应该根据不同的测试集计算不同的得分
                // 这里模拟：公榜得分稍高（基于公开测试集），私榜得分基于全量测试集
                BigDecimal publicScore = score.multiply(new BigDecimal("1.05")); // 模拟公榜得分略高
                BigDecimal privateScore = score; // 私榜得分就是全量测试集得分

                evaluation.setPublicScore(publicScore);
                evaluation.setPrivateScore(privateScore);
                evaluation.setLeaderboardType("BOTH");
                log.info("公私榜模式 - 公榜得分: {}, 私榜得分: {}", publicScore, privateScore);
            } else {
                // 不启用公私榜：只有公榜
                evaluation.setPublicScore(score);
                evaluation.setLeaderboardType("PUBLIC");
                log.info("单榜模式 - 公榜得分: {}", score);
            }

            evaluationMapper.insert(evaluation);
            log.info("评测记录创建成功: evaluationId={}, score={}", evaluation.getId(), score);

            // ========== 自动更新榜单排名 ==========
            try {
                log.info("开始更新竞赛 {} 的榜单排名", competitionId);
                leaderboardService.updateRankings(competitionId);
                log.info("榜单排名更新成功: competitionId={}", competitionId);
            } catch (Exception e) {
                log.error("更新榜单排名失败: competitionId={}", competitionId, e);
                // 排名更新失败不应影响评测结果，所以只记录日志
            }

            // ========== 上链处理 ==========
            try {
                // 1. 计算评测结果哈希（包含关键评测数据）
                // 注意：排名在updateRankings()中才计算，此时使用公榜排名（如果有）
                Integer currentRank = evaluation.getPublicRank() != null ? evaluation.getPublicRank() : 0;
                String resultHash = calculateEvaluationHash(
                    evaluation.getId(),
                    submissionId,
                    competitionId,
                    userId,
                    score,
                    currentRank,
                    resourceUsage
                );
                evaluation.setResultHash(resultHash);

                // 2. 更新状态为上链中
                evaluation.setChainStatus(1);
                evaluationMapper.updateById(evaluation);

                // 3. 构造元数据（JSON格式）
                String metadata = String.format(
                    "{\"evaluationId\":%d,\"submissionId\":%d,\"competitionId\":%d,\"userId\":%d,\"score\":\"%s\",\"publicRank\":%d,\"timestamp\":\"%s\"}",
                    evaluation.getId(),
                    submissionId,
                    competitionId,
                    userId,
                    score.toString(),
                    currentRank,
                    LocalDateTime.now().toString()
                );

                // 4. 调用区块链服务上链
                TransactionReceipt receipt = blockchainService.saveEvidence(
                    "EVALUATION",
                    String.valueOf(evaluation.getId()),
                    resultHash
                );

                // 5. 解析交易回执
                String txHash = receipt.getTransactionHash();
                Long blockHeight = Long.valueOf(receipt.getBlockNumber());
                LocalDateTime blockTime = LocalDateTime.now();

                // 6. 保存链上存证记录
                ChainProof chainProof = new ChainProof();
                chainProof.setBizType("EVALUATION");
                chainProof.setBizId(evaluation.getId());
                chainProof.setDataHash(resultHash);
                chainProof.setTxHash(txHash);
                chainProof.setBlockHeight(blockHeight);
                chainProof.setBlockTime(blockTime);
                chainProof.setMetadata(metadata);
                chainProof.setStatus(2); // 已上链
                chainProofMapper.insert(chainProof);

                // 7. 更新评测记录的链上状态
                evaluation.setChainStatus(2); // 已上链
                evaluation.setChainTxHash(txHash);
                evaluation.setBlockHeight(blockHeight);
                evaluation.setBlockTime(blockTime);
                evaluationMapper.updateById(evaluation);

                log.info("评测结果上链成功: evaluationId={}, txHash={}, blockHeight={}",
                    evaluation.getId(), txHash, blockHeight);

            } catch (Exception e) {
                log.error("评测结果上链失败: evaluationId={}", evaluation.getId(), e);
                // 更新为上链失败状态
                evaluation.setChainStatus(3);
                evaluationMapper.updateById(evaluation);
            }

            log.info("评测任务完成: submissionId={}, evaluationId={}", submissionId, evaluation.getId());

        } catch (Exception e) {
            log.error("处理评测任务失败", e);
            throw new RuntimeException("评测任务失败: " + e.getMessage(), e);
        }
    }

    /**
     * 计算评测结果哈希
     * 包含所有关键评测数据，确保结果不可篡改
     */
    private String calculateEvaluationHash(Long evaluationId, Long submissionId, Long competitionId,
                                          Long userId, BigDecimal score, Integer rank, String resourceUsage) {
        try {
            // 拼接评测关键数据
            String data = String.format("%d|%d|%d|%d|%s|%d|%s",
                evaluationId, submissionId, competitionId, userId, score.toString(), rank, resourceUsage);

            // 计算 SHA-256 哈希
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(data.getBytes(StandardCharsets.UTF_8));

            // 转换为十六进制字符串
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            return hexString.toString();

        } catch (Exception e) {
            log.error("计算评测结果哈希失败", e);
            throw new RuntimeException("哈希计算失败", e);
        }
    }
}
