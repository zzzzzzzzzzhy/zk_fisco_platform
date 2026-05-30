package com.wereen.competitionplatform.controller;

import com.wereen.competitionplatform.model.entity.PrizeAllocation;
import com.wereen.competitionplatform.service.PrizeAllocationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 奖金分配 Controller
 */
@Tag(name = "奖金分配管理", description = "奖金分配相关接口")
@RestController
@RequestMapping("/api/prize-allocation")
@RequiredArgsConstructor
public class PrizeAllocationController {

    private final PrizeAllocationService allocationService;

    /**
     * 批量分配奖金（管理员）
     */
    @Operation(summary = "批量分配奖金（管理员）")
    @PostMapping("/batch-allocate")
    public List<PrizeAllocation> batchAllocate(@RequestBody BatchAllocateRequest request) {
        List<PrizeAllocationService.UserRankDTO> userRankList = request.getUserRanks().stream()
                .map(ur -> {
                    PrizeAllocationService.UserRankDTO dto = new PrizeAllocationService.UserRankDTO();
                    dto.setUserId(ur.getUserId());
                    dto.setRank(ur.getRank());
                    return dto;
                })
                .collect(Collectors.toList());

        return allocationService.batchAllocate(request.getPoolId(), userRankList);
    }

    /**
     * 批量发送KYC通知（管理员）
     */
    @Operation(summary = "批量发送KYC通知（管理员）")
    @PostMapping("/batch-notify-kyc/{poolId}")
    public void batchNotifyKyc(@PathVariable Long poolId) {
        allocationService.batchNotifyKyc(poolId);
    }

    /**
     * 发送单个KYC通知（管理员）
     */
    @Operation(summary = "发送单个KYC通知（管理员）")
    @PostMapping("/notify-kyc/{allocationId}")
    public void notifyKyc(@PathVariable Long allocationId) {
        allocationService.notifyKyc(allocationId);
    }

    /**
     * 用户提交KYC后的回调
     */
    @Operation(summary = "KYC提交回调")
    @PostMapping("/kyc-submitted/{allocationId}")
    public void onKycSubmitted(@PathVariable Long allocationId) {
        allocationService.onKycSubmitted(allocationId);
    }

    /**
     * KYC审核通过回调
     */
    @Operation(summary = "KYC审核通过回调")
    @PostMapping("/kyc-approved/{allocationId}")
    public void onKycApproved(@PathVariable Long allocationId) {
        allocationService.onKycApproved(allocationId);
    }

    /**
     * 执行风控检查（管理员）
     */
    @Operation(summary = "执行风控检查（管理员）")
    @PostMapping("/risk-check/{allocationId}")
    public void performRiskCheck(@PathVariable Long allocationId) {
        allocationService.performRiskCheck(allocationId);
    }

    /**
     * 风控通过回调
     */
    @Operation(summary = "风控通过回调")
    @PostMapping("/risk-passed/{allocationId}")
    public void onRiskPassed(@PathVariable Long allocationId) {
        allocationService.onRiskPassed(allocationId);
    }

    /**
     * 风控拒绝回调
     */
    @Operation(summary = "风控拒绝回调")
    @PostMapping("/risk-rejected/{allocationId}")
    public void onRiskRejected(
            @PathVariable Long allocationId,
            @RequestBody RiskRejectedRequest request) {
        allocationService.onRiskRejected(allocationId, request.getReason());
    }

    /**
     * 加入发放队列
     */
    @Operation(summary = "加入发放队列")
    @PostMapping("/queue/{allocationId}")
    public void queueForDisbursement(@PathVariable Long allocationId) {
        allocationService.queueForDisbursement(allocationId);
    }

    /**
     * 处理超时未完成KYC的记录（定时任务）
     */
    @Operation(summary = "处理超时未完成KYC的记录（定时任务）")
    @PostMapping("/forfeit-expired")
    public void forfeitExpiredAllocations() {
        allocationService.forfeitExpiredAllocations();
    }

    /**
     * 查询当前用户的奖金记录
     */
    @Operation(summary = "查询当前用户的奖金记录")
    @GetMapping("/my-prizes")
    public List<PrizeAllocation> getMyPrizes() {
        // TODO: 从认证上下文获取当前用户ID
        Long userId = 1L; // 临时hardcode

        return allocationService.getUserAllocations(userId);
    }

    /**
     * 查询指定用户的奖金记录（管理员）
     */
    @Operation(summary = "查询指定用户的奖金记录（管理员）")
    @GetMapping("/user/{userId}")
    public List<PrizeAllocation> getUserAllocations(@PathVariable Long userId) {
        return allocationService.getUserAllocations(userId);
    }

    /**
     * 查询单个分配记录
     */
    @Operation(summary = "查询单个分配记录")
    @GetMapping("/{allocationId}")
    public PrizeAllocation getAllocation(@PathVariable Long allocationId) {
        return allocationService.getAllocation(allocationId);
    }

    /**
     * 查询待发放的分配记录（管理员）
     */
    @Operation(summary = "查询待发放的分配记录（管理员）")
    @GetMapping("/pending/{poolId}")
    public List<PrizeAllocation> getPendingDisbursements(@PathVariable Long poolId) {
        return allocationService.getPendingDisbursements(poolId);
    }

    /**
     * 根据批次ID查询分配记录（管理员）
     */
    @Operation(summary = "根据批次ID查询分配记录（管理员）")
    @GetMapping("/batch/{batchId}")
    public List<PrizeAllocation> getAllocationsByBatch(@PathVariable Long batchId) {
        return allocationService.getAllocationsByBatch(batchId);
    }

    /**
     * 标记发放成功（管理员）
     */
    @Operation(summary = "标记发放成功（管理员）")
    @PostMapping("/mark-disbursed/{allocationId}")
    public void markDisbursed(@PathVariable Long allocationId) {
        allocationService.markDisbursed(allocationId);
    }

    /**
     * 标记发放失败（管理员）
     */
    @Operation(summary = "标记发放失败（管理员）")
    @PostMapping("/mark-failed/{allocationId}")
    public void markFailed(
            @PathVariable Long allocationId,
            @RequestBody MarkFailedRequest request) {
        allocationService.markFailed(allocationId, request.getFailureReason());
    }

    // ==================== Request/Response DTO ====================

    @Data
    public static class BatchAllocateRequest {
        private Long poolId;
        private List<UserRankItem> userRanks;
    }

    @Data
    public static class UserRankItem {
        private Long userId;
        private Integer rank;
    }

    @Data
    public static class RiskRejectedRequest {
        private String reason;
    }

    @Data
    public static class MarkFailedRequest {
        private String failureReason;
    }
}
