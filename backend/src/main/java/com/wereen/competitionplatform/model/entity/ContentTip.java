package com.wereen.competitionplatform.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 内容打赏记录
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("content_tips")
public class ContentTip {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField(value = "create_time")
    private LocalDateTime createdAt;

    @TableField(value = "update_time")
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;

    /**
     * 打赏者用户ID
     */
    private Long tipperId;

    /**
     * 内容创作者用户ID
     */
    private Long creatorId;

    /**
     * 内容类型 (POST-帖子, CONTENT_SHARE-内容分享)
     */
    private String contentType;

    /**
     * 内容ID (帖子ID或内容分享ID)
     */
    private Long contentId;

    /**
     * 打赏金额 (MTK代币数量)
     */
    private BigDecimal amount;

    /**
     * 交易哈希
     */
    private String txHash;

    /**
     * 区块号
     */
    private Long blockNumber;

    /**
     * 交易状态 (0-待处理 1-成功 2-失败)
     */
    private Integer status;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 打赏者名称（非持久化）
     */
    @TableField(exist = false)
    private String tipperName;

    /**
     * 创作者名称（非持久化）
     */
    @TableField(exist = false)
    private String creatorName;

    /**
     * 内容标题（非持久化）
     */
    @TableField(exist = false)
    private String contentTitle;
}
