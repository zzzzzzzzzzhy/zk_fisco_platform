package com.wereen.competitionplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wereen.competitionplatform.common.PageResult;
import com.wereen.competitionplatform.exception.BusinessException;
import com.wereen.competitionplatform.mapper.WalletBalanceMapper;
import com.wereen.competitionplatform.mapper.WalletTransactionMapper;
import com.wereen.competitionplatform.model.entity.WalletBalance;
import com.wereen.competitionplatform.model.entity.WalletTransaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 钱包服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletBalanceMapper walletBalanceMapper;
    private final WalletTransactionMapper walletTransactionMapper;

    /**
     * 获取或创建用户钱包余额
     */
    @Transactional(rollbackFor = Exception.class)
    public WalletBalance getOrCreateBalance(Long userId, String currency) {
        LambdaQueryWrapper<WalletBalance> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WalletBalance::getUserId, userId)
                .eq(WalletBalance::getCurrency, currency);

        WalletBalance balance = walletBalanceMapper.selectOne(wrapper);

        if (balance == null) {
            balance = new WalletBalance();
            balance.setUserId(userId);
            balance.setCurrency(currency);
            balance.setBalance(0L);
            balance.setFrozenAmount(0L);
            balance.setVersion(0);

            walletBalanceMapper.insert(balance);
            log.info("创建用户钱包: userId={}, currency={}", userId, currency);
        }

        return balance;
    }

    /**
     * 查询用户余额
     */
    public WalletBalance getUserBalance(Long userId, String currency) {
        return getOrCreateBalance(userId, currency);
    }

    /**
     * 增加余额（奖金入账）
     */
    @Transactional(rollbackFor = Exception.class)
    public void increaseBalance(Long userId, String currency, Long amount, String bizRef, String remark) {
        if (amount <= 0) {
            throw new BusinessException("金额必须大于0");
        }

        WalletBalance balance = getOrCreateBalance(userId, currency);

        // 使用乐观锁更新余额
        balance.setBalance(balance.getBalance() + amount);
        int rows = walletBalanceMapper.updateById(balance);

        if (rows == 0) {
            throw new BusinessException("余额更新失败，请重试");
        }

        // 记录交易流水
        WalletTransaction transaction = new WalletTransaction();
        transaction.setUserId(userId);
        transaction.setType("PRIZE_IN");
        transaction.setAmount(amount);
        transaction.setBalanceAfter(balance.getBalance());
        transaction.setBizRef(bizRef);
        transaction.setStatus("SUCCESS");
        transaction.setRemark(remark);

        walletTransactionMapper.insert(transaction);

        log.info("余额入账成功: userId={}, amount={}, balanceAfter={}", userId, amount, balance.getBalance());
    }

    /**
     * 冻结余额（提现申请）
     */
    @Transactional(rollbackFor = Exception.class)
    public void freezeBalance(Long userId, String currency, Long amount, String bizRef) {
        if (amount <= 0) {
            throw new BusinessException("金额必须大于0");
        }

        WalletBalance balance = getOrCreateBalance(userId, currency);

        if (balance.getBalance() < amount) {
            throw new BusinessException("余额不足");
        }

        // 使用乐观锁更新
        balance.setBalance(balance.getBalance() - amount);
        balance.setFrozenAmount(balance.getFrozenAmount() + amount);
        int rows = walletBalanceMapper.updateById(balance);

        if (rows == 0) {
            throw new BusinessException("余额冻结失败，请重试");
        }

        // 记录交易流水
        WalletTransaction transaction = new WalletTransaction();
        transaction.setUserId(userId);
        transaction.setType("WITHDRAW_FREEZE");
        transaction.setAmount(amount);
        transaction.setBalanceAfter(balance.getBalance());
        transaction.setBizRef(bizRef);
        transaction.setStatus("SUCCESS");

        walletTransactionMapper.insert(transaction);

        log.info("余额冻结成功: userId={}, amount={}, frozen={}", userId, amount, balance.getFrozenAmount());
    }

    /**
     * 解冻余额（提现失败/取消）
     */
    @Transactional(rollbackFor = Exception.class)
    public void unfreezeBalance(Long userId, String currency, Long amount, String bizRef) {
        if (amount <= 0) {
            throw new BusinessException("金额必须大于0");
        }

        WalletBalance balance = getOrCreateBalance(userId, currency);

        if (balance.getFrozenAmount() < amount) {
            throw new BusinessException("冻结金额不足");
        }

        // 使用乐观锁更新
        balance.setBalance(balance.getBalance() + amount);
        balance.setFrozenAmount(balance.getFrozenAmount() - amount);
        int rows = walletBalanceMapper.updateById(balance);

        if (rows == 0) {
            throw new BusinessException("余额解冻失败，请重试");
        }

        // 记录交易流水
        WalletTransaction transaction = new WalletTransaction();
        transaction.setUserId(userId);
        transaction.setType("REFUND");
        transaction.setAmount(amount);
        transaction.setBalanceAfter(balance.getBalance());
        transaction.setBizRef(bizRef);
        transaction.setStatus("SUCCESS");

        walletTransactionMapper.insert(transaction);

        log.info("余额解冻成功: userId={}, amount={}, balance={}", userId, amount, balance.getBalance());
    }

    /**
     * 扣除冻结金额（提现成功）
     */
    @Transactional(rollbackFor = Exception.class)
    public void deductFrozenAmount(Long userId, String currency, Long amount, String bizRef) {
        if (amount <= 0) {
            throw new BusinessException("金额必须大于0");
        }

        WalletBalance balance = getOrCreateBalance(userId, currency);

        if (balance.getFrozenAmount() < amount) {
            throw new BusinessException("冻结金额不足");
        }

        // 使用乐观锁更新
        balance.setFrozenAmount(balance.getFrozenAmount() - amount);
        int rows = walletBalanceMapper.updateById(balance);

        if (rows == 0) {
            throw new BusinessException("扣除冻结金额失败，请重试");
        }

        // 记录交易流水
        WalletTransaction transaction = new WalletTransaction();
        transaction.setUserId(userId);
        transaction.setType("WITHDRAW_SUCCESS");
        transaction.setAmount(amount);
        transaction.setBalanceAfter(balance.getBalance());
        transaction.setBizRef(bizRef);
        transaction.setStatus("SUCCESS");

        walletTransactionMapper.insert(transaction);

        log.info("扣除冻结金额成功: userId={}, amount={}", userId, amount);
    }

    /**
     * 查询交易流水
     */
    public PageResult<WalletTransaction> getTransactions(Long userId, String type, Long current, Long size) {
        Page<WalletTransaction> page = new Page<>(current, size);
        LambdaQueryWrapper<WalletTransaction> wrapper = new LambdaQueryWrapper<>();

        wrapper.eq(WalletTransaction::getUserId, userId);
        if (type != null && !type.isEmpty()) {
            wrapper.eq(WalletTransaction::getType, type);
        }
        wrapper.orderByDesc(WalletTransaction::getCreatedAt);

        Page<WalletTransaction> resultPage = walletTransactionMapper.selectPage(page, wrapper);

        return new PageResult<>(
                resultPage.getTotal(),
                resultPage.getCurrent(),
                resultPage.getSize(),
                resultPage.getRecords()
        );
    }
}
