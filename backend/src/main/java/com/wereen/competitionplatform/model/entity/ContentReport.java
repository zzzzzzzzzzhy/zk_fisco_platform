package com.wereen.competitionplatform.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 内容举报记录
 */
@Data
@TableName("content_reports")
public class ContentReport {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 被举报的内容分享ID
     */
    @TableField("content_share_id")
    private Long contentShareId;

    /**
     * 举报人用户ID（未登录可为空）
     */
    @TableField("reporter_id")
    private Long reporterId;

    /**
     * 举报类型(SPAM/ILLEGAL/INFRINGE/OTHER)
     */
    @TableField("reason_code")
    private String reasonCode;

    /**
     * 举报说明
     */
    @TableField("reason_text")
    private String reasonText;

    /**
     * 处理状态(0-待处理 1-已处理 2-已忽略)
     */
    @TableField("status")
    private Integer status;

    /**
     * 处理人ID
     */
    @TableField("handler_id")
    private Long handlerId;

    /**
     * 处理时间
     */
    @TableField("handled_at")
    private LocalDateTime handledAt;

    /**
     * 处理结果说明
     */
    @TableField("result_note")
    private String resultNote;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    @TableField("deleted")
    private Integer deleted;
}


