package com.wereen.competitionplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wereen.competitionplatform.model.entity.WalletBalance;
import org.apache.ibatis.annotations.Mapper;

/**
 * 钱包余额Mapper
 */
@Mapper
public interface WalletBalanceMapper extends BaseMapper<WalletBalance> {
}
