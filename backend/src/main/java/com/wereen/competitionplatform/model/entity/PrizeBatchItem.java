package com.wereen.competitionplatform.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.wereen.competitionplatform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 奖金发放批次明细实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("prize_batch_items")
public class PrizeBatchItem extends BaseEntity {

    /**
     * 批次ID
     */
    private Long batchId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 排名
     */
    private Integer rank;

    /**
     * 奖金金额（单位：分）
     */
    private Long amount;

    /**
     * 项哈希
     */
    private String itemHash;

    /**
     * 状态 (PENDING/SUCCESS/FAILED)
     */
    private String status;

    /**
     * 失败原因
     */
    private String reason;
}
