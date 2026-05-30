package com.wereen.competitionplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wereen.competitionplatform.model.entity.Competition;
import org.apache.ibatis.annotations.Mapper;

/**
 * 竞赛Mapper
 */
@Mapper
public interface CompetitionMapper extends BaseMapper<Competition> {
}
