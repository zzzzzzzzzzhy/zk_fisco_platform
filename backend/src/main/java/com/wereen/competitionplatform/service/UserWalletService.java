package com.wereen.competitionplatform.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wereen.competitionplatform.model.entity.UserWallet;

/**
 * 用户钱包服务接口
 */
public interface UserWalletService extends IService<UserWallet> {

    /**
     * 获取用户钱包地址
     * @param userId 用户ID
     * @return 钱包地址
     */
    UserWallet getUserWallet(Long userId);

    /**
     * 绑定用户钱包地址
     * @param userId 用户ID
     * @param walletAddress 钱包地址
     * @return 钱包记录
     */
    UserWallet bindUserWallet(Long userId, String walletAddress);
}