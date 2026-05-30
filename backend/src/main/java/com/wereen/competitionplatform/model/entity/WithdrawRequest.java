package com.wereen.competitionplatform.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.wereen.competitionplatform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 提现申请实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("withdraw_requests")
public class WithdrawRequest extends BaseEntity {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 提现方式 (BANK/ALIPAY/WECHAT/STRIPE)
     */
    private String method;

    /**
     * 收款账号（加密）
     */
    private String accountPayload;

    /**
     * 提现金额（单位：分）
     */
    private Long amount;

    /**
     * 手续费（单位：分）
     */
    private Long fee;

    /**
     * 税费（单位：分）
     */
    private Long tax;

    /**
     * 提现状态 (APPLIED/REVIEWING/APPROVED/PAID/REJECTED/FAILED)
     */
    private String status;

    /**
     * 支付渠道
     */
    private String provider;

    /**
     * 渠道交易ID
     */
    private String providerTxId;

    /**
     * 失败原因
     */
    private String failureReason;

    /**
     * 支付完成时间
     */
    private LocalDateTime paidAt;

    /**
     * 风控评分
     */
    private Integer riskScore;

    /**
     * 提现申请哈希（用于上链）
     */
    private String requestHash;

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
    private java.time.LocalDateTime blockTime;

    /**
     * 上链状态 (0-未上链 1-上链中 2-已上链 3-失败)
     */
    private Integer chainStatus;
}
