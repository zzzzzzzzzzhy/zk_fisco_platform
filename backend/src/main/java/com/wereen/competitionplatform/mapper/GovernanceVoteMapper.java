package com.wereen.competitionplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wereen.competitionplatform.model.entity.GovernanceVote;
import org.apache.ibatis.annotations.Mapper;

/**
 * DAO 投票记录 Mapper
 */
@Mapper
public interface GovernanceVoteMapper extends BaseMapper<GovernanceVote> {
}

