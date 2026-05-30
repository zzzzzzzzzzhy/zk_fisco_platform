package com.wereen.competitionplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wereen.competitionplatform.model.entity.UserWallet;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 用户钱包 Mapper
 */
@Mapper
public interface UserWalletMapper extends BaseMapper<UserWallet> {

    /**
     * 根据用户ID查询钱包
     */
    @Select("SELECT * FROM user_wallets WHERE user_id = #{userId} LIMIT 1")
    UserWallet selectByUserId(@Param("userId") Long userId);

    /**
     * 根据钱包地址查询钱包
     */
    @Select("SELECT * FROM user_wallets WHERE address = #{address} LIMIT 1")
    UserWallet selectByAddress(@Param("address") String address);
}