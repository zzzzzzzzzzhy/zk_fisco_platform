package com.wereen.competitionplatform.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wereen.competitionplatform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 帖子评论
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("forum_comments")
public class ForumComment extends BaseEntity {

    /**
     * 所属帖子ID
     */
    private Long postId;

    /**
     * 父评论ID（楼中楼）
     */
    private Long parentId;

    /**
     * 作者ID
     */
    private Long authorId;

    /**
     * 评论正文
     */
    private String content;

    /**
     * 评论状态：0-待审核 1-正常 2-屏蔽
     */
    private Integer status;

    /**
     * 点赞数量
     */
    private Integer likeCount;

    /**
     * 作者名称（非持久化）
     */
    @TableField(exist = false)
    private String authorName;

    /**
     * 评论类型 (POST-帖子评论, CONTENT_SHARE-内容分享评论)
     */
    @TableField("comment_type")
    private String commentType;

    /**
     * 关联的内容分享ID（如果是内容分享评论）
     */
    @TableField("content_share_id")
    private Long contentShareId;

    /**
     * 帖子标题（非持久化）
     */
    @TableField(exist = false)
    private String postTitle;

    /**
     * 内容分享标题（非持久化）
     */
    @TableField(exist = false)
    private String contentShareTitle;
}
