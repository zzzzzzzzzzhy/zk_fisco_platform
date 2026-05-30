package com.wereen.competitionplatform.service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wereen.competitionplatform.mapper.GasTransactionMapper;
import com.wereen.competitionplatform.model.entity.GasTransaction;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Gas 交易记录服务，用于支撑后台 Gas 监控面板
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GasTransactionService {

    private final GasTransactionMapper gasTransactionMapper;

    private static final BigDecimal WEI_TO_GWEI = BigDecimal.valueOf(1_000_000_000L);
    private static final BigDecimal WEI_TO_MATIC = BigDecimal.valueOf(1_000_000_000_000_000_000L);

    /**
     * 记录一笔 ForumToken 相关交易的 Gas 消耗
     *
     * @param action  调用动作标识，例如 ForumTokenExtension.dailyCheckin
     * @param txHash  交易哈希
     * @param from    发送方地址
     * @param to      接收方地址
     * @param contract 合约地址
     * @param gasUsed Gas 使用量（单位：gas）
     * @param gasPriceWei Gas 单价（单位：wei）
     * @param blockNumber 区块高度
     * @param success 是否成功
     */
    public void recordForumTx(String action,
                              String txHash,
                              String from,
                              String to,
                              String contract,
                              BigInteger gasUsed,
                              BigInteger gasPriceWei,
                              BigInteger blockNumber,
                              boolean success) {
        try {
            if (gasUsed == null || gasPriceWei == null) {
                log.warn("recordForumTx 缺少 gasUsed 或 gasPriceWei, txHash={}", txHash);
                return;
            }

            BigDecimal gasPriceGwei = new BigDecimal(gasPriceWei)
                .divide(WEI_TO_GWEI, 6, RoundingMode.HALF_UP);
            BigDecimal gasFeeMatic = new BigDecimal(gasPriceWei)
                .multiply(new BigDecimal(gasUsed))
                .divide(WEI_TO_MATIC, 18, RoundingMode.HALF_UP);

            String bizType = resolveBizType(action);

            GasTransaction tx = new GasTransaction()
                .setTxHash(txHash)
                .setFromAddress(from)
                .setToAddress(to)
                .setContractAddress(contract)
                .setBizType(bizType)
                .setGasUsed(gasUsed.longValue())
                .setGasPriceGwei(gasPriceGwei)
                .setGasFeeMatic(gasFeeMatic)
                .setBlockNumber(blockNumber != null ? blockNumber.longValue() : null)
                .setSuccess(success);

            gasTransactionMapper.insert(tx);
            log.info("记录 Gas 交易: action={}, bizType={}, txHash={}, gasFee={} MATIC",
                action, bizType, txHash, gasFeeMatic);
        } catch (Exception e) {
            log.error("记录 Gas 交易失败: txHash={}", txHash, e);
        }
    }

    private String resolveBizType(String action) {
        if (action == null) {
            return "OTHER";
        }
        if (action.endsWith(".dailyCheckin")) {
            return "CHECKIN";
        }
        if (action.endsWith(".rewardPost") || action.endsWith(".rewardContentShare")) {
            return "POST";
        }
        if (action.endsWith(".rewardComment")) {
            return "COMMENT";
        }
        if (action.endsWith(".tipContent")) {
            return "TIP";
        }
        if (action.endsWith(".purchasePinPost")) {
            return "PIN";
        }
        return "OTHER";
    }

    /**
     * 最近 N 条交易记录（用于表格）
     */
    public List<GasTransaction> listRecentTransactions(int limit) {
        LambdaQueryWrapper<GasTransaction> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GasTransaction::getSuccess, true)
            .orderByDesc(GasTransaction::getCreateTime)
            .last("LIMIT " + Math.max(limit, 1));
        return gasTransactionMapper.selectList(wrapper);
    }

    /**
     * 统计最近 24 小时每小时的 Gas 费用（MATIC）
     */
    public List<HourlyGasStat> getLast24HoursTrend() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime from = now.minusHours(24);

        LambdaQueryWrapper<GasTransaction> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GasTransaction::getSuccess, true)
            .ge(GasTransaction::getCreateTime, from)
            .le(GasTransaction::getCreateTime, now);

        List<GasTransaction> list = gasTransactionMapper.selectList(wrapper);

        Map<Integer, BigDecimal> hourToFee = new java.util.HashMap<>();
        for (GasTransaction tx : list) {
            LocalDateTime t = tx.getCreateTime();
            if (t == null) continue;
            int hourOffset = (int) ChronoUnit.HOURS.between(from.truncatedTo(ChronoUnit.HOURS),
                t.truncatedTo(ChronoUnit.HOURS));
            if (hourOffset < 0 || hourOffset >= 24) continue;
            BigDecimal fee = tx.getGasFeeMatic() != null ? tx.getGasFeeMatic() : BigDecimal.ZERO;
            hourToFee.merge(hourOffset, fee, BigDecimal::add);
        }

        List<HourlyGasStat> result = new ArrayList<>();
        LocalDateTime cursor = from.truncatedTo(ChronoUnit.HOURS);
        for (int i = 0; i < 24; i++) {
            LocalDateTime slot = cursor.plusHours(i);
            String label = String.format("%02d:00", slot.getHour());
            BigDecimal value = hourToFee.getOrDefault(i, BigDecimal.ZERO);
            result.add(new HourlyGasStat(label, value));
        }
        return result;
    }

    /**
     * 统计最近 N 小时内，不同业务类型的 Gas 消耗占比
     */
    public List<BizTypeStat> getBizTypeDistribution(int hours) {
        if (hours <= 0) {
            hours = 24;
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime from = now.minusHours(hours);

        LambdaQueryWrapper<GasTransaction> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GasTransaction::getSuccess, true)
            .ge(GasTransaction::getCreateTime, from)
            .le(GasTransaction::getCreateTime, now);

        List<GasTransaction> list = gasTransactionMapper.selectList(wrapper);

        Map<String, BigDecimal> bizToFee = new java.util.HashMap<>();
        for (GasTransaction tx : list) {
            String biz = tx.getBizType() != null ? tx.getBizType() : "OTHER";
            BigDecimal fee = tx.getGasFeeMatic() != null ? tx.getGasFeeMatic() : BigDecimal.ZERO;
            bizToFee.merge(biz, fee, BigDecimal::add);
        }

        List<BizTypeStat> result = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : bizToFee.entrySet()) {
            result.add(new BizTypeStat(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    @Data
    @AllArgsConstructor
    public static class HourlyGasStat {
        /**
         * 小时标签，如 09:00
         */
        private String hourLabel;
        /**
         * 本小时总 Gas 费用（MATIC）
         */
        private BigDecimal totalGasFee;
    }

    @Data
    @AllArgsConstructor
    public static class BizTypeStat {
        /**
         * 业务类型
         */
        private String bizType;
        /**
         * 总 Gas 费用（MATIC）
         */
        private BigDecimal totalGasFee;
    }
}


