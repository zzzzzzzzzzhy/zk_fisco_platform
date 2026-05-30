package com.wereen.competitionplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wereen.competitionplatform.model.entity.Leaderboard;
import org.apache.ibatis.annotations.Mapper;

/**
 * 榜单快照Mapper
 */
@Mapper
public interface LeaderboardMapper extends BaseMapper<Leaderboard> {
}
