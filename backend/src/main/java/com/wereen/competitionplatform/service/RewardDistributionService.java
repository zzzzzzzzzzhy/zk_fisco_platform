package com.wereen.competitionplatform.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wereen.competitionplatform.mapper.ChainProofMapper;
import com.wereen.competitionplatform.mapper.TokenRewardMapper;
import com.wereen.competitionplatform.model.entity.ChainProof;
import com.wereen.competitionplatform.model.entity.RewardEvent;
import com.wereen.competitionplatform.model.entity.TokenReward;
import com.wereen.competitionplatform.model.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RewardDistributionService {

    private final RewardEventService rewardEventService;
    private final ChainProofMapper chainProofMapper;
    private final ForumTokenRewardService forumTokenRewardService;
    private final RollupRewardDistributorService distributorService;
    private final RollupChainService rollupChainService;
    private final UserService userService;
    private final TokenRewardMapper tokenRewardMapper;
    private final ObjectMapper objectMapper;
    private final RollupTaskLogService rollupTaskLogService;

    @org.springframework.beans.factory.annotation.Value("${reward.rollup.distribute-retry-minutes:3}")
    private long retryMinutes;

    public void distributePendingBatches() {
        distributeByType("CONTENT_SHARE", "CONTENT_SHARE_ROLLUP", "CONTENT_IMAGE", "CONTENT_VIDEO");
        distributeByType("COMMENT", "COMMENT_ROLLUP", "COMMENT", null);
        distributeByType("CHECKIN", "CHECKIN_ROLLUP", "DAILY_CHECKIN", null);
    }

    private void distributeByType(String eventType, String bizType, String rewardType, String rewardTypeAlt) {
        List<ChainProof> proofs = chainProofMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ChainProof>()
                .eq(ChainProof::getBizType, bizType)
                .eq(ChainProof::getStatus, 2)
                .eq(ChainProof::getDeleted, 0)
                .orderByAsc(ChainProof::getId)
        );

        for (ChainProof proof : proofs) {
            List<RewardEvent> events = rewardEventService.listEventsByBatch(eventType, proof.getBizId(), 1);
            if (events.isEmpty()) {
                events = findRetryEvents(eventType, proof.getBizId());
            }
            if (events.isEmpty()) {
                continue;
            }
            try {
            if (!rollupChainService.isBatchSubmitted(proof.getBizId())) {
                log.info("Rollup 批次未上链，跳过发放: eventType={}, batchId={}", eventType, proof.getBizId());
                continue;
            }
            if (distributorService.isBatchDistributed(proof.getBizId())) {
                log.info("Rollup 批次已发放，跳过重复发放: eventType={}, batchId={}", eventType, proof.getBizId());
                continue;
            }
            distributeBatch(eventType, rewardType, rewardTypeAlt, proof, events);
            rollupTaskLogService.log("rollup_distribute_success", Map.of(
                "eventType", eventType,
                "batchId", String.valueOf(proof.getBizId()),
                    "count", String.valueOf(events.size())
                ));
            } catch (Exception e) {
                rollupTaskLogService.log("rollup_distribute_failed", Map.of(
                    "eventType", eventType,
                    "batchId", String.valueOf(proof.getBizId()),
                    "error", e.getMessage() == null ? "unknown" : e.getMessage()
                ));
                log.error("奖励批次发放失败: eventType={}, batchId={}", eventType, proof.getBizId(), e);
            }
        }
    }

    private List<RewardEvent> findRetryEvents(String eventType, Long batchId) {
        List<RewardEvent> rewarded = rewardEventService.listEventsByBatch(eventType, batchId, 2);
        if (rewarded.isEmpty()) {
            return List.of();
        }
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(Math.max(1, retryMinutes));
        List<RewardEvent> retry = new ArrayList<>();
        for (RewardEvent event : rewarded) {
            if (event.getUpdatedAt() != null && event.getUpdatedAt().isAfter(threshold)) {
                continue;
            }
            RollupRewardDistributorService.TransactionState state =
                distributorService.getTransactionState(event.getTxHash());
            if (state == RollupRewardDistributorService.TransactionState.MINED_SUCCESS
                || state == RollupRewardDistributorService.TransactionState.PENDING) {
                continue;
            }
            if (state != RollupRewardDistributorService.TransactionState.MINED_SUCCESS) {
                retry.add(event);
            }
        }
        if (!retry.isEmpty()) {
            log.warn("检测到未确认发放交易，触发重试: eventType={}, batchId={}, count={}",
                eventType, batchId, retry.size());
        }
        return retry;
    }

    private void distributeBatch(String eventType, String rewardType, String rewardTypeAlt,
                                 ChainProof proof, List<RewardEvent> events) throws Exception {
        ForumTokenRewardService.RewardConfig config = forumTokenRewardService.fetchRewardConfig();
        if (config == null) {
            throw new IllegalStateException("无法获取链上奖励配置");
        }

        Map<Long, String> walletMap = fetchWallets(events);
        List<String> recipients = new ArrayList<>();
        List<BigInteger> amounts = new ArrayList<>();
        List<Long> rewardedIds = new ArrayList<>();
        List<RewardEvent> rewardedEvents = new ArrayList<>();

        for (RewardEvent event : events) {
            String wallet = walletMap.get(event.getUserId());
            if (!StringUtils.hasText(wallet)) {
                continue;
            }
            Map<String, Object> payload = readPayload(event.getPayload());
            BigInteger amount = resolveRewardAmount(eventType, config, payload, event);
            if (amount == null || amount.signum() <= 0) {
                continue;
            }
            recipients.add(wallet);
            amounts.add(amount);
            rewardedIds.add(event.getId());
            rewardedEvents.add(event);
        }

        if (recipients.isEmpty()) {
            return;
        }

        String txHash = distributorService.distributeBatch(proof.getBizId(), recipients, amounts);
        rewardEventService.markRewarded(rewardedIds, txHash);
        saveRewardRecords(rewardedEvents, rewardType, rewardTypeAlt, txHash, config);
        log.info("奖励批次发放完成: eventType={}, batchId={}, recipients={}, txHash={}",
            eventType, proof.getBizId(), recipients.size(), txHash);
    }

    private Map<Long, String> fetchWallets(List<RewardEvent> events) {
        List<Long> userIds = events.stream()
            .map(RewardEvent::getUserId)
            .distinct()
            .toList();
        Map<Long, User> users = userService.getUserMapByIds(userIds);
        if (users == null || users.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> map = new HashMap<>();
        for (User user : users.values()) {
            if (StringUtils.hasText(user.getWalletAddress())) {
                map.put(user.getId(), user.getWalletAddress());
            }
        }
        return map;
    }

    private Map<String, Object> readPayload(String payload) {
        if (!StringUtils.hasText(payload)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(payload, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private BigInteger resolveRewardAmount(String eventType, ForumTokenRewardService.RewardConfig config,
                                           Map<String, Object> payload, RewardEvent event) {
        if ("CONTENT_SHARE".equals(eventType)) {
            String mediaType = payload.getOrDefault("mediaType", "").toString();
            if ("VIDEO".equalsIgnoreCase(mediaType)) {
                return config.getContentVideoReward();
            }
            return config.getContentImageReward();
        }
        if ("COMMENT".equals(eventType)) {
            return config.getCommentReward();
        }
        if ("CHECKIN".equals(eventType)) {
            BigInteger amount = config.getDailyCheckinReward();
            Integer streak = computeCheckinStreak(event.getUserId(), payload);
            if (streak != null && streak % 7 == 0) {
                amount = amount.add(config.getConsecutiveBonus());
            }
            return amount;
        }
        return BigInteger.ZERO;
    }

    private Integer computeCheckinStreak(Long userId, Map<String, Object> payload) {
        if (userId == null) {
            return null;
        }
        String dayStr = payload.getOrDefault("day", "").toString();
        if (!StringUtils.hasText(dayStr)) {
            return null;
        }
        try {
            LocalDate day = LocalDate.parse(dayStr);
            List<RewardEvent> recent = rewardEventService.listRecentCheckins(userId, 10);
            return calculateStreak(day, recent);
        } catch (Exception e) {
            return null;
        }
    }

    private int calculateStreak(LocalDate day, List<RewardEvent> recent) {
        List<LocalDate> days = recent.stream()
            .map(e -> readPayload(e.getPayload()).getOrDefault("day", "").toString())
            .filter(StringUtils::hasText)
            .map(LocalDate::parse)
            .filter(d -> d.isBefore(day))
            .sorted(Comparator.reverseOrder())
            .collect(Collectors.toList());

        int streak = 1;
        LocalDate cursor = day.minusDays(1);
        for (LocalDate d : days) {
            if (d.isEqual(cursor)) {
                streak++;
                cursor = cursor.minusDays(1);
            }
            if (streak >= 7) {
                break;
            }
        }
        return streak;
    }

    private void saveRewardRecords(List<RewardEvent> events, String rewardType, String rewardTypeAlt,
                                   String txHash, ForumTokenRewardService.RewardConfig config) {
        for (RewardEvent event : events) {
            String resolvedType = resolveRewardType(event, rewardType, rewardTypeAlt, config);
            TokenReward reward = findExistingReward(event, resolvedType);
            if (reward == null) {
                reward = new TokenReward();
                reward.setUserId(event.getUserId());
                reward.setContentId(event.getBizId());
                reward.setRewardType(resolvedType);
                reward.setAmount(resolveRewardAmount(event.getEventType(), config, readPayload(event.getPayload()), event));
                reward.setTxHash(txHash);
                tokenRewardMapper.insert(reward);
            } else {
                reward.setTxHash(txHash);
                reward.setAmount(resolveRewardAmount(event.getEventType(), config, readPayload(event.getPayload()), event));
                tokenRewardMapper.updateById(reward);
            }
        }
    }

    private TokenReward findExistingReward(RewardEvent event, String rewardType) {
        return tokenRewardMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TokenReward>()
                .eq(TokenReward::getUserId, event.getUserId())
                .eq(TokenReward::getContentId, event.getBizId())
                .eq(TokenReward::getRewardType, rewardType)
                .eq(TokenReward::getDeleted, 0)
                .last("limit 1")
        );
    }

    private String resolveRewardType(RewardEvent event, String rewardType, String rewardTypeAlt,
                                     ForumTokenRewardService.RewardConfig config) {
        if ("CONTENT_SHARE".equals(event.getEventType())) {
            Map<String, Object> payload = readPayload(event.getPayload());
            String mediaType = payload.getOrDefault("mediaType", "").toString();
            return "VIDEO".equalsIgnoreCase(mediaType) ? rewardTypeAlt : rewardType;
        }
        return rewardType;
    }
}
