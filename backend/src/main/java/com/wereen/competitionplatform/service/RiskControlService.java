package com.wereen.competitionplatform.service;

import cn.hutool.crypto.SecureUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wereen.competitionplatform.exception.BusinessException;
import com.wereen.competitionplatform.mapper.RiskControlRecordMapper;
import com.wereen.competitionplatform.model.entity.PrizeAllocation;
import com.wereen.competitionplatform.model.entity.RiskControlRecord;
import com.wereen.competitionplatform.model.entity.UserKyc;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 风控服务
 *
 * 功能：
 * 1. 黑名单校验
 * 2. 欺诈检测（异常行为模式识别）
 * 3. 反洗钱（AML）检查
 * 4. 风险评分（0-100）
 * 5. 自动决策（低风险/中风险/高风险）
 * 6. 人工审核流程
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiskControlService {

    private final RiskControlRecordMapper riskControlRecordMapper;
    private final UserKycService userKycService;

    /**
     * 风险等级阈值
     */
    private static final int LOW_RISK_THRESHOLD = 30;
    private static final int MEDIUM_RISK_THRESHOLD = 60;

    /**
     * 黑名单缓存（实际应使用Redis或数据库）
     * TODO: 后期扩展 - 使用Redis缓存黑名单
     */
    private static final java.util.Set<String> BLACKLIST = new java.util.HashSet<>();

    /**
     * 执行风控检查
     *
     * @param allocationId 奖金分配ID
     * @param userId 用户ID
     * @param prizeAmount 奖金金额（分）
     * @return 风控记录
     */
    @Transactional(rollbackFor = Exception.class)
    public RiskControlRecord performRiskCheck(Long allocationId, Long userId, Long prizeAmount) {
        log.info("开始风控检查: allocationId={}, userId={}, prizeAmount={}", allocationId, userId, prizeAmount);

        // 获取用户KYC信息
        UserKyc kyc = userKycService.getUserKyc(userId);
        if (kyc == null || !"APPROVED".equals(kyc.getStatus())) {
            throw new BusinessException("用户KYC未通过，无法进行风控检查");
        }

        // 创建风控记录
        RiskControlRecord record = new RiskControlRecord();
        record.setAllocationId(allocationId);
        record.setUserId(userId);
        record.setPrizeAmount(prizeAmount);
        record.setIdCardHash(kyc.getIdCardHash());
        record.setMobileHash(kyc.getMobileHash());
        record.setBankCardHash(kyc.getBankCardHash());

        // 1. 黑名单检查
        boolean isBlacklisted = checkBlacklist(kyc);
        record.setBlacklistHit(isBlacklisted ? 1 : 0);

        // 2. 欺诈检测
        int fraudScore = detectFraud(userId, kyc, prizeAmount);
        record.setFraudScore(fraudScore);

        // 3. AML检查
        int amlScore = checkAML(userId, kyc, prizeAmount);
        record.setAmlScore(amlScore);

        // 4. 计算综合风险分数（0-100）
        int totalScore = calculateTotalRiskScore(isBlacklisted, fraudScore, amlScore);
        record.setRiskScore(totalScore);

        // 5. 确定风险等级和决策
        determineRiskLevelAndDecision(record, totalScore, isBlacklisted);

        // 6. 记录风险原因
        StringBuilder reasons = new StringBuilder();
        if (isBlacklisted) {
            reasons.append("命中黑名单;");
        }
        if (fraudScore > 50) {
            reasons.append("欺诈风险较高;");
        }
        if (amlScore > 50) {
            reasons.append("反洗钱风险较高;");
        }
        record.setRiskReason(reasons.length() > 0 ? reasons.toString() : "正常");

        record.setDeleted(0);
        riskControlRecordMapper.insert(record);

        log.info("风控检查完成: allocationId={}, riskLevel={}, decision={}, score={}",
                allocationId, record.getRiskLevel(), record.getDecision(), totalScore);

        return record;
    }

    /**
     * 人工审核风控记录
     *
     * @param recordId 风控记录ID
     * @param reviewerId 审核员ID
     * @param approved 是否通过
     * @param reviewRemark 审核备注
     */
    @Transactional(rollbackFor = Exception.class)
    public void manualReview(Long recordId, Long reviewerId, boolean approved, String reviewRemark) {
        RiskControlRecord record = riskControlRecordMapper.selectById(recordId);
        if (record == null) {
            throw new BusinessException("风控记录不存在");
        }

        if ("APPROVED".equals(record.getDecision()) || "REJECTED".equals(record.getDecision())) {
            throw new BusinessException("该记录已完成审核，无需重复审核");
        }

        record.setReviewerId(reviewerId);
        record.setReviewedAt(LocalDateTime.now());
        record.setReviewRemark(reviewRemark);

        if (approved) {
            record.setDecision("APPROVED");
            log.info("风控人工审核通过: recordId={}, reviewerId={}", recordId, reviewerId);
        } else {
            record.setDecision("REJECTED");
            log.info("风控人工审核拒绝: recordId={}, reviewerId={}, remark={}", recordId, reviewerId, reviewRemark);
        }

        riskControlRecordMapper.updateById(record);
    }

    /**
     * 获取风控记录
     *
     * @param allocationId 奖金分配ID
     * @return 风控记录
     */
    public RiskControlRecord getRiskRecord(Long allocationId) {
        LambdaQueryWrapper<RiskControlRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RiskControlRecord::getAllocationId, allocationId)
                .eq(RiskControlRecord::getDeleted, 0)
                .orderByDesc(RiskControlRecord::getCreatedAt)
                .last("LIMIT 1");

        return riskControlRecordMapper.selectOne(wrapper);
    }

    /**
     * 检查风控是否通过
     *
     * @param allocationId 奖金分配ID
     * @return true-通过 false-未通过
     */
    public boolean isRiskPassed(Long allocationId) {
        RiskControlRecord record = getRiskRecord(allocationId);
        return record != null && "APPROVED".equals(record.getDecision());
    }

    // ==================== 私有方法 ====================

    /**
     * 黑名单检查
     */
    private boolean checkBlacklist(UserKyc kyc) {
        // TODO: 后期扩展 - 从Redis或数据库查询黑名单
        // 当前使用内存Set模拟

        // 检查身份证号哈希
        if (BLACKLIST.contains(kyc.getIdCardHash())) {
            log.warn("命中黑名单: idCardHash={}", kyc.getIdCardHash());
            return true;
        }

        // 检查手机号哈希
        if (BLACKLIST.contains(kyc.getMobileHash())) {
            log.warn("命中黑名单: mobileHash={}", kyc.getMobileHash());
            return true;
        }

        // 检查银行卡号哈希
        if (kyc.getBankCardHash() != null && BLACKLIST.contains(kyc.getBankCardHash())) {
            log.warn("命中黑名单: bankCardHash={}", kyc.getBankCardHash());
            return true;
        }

        return false;
    }

    /**
     * 欺诈检测
     *
     * 检测项：
     * 1. 同一身份证号多个账户获奖
     * 2. 短时间内多次获奖
     * 3. 异常提交行为模式
     * 4. IP地址异常
     *
     * @return 欺诈分数 (0-100)
     */
    private int detectFraud(Long userId, UserKyc kyc, Long prizeAmount) {
        int score = 0;

        // TODO: 后期扩展 - 实现更复杂的欺诈检测逻辑

        // 1. 检查同一身份证号是否有多个账户获奖
        int sameIdCardCount = countByIdCardHash(kyc.getIdCardHash());
        if (sameIdCardCount > 1) {
            score += 30;
            log.warn("欺诈检测: 同一身份证号多个账户, count={}", sameIdCardCount);
        }

        // 2. 检查同一手机号是否有多个账户获奖
        int sameMobileCount = countByMobileHash(kyc.getMobileHash());
        if (sameMobileCount > 1) {
            score += 30;
            log.warn("欺诈检测: 同一手机号多个账户, count={}", sameMobileCount);
        }

        // 3. 检查近期获奖频率
        int recentWinsCount = countRecentWins(userId, 30); // 30天内
        if (recentWinsCount > 3) {
            score += 20;
            log.warn("欺诈检测: 近期获奖频率过高, count={}", recentWinsCount);
        }

        // 4. 检查单笔奖金金额是否异常高
        if (prizeAmount > 100000000L) { // 100万元
            score += 20;
            log.warn("欺诈检测: 单笔奖金金额异常高, amount={}", prizeAmount);
        }

        return Math.min(score, 100);
    }

    /**
     * 反洗钱（AML）检查
     *
     * 检测项：
     * 1. 累计奖金金额是否超过阈值
     * 2. 频繁小额获奖（可能用于洗钱）
     * 3. 银行卡归属地异常
     *
     * @return AML风险分数 (0-100)
     */
    private int checkAML(Long userId, UserKyc kyc, Long prizeAmount) {
        int score = 0;

        // TODO: 后期扩展 - 接入反洗钱系统API

        // 1. 检查累计奖金金额
        Long totalPrizeAmount = getTotalPrizeAmount(userId);
        if (totalPrizeAmount > 500000000L) { // 累计500万元
            score += 40;
            log.warn("AML检测: 累计奖金金额过高, totalAmount={}", totalPrizeAmount);
        }

        // 2. 检查频繁小额获奖
        int smallPrizeCount = countSmallPrizes(userId, 10000L); // 100元以下
        if (smallPrizeCount > 20) {
            score += 30;
            log.warn("AML检测: 频繁小额获奖, count={}", smallPrizeCount);
        }

        // 3. 单笔大额奖金
        if (prizeAmount > 100000000L) { // 100万元
            score += 30;
            log.warn("AML检测: 单笔大额奖金, amount={}", prizeAmount);
        }

        return Math.min(score, 100);
    }

    /**
     * 计算综合风险分数
     *
     * @param isBlacklisted 是否命中黑名单
     * @param fraudScore 欺诈分数
     * @param amlScore AML分数
     * @return 综合风险分数 (0-100)
     */
    private int calculateTotalRiskScore(boolean isBlacklisted, int fraudScore, int amlScore) {
        // 黑名单直接100分
        if (isBlacklisted) {
            return 100;
        }

        // 加权计算：欺诈60%，AML 40%
        return (int) (fraudScore * 0.6 + amlScore * 0.4);
    }

    /**
     * 确定风险等级和决策
     */
    private void determineRiskLevelAndDecision(RiskControlRecord record, int totalScore, boolean isBlacklisted) {
        if (isBlacklisted || totalScore >= MEDIUM_RISK_THRESHOLD) {
            // 高风险：自动拒绝
            record.setRiskLevel("HIGH");
            record.setDecision("REJECTED");
        } else if (totalScore >= LOW_RISK_THRESHOLD) {
            // 中风险：需要人工审核
            record.setRiskLevel("MEDIUM");
            record.setDecision("PENDING");
        } else {
            // 低风险：自动通过
            record.setRiskLevel("LOW");
            record.setDecision("APPROVED");
        }
    }

    // ==================== 数据统计方法（预留） ====================

    /**
     * 统计同一身份证号的获奖次数
     */
    private int countByIdCardHash(String idCardHash) {
        // TODO: 后期扩展 - 从数据库查询
        LambdaQueryWrapper<RiskControlRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RiskControlRecord::getIdCardHash, idCardHash)
                .eq(RiskControlRecord::getDeleted, 0)
                .in(RiskControlRecord::getDecision, "APPROVED", "PENDING");

        return Math.toIntExact(riskControlRecordMapper.selectCount(wrapper));
    }

    /**
     * 统计同一手机号的获奖次数
     */
    private int countByMobileHash(String mobileHash) {
        // TODO: 后期扩展 - 从数据库查询
        LambdaQueryWrapper<RiskControlRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RiskControlRecord::getMobileHash, mobileHash)
                .eq(RiskControlRecord::getDeleted, 0)
                .in(RiskControlRecord::getDecision, "APPROVED", "PENDING");

        return Math.toIntExact(riskControlRecordMapper.selectCount(wrapper));
    }

    /**
     * 统计用户近期获奖次数
     *
     * @param userId 用户ID
     * @param days 天数
     * @return 获奖次数
     */
    private int countRecentWins(Long userId, int days) {
        // TODO: 后期扩展 - 从数据库查询
        LocalDateTime startDate = LocalDateTime.now().minusDays(days);

        LambdaQueryWrapper<RiskControlRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RiskControlRecord::getUserId, userId)
                .eq(RiskControlRecord::getDeleted, 0)
                .in(RiskControlRecord::getDecision, "APPROVED", "PENDING")
                .ge(RiskControlRecord::getCreatedAt, startDate);

        return Math.toIntExact(riskControlRecordMapper.selectCount(wrapper));
    }

    /**
     * 获取用户累计奖金金额
     */
    private Long getTotalPrizeAmount(Long userId) {
        // TODO: 后期扩展 - 从prize_allocations表聚合查询
        // 当前返回0作为占位
        LambdaQueryWrapper<RiskControlRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RiskControlRecord::getUserId, userId)
                .eq(RiskControlRecord::getDeleted, 0)
                .eq(RiskControlRecord::getDecision, "APPROVED");

        List<RiskControlRecord> records = riskControlRecordMapper.selectList(wrapper);
        return records.stream()
                .mapToLong(RiskControlRecord::getPrizeAmount)
                .sum();
    }

    /**
     * 统计小额奖金次数
     *
     * @param userId 用户ID
     * @param threshold 阈值（分）
     * @return 小额奖金次数
     */
    private int countSmallPrizes(Long userId, Long threshold) {
        // TODO: 后期扩展 - 从数据库查询
        LambdaQueryWrapper<RiskControlRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RiskControlRecord::getUserId, userId)
                .eq(RiskControlRecord::getDeleted, 0)
                .in(RiskControlRecord::getDecision, "APPROVED", "PENDING")
                .le(RiskControlRecord::getPrizeAmount, threshold);

        return Math.toIntExact(riskControlRecordMapper.selectCount(wrapper));
    }

    // ==================== 黑名单管理（预留） ====================

    /**
     * 添加到黑名单
     *
     * @param hash 哈希值（身份证/手机号/银行卡号）
     */
    public void addToBlacklist(String hash) {
        // TODO: 后期扩展 - 写入Redis或数据库
        BLACKLIST.add(hash);
        log.info("添加到黑名单: hash={}", hash);
    }

    /**
     * 从黑名单移除
     *
     * @param hash 哈希值
     */
    public void removeFromBlacklist(String hash) {
        // TODO: 后期扩展 - 从Redis或数据库删除
        BLACKLIST.remove(hash);
        log.info("从黑名单移除: hash={}", hash);
    }

    /**
     * 检查是否在黑名单中
     *
     * @param hash 哈希值
     * @return true-在黑名单 false-不在黑名单
     */
    public boolean isInBlacklist(String hash) {
        // TODO: 后期扩展 - 从Redis或数据库查询
        return BLACKLIST.contains(hash);
    }
}
