package com.wereen.competitionplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wereen.competitionplatform.exception.BusinessException;
import com.wereen.competitionplatform.mapper.CompetitionMapper;
import com.wereen.competitionplatform.mapper.EvaluationMapper;
import com.wereen.competitionplatform.mapper.LeaderboardMapper;
import com.wereen.competitionplatform.mapper.UserMapper;
import com.wereen.competitionplatform.model.entity.*;
import com.wereen.competitionplatform.util.MerkleTreeUtil;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fisco.bcos.sdk.model.TransactionReceipt;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 榜单服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeaderboardService {

    private final LeaderboardMapper leaderboardMapper;
    private final EvaluationMapper evaluationMapper;
    private final CompetitionMapper competitionMapper;
    private final UserMapper userMapper;
    private final BlockchainService blockchainService;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;

    /**
     * 获取竞赛榜单（支持公私榜）
     *
     * @param competitionId 竞赛ID
     * @param type 榜单类型（PUBLIC-公榜, PRIVATE-私榜）
     * @return 榜单列表
     */
    @Cacheable(value = "leaderboard", key = "#competitionId + ':' + #type", unless = "#result == null || #result.isEmpty()")
    public List<LeaderboardEntry> getLeaderboard(Long competitionId, String type) {
        // 1. 检查竞赛是否存在
        Competition competition = competitionMapper.selectById(competitionId);
        if (competition == null) {
            throw new BusinessException("竞赛不存在");
        }

        // 2. 检查私榜是否可以查看
        if ("PRIVATE".equalsIgnoreCase(type)) {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime publishTime = competition.getPrivateLeaderboardPublishTime();

            if (publishTime == null) {
                publishTime = competition.getSubmissionEndTime();
            }

            if (now.isBefore(publishTime)) {
                throw new BusinessException("私榜尚未公布，请等待竞赛结束后查看");
            }
        }

        // 3. 从evaluations表查询真实数据
        List<Evaluation> evaluations = queryEvaluations(competitionId, type);

        // 4. 转换为榜单条目
        List<LeaderboardEntry> leaderboard = evaluations.stream()
                .map(e -> convertToLeaderboardEntry(e, type))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        log.info("获取竞赛榜单: competitionId={}, type={}, 记录数={}", competitionId, type, leaderboard.size());
        return leaderboard;
    }

    /**
     * 获取竞赛榜单（默认公榜）
     */
    public List<LeaderboardEntry> getLeaderboard(Long competitionId) {
        return getLeaderboard(competitionId, "PUBLIC");
    }

    /**
     * 从数据库查询评测结果
     */
    private List<Evaluation> queryEvaluations(Long competitionId, String type) {
        LambdaQueryWrapper<Evaluation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Evaluation::getCompetitionId, competitionId)
                .eq(Evaluation::getStatus, 2); // 评测成功

        // 根据榜单类型选择排序字段
        if ("PRIVATE".equalsIgnoreCase(type)) {
            wrapper.isNotNull(Evaluation::getPrivateRank)
                    .orderByAsc(Evaluation::getPrivateRank);
        } else {
            wrapper.isNotNull(Evaluation::getPublicRank)
                    .orderByAsc(Evaluation::getPublicRank);
        }

        wrapper.last("LIMIT 100"); // 只返回前100名

        return evaluationMapper.selectList(wrapper);
    }

    /**
     * 转换为榜单条目
     */
    private LeaderboardEntry convertToLeaderboardEntry(Evaluation evaluation, String type) {
        try {
            // 查询用户信息
            User user = userMapper.selectById(evaluation.getUserId());
            if (user == null) {
                return null;
            }

            LeaderboardEntry entry = new LeaderboardEntry();
            entry.setUserId(evaluation.getUserId());
            entry.setUsername(user.getUsername());
            entry.setSubmissionId(evaluation.getSubmissionId());

            // 根据榜单类型设置得分和排名
            if ("PRIVATE".equalsIgnoreCase(type)) {
                entry.setRank(evaluation.getPrivateRank());
                entry.setScore(evaluation.getPrivateScore());
            } else {
                entry.setRank(evaluation.getPublicRank());
                entry.setScore(evaluation.getPublicScore());
            }

            // 查询提交时间（需要关联submissions表）
            // 这里简化处理，使用评测更新时间
            entry.setSubmitTime(evaluation.getUpdatedAt());

            return entry;
        } catch (Exception e) {
            log.error("转换榜单条目失败: evaluationId={}", evaluation.getId(), e);
            return null;
        }
    }

    /**
     * 冻结榜单（支持公私榜）
     * 将榜单快照保存并上链，防止篡改
     *
     * @param competitionId 竞赛ID
     * @param userId 操作用户ID
     * @param type 榜单类型（PUBLIC-公榜, PRIVATE-私榜）
     * @param publicityDays 公示天数（默认7天）
     * @return 冻结的榜单快照
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "leaderboard", key = "#competitionId + ':' + #type")
    public Leaderboard freezeLeaderboard(Long competitionId, Long userId, String type, Integer publicityDays) {
        // 1. 检查该类型榜单是否已冻结
        LambdaQueryWrapper<Leaderboard> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Leaderboard::getCompetitionId, competitionId)
                .eq(Leaderboard::getLeaderboardType, type)
                .eq(Leaderboard::getFrozen, 1);

        if (leaderboardMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("该竞赛" + type + "榜单已冻结");
        }

        try {
            // 2. 获取榜单数据（指定类型）
            List<LeaderboardEntry> leaderboardData = getLeaderboard(competitionId, type);

            if (leaderboardData.isEmpty()) {
                throw new BusinessException("榜单数据为空，无法冻结");
            }

            // 3. 序列化榜单数据
            String leaderboardJson = objectMapper.writeValueAsString(leaderboardData);

            // 4. 计算Merkle Root
            List<String> dataForMerkle = leaderboardData.stream()
                    .map(entry -> String.format("%d|%s|%s|%d",
                            entry.getRank(), entry.getUsername(), entry.getScore(), entry.getUserId()))
                    .collect(Collectors.toList());
            String merkleRoot = MerkleTreeUtil.calculateMerkleRoot(dataForMerkle);

            // 5. 生成快照ID
            String snapshotId = String.format("snapshot-%s-%d-%d", type, competitionId, System.currentTimeMillis());

            // 6. 计算公示期
            if (publicityDays == null || publicityDays <= 0) {
                publicityDays = 7; // 默认7天公示期
            }
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime publicityStartTime = now;
            LocalDateTime publicityEndTime = now.plusDays(publicityDays);

            // 7. 创建榜单记录
            Leaderboard leaderboard = new Leaderboard();
            leaderboard.setCompetitionId(competitionId);
            leaderboard.setLeaderboardType(type);
            leaderboard.setSnapshotId(snapshotId);
            leaderboard.setMerkleRoot(merkleRoot);
            leaderboard.setFrozen(1);
            leaderboard.setFrozenBy(userId);
            leaderboard.setFrozenAt(now);
            leaderboard.setPublicityStatus("IN_PUBLICITY");
            leaderboard.setPublicityStartTime(publicityStartTime);
            leaderboard.setPublicityEndTime(publicityEndTime);
            leaderboard.setPublicityDays(publicityDays);
            leaderboard.setLeaderboardData(leaderboardJson);

            // 8. 构造元数据（JSON格式）
            String metadata = String.format(
                "{\"competitionId\":%d,\"leaderboardType\":\"%s\",\"snapshotId\":\"%s\",\"recordCount\":%d,\"publicityDays\":%d,\"frozenAt\":\"%s\"}",
                competitionId,
                type,
                snapshotId,
                leaderboardData.size(),
                publicityDays,
                now.toString()
            );

            // 9. 调用真实区块链服务上链（使用Merkle Root作为数据哈希）
            TransactionReceipt receipt = blockchainService.freezeLeaderboard(
                String.valueOf(competitionId) + ":" + type,
                merkleRoot,
                metadata
            );

            // 10. 解析交易回执
            String txHash = receipt.getTransactionHash();
            Long blockHeight = Long.valueOf(receipt.getBlockNumber());
            LocalDateTime blockTime = LocalDateTime.now();

            leaderboard.setChainTxHash(txHash);
            leaderboard.setBlockHeight(blockHeight);
            leaderboard.setBlockTime(blockTime);

            // 11. 保存榜单记录
            leaderboardMapper.insert(leaderboard);

            log.info("榜单冻结成功: competitionId={}, type={}, snapshotId={}, merkleRoot={}, txHash={}, publicityDays={}",
                    competitionId, type, snapshotId, merkleRoot, txHash, publicityDays);

            // 12. 清除缓存
            clearLeaderboardCache(competitionId, type);

            return leaderboard;

        } catch (Exception e) {
            log.error("榜单冻结失败: competitionId={}, type={}", competitionId, type, e);
            throw new BusinessException("榜单冻结失败: " + e.getMessage());
        }
    }

    /**
     * 冻结榜单（默认公榜，7天公示期）- 兼容旧方法
     */
    public Leaderboard freezeLeaderboard(Long competitionId, Long userId) {
        return freezeLeaderboard(competitionId, userId, "PUBLIC", 7);
    }

    /**
     * 获取冻结的榜单
     */
    public Leaderboard getFrozenLeaderboard(Long competitionId) {
        LambdaQueryWrapper<Leaderboard> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Leaderboard::getCompetitionId, competitionId)
                .eq(Leaderboard::getFrozen, 1)
                .orderByDesc(Leaderboard::getFrozenAt)
                .last("LIMIT 1");

        return leaderboardMapper.selectOne(wrapper);
    }

    /**
     * 验证榜单完整性
     */
    public boolean verifyLeaderboard(Long competitionId) {
        try {
            Leaderboard frozenLeaderboard = getFrozenLeaderboard(competitionId);
            if (frozenLeaderboard == null) {
                log.warn("榜单未冻结: competitionId={}", competitionId);
                return false;
            }

            // 解析榜单数据
            @SuppressWarnings("unchecked")
            List<LeaderboardEntry> leaderboardData = objectMapper.readValue(
                    frozenLeaderboard.getLeaderboardData(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, LeaderboardEntry.class)
            );

            // 重新计算Merkle Root
            List<String> dataForMerkle = leaderboardData.stream()
                    .map(entry -> String.format("%d|%s|%s|%d",
                            entry.getRank(), entry.getUsername(), entry.getScore(), entry.getUserId()))
                    .collect(Collectors.toList());
            String calculatedRoot = MerkleTreeUtil.calculateMerkleRoot(dataForMerkle);

            // 对比
            boolean valid = calculatedRoot.equals(frozenLeaderboard.getMerkleRoot());
            log.info("榜单验证: competitionId={}, valid={}", competitionId, valid);
            return valid;

        } catch (Exception e) {
            log.error("榜单验证失败: competitionId={}", competitionId, e);
            return false;
        }
    }

    /**
     * 解冻榜单（管理员操作）
     *
     * @param leaderboardId 榜单快照ID
     * @param userId 操作用户ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void unfreezeLeaderboard(Long leaderboardId, Long userId) {
        Leaderboard leaderboard = leaderboardMapper.selectById(leaderboardId);
        if (leaderboard == null) {
            throw new BusinessException("榜单快照不存在");
        }

        if (leaderboard.getFrozen() == 0) {
            throw new BusinessException("榜单未冻结");
        }

        // 更新榜单状态
        leaderboard.setFrozen(0);
        leaderboard.setPublicityStatus("CANCELLED");
        leaderboard.setRemark("管理员解冻，操作人ID：" + userId);
        leaderboardMapper.updateById(leaderboard);

        // 清除缓存
        clearLeaderboardCache(leaderboard.getCompetitionId(), leaderboard.getLeaderboardType());

        log.info("榜单解冻成功: leaderboardId={}, userId={}", leaderboardId, userId);
    }

    /**
     * 确认榜单（公示期结束后）
     *
     * @param leaderboardId 榜单快照ID
     * @param userId 操作用户ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void confirmLeaderboard(Long leaderboardId, Long userId) {
        Leaderboard leaderboard = leaderboardMapper.selectById(leaderboardId);
        if (leaderboard == null) {
            throw new BusinessException("榜单快照不存在");
        }

        if (!"IN_PUBLICITY".equals(leaderboard.getPublicityStatus())) {
            throw new BusinessException("榜单状态不正确，无法确认");
        }

        // 检查是否过了公示期
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(leaderboard.getPublicityEndTime())) {
            throw new BusinessException("公示期未结束，无法确认");
        }

        // 更新榜单状态
        leaderboard.setPublicityStatus("CONFIRMED");
        leaderboard.setConfirmedBy(userId);
        leaderboard.setConfirmedAt(now);
        leaderboardMapper.updateById(leaderboard);

        log.info("榜单确认成功: leaderboardId={}, userId={}", leaderboardId, userId);
    }

    /**
     * 查询榜单历史快照列表
     *
     * @param competitionId 竞赛ID
     * @param type 榜单类型（可选）
     * @return 历史快照列表
     */
    public List<Leaderboard> getLeaderboardHistory(Long competitionId, String type) {
        LambdaQueryWrapper<Leaderboard> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Leaderboard::getCompetitionId, competitionId)
                .eq(Leaderboard::getFrozen, 1);

        if (type != null && !type.isEmpty()) {
            wrapper.eq(Leaderboard::getLeaderboardType, type);
        }

        wrapper.orderByDesc(Leaderboard::getFrozenAt);

        return leaderboardMapper.selectList(wrapper);
    }

    /**
     * 根据快照ID查询榜单
     *
     * @param snapshotId 快照ID
     * @return 榜单快照
     */
    public Leaderboard getLeaderboardBySnapshotId(String snapshotId) {
        LambdaQueryWrapper<Leaderboard> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Leaderboard::getSnapshotId, snapshotId);

        return leaderboardMapper.selectOne(wrapper);
    }

    /**
     * 清除榜单缓存
     */
    private void clearLeaderboardCache(Long competitionId, String type) {
        try {
            String cacheKey = "leaderboard::" + competitionId + ":" + type;
            redisTemplate.delete(cacheKey);
            log.info("清除榜单缓存: competitionId={}, type={}", competitionId, type);
        } catch (Exception e) {
            log.error("清除榜单缓存失败", e);
        }
    }

    /**
     * 自动更新排名（评测完成后调用）
     *
     * @param competitionId 竞赛ID
     */
    @CacheEvict(value = "leaderboard", allEntries = true)
    public void updateRankings(Long competitionId) {
        try {
            log.info("开始更新排名: competitionId={}", competitionId);

            // 查询竞赛配置
            Competition competition = competitionMapper.selectById(competitionId);
            if (competition == null) {
                return;
            }

            boolean useAbLeaderboard = competition.getUseAbLeaderboard() != null && competition.getUseAbLeaderboard() == 1;

            // 更新公榜排名
            updatePublicRankings(competitionId);

            // 如果启用了公私榜，也更新私榜排名
            if (useAbLeaderboard) {
                updatePrivateRankings(competitionId);
            }

            // 清除所有相关缓存
            clearLeaderboardCache(competitionId, "PUBLIC");
            if (useAbLeaderboard) {
                clearLeaderboardCache(competitionId, "PRIVATE");
            }

            log.info("排名更新完成: competitionId={}", competitionId);

        } catch (Exception e) {
            log.error("更新排名失败: competitionId={}", competitionId, e);
        }
    }

    /**
     * 更新公榜排名
     */
    private void updatePublicRankings(Long competitionId) {
        // 查询所有评测成功的记录，按公榜得分降序排序
        LambdaQueryWrapper<Evaluation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Evaluation::getCompetitionId, competitionId)
                .eq(Evaluation::getStatus, 2)
                .isNotNull(Evaluation::getPublicScore)
                .orderByDesc(Evaluation::getPublicScore)
                .orderByAsc(Evaluation::getUpdatedAt); // 得分相同时，早提交的排名靠前

        List<Evaluation> evaluations = evaluationMapper.selectList(wrapper);

        // 更新排名
        int rank = 1;
        for (Evaluation evaluation : evaluations) {
            evaluation.setPublicRank(rank++);
            evaluationMapper.updateById(evaluation);
        }

        log.info("公榜排名更新完成: competitionId={}, 记录数={}", competitionId, evaluations.size());
    }

    /**
     * 更新私榜排名
     */
    private void updatePrivateRankings(Long competitionId) {
        // 查询所有评测成功的记录，按私榜得分降序排序
        LambdaQueryWrapper<Evaluation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Evaluation::getCompetitionId, competitionId)
                .eq(Evaluation::getStatus, 2)
                .isNotNull(Evaluation::getPrivateScore)
                .orderByDesc(Evaluation::getPrivateScore)
                .orderByAsc(Evaluation::getUpdatedAt);

        List<Evaluation> evaluations = evaluationMapper.selectList(wrapper);

        // 更新排名
        int rank = 1;
        for (Evaluation evaluation : evaluations) {
            evaluation.setPrivateRank(rank++);
            evaluationMapper.updateById(evaluation);
        }

        log.info("私榜排名更新完成: competitionId={}, 记录数={}", competitionId, evaluations.size());
    }

    /**
     * 榜单条目
     */
    @Data
    public static class LeaderboardEntry {
        private Integer rank;
        private Long userId;
        private String username;
        private BigDecimal score;
        private Long submissionId;
        private LocalDateTime submitTime;
    }
}
