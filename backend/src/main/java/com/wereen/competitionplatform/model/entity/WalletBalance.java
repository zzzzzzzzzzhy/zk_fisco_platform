package com.wereen.competitionplatform.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.wereen.competitionplatform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 钱包余额实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wallet_balance")
public class WalletBalance extends BaseEntity {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 币种 (CNY/USD等)
     */
    private String currency;

    /**
     * 可用余额（单位：分）
     */
    private Long balance;

    /**
     * 冻结金额（单位：分）
     */
    private Long frozenAmount;

    /**
     * 乐观锁版本号
     */
    @Version
    private Integer version;
}
