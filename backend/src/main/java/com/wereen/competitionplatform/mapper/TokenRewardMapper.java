package com.wereen.competitionplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wereen.competitionplatform.model.entity.TokenReward;
import java.math.BigDecimal;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * Token 奖励记录 Mapper
 */
@Mapper
public interface TokenRewardMapper extends BaseMapper<TokenReward> {
    @Select("SELECT COALESCE(SUM(amount), 0) FROM token_rewards " +
        "WHERE user_id = #{userId} AND deleted = 0 AND amount > 0")
    BigDecimal sumPositiveRewardsByUser(Long userId);
}
