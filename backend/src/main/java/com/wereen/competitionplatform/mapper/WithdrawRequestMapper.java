package com.wereen.competitionplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wereen.competitionplatform.model.entity.WithdrawRequest;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提现申请Mapper
 */
@Mapper
public interface WithdrawRequestMapper extends BaseMapper<WithdrawRequest> {
}
