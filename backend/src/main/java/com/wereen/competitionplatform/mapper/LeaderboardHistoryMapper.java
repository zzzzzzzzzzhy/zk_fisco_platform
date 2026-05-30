package com.wereen.competitionplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wereen.competitionplatform.model.entity.LeaderboardHistory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 榜单历史Mapper
 */
@Mapper
public interface LeaderboardHistoryMapper extends BaseMapper<LeaderboardHistory> {
}
