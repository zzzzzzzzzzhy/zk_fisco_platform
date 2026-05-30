package com.wereen.competitionplatform.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.wereen.competitionplatform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户KYC认证实体（简化版）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user_kyc")
public class UserKyc extends BaseEntity {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 真实姓名
     */
    private String realName;

    /**
     * 身份证号（加密存储）
     */
    private String idCardNumber;

    /**
     * 身份证号哈希（用于索引）
     */
    private String idCardHash;

    /**
     * 手机号（加密存储）
     */
    private String mobilePhone;

    /**
     * 手机号哈希（用于索引）
     */
    private String mobileHash;

    /**
     * 银行卡号（加密存储）
     */
    private String bankCardNumber;

    /**
     * 银行卡号哈希（用于索引）
     */
    private String bankCardHash;

    /**
     * 开户行
     */
    private String bankName;

    /**
     * 开户支行
     */
    private String bankBranch;

    /**
     * 人脸相似度（预留）
     */
    private BigDecimal faceSimilarity;

    /**
     * 活体检测是否通过（预留）
     */
    private Integer livenessPassed;

    /**
     * 第三方KYC请求ID（阿里云等）
     */
    private String thirdPartyKycId;

    /**
     * 第三方返回结果
     */
    private String thirdPartyResult;

    /**
     * 状态 (PENDING-待审核 APPROVED-已通过 REJECTED-已拒绝)
     */
    private String status;

    /**
     * 拒绝原因
     */
    private String rejectReason;

    /**
     * 审核员ID
     */
    private Long reviewerId;

    /**
     * 审核时间
     */
    private LocalDateTime reviewedAt;

    /**
     * 重试次数
     */
    private Integer retryCount;
}
