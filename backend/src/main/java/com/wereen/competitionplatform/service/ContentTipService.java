package com.wereen.competitionplatform.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wereen.competitionplatform.exception.BusinessException;
import com.wereen.competitionplatform.mapper.ContentTipMapper;
import com.wereen.competitionplatform.mapper.UserMapper;
import com.wereen.competitionplatform.mapper.UserWalletMapper;
import com.wereen.competitionplatform.model.entity.ContentTip;
import com.wereen.competitionplatform.model.entity.User;
import com.wereen.competitionplatform.model.entity.UserWallet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 内容打赏服务
 */
@Slf4j
@Service
public class ContentTipService extends ServiceImpl<ContentTipMapper, ContentTip> {

    @Autowired
    private ForumTokenService forumTokenService;

    @Autowired
    private UserWalletMapper userWalletMapper;

    @Autowired
    private UserMapper userMapper;

    /**
     * 创建打赏记录
     */
    @Transactional(rollbackFor = Exception.class)
    public ContentTip createTip(Long tipperId, Long creatorId, String contentType,
                               Long contentId, BigDecimal amount, String txHash, Long blockNumber) {
        // 验证参数
        if (tipperId.equals(creatorId)) {
            throw new BusinessException("不能给自己打赏");
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("打赏金额必须大于0");
        }

        if (!StringUtils.hasText(txHash)) {
            throw new BusinessException("交易哈希不能为空");
        }

        // 检查打赏者链上余额，确保交易真实发生
        BigDecimal tokenBalance = forumTokenService.getUserTokenBalance(tipperId);
        if (tokenBalance.compareTo(amount) < 0) {
            throw new BusinessException("余额不足");
        }

        requireWallet(tipperId);
        requireWallet(creatorId);

        // 创建打赏记录
        ContentTip tip = new ContentTip();
        tip.setTipperId(tipperId);
        tip.setCreatorId(creatorId);
        tip.setContentType(contentType);
        tip.setContentId(contentId);
        tip.setAmount(amount);
        tip.setStatus(0); // 待处理
        tip.setCreatedAt(LocalDateTime.now());
        tip.setUpdatedAt(LocalDateTime.now());

        this.save(tip);

        // 记录链上交易结果
        tip.setTxHash(txHash);
        tip.setBlockNumber(blockNumber);
        tip.setStatus(1); // 成功
        tip.setUpdatedAt(LocalDateTime.now());
        this.updateById(tip);

        refreshWalletBalance(tipperId);
        refreshWalletBalance(creatorId);

        log.info("打赏记录已生成: tipperId={}, creatorId={}, amount={}, txHash={}",
                tipperId, creatorId, amount, txHash);

        return tip;
    }

    private UserWallet requireWallet(Long userId) {
        UserWallet wallet = userWalletMapper.selectByUserId(userId);
        if (wallet != null && StringUtils.hasText(wallet.getWalletAddress())) {
            return wallet;
        }

        User user = userMapper.selectById(userId);
        if (user == null || !StringUtils.hasText(user.getWalletAddress())) {
            throw new BusinessException("请先绑定钱包地址");
        }

        if (wallet == null) {
            wallet = new UserWallet();
            wallet.setUserId(userId);
            wallet.setBalance(BigDecimal.ZERO);
            wallet.setCreateTime(LocalDateTime.now());
        }
        wallet.setWalletAddress(user.getWalletAddress());
        wallet.setUpdateTime(LocalDateTime.now());

        if (wallet.getId() == null) {
            userWalletMapper.insert(wallet);
        } else {
            userWalletMapper.updateById(wallet);
        }
        return wallet;
    }

    private void refreshWalletBalance(Long userId) {
        try {
            BigDecimal balance = forumTokenService.getUserTokenBalance(userId);
            UserWallet wallet = userWalletMapper.selectByUserId(userId);
            if (wallet == null) {
                wallet = requireWallet(userId);
            }
            wallet.setBalance(balance);
            wallet.setUpdateTime(LocalDateTime.now());
            userWalletMapper.updateById(wallet);
        } catch (Exception e) {
            log.warn("刷新用户钱包余额失败: userId={}, error={}", userId, e.getMessage());
        }
    }

    /**
     * 查询创作者收到的打赏记录
     */
    public List<ContentTip> getTipsByCreator(Long creatorId, int limit) {
        return baseMapper.selectByCreatorId(creatorId, limit);
    }

    /**
     * 查询内容的打赏记录
     */
    public List<ContentTip> getTipsByContent(String contentType, Long contentId) {
        return baseMapper.selectByContent(contentType, contentId);
    }

    /**
     * 查询创作者收到的总打赏金额
     */
    public BigDecimal getTotalTipsByCreator(Long creatorId) {
        return baseMapper.selectTotalTipsByCreator(creatorId);
    }

    /**
     * 查询内容的总打赏金额
     */
    public BigDecimal getTotalTipsByContent(String contentType, Long contentId) {
        return baseMapper.selectTotalTipsByContent(contentType, contentId);
    }

    /**
     * 查询用户的打赏统计
     */
    public ContentTip getUserTipStats(Long userId) {
        return baseMapper.selectUserTipStats(userId);
    }
}
