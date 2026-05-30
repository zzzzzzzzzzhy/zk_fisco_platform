package com.wereen.competitionplatform.model.entity;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * Polygon Gas 交易记录（用于后台 Gas 监控面板）
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("gas_transactions")
public class GasTransaction implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 交易哈希
     */
    @TableField("tx_hash")
    private String txHash;

    /**
     * 发送方地址
     */
    @TableField("from_address")
    private String fromAddress;

    /**
     * 接收方地址
     */
    @TableField("to_address")
    private String toAddress;

    /**
     * 合约地址（如果是合约调用）
     */
    @TableField("contract_address")
    private String contractAddress;

    /**
     * 业务类型：CHECKIN / POST / COMMENT / TIP / PIN / OTHER ...
     */
    @TableField("biz_type")
    private String bizType;

    /**
     * 消耗的 Gas 数量
     */
    @TableField("gas_used")
    private Long gasUsed;

    /**
     * Gas 单价（Gwei）
     */
    @TableField("gas_price_gwei")
    private BigDecimal gasPriceGwei;

    /**
     * 本次交易消耗的费用（MATIC）
     */
    @TableField("gas_fee_matic")
    private BigDecimal gasFeeMatic;

    /**
     * 区块高度
     */
    @TableField("block_number")
    private Long blockNumber;

    /**
     * 是否执行成功
     */
    @TableField("success")
    private Boolean success;

    /**
     * 创建时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 逻辑删除
     */
    @TableLogic
    @TableField("deleted")
    private Integer deleted;
}


