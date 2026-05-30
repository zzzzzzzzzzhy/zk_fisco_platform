package com.wereen.competitionplatform.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.wereen.competitionplatform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 报名记录实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("registrations")
public class Registration extends BaseEntity {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 竞赛ID
     */
    private Long competitionId;

    /**
     * 协议版本
     */
    private String agreementVersion;

    /**
     * 报名状态 (0-待审核 1-已通过 2-已拒绝)
     */
    private Integer status;

    /**
     * 审核备注
     */
    private String remark;
}
