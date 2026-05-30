package com.wereen.competitionplatform.controller;

import com.wereen.competitionplatform.annotation.RequireRole;
import com.wereen.competitionplatform.common.Result;
import com.wereen.competitionplatform.common.UserRole;
import com.wereen.competitionplatform.model.entity.LeaderboardAppeal;
import com.wereen.competitionplatform.service.LeaderboardAppealService;
import com.wereen.competitionplatform.util.JwtUtil;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 榜单异议控制器
 */
@RestController
@RequestMapping("/leaderboard-appeals")
@RequiredArgsConstructor
public class LeaderboardAppealController {

    private final LeaderboardAppealService appealService;
    private final JwtUtil jwtUtil;

    /**
     * 创建榜单异议
     */
    @PostMapping
    public Result<LeaderboardAppeal> createAppeal(
            @RequestBody CreateAppealRequest request,
            @RequestHeader("Authorization") String token) {

        String jwtToken = token.substring(7);
        Long userId = jwtUtil.getUserIdFromToken(jwtToken);

        LeaderboardAppeal appeal = appealService.createAppeal(
                request.getLeaderboardId(),
                userId,
                request.getAppealType(),
                request.getAppealReason(),
                request.getEvidenceFiles()
        );

        return Result.success(appeal);
    }

    /**
     * 获取用户的异议列表
     */
    @GetMapping("/my")
    public Result<List<LeaderboardAppeal>> getUserAppeals(
            @RequestHeader("Authorization") String token) {

        String jwtToken = token.substring(7);
        Long userId = jwtUtil.getUserIdFromToken(jwtToken);

        List<LeaderboardAppeal> appeals = appealService.getUserAppeals(userId);
        return Result.success(appeals);
    }

    /**
     * 获取竞赛的所有异议（仅管理员）
     */
    @GetMapping("/competition/{competitionId}")
    @RequireRole(UserRole.ADMIN)
    public Result<List<LeaderboardAppeal>> getAppealsByCompetition(
            @PathVariable Long competitionId) {
        List<LeaderboardAppeal> appeals = appealService.getAppealsByCompetition(competitionId);
        return Result.success(appeals);
    }

    /**
     * 审核异议（仅管理员）
     */
    @PostMapping("/{appealId}/review")
    @RequireRole(UserRole.ADMIN)
    public Result<Void> reviewAppeal(
            @PathVariable Long appealId,
            @RequestBody ReviewAppealRequest request,
            @RequestHeader("Authorization") String token) {

        String jwtToken = token.substring(7);
        Long reviewerId = jwtUtil.getUserIdFromToken(jwtToken);

        appealService.reviewAppeal(
                appealId,
                reviewerId,
                request.getStatus(),
                request.getReviewResult(),
                request.getReviewNotes()
        );

        return Result.success(null);
    }

    @Data
    public static class CreateAppealRequest {
        private Long leaderboardId;
        private String appealType;
        private String appealReason;
        private String evidenceFiles;
    }

    @Data
    public static class ReviewAppealRequest {
        private String status; // ACCEPTED, REJECTED
        private String reviewResult;
        private String reviewNotes;
    }
}
