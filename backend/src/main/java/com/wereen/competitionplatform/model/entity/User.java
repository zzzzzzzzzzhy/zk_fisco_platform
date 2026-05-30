package com.wereen.competitionplatform.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.wereen.competitionplatform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("users")
public class User extends BaseEntity {

    /**
     * 邮箱
     */
    private String email;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 密码（加密）
     */
    private String password;

    /**
     * 用户名
     */
    private String username;

    /**
     * 真实姓名
     */
    private String realname;

    /**
     * 身份证号
     */
    private String idNumber;

    /**
     * KYC状态 (0-未认证 1-审核中 2-已通过 3-未通过)
     */
    private Integer kycStatus;

    /**
     * 风控标志 (0-正常 1-风险用户)
     */
    private Integer riskFlag;

    /**
     * 用户状态 (0-禁用 1-启用)
     */
    private Integer status;

    /**
     * 用户角色 (USER-普通用户 ADMIN-管理员)
     */
    private String role;

    /**
     * 用户绑定的钱包地址
     */
    private String walletAddress;
}
