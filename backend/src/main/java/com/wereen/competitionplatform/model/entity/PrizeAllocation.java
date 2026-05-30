package com.wereen.competitionplatform.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.wereen.competitionplatform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 奖金分配实体（用户奖金记录）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("prize_allocations")
public class PrizeAllocation extends BaseEntity {

    /**
     * 分配编号
     */
    private String allocationNo;

    /**
     * 奖金池ID
     */
    private Long poolId;

    /**
     * 分配方案ID
     */
    private Long schemeId;

    /**
     * 发放批次ID
     */
    private Long batchId;

    /**
     * 竞赛ID
     */
    private Long competitionId;

    /**
     * 榜单ID
     */
    private Long leaderboardId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 排名
     */
    private Integer rank;

    /**
     * 奖金金额（分，税前）
     */
    private Long prizeAmount;

    /**
     * 个税（分，预留字段）
     */
    private Long taxAmount;

    /**
     * 实发金额（分，税后）
     */
    private Long actualAmount;

    /**
     * 状态 (ALLOCATED-已分配 KYC_NOTIFIED-已通知 KYC_PENDING-KYC待审 KYC_APPROVED-KYC通过
     *      RISK_PENDING-风控中 RISK_PASSED-风控通过 QUEUE_BATCH-待发放
     *      DISBURSING-发放中 COMPLETED-已完成 FAILED-失败 FORFEITED-已作废)
     */
    private String status;

    /**
     * KYC记录ID
     */
    private Long kycId;

    /**
     * KYC通知时间
     */
    private LocalDateTime kycNotifiedAt;

    /**
     * KYC截止时间（15天）
     */
    private LocalDateTime kycDeadline;

    /**
     * KYC完成时间
     */
    private LocalDateTime kycCompletedAt;

    /**
     * 风险评分 (0-100)
     */
    private Integer riskScore;

    /**
     * 风控状态 (PENDING-待审 PASSED-通过 REJECTED-拒绝)
     */
    private String riskStatus;

    /**
     * 风控检查时间
     */
    private LocalDateTime riskCheckAt;

    /**
     * 风控通过时间
     */
    private LocalDateTime riskPassedAt;

    /**
     * 收款银行卡（加密）
     */
    private String disbursementAccount;

    /**
     * 开户行
     */
    private String disbursementBank;

    /**
     * 银行流水号
     */
    private String transactionId;

    /**
     * 失败原因
     */
    private String failureReason;

    /**
     * 重试次数
     */
    private Integer retryCount;

    /**
     * 发放时间
     */
    private LocalDateTime disbursedAt;

    /**
     * 链上交易哈希
     */
    private String chainTxHash;

    /**
     * 区块高度
     */
    private Long blockHeight;

    /**
     * 区块时间
     */
    private LocalDateTime blockTime;

    /**
     * 发放完成时间
     */
    private LocalDateTime completedAt;
}
