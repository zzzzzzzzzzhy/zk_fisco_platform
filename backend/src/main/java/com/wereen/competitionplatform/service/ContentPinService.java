package com.wereen.competitionplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wereen.competitionplatform.exception.BusinessException;
import com.wereen.competitionplatform.mapper.ContentPinMapper;
import com.wereen.competitionplatform.mapper.UserMapper;
import com.wereen.competitionplatform.mapper.UserWalletMapper;
import com.wereen.competitionplatform.model.entity.ContentPin;
import com.wereen.competitionplatform.model.entity.User;
import com.wereen.competitionplatform.model.entity.UserWallet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 内容置顶服务
 */
@Slf4j
@Service
public class ContentPinService extends ServiceImpl<ContentPinMapper, ContentPin> {

    @Autowired
    private ForumTokenRewardService forumTokenRewardService;

    @Autowired
    private ForumTokenService forumTokenService;

    @Autowired
    private ContentShareService contentShareService;

    @Autowired
    private ForumPostService forumPostService;

    @Autowired
    private UserWalletMapper userWalletMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private TokenRewardService tokenRewardService;

    private static final BigDecimal PIN_PRICE = new BigDecimal("50"); // 50 MTK
    private static final int PIN_DURATION_HOURS = 24; // 置顶24小时

    /**
     * 购买内容置顶
     *
     * 新版流程（推荐）：
     * - 前端先调用 ForumTokenExtension.purchasePinPost，由用户钱包支付 50 WEE + Gas
     * - 前端再把 txHash / blockNumber 传给后端，后端只做记录与状态更新
     *
     * 兼容旧逻辑：
     * - 如果 txHash 为空，则仍由后端通过 ForumTokenRewardService.purchasePinPost 发起链上交易
     */
    @Transactional(rollbackFor = Exception.class)
    public ContentPin purchasePin(Long userId, String contentType, Long contentId, String txHashFromFrontend, Long blockNumberFromFrontend) {
        // 检查内容是否存在
        validateContent(contentType, contentId);

        // 检查是否已经置顶
        if (baseMapper.isContentPinned(contentType, contentId)) {
            throw new BusinessException("该内容已经置顶");
        }

        // 检查钱包信息（无论新旧流程都需要）
        UserWallet wallet = requireWallet(userId);

        // 创建置顶记录
        ContentPin pin = new ContentPin();
        pin.setUserId(userId);
        pin.setContentType(contentType);
        pin.setContentId(contentId);
        pin.setAmount(PIN_PRICE);
        pin.setStartTime(LocalDateTime.now());
        pin.setEndTime(LocalDateTime.now().plusHours(PIN_DURATION_HOURS));
        pin.setStatus(0); // 待生效
        pin.setCreatedAt(LocalDateTime.now());
        pin.setUpdatedAt(LocalDateTime.now());

        this.save(pin);

        try {
            String txHash;
            Long blockNumber;

            if (txHashFromFrontend != null && !txHashFromFrontend.isEmpty()) {
                // 新版：前端已完成链上交易，这里只记录
                txHash = txHashFromFrontend;
                blockNumber = blockNumberFromFrontend != null ? blockNumberFromFrontend : System.currentTimeMillis();
            } else {
                // 旧逻辑已废弃：置顶功能必须由前端钱包发起交易
                throw new BusinessException("置顶失败: 请使用钱包支付置顶费用");
            }

            // 更新置顶记录
            pin.setTxHash(txHash);
            pin.setBlockNumber(blockNumber);
            pin.setStatus(1); // 生效中
            pin.setUpdatedAt(LocalDateTime.now());
            this.updateById(pin);

            // 更新内容的置顶状态
            updateContentPinStatus(contentType, contentId, true, pin.getEndTime());

            // 记录代币账单（置顶支出）
            tokenRewardService.recordPinConsume(userId, contentType, contentId.toString(), PIN_PRICE, txHash);

            log.info("内容置顶成功: userId={}, contentType={}, contentId={}, txHash={}",
                    userId, contentType, contentId, txHash);

            return pin;

        } catch (Exception e) {
            // 更新置顶记录为失败
            pin.setStatus(2); // 失败
            pin.setErrorMessage(e.getMessage());
            pin.setUpdatedAt(LocalDateTime.now());
            this.updateById(pin);

            log.error("内容置顶失败: userId={}, contentType={}, contentId={}, error={}",
                    userId, contentType, contentId, e.getMessage());

            throw new BusinessException("置顶失败: " + e.getMessage());
        }
    }

    /**
     * 验证内容是否存在
     */
    private void validateContent(String contentType, Long contentId) {
        switch (contentType) {
            case "POST":
                // 简化验证，直接检查帖子ID是否存在
        // TODO: 添加更好的帖子验证逻辑
        if (contentId == null || contentId <= 0) {
                    throw new BusinessException("帖子不存在");
                }
                break;
            case "CONTENT_SHARE":
                if (contentShareService.getById(contentId) == null) {
                    throw new BusinessException("内容分享不存在");
                }
                break;
            default:
                throw new BusinessException("不支持的内容类型");
        }
    }

    /**
     * 执行链上置顶
     */
    private String executeChainPin(UserWallet wallet, String contentType, String contentId, BigDecimal amount) throws Exception {
        if (wallet.getAddress() == null) {
            throw new Exception("用户钱包地址不存在");
        }

        return forumTokenRewardService.purchasePinPost(wallet.getAddress(), contentId, amount);
    }

    /**
     * 更新内容的置顶状态
     */
    private void updateContentPinStatus(String contentType, Long contentId, boolean pinned, LocalDateTime endTime) {
        switch (contentType) {
            case "POST":
                // 更新帖子置顶状态
                // 这里需要相应的Mapper来更新
                break;
            case "CONTENT_SHARE":
                // 更新内容分享置顶状态
                contentShareService.updatePinStatus(contentId, pinned, endTime);
                break;
        }
    }

    /**
     * 查询当前生效的置顶记录
     */
    public List<ContentPin> getActivePins() {
        return baseMapper.selectActivePins();
    }

    /**
     * 查询内容的置顶记录
     */
    public List<ContentPin> getPinByContent(String contentType, Long contentId) {
        return baseMapper.selectByContent(contentType, contentId);
    }

    /**
     * 查询用户购买的置顶记录
     */
    public List<ContentPin> getPinByUserId(Long userId) {
        return baseMapper.selectByUserId(userId);
    }

    /**
     * 检查内容是否置顶
     */
    public boolean isContentPinned(String contentType, Long contentId) {
        return baseMapper.isContentPinned(contentType, contentId);
    }

    /**
     * 定时任务：更新过期的置顶记录
     */
    @Scheduled(fixedRate = 300000) // 每5分钟执行一次
    public void updateExpiredPins() {
        try {
            int updated = baseMapper.updateExpiredPins();
            if (updated > 0) {
                log.info("更新了{}条过期置顶记录", updated);

                // 更新相关内容的置顶状态
                updateExpiredContentPins();
            }
        } catch (Exception e) {
            log.error("更新过期置顶记录失败", e);
        }
    }

    /**
     * 更新过期内容的置顶状态
     */
    private void updateExpiredContentPins() {
        QueryWrapper<ContentPin> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("status", 2); // 已过期的置顶记录
        List<ContentPin> expiredPins = this.list(queryWrapper);

        for (ContentPin pin : expiredPins) {
            updateContentPinStatus(pin.getContentType(), pin.getContentId(), false, null);
        }
    }

    private UserWallet requireWallet(Long userId) {
        UserWallet wallet = userWalletMapper.selectByUserId(userId);
        if (wallet == null) {
            User user = userMapper.selectById(userId);
            if (user != null && StringUtils.hasText(user.getWalletAddress())) {
                wallet = new UserWallet();
                wallet.setUserId(userId);
                wallet.setWalletAddress(user.getWalletAddress());
                wallet.setBalance(BigDecimal.ZERO);
                wallet.setCreateTime(LocalDateTime.now());
                wallet.setUpdateTime(LocalDateTime.now());
                userWalletMapper.insert(wallet);
            }
        }
        if (wallet == null || !StringUtils.hasText(wallet.getAddress())) {
            throw new BusinessException("请先绑定钱包地址");
        }
        return wallet;
    }

    private void deductWalletBalance(UserWallet wallet, BigDecimal currentBalance) {
        BigDecimal balance = currentBalance == null ? BigDecimal.ZERO : currentBalance;
        wallet.setBalance(balance.subtract(PIN_PRICE));
        wallet.setUpdateTime(LocalDateTime.now());
        userWalletMapper.updateById(wallet);
    }
}
