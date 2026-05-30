package com.wereen.competitionplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wereen.competitionplatform.model.entity.ContentSharePostRelation;
import org.apache.ibatis.annotations.Mapper;

/**
 * 内容分享与帖子关联关系 Mapper
 */
@Mapper
public interface ContentSharePostRelationMapper extends BaseMapper<ContentSharePostRelation> {
}
