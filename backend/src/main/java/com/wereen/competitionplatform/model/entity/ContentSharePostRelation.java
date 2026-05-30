package com.wereen.competitionplatform.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.wereen.competitionplatform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 记录内容分享与论坛帖子的关联
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("content_share_post_relations")
public class ContentSharePostRelation extends BaseEntity {

    /**
     * 内容分享ID
     */
    private Long contentShareId;

    /**
     * 论坛帖子ID
     */
    private Long postId;
}
