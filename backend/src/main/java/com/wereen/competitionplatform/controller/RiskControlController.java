package com.wereen.competitionplatform.controller;

import com.wereen.competitionplatform.model.entity.RiskControlRecord;
import com.wereen.competitionplatform.service.RiskControlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 风控管理 Controller
 */
@Tag(name = "风控管理", description = "风控管理相关接口（管理员）")
@RestController
@RequestMapping("/api/risk-control")
@RequiredArgsConstructor
public class RiskControlController {

    private final RiskControlService riskControlService;

    /**
     * 执行风控检查
     */
    @Operation(summary = "执行风控检查")
    @PostMapping("/check")
    public RiskControlRecord performRiskCheck(@RequestBody RiskCheckRequest request) {
        return riskControlService.performRiskCheck(
                request.getAllocationId(),
                request.getUserId(),
                request.getPrizeAmount()
        );
    }

    /**
     * 人工审核风控记录
     */
    @Operation(summary = "人工审核风控记录")
    @PostMapping("/review/{recordId}")
    public void manualReview(
            @PathVariable Long recordId,
            @RequestBody ReviewRiskRequest request) {
        // TODO: 从认证上下文获取当前管理员ID
        Long reviewerId = 1L; // 临时hardcode

        riskControlService.manualReview(
                recordId,
                reviewerId,
                request.getApproved(),
                request.getReviewRemark()
        );
    }

    /**
     * 查询风控记录
     */
    @Operation(summary = "查询风控记录")
    @GetMapping("/record/{allocationId}")
    public RiskControlRecord getRiskRecord(@PathVariable Long allocationId) {
        return riskControlService.getRiskRecord(allocationId);
    }

    /**
     * 检查风控是否通过
     */
    @Operation(summary = "检查风控是否通过")
    @GetMapping("/is-passed/{allocationId}")
    public RiskPassedResponse isRiskPassed(@PathVariable Long allocationId) {
        boolean passed = riskControlService.isRiskPassed(allocationId);

        RiskPassedResponse response = new RiskPassedResponse();
        response.setPassed(passed);
        return response;
    }

    /**
     * 添加到黑名单
     */
    @Operation(summary = "添加到黑名单")
    @PostMapping("/blacklist/add")
    public void addToBlacklist(@RequestBody BlacklistRequest request) {
        riskControlService.addToBlacklist(request.getHash());
    }

    /**
     * 从黑名单移除
     */
    @Operation(summary = "从黑名单移除")
    @PostMapping("/blacklist/remove")
    public void removeFromBlacklist(@RequestBody BlacklistRequest request) {
        riskControlService.removeFromBlacklist(request.getHash());
    }

    /**
     * 检查是否在黑名单中
     */
    @Operation(summary = "检查是否在黑名单中")
    @PostMapping("/blacklist/check")
    public BlacklistCheckResponse isInBlacklist(@RequestBody BlacklistRequest request) {
        boolean inBlacklist = riskControlService.isInBlacklist(request.getHash());

        BlacklistCheckResponse response = new BlacklistCheckResponse();
        response.setInBlacklist(inBlacklist);
        return response;
    }

    // ==================== Request/Response DTO ====================

    @Data
    public static class RiskCheckRequest {
        private Long allocationId;
        private Long userId;
        private Long prizeAmount;
    }

    @Data
    public static class ReviewRiskRequest {
        private Boolean approved;
        private String reviewRemark;
    }

    @Data
    public static class RiskPassedResponse {
        private Boolean passed;
    }

    @Data
    public static class BlacklistRequest {
        private String hash;
    }

    @Data
    public static class BlacklistCheckResponse {
        private Boolean inBlacklist;
    }
}
