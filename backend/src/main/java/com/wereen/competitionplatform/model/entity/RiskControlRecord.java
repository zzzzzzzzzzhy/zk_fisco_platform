package com.wereen.competitionplatform.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.wereen.competitionplatform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 风控记录实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("risk_control_records")
public class RiskControlRecord extends BaseEntity {

    /**
     * 奖金分配ID
     */
    private Long allocationId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 身份证号哈希
     */
    private String idCardHash;

    /**
     * 手机号哈希
     */
    private String mobileHash;

    /**
     * 银行卡号哈希
     */
    private String bankCardHash;

    /**
     * 奖金金额（分）
     */
    private Long prizeAmount;

    /**
     * 是否命中黑名单 (0-否 1-是)
     */
    private Integer blacklistHit;

    /**
     * 欺诈分数 (0-100)
     */
    private Integer fraudScore;

    /**
     * AML风险分数 (0-100)
     */
    private Integer amlScore;

    /**
     * 综合风险评分 (0-100)
     */
    private Integer riskScore;

    /**
     * 风险等级 (LOW-低 MEDIUM-中 HIGH-高)
     */
    private String riskLevel;

    /**
     * 风险原因
     */
    private String riskReason;

    /**
     * 决策 (APPROVED-通过 PENDING-待审核 REJECTED-拒绝)
     */
    private String decision;

    /**
     * 复核人ID
     */
    private Long reviewerId;

    /**
     * 复核备注
     */
    private String reviewRemark;

    /**
     * 复核时间
     */
    private LocalDateTime reviewedAt;
}
