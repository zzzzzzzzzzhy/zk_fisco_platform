package com.wereen.competitionplatform.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.wereen.competitionplatform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 榜单异议实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("leaderboard_appeals")
public class LeaderboardAppeal extends BaseEntity {

    /**
     * 榜单快照ID
     */
    private Long leaderboardId;

    /**
     * 竞赛ID
     */
    private Long competitionId;

    /**
     * 申诉用户ID
     */
    private Long userId;

    /**
     * 申诉类型 (SCORE_ERROR-分数错误 RANK_ERROR-排名错误 DATA_ERROR-数据错误 OTHER-其他)
     */
    private String appealType;

    /**
     * 申诉理由
     */
    private String appealReason;

    /**
     * 证据文件列表（JSON格式，MinIO路径）
     */
    private String evidenceFiles;

    /**
     * 处理状态 (PENDING-待处理 REVIEWING-审核中 ACCEPTED-已接受 REJECTED-已拒绝)
     */
    private String status;

    /**
     * 审核人ID
     */
    private Long reviewerId;

    /**
     * 审核结果
     */
    private String reviewResult;

    /**
     * 审核备注
     */
    private String reviewNotes;

    /**
     * 审核时间
     */
    private LocalDateTime reviewedAt;
}
