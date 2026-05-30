package com.wereen.competitionplatform.model.dto.forum;

import lombok.Data;

/**
 * 创建评论请求
 */
@Data
public class ForumCommentRequest {

    /**
     * 作者ID
     */
    private Long authorId;

    /**
     * 父评论ID，可为空
     */
    private Long parentId;

    /**
     * 评论正文
     */
    private String content;
}
