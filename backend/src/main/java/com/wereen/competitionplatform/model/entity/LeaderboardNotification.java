package com.wereen.competitionplatform.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.wereen.competitionplatform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 榜单通知实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("leaderboard_notifications")
public class LeaderboardNotification extends BaseEntity {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 竞赛ID
     */
    private Long competitionId;

    /**
     * 通知类型 (RANK_CHANGE-排名变化 LEADERBOARD_FROZEN-榜单冻结 PUBLICITY_START-公示开始 APPEAL_RESULT-异议结果)
     */
    private String notificationType;

    /**
     * 通知标题
     */
    private String title;

    /**
     * 通知内容
     */
    private String content;

    /**
     * 附加数据（JSON格式，如旧排名、新排名）
     */
    private String data;

    /**
     * 是否已读 (0-未读 1-已读)
     */
    private Integer isRead;

    /**
     * 阅读时间
     */
    private LocalDateTime readAt;
}
