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
 * 内容置顶记录
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("content_pins")
public class ContentPin {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField(value = "create_time")
    private LocalDateTime createdAt;

    @TableField(value = "update_time")
    private LocalDateTime updatedAt;

    @TableLogic
    @TableField("deleted")
    private Integer deleted;

    /**
     * 用户ID（购买置顶的用户）
     */
    private Long userId;

    /**
     * 内容类型 (POST-帖子, CONTENT_SHARE-内容分享)
     */
    private String contentType;

    /**
     * 内容ID (帖子ID或内容分享ID)
     */
    private Long contentId;

    /**
     * 置顶费用 (MTK代币数量)
     */
    private BigDecimal amount;

    /**
     * 置顶开始时间
     */
    private LocalDateTime startTime;

    /**
     * 置顶结束时间
     */
    private LocalDateTime endTime;

    /**
     * 置顶状态 (0-待生效 1-生效中 2-已过期)
     */
    private Integer status;

    /**
     * 交易哈希
     */
    private String txHash;

    /**
     * 区块号
     */
    private Long blockNumber;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 用户名称（非持久化）
     */
    @TableField(exist = false)
    private String userName;

    /**
     * 内容标题（非持久化）
     */
    @TableField(exist = false)
    private String contentTitle;

    /**
     * 剩余时间（小时，非持久化）
     */
    @TableField(exist = false)
    private Long remainingHours;
}
