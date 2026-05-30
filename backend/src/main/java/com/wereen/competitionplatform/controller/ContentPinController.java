package com.wereen.competitionplatform.controller;

import com.wereen.competitionplatform.common.Result;
import com.wereen.competitionplatform.model.entity.ContentPin;
import com.wereen.competitionplatform.service.ContentPinService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 内容置顶控制器
 */
@Slf4j
@RestController
@RequestMapping("/content-pins")
@Tag(name = "内容置顶", description = "内容置顶相关接口")
public class ContentPinController {

    @Autowired
    private ContentPinService contentPinService;

    /**
     * 购买置顶
     */
    @PostMapping("/purchase")
    @Operation(summary = "购买置顶")
    public Result<ContentPin> purchasePin(@RequestBody Map<String, Object> request) {
        try {
            Long userId = Long.valueOf(request.get("userId").toString());
            String contentType = request.get("contentType").toString();
            Long contentId = Long.valueOf(request.get("contentId").toString());
            // 前端链上交易的 txHash / blockNumber（可选）
            String txHash = request.getOrDefault("txHash", "").toString();
            Long blockNumber = null;
            if (request.get("blockNumber") != null) {
                blockNumber = Long.valueOf(request.get("blockNumber").toString());
            }

            ContentPin pin = contentPinService.purchasePin(userId, contentType, contentId, txHash, blockNumber);
            return Result.success(pin);
        } catch (Exception e) {
            log.error("购买置顶失败", e);
            return Result.error("购买置顶失败: " + e.getMessage());
        }
    }

    /**
     * 查询当前有效的置顶内容
     */
    @GetMapping("/active")
    @Operation(summary = "查询当前有效的置顶内容")
    public Result<List<ContentPin>> getActivePins() {
        try {
            List<ContentPin> pins = contentPinService.getActivePins();
            return Result.success(pins);
        } catch (Exception e) {
            log.error("查询有效置顶内容失败", e);
            return Result.error("查询有效置顶内容失败: " + e.getMessage());
        }
    }

    /**
     * 查询指定内容的置顶记录
     */
    @GetMapping("/content/{contentType}/{contentId}")
    @Operation(summary = "查询指定内容的置顶记录")
    public Result<List<ContentPin>> getPinsByContent(
            @PathVariable String contentType,
            @PathVariable Long contentId) {
        try {
            List<ContentPin> pins = contentPinService.getPinByContent(contentType, contentId);
            return Result.success(pins);
        } catch (Exception e) {
            log.error("查询内容置顶记录失败", e);
            return Result.error("查询内容置顶记录失败: " + e.getMessage());
        }
    }

    /**
     * 查询用户购买的置顶记录
     */
    @GetMapping("/user/{userId}")
    @Operation(summary = "查询用户购买的置顶记录")
    public Result<List<ContentPin>> getPinsByUser(@PathVariable Long userId) {
        try {
            List<ContentPin> pins = contentPinService.getPinByUserId(userId);
            return Result.success(pins);
        } catch (Exception e) {
            log.error("查询用户置顶记录失败", e);
            return Result.error("查询用户置顶记录失败: " + e.getMessage());
        }
    }

    /**
     * 检查内容是否置顶
     */
    @GetMapping("/check/{contentType}/{contentId}")
    @Operation(summary = "检查内容是否置顶")
    public Result<Boolean> isContentPinned(
            @PathVariable String contentType,
            @PathVariable Long contentId) {
        try {
            boolean pinned = contentPinService.isContentPinned(contentType, contentId);
            return Result.success(pinned);
        } catch (Exception e) {
            log.error("检查内容置顶状态失败", e);
            return Result.error("检查内容置顶状态失败: " + e.getMessage());
        }
    }
}
