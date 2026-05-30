package com.wereen.competitionplatform.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.wereen.competitionplatform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 奖金池注资记录实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("prize_pool_fundings")
public class PrizePoolFunding extends BaseEntity {

    /**
     * 奖金池ID
     */
    private Long poolId;

    /**
     * 注资编号
     */
    private String fundingNo;

    /**
     * 资金来源类型 (ORGANIZER-主办方 SPONSOR-赞助商 PLATFORM-平台)
     */
    private String sourceType;

    /**
     * 资金来源名称
     */
    private String sourceName;

    /**
     * 注资人ID
     */
    private Long funderId;

    /**
     * 注资金额（分）
     */
    private Long fundingAmount;

    /**
     * 注资时间
     */
    private LocalDateTime fundingTime;

    /**
     * 状态 (PENDING-待确认 CONFIRMED-已确认 CANCELLED-已取消)
     */
    private String status;

    /**
     * 付款账号（加密）
     */
    private String bankAccount;

    /**
     * 银行流水号
     */
    private String transactionId;

    /**
     * 转账凭证URL（MinIO）
     */
    private String transferVoucher;

    /**
     * 确认人ID
     */
    private Long confirmedBy;

    /**
     * 确认时间
     */
    private LocalDateTime confirmedAt;

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
     * 备注
     */
    private String remark;
}
