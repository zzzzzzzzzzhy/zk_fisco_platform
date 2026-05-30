package com.wereen.competitionplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wereen.competitionplatform.common.PageResult;
import com.wereen.competitionplatform.exception.BusinessException;
import com.wereen.competitionplatform.mapper.TokenRewardMapper;
import com.wereen.competitionplatform.model.entity.TokenReward;
import com.wereen.competitionplatform.model.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Forum Token 业务服务：封装链上调用与奖励记录
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ForumTokenService {

    private static final BigDecimal DECIMAL = BigDecimal.TEN.pow(18);

    private final ForumTokenRewardService forumTokenRewardService;
    private final TokenRewardMapper tokenRewardMapper;
    private final UserService userService;

    public BigDecimal getUserTokenBalance(Long userId) {
        String wallet = requireWalletAddress(userId);
        return forumTokenRewardService.getTokenBalance(wallet);
    }

    public ForumRewardInfo getUserRewardInfo(Long userId) {
        String wallet = requireWalletAddress(userId);
        ForumTokenRewardService.OnChainRewardInfo info = forumTokenRewardService.getUserRewardInfo(wallet);
        BigDecimal totalFromDb = getTotalRewardedFromDb(userId);
        BigDecimal onChainTotal = info == null
            ? BigDecimal.ZERO
            : info.getTotalRewarded().divide(DECIMAL);
        BigDecimal totalRewarded = totalFromDb.compareTo(onChainTotal) > 0
            ? totalFromDb
            : onChainTotal;
        if (info == null) {
            return ForumRewardInfo.builder()
                .userId(userId)
                .consecutiveDays(0)
                .dailyRewardAmount(BigDecimal.ZERO)
                .totalRewarded(totalRewarded)
                .lastCheckinTime(null)
                .build();
        }
        return ForumRewardInfo.builder()
            .userId(userId)
            .consecutiveDays(info.getConsecutiveDays())
            .dailyRewardAmount(info.getDailyRewardAmount().divide(DECIMAL))
            .totalRewarded(totalRewarded)
            .lastCheckinTime(info.getLastCheckinTime())
            .build();
    }

    public boolean canCheckinToday(Long userId) {
        String wallet = requireWalletAddress(userId);
        return forumTokenRewardService.canCheckin(wallet);
    }

    public String dailyCheckin(Long userId) {
        String wallet = requireWalletAddress(userId);
        ForumTokenRewardService.RewardConfig config = requireRewardConfig();
        ForumTokenRewardService.OnChainRewardInfo before = forumTokenRewardService.getUserRewardInfo(wallet);
        BigInteger amount = config.getDailyCheckinReward();
        int consecutive = before != null ? before.getConsecutiveDays() : 0;
        if ((consecutive + 1) % 7 == 0) {
            amount = amount.add(config.getConsecutiveBonus());
        }
        Optional<String> tx = forumTokenRewardService.dailyCheckin(wallet);
        String txHash = tx.orElseThrow(() -> new BusinessException("签到失败，请稍后重试"));
        saveRewardRecord(userId, "DAILY_CHECKIN", "daily_checkin", amount, txHash);
        return txHash;
    }

    public String rewardPost(Long userId, String postId) {
        String wallet = requireWalletAddress(userId);
        ForumTokenRewardService.RewardConfig config = requireRewardConfig();
        Optional<String> tx = forumTokenRewardService.rewardPost(wallet, postId);
        String txHash = tx.orElseThrow(() -> new BusinessException("链上发帖奖励失败"));
        saveRewardRecord(userId, "POST", postId, config.getPostReward(), txHash);
        return txHash;
    }

    public String rewardComment(Long userId, String commentId) {
        String wallet = requireWalletAddress(userId);
        ForumTokenRewardService.RewardConfig config = requireRewardConfig();
        Optional<String> tx = forumTokenRewardService.rewardComment(wallet, commentId);
        String txHash = tx.orElseThrow(() -> new BusinessException("链上评论奖励失败"));
        saveRewardRecord(userId, "COMMENT", commentId, config.getCommentReward(), txHash);
        return txHash;
    }

    public String rewardFeaturedPost(Long userId, String postId) {
        String wallet = requireWalletAddress(userId);
        ForumTokenRewardService.RewardConfig config = requireRewardConfig();
        Optional<String> tx = forumTokenRewardService.rewardFeaturedPost(wallet, postId);
        String txHash = tx.orElseThrow(() -> new BusinessException("链上精华奖励失败"));
        saveRewardRecord(userId, "FEATURED_POST", postId, config.getFeaturedPostReward(), txHash);
        return txHash;
    }

    public String rewardContentShare(Long userId, Long shareId, String mediaType) {
        String wallet = requireWalletAddress(userId);
        ForumTokenRewardService.RewardConfig config = requireRewardConfig();
        boolean isVideo = "VIDEO".equalsIgnoreCase(mediaType);
        BigInteger amount = isVideo ? config.getContentVideoReward() : config.getContentImageReward();

        // 数据库侧防重复：同一用户 + 同一内容只奖励一次（无论图片/视频）
        String baseContentId = "share_" + shareId;
        long existed = tokenRewardMapper.selectCount(
            new LambdaQueryWrapper<TokenReward>()
                .eq(TokenReward::getUserId, userId)
                .eq(TokenReward::getContentId, baseContentId)
                .in(TokenReward::getRewardType, "CONTENT_IMAGE", "CONTENT_VIDEO")
        );
        if (existed > 0) {
            log.info("内容分享已在数据库中记录奖励，不再重复发放: userId={}, shareId={}", userId, shareId);
            return null;
        }

        // 链上侧使用更长、更唯一的 contentId，避免与历史测试数据冲突
        String onchainContentId = baseContentId + "_" + System.currentTimeMillis();

        Optional<String> tx = forumTokenRewardService.rewardContentShare(
            wallet,
            onchainContentId,
            isVideo
        );
        String txHash = tx.orElse(null);
        if (txHash != null) {
            saveRewardRecord(
                userId,
                isVideo ? "CONTENT_VIDEO" : "CONTENT_IMAGE",
                baseContentId,
                amount,
                txHash
            );
        }
        return txHash;
    }

    // ===================== 异步封装：避免阻塞主业务请求 =====================

    @Async
    public void rewardPostAsync(Long userId, String postId) {
        try {
            String txHash = rewardPost(userId, postId);
            log.info("异步发帖代币奖励成功: userId={}, postId={}, txHash={}", userId, postId, txHash);
        } catch (Exception e) {
            log.warn("异步发帖代币奖励失败（不影响发帖）: userId={}, postId={}, error={}", userId, postId, e.getMessage());
        }
    }

    @Async
    public void rewardCommentAsync(Long userId, String commentId) {
        try {
            String txHash = rewardComment(userId, commentId);
            log.info("异步评论代币奖励成功: userId={}, commentId={}, txHash={}", userId, commentId, txHash);
        } catch (Exception e) {
            log.warn("异步评论代币奖励失败（不影响评论）: userId={}, commentId={}, error={}", userId, commentId, e.getMessage());
        }
    }

    @Async
    public void rewardFeaturedPostAsync(Long userId, String postId) {
        try {
            String txHash = rewardFeaturedPost(userId, postId);
            log.info("异步精华帖子奖励成功: userId={}, postId={}, txHash={}", userId, postId, txHash);
        } catch (Exception e) {
            log.warn("异步精华帖子奖励失败: userId={}, postId={}, error={}", userId, postId, e.getMessage());
        }
    }

    @Async
    public void rewardContentShareAsync(Long userId, Long shareId, String mediaType) {
        try {
            String txHash = rewardContentShare(userId, shareId, mediaType);
            log.info("异步内容分享奖励成功: userId={}, shareId={}, mediaType={}, txHash={}",
                userId, shareId, mediaType, txHash);
        } catch (Exception e) {
            log.warn("异步内容分享奖励失败（不影响内容发布）: userId={}, shareId={}, error={}",
                userId, shareId, e.getMessage());
        }
    }

    public PageResult<TokenRewardView> getRewardHistory(Long userId, long current, long size) {
        Page<TokenReward> page = new Page<>(current, size);
        LambdaQueryWrapper<TokenReward> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TokenReward::getUserId, userId)
            .orderByDesc(TokenReward::getCreateTime);
        Page<TokenReward> result = tokenRewardMapper.selectPage(page, wrapper);
        return new PageResult<>(
            result.getTotal(),
            result.getCurrent(),
            result.getSize(),
            result.getRecords().stream().map(TokenRewardView::fromEntity).toList()
        );
    }

    private ForumTokenRewardService.RewardConfig requireRewardConfig() {
        ForumTokenRewardService.RewardConfig config = forumTokenRewardService.fetchRewardConfig();
        if (config == null) {
            throw new BusinessException("无法获取链上奖励配置");
        }
        return config;
    }

    private String requireWalletAddress(Long userId) {
        User user = userService.getUserById(userId);
        if (user == null || !StringUtils.hasText(user.getWalletAddress())) {
            throw new BusinessException("请先绑定钱包地址");
        }
        return user.getWalletAddress();
    }

    private void saveRewardRecord(Long userId, String rewardType, String contentId,
                                  BigInteger amount, String txHash) {
        TokenReward reward = new TokenReward();
        reward.setUserId(userId);
        reward.setRewardType(rewardType);
        reward.setContentId(contentId);
        reward.setAmount(amount);
        reward.setTxHash(txHash);
        tokenRewardMapper.insert(reward);
    }

    private BigDecimal getTotalRewardedFromDb(Long userId) {
        BigDecimal total = tokenRewardMapper.sumPositiveRewardsByUser(userId);
        if (total == null || total.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return total.divide(DECIMAL);
    }

    @Data
    @Builder
    public static class ForumRewardInfo {
        private Long userId;
        private Integer consecutiveDays;
        private BigDecimal dailyRewardAmount;
        private BigDecimal totalRewarded;
        private LocalDateTime lastCheckinTime;
    }

    @Data
    @AllArgsConstructor
    public static class TokenRewardView {
        private Long id;
        private String rewardType;
        private String contentId;
        private String amount;
        private String txHash;
        private LocalDateTime createTime;

        public static TokenRewardView fromEntity(TokenReward entity) {
            BigDecimal decimalAmount = new BigDecimal(
                entity.getAmount() == null ? BigInteger.ZERO : entity.getAmount()
            ).divide(DECIMAL);
            return new TokenRewardView(
                entity.getId(),
                entity.getRewardType(),
                entity.getContentId(),
                decimalAmount.stripTrailingZeros().toPlainString(),
                entity.getTxHash(),
                entity.getCreateTime()
            );
        }
    }
}
