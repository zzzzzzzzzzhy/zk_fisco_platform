package com.wereen.competitionplatform.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wereen.competitionplatform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 社区帖子实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("forum_posts")
public class ForumPost extends BaseEntity {

    /**
     * 作者ID
     */
    private Long authorId;

    /**
     * 关联竞赛ID，可为空
     */
    private Long competitionId;

    /**
     * 帖子标题
     */
    private String title;

    /**
     * 帖子正文
     */
    private String content;

    /**
     * 分类标签（question/discussion 等）
     */
    private String category;

    /**
     * 自定义标签集合（逗号分隔）
     */
    private String tags;

    /**
     * 帖子状态：0-草稿 1-已发布 2-隐藏
     */
    private Integer status;

    /**
     * 是否置顶
     */
    @TableField("is_pinned")
    private Boolean pinned;

    /**
     * 浏览次数
     */
    private Integer viewCount;

    /**
     * 回复次数
     */
    private Integer replyCount;

    /**
     * 点赞次数
     */
    private Integer likeCount;

    /**
     * 最近回复时间
     */
    private LocalDateTime lastReplyAt;

    /**
     * 作者名称（非持久化）
     */
    @TableField(exist = false)
    private String authorName;

    /**
     * 关联的内容分享ID列表（逗号分隔，非持久化）
     */
    @TableField(exist = false)
    private String relatedContentShareIds;

    /**
     * 关联的内容分享数量（非持久化）
     */
    @TableField(exist = false)
    private Integer relatedContentShareCount;
}
