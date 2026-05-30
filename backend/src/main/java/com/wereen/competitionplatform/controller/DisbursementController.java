package com.wereen.competitionplatform.controller;

import com.wereen.competitionplatform.model.entity.DisbursementBatch;
import com.wereen.competitionplatform.service.DisbursementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 批次发放 Controller
 */
@Tag(name = "批次发放管理", description = "批次发放相关接口（管理员）")
@RestController
@RequestMapping("/api/disbursement")
@RequiredArgsConstructor
public class DisbursementController {

    private final DisbursementService disbursementService;

    /**
     * 创建发放批次
     */
    @Operation(summary = "创建发放批次")
    @PostMapping("/create-batch/{poolId}")
    public DisbursementBatch createBatch(@PathVariable Long poolId) {
        // TODO: 从认证上下文获取当前管理员ID
        Long operatorId = 1L; // 临时hardcode

        return disbursementService.createBatch(poolId, operatorId);
    }

    /**
     * 提交批次到银行
     */
    @Operation(summary = "提交批次到银行")
    @PostMapping("/submit-batch/{batchId}")
    public void submitBatch(@PathVariable Long batchId) {
        disbursementService.submitBatch(batchId);
    }

    /**
     * 查询批次状态（从银行）
     */
    @Operation(summary = "查询批次状态（从银行）")
    @PostMapping("/query-status/{batchId}")
    public void queryBatchStatus(@PathVariable Long batchId) {
        disbursementService.queryBatchStatus(batchId);
    }

    /**
     * 批次完成回调（银行回调或手动触发）
     */
    @Operation(summary = "批次完成回调")
    @PostMapping("/complete/{batchId}")
    public void onBatchCompleted(
            @PathVariable Long batchId,
            @RequestBody BatchCompletedRequest request) {
        disbursementService.onBatchCompleted(
                batchId,
                request.getSuccessCount(),
                request.getFailedCount(),
                request.getFailedAllocationIds()
        );
    }

    /**
     * 手动标记批次完成（测试用）
     */
    @Operation(summary = "手动标记批次完成（测试用）")
    @PostMapping("/manual-complete/{batchId}")
    public void manualCompleteBatch(
            @PathVariable Long batchId,
            @RequestBody ManualCompleteRequest request) {
        disbursementService.manualCompleteBatch(batchId, request.getAllSuccess());
    }

    /**
     * 重试失败的发放
     */
    @Operation(summary = "重试失败的发放")
    @PostMapping("/retry/{batchId}")
    public DisbursementBatch retryFailedDisbursements(@PathVariable Long batchId) {
        // TODO: 从认证上下文获取当前管理员ID
        Long operatorId = 1L; // 临时hardcode

        return disbursementService.retryFailedDisbursements(batchId, operatorId);
    }

    /**
     * 查询批次详情
     */
    @Operation(summary = "查询批次详情")
    @GetMapping("/{batchId}")
    public DisbursementBatch getBatch(@PathVariable Long batchId) {
        return disbursementService.getBatch(batchId);
    }

    /**
     * 查询奖金池的所有批次
     */
    @Operation(summary = "查询奖金池的所有批次")
    @GetMapping("/by-pool/{poolId}")
    public List<DisbursementBatch> getBatchesByPool(@PathVariable Long poolId) {
        return disbursementService.getBatchesByPool(poolId);
    }

    /**
     * 查询处理中的批次（定时任务）
     */
    @Operation(summary = "查询处理中的批次（定时任务）")
    @GetMapping("/processing")
    public List<DisbursementBatch> getProcessingBatches() {
        return disbursementService.getProcessingBatches();
    }

    // ==================== Request/Response DTO ====================

    @Data
    public static class BatchCompletedRequest {
        private Integer successCount;
        private Integer failedCount;
        private List<Long> failedAllocationIds;
    }

    @Data
    public static class ManualCompleteRequest {
        private Boolean allSuccess;
    }
}
