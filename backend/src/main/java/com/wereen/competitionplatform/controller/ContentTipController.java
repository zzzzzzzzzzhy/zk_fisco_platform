package com.wereen.competitionplatform.controller;

import com.wereen.competitionplatform.common.Result;
import com.wereen.competitionplatform.model.entity.ContentTip;
import com.wereen.competitionplatform.service.ContentTipService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

/**
 * 内容打赏控制器
 */
@Slf4j
@RestController
@RequestMapping("/content-tips")
@Tag(name = "内容打赏", description = "内容打赏相关接口")
public class ContentTipController {

    @Autowired
    private ContentTipService contentTipService;

    /**
     * 创建打赏
     */
    @PostMapping
    @Operation(summary = "创建打赏")
    public Result<ContentTip> createTip(@Valid @RequestBody CreateTipRequest request) {
        try {
            ContentTip tip = contentTipService.createTip(
                request.getTipperId(),
                request.getCreatorId(),
                request.getContentType(),
                request.getContentId(),
                request.getAmount(),
                request.getTxHash(),
                request.getBlockNumber()
            );
            return Result.success(tip);
        } catch (Exception e) {
            log.error("创建打赏失败", e);
            return Result.error("打赏失败: " + e.getMessage());
        }
    }

    /**
     * 查询创作者收到的打赏记录
     */
    @GetMapping("/creator/{creatorId}")
    @Operation(summary = "查询创作者收到的打赏记录")
    public Result<List<ContentTip>> getTipsByCreator(
            @PathVariable Long creatorId,
            @RequestParam(defaultValue = "20") int limit) {
        List<ContentTip> tips = contentTipService.getTipsByCreator(creatorId, limit);
        return Result.success(tips);
    }

    /**
     * 查询内容的打赏记录
     */
    @GetMapping("/content/{contentType}/{contentId}")
    @Operation(summary = "查询内容的打赏记录")
    public Result<List<ContentTip>> getTipsByContent(
            @PathVariable String contentType,
            @PathVariable Long contentId) {
        List<ContentTip> tips = contentTipService.getTipsByContent(contentType, contentId);
        return Result.success(tips);
    }

    /**
     * 查询创作者收到的总打赏金额
     */
    @GetMapping("/creator/{creatorId}/total")
    @Operation(summary = "查询创作者收到的总打赏金额")
    public Result<BigDecimal> getTotalTipsByCreator(@PathVariable Long creatorId) {
        BigDecimal totalTips = contentTipService.getTotalTipsByCreator(creatorId);
        return Result.success(totalTips);
    }

    /**
     * 查询内容的总打赏金额
     */
    @GetMapping("/content/{contentType}/{contentId}/total")
    @Operation(summary = "查询内容的总打赏金额")
    public Result<BigDecimal> getTotalTipsByContent(
            @PathVariable String contentType,
            @PathVariable Long contentId) {
        BigDecimal totalTips = contentTipService.getTotalTipsByContent(contentType, contentId);
        return Result.success(totalTips);
    }

    /**
     * 查询用户的打赏统计
     */
    @GetMapping("/user/{userId}/stats")
    @Operation(summary = "查询用户的打赏统计")
    public Result<ContentTip> getUserTipStats(@PathVariable Long userId) {
        ContentTip stats = contentTipService.getUserTipStats(userId);
        return Result.success(stats);
    }

    /**
     * 创建打赏请求
     */
    public static class CreateTipRequest {
        @NotNull(message = "打赏者ID不能为空")
        private Long tipperId;

        @NotNull(message = "创作者ID不能为空")
        private Long creatorId;

        @NotNull(message = "内容类型不能为空")
        private String contentType;

        @NotNull(message = "内容ID不能为空")
        private Long contentId;

        @NotNull(message = "打赏金额不能为空")
        @DecimalMin(value = "0.01", message = "打赏金额必须大于0")
        private BigDecimal amount;

        @NotBlank(message = "交易哈希不能为空")
        private String txHash;

        private Long blockNumber;

        // Getters and Setters
        public Long getTipperId() { return tipperId; }
        public void setTipperId(Long tipperId) { this.tipperId = tipperId; }

        public Long getCreatorId() { return creatorId; }
        public void setCreatorId(Long creatorId) { this.creatorId = creatorId; }

        public String getContentType() { return contentType; }
        public void setContentType(String contentType) { this.contentType = contentType; }

        public Long getContentId() { return contentId; }
        public void setContentId(Long contentId) { this.contentId = contentId; }

        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }

        public String getTxHash() { return txHash; }
        public void setTxHash(String txHash) { this.txHash = txHash; }

        public Long getBlockNumber() { return blockNumber; }
        public void setBlockNumber(Long blockNumber) { this.blockNumber = blockNumber; }
    }
}
