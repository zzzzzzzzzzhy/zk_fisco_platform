package com.wereen.competitionplatform.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigInteger;
import java.time.LocalDateTime;

/**
 * 代币奖励记录实体类
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("token_rewards")
public class TokenReward implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 内容ID（帖子ID或评论ID）
     */
    @TableField("content_id")
    private String contentId;

    /**
     * 奖励类型
     */
    @TableField("reward_type")
    private String rewardType;

    /**
     * 奖励数量（最小的货币单位）
     */
    @TableField("amount")
    private BigInteger amount;

    /**
     * 区块链交易哈希
     */
    @TableField("tx_hash")
    private String txHash;

    /**
     * 创建时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 逻辑删除
     */
    @TableLogic
    @TableField("deleted")
    private Integer deleted;
}