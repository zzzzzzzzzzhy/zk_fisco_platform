package com.wereen.competitionplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wereen.competitionplatform.model.entity.ForumComment;
import org.apache.ibatis.annotations.Mapper;

/**
 * 评论 Mapper
 */
@Mapper
public interface ForumCommentMapper extends BaseMapper<ForumComment> {
}
