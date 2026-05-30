package com.wereen.competitionplatform.model.dto.forum;

import lombok.Data;

/**
 * 创建/更新帖子请求
 */
@Data
public class ForumPostRequest {

    /**
     * 作者ID
     */
    private Long authorId;

    /**
     * 关联竞赛
     */
    private Long competitionId;

    /**
     * 标题
     */
    private String title;

    /**
     * 正文
     */
    private String content;

    /**
     * 分类标签
     */
    private String category;

    /**
     * 自定义标签
     */
    private String tags;

    /**
     * 关联内容分享ID
     */
    private Long relatedContentShareId;

    /**
     * 状态（默认发布 1）
     */
    private Integer status;
}
