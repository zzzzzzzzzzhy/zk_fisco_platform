package com.wereen.competitionplatform.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.wereen.competitionplatform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 榜单历史实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("leaderboard_history")
public class LeaderboardHistory extends BaseEntity {

    /**
     * 竞赛ID
     */
    private Long competitionId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 榜单类型 (PUBLIC-公榜 PRIVATE-私榜)
     */
    private String leaderboardType;

    /**
     * 排名
     */
    private Integer rank;

    /**
     * 得分
     */
    private BigDecimal score;

    /**
     * 提交ID
     */
    private Long submissionId;

    /**
     * 快照时间
     */
    private LocalDateTime snapshotTime;
}
