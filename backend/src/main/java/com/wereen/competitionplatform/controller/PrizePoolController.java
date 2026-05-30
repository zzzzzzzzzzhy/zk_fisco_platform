package com.wereen.competitionplatform.controller;

import com.wereen.competitionplatform.model.entity.PrizeAllocationScheme;
import com.wereen.competitionplatform.model.entity.PrizePool;
import com.wereen.competitionplatform.model.entity.PrizePoolFunding;
import com.wereen.competitionplatform.service.PrizePoolService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 奖金池管理 Controller
 */
@Tag(name = "奖金池管理", description = "奖金池管理相关接口（管理员）")
@RestController
@RequestMapping("/api/prize-pool")
@RequiredArgsConstructor
public class PrizePoolController {

    private final PrizePoolService prizePoolService;

    /**
     * 创建奖金池
     */
    @Operation(summary = "创建奖金池")
    @PostMapping("/create")
    public PrizePool createPrizePool(@RequestBody CreatePoolRequest request) {
        return prizePoolService.createPrizePool(
                request.getLeaderboardId(),
                request.getPoolName(),
                request.getDescription()
        );
    }

    /**
     * 奖金池注资
     */
    @Operation(summary = "奖金池注资")
    @PostMapping("/{poolId}/funding")
    public PrizePoolFunding addFunding(
            @PathVariable Long poolId,
            @RequestBody AddFundingRequest request) {
        // TODO: 从认证上下文获取当前管理员ID
        Long funderId = 1L; // 临时hardcode

        return prizePoolService.addFunding(
                poolId,
                request.getSourceType(),
                request.getSourceName(),
                request.getFundingAmount(),
                funderId
        );
    }

    /**
     * 创建分配方案
     */
    @Operation(summary = "创建分配方案")
    @PostMapping("/{poolId}/scheme")
    public PrizeAllocationScheme createScheme(
            @PathVariable Long poolId,
            @RequestBody CreateSchemeRequest request) {
        return prizePoolService.createAllocationScheme(
                poolId,
                request.getSchemeName(),
                request.getRankStart(),
                request.getRankEnd(),
                request.getPrizeAmountPerUser(),
                request.getPercentage()
        );
    }

    /**
     * 批量创建分配方案
     */
    @Operation(summary = "批量创建分配方案")
    @PostMapping("/{poolId}/schemes/batch")
    public List<PrizeAllocationScheme> batchCreateSchemes(
            @PathVariable Long poolId,
            @RequestBody BatchCreateSchemesRequest request) {
        List<PrizePoolService.SchemeDTO> schemeDTOs = request.getSchemes().stream()
                .map(req -> {
                    PrizePoolService.SchemeDTO dto = new PrizePoolService.SchemeDTO();
                    dto.setSchemeName(req.getSchemeName());
                    dto.setRankStart(req.getRankStart());
                    dto.setRankEnd(req.getRankEnd());
                    dto.setPrizeAmountPerUser(req.getPrizeAmountPerUser());
                    dto.setPercentage(req.getPercentage());
                    return dto;
                })
                .collect(Collectors.toList());

        return prizePoolService.batchCreateSchemes(poolId, schemeDTOs);
    }

    /**
     * 锁定奖金池
     */
    @Operation(summary = "锁定奖金池")
    @PostMapping("/{poolId}/lock")
    public void lockPrizePool(@PathVariable Long poolId) {
        prizePoolService.lockPrizePool(poolId);
    }

    /**
     * 解锁奖金池
     */
    @Operation(summary = "解锁奖金池（管理员）")
    @PostMapping("/{poolId}/unlock")
    public void unlockPrizePool(@PathVariable Long poolId) {
        prizePoolService.unlockPrizePool(poolId);
    }

    /**
     * 查询奖金池详情
     */
    @Operation(summary = "查询奖金池详情")
    @GetMapping("/{poolId}")
    public PrizePoolDetailResponse getPrizePoolDetail(@PathVariable Long poolId) {
        PrizePool pool = prizePoolService.getPrizePool(poolId);
        List<PrizePoolFunding> fundings = prizePoolService.getFundingList(poolId);
        List<PrizeAllocationScheme> schemes = prizePoolService.getSchemeList(poolId);

        PrizePoolDetailResponse response = new PrizePoolDetailResponse();
        response.setPool(pool);
        response.setFundings(fundings);
        response.setSchemes(schemes);

        return response;
    }

    /**
     * 根据榜单ID查询奖金池
     */
    @Operation(summary = "根据榜单ID查询奖金池")
    @GetMapping("/by-leaderboard/{leaderboardId}")
    public PrizePool getPrizePoolByLeaderboard(@PathVariable Long leaderboardId) {
        return prizePoolService.getPrizePoolByLeaderboard(leaderboardId);
    }

    /**
     * 查询注资记录
     */
    @Operation(summary = "查询注资记录")
    @GetMapping("/{poolId}/fundings")
    public List<PrizePoolFunding> getFundingList(@PathVariable Long poolId) {
        return prizePoolService.getFundingList(poolId);
    }

    /**
     * 查询分配方案
     */
    @Operation(summary = "查询分配方案")
    @GetMapping("/{poolId}/schemes")
    public List<PrizeAllocationScheme> getSchemeList(@PathVariable Long poolId) {
        return prizePoolService.getSchemeList(poolId);
    }

    // ==================== Request/Response DTO ====================

    @Data
    public static class CreatePoolRequest {
        private Long leaderboardId;
        private String poolName;
        private String description;
    }

    @Data
    public static class AddFundingRequest {
        private String sourceType;  // ORGANIZER, SPONSOR, PLATFORM
        private String sourceName;
        private Long fundingAmount;
    }

    @Data
    public static class CreateSchemeRequest {
        private String schemeName;
        private Integer rankStart;
        private Integer rankEnd;
        private Long prizeAmountPerUser;
        private BigDecimal percentage;
    }

    @Data
    public static class BatchCreateSchemesRequest {
        private List<CreateSchemeRequest> schemes;
    }

    @Data
    public static class PrizePoolDetailResponse {
        private PrizePool pool;
        private List<PrizePoolFunding> fundings;
        private List<PrizeAllocationScheme> schemes;
    }
}
