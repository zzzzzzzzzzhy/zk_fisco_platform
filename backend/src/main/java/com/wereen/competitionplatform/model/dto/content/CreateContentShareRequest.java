package com.wereen.competitionplatform.model.dto.content;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建内容分享请求
 */
@Data
public class CreateContentShareRequest {

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotBlank(message = "标题不能为空")
    @Size(max = 120, message = "标题最长120个字符")
    private String title;

    @Size(max = 2000, message = "描述最长2000个字符")
    private String description;

    @NotBlank(message = "媒体类型不能为空")
    private String mediaType;

    @NotBlank(message = "媒体地址不能为空")
    private String mediaUrl;

    private String thumbnailUrl;

    private Long durationSeconds;

    private String hashAlgorithm;

    private String fileHash;

    /**
     * 前端透传的额外元数据（JSON字符串）
     */
    private String metadata;
}
