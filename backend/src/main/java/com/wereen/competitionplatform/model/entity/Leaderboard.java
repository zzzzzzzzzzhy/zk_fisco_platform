package com.wereen.competitionplatform.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.wereen.competitionplatform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 榜单快照实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("leaderboards")
public class Leaderboard extends BaseEntity {

    /**
     * 竞赛ID
     */
    private Long competitionId;

    /**
     * 榜单类型 (PUBLIC-公榜 PRIVATE-私榜)
     */
    private String leaderboardType;

    /**
     * 快照ID（版本号）
     */
    private String snapshotId;

    /**
     * Merkle Root
     */
    private String merkleRoot;

    /**
     * 是否冻结 (0-否 1-是)
     */
    private Integer frozen;

    /**
     * 公示状态 (IN_PUBLICITY-公示中 CONFIRMED-已确认 CANCELLED-已取消)
     */
    private String publicityStatus;

    /**
     * 公示开始时间
     */
    private LocalDateTime publicityStartTime;

    /**
     * 公示结束时间
     */
    private LocalDateTime publicityEndTime;

    /**
     * 公示天数（默认7天）
     */
    private Integer publicityDays;

    /**
     * 确认操作人ID
     */
    private Long confirmedBy;

    /**
     * 确认时间
     */
    private LocalDateTime confirmedAt;

    /**
     * 榜单备注
     */
    private String remark;

    /**
     * 冻结操作人ID
     */
    private Long frozenBy;

    /**
     * 冻结时间
     */
    private LocalDateTime frozenAt;

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
     * 榜单数据（JSON格式）
     */
    private String leaderboardData;
}
