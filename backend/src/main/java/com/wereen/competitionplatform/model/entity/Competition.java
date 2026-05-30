package com.wereen.competitionplatform.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.wereen.competitionplatform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 竞赛实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("competitions")
public class Competition extends BaseEntity {

    /**
     * 竞赛标题
     */
    private String title;

    /**
     * 竞赛简介
     */
    private String description;

    /**
     * 赛题详情
     */
    private String detail;

    /**
     * 数据说明
     */
    private String dataDescription;

    /**
     * 评测标准
     */
    private String evaluationStandard;

    /**
     * 提交要求
     */
    private String submissionRequirement;

    /**
     * 奖金配置（JSON格式，如：[{"rank":1,"amount":10000},...]）
     */
    private String prizeConfig;

    /**
     * 总奖金池（单位：分）
     */
    private Long totalPrize;

    /**
     * 报名开始时间
     */
    private LocalDateTime registrationStartTime;

    /**
     * 报名结束时间
     */
    private LocalDateTime registrationEndTime;

    /**
     * 提交开始时间
     */
    private LocalDateTime submissionStartTime;

    /**
     * 提交结束时间
     */
    private LocalDateTime submissionEndTime;

    /**
     * 评测镜像
     */
    private String evaluationImage;

    /**
     * 数据集路径（MinIO）
     */
    private String datasetPath;

    /**
     * 竞赛状态 (0-草稿 1-报名中 2-进行中 3-已结束 4-已取消)
     */
    private Integer status;

    /**
     * 是否启用公私榜 (0-否 1-是)
     */
    private Integer useAbLeaderboard;

    /**
     * 公开测试集比例（0.30表示30%）
     */
    private Double publicTestRatio;

    /**
     * 私榜公布时间（通常=竞赛结束时间）
     */
    private LocalDateTime privateLeaderboardPublishTime;

    /**
     * 每日最大提交次数
     */
    private Integer maxDailySubmissions;

    /**
     * 总最大提交次数
     */
    private Integer maxTotalSubmissions;

    /**
     * 封面图片URL
     */
    private String coverImage;

    /**
     * 详情图片URL
     */
    private String detailImage;

    /**
     * 创建人ID
     */
    private Long creatorId;
}
