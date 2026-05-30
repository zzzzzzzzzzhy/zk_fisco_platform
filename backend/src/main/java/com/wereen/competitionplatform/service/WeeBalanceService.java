package com.wereen.competitionplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.wereen.competitionplatform.mapper.WalletBalanceMapper;
import com.wereen.competitionplatform.model.entity.WalletBalance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * WEE 虚拟积分余额服务
 *
 * 使用 wallet_balance 表（currency='WEE'）存储每个用户的积分余额，
 * 1 WEE = 1 单位，无需链上交互，纯数据库操作。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeeBalanceService {

    // 奖励配置（单位：WEE）
    public static final long REWARD_POST      = 10;
    public static final long REWARD_COMMENT   = 2;
    public static final long REWARD_CHECKIN   = 5;
    public static final long REWARD_CONTENT_IMAGE = 5;
    public static final long REWARD_CONTENT_VIDEO = 10;

    private static final String CURRENCY = "WEE";

    private final WalletBalanceMapper walletBalanceMapper;

    /**
     * 查询用户 WEE 余额，不存在则返回 0
     */
    public long getBalance(Long userId) {
        WalletBalance wb = findOrNull(userId);
        return wb == null ? 0L : (wb.getBalance() == null ? 0L : wb.getBalance());
    }

    /**
     * 增加 WEE 余额（线程安全，乐观锁）
     */
    @Transactional(rollbackFor = Exception.class)
    public void addReward(Long userId, long amount, String reason) {
        if (amount <= 0) return;
        WalletBalance wb = findOrNull(userId);
        if (wb == null) {
            wb = new WalletBalance();
            wb.setUserId(userId);
            wb.setCurrency(CURRENCY);
            wb.setBalance(amount);
            wb.setFrozenAmount(0L);
            walletBalanceMapper.insert(wb);
        } else {
            walletBalanceMapper.update(null,
                new LambdaUpdateWrapper<WalletBalance>()
                    .eq(WalletBalance::getId, wb.getId())
                    .eq(WalletBalance::getVersion, wb.getVersion())
                    .setSql("balance = balance + " + amount)
                    .set(WalletBalance::getVersion, wb.getVersion() + 1)
            );
        }
        log.info("WEE 奖励 +{}: userId={}, reason={}", amount, userId, reason);
    }

    private WalletBalance findOrNull(Long userId) {
        return walletBalanceMapper.selectOne(
            new LambdaQueryWrapper<WalletBalance>()
                .eq(WalletBalance::getUserId, userId)
                .eq(WalletBalance::getCurrency, CURRENCY)
                .eq(WalletBalance::getDeleted, 0)
        );
    }
}
