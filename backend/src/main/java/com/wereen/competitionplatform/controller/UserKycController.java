package com.wereen.competitionplatform.controller;

import com.wereen.competitionplatform.model.entity.UserKyc;
import com.wereen.competitionplatform.service.UserKycService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 用户KYC认证 Controller
 */
@Tag(name = "用户KYC认证", description = "用户身份认证相关接口")
@RestController
@RequestMapping("/api/kyc")
@RequiredArgsConstructor
public class UserKycController {

    private final UserKycService kycService;

    /**
     * 提交KYC认证
     */
    @Operation(summary = "提交KYC认证")
    @PostMapping("/submit")
    public UserKyc submitKyc(@RequestBody SubmitKycRequest request) {
        // TODO: 从认证上下文获取当前用户ID
        Long userId = 1L; // 临时hardcode

        return kycService.submitKyc(
                userId,
                request.getRealName(),
                request.getIdCardNumber(),
                request.getMobilePhone(),
                request.getBankCardNumber(),
                request.getBankName(),
                request.getBankBranch()
        );
    }

    /**
     * 查询KYC状态
     */
    @Operation(summary = "查询KYC状态")
    @GetMapping("/status")
    public UserKyc getKycStatus() {
        // TODO: 从认证上下文获取当前用户ID
        Long userId = 1L; // 临时hardcode

        return kycService.getUserKyc(userId);
    }

    /**
     * 检查KYC是否已通过
     */
    @Operation(summary = "检查KYC是否已通过")
    @GetMapping("/is-approved")
    public KycApprovedResponse isKycApproved() {
        // TODO: 从认证上下文获取当前用户ID
        Long userId = 1L; // 临时hardcode

        boolean approved = kycService.isKycApproved(userId);

        KycApprovedResponse response = new KycApprovedResponse();
        response.setApproved(approved);
        return response;
    }

    /**
     * 人工审核KYC（管理员）
     */
    @Operation(summary = "人工审核KYC（管理员）")
    @PostMapping("/review/{kycId}")
    public void reviewKyc(
            @PathVariable Long kycId,
            @RequestBody ReviewKycRequest request) {
        // TODO: 从认证上下文获取当前管理员ID
        Long reviewerId = 1L; // 临时hardcode

        kycService.manualReview(
                kycId,
                reviewerId,
                request.getApproved(),
                request.getRejectReason()
        );
    }

    // ==================== Request/Response DTO ====================

    @Data
    public static class SubmitKycRequest {
        private String realName;
        private String idCardNumber;
        private String mobilePhone;
        private String bankCardNumber;
        private String bankName;
        private String bankBranch;
    }

    @Data
    public static class ReviewKycRequest {
        private Boolean approved;
        private String rejectReason;
    }

    @Data
    public static class KycApprovedResponse {
        private Boolean approved;
    }
}
