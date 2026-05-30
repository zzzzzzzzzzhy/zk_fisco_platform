package com.wereen.competitionplatform.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wereen.competitionplatform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户奖励事件（用于 Rollup）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("reward_events")
public class RewardEvent extends BaseEntity {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 事件类型 (CONTENT_SHARE/COMMENT/CHECKIN)
     */
    private String eventType;

    /**
     * 业务ID
     */
    private String bizId;

    /**
     * 用户签名（hex）
     */
    private String signature;

    /**
     * 事件载荷（JSON）
     */
    private String payload;

    /**
     * 批次ID
     */
    private Long batchId;

    /**
     * 处理状态 (0-待打包 1-已打包 2-已发放)
     */
    private Integer status;

    /**
     * 发放交易哈希
     */
    private String txHash;

    /**
     * 逻辑删除
     */
    @TableLogic
    @TableField("deleted")
    private Integer deleted;
}
