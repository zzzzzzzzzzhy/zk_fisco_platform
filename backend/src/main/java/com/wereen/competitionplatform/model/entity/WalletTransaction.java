package com.wereen.competitionplatform.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.wereen.competitionplatform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 钱包交易记录实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wallet_transactions")
public class WalletTransaction extends BaseEntity {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 交易类型 (PRIZE_IN/ADJUST/WITHDRAW_APPLY/WITHDRAW_FREEZE/WITHDRAW_SUCCESS/WITHDRAW_FAIL/REFUND)
     */
    private String type;

    /**
     * 交易金额（单位：分）
     */
    private Long amount;

    /**
     * 交易后余额（单位：分）
     */
    private Long balanceAfter;

    /**
     * 业务关联ID（submission_id/batch_id/withdraw_id）
     */
    private String bizRef;

    /**
     * 交易状态 (PENDING/SUCCESS/FAILED)
     */
    private String status;

    /**
     * 备注
     */
    private String remark;
}
