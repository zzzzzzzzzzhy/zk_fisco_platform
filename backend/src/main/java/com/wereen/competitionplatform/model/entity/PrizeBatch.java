package com.wereen.competitionplatform.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.wereen.competitionplatform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 奖金发放批次实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("prize_batches")
public class PrizeBatch extends BaseEntity {

    /**
     * 竞赛ID
     */
    private Long competitionId;

    /**
     * 批次号
     */
    private String batchNo;

    /**
     * 总金额（单位：分）
     */
    private Long totalAmount;

    /**
     * 获奖人数
     */
    private Integer winnersCount;

    /**
     * Merkle Root
     */
    private String merkleRoot;

    /**
     * 冻结快照ID
     */
    private Long frozenSnapshotId;

    /**
     * 批次状态 (CREATED/ACCOUNTED/REVERTED)
     */
    private String status;

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
     * 入账时间
     */
    private LocalDateTime accountedAt;
}
