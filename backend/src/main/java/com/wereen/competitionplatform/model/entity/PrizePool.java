package com.wereen.competitionplatform.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.wereen.competitionplatform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 奖金池实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("prize_pools")
public class PrizePool extends BaseEntity {

    /**
     * 奖金池编号
     */
    private String poolNo;

    /**
     * 奖金池名称
     */
    private String poolName;

    /**
     * 描述
     */
    private String description;

    /**
     * 竞赛ID
     */
    private Long competitionId;

    /**
     * 榜单ID
     */
    private Long leaderboardId;

    /**
     * 总金额（分）
     */
    private Long totalAmount;

    /**
     * 已分配金额（分）
     */
    private Long allocatedAmount;

    /**
     * 已发放金额（分）
     */
    private Long disbursedAmount;

    /**
     * 储备金（分）
     */
    private Long reservedAmount;

    /**
     * 状态 (DRAFT-草稿 CREATED-已创建 FUNDING-募集中 LOCKED-已锁定 ALLOCATED-已分配 DISBURSING-发放中 COMPLETED-已完成 SETTLED-已结算)
     */
    private String status;

    /**
     * 是否锁定 (0-否 1-是)
     */
    private Integer locked;

    /**
     * 注资记录Merkle根
     */
    private String fundingMerkleRoot;

    /**
     * 分配方案Merkle根
     */
    private String allocationMerkleRoot;

    /**
     * 分配方案哈希
     */
    private String allocationHash;

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
     * 创建人ID
     */
    private Long createdBy;

    /**
     * 锁定人ID
     */
    private Long lockedBy;

    /**
     * 锁定时间
     */
    private LocalDateTime lockedAt;

    /**
     * 分配时间
     */
    private LocalDateTime allocatedAt;
}
