package com.wereen.competitionplatform.controller;

import com.wereen.competitionplatform.annotation.RateLimit;
import com.wereen.competitionplatform.annotation.RequireRole;
import com.wereen.competitionplatform.common.Result;
import com.wereen.competitionplatform.common.UserRole;
import com.wereen.competitionplatform.model.entity.Leaderboard;
import com.wereen.competitionplatform.service.LeaderboardService;
import com.wereen.competitionplatform.util.JwtUtil;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 榜单控制器
 */
@RestController
@RequestMapping("/leaderboards")
@RequiredArgsConstructor
public class LeaderboardController {

    private final LeaderboardService leaderboardService;
    private final JwtUtil jwtUtil;

    /**
     * 获取竞赛榜单
     * @param competitionId 竞赛ID
     * @param type 榜单类型 (PUBLIC-公榜 PRIVATE-私榜，默认PUBLIC)
     */
    @GetMapping("/{competitionId}")
    @RateLimit(key = "leaderboard:query", time = 10, count = 5, limitType = RateLimit.LimitType.IP)
    public Result<List<LeaderboardService.LeaderboardEntry>> getLeaderboard(
            @PathVariable Long competitionId,
            @RequestParam(defaultValue = "PUBLIC") String type) {
        List<LeaderboardService.LeaderboardEntry> leaderboard = leaderboardService.getLeaderboard(competitionId, type);
        return Result.success(leaderboard);
    }

    /**
     * 冻结榜单 (仅管理员)
     * @param competitionId 竞赛ID
     * @param type 榜单类型 (PUBLIC-公榜 PRIVATE-私榜，默认PUBLIC)
     * @param publicityDays 公示天数（默认7天）
     */
    @PostMapping("/{competitionId}/freeze")
    @RequireRole(UserRole.ADMIN)
    public Result<Leaderboard> freezeLeaderboard(
            @PathVariable Long competitionId,
            @RequestParam(defaultValue = "PUBLIC") String type,
            @RequestParam(defaultValue = "7") Integer publicityDays,
            @RequestHeader("Authorization") String token) {

        // 从token获取用户ID
        String jwtToken = token.substring(7);
        Long userId = jwtUtil.getUserIdFromToken(jwtToken);

        Leaderboard leaderboard = leaderboardService.freezeLeaderboard(competitionId, userId, type, publicityDays);
        return Result.success(leaderboard);
    }

    /**
     * 获取冻结的榜单
     */
    @GetMapping("/{competitionId}/frozen")
    public Result<FrozenLeaderboardResponse> getFrozenLeaderboard(@PathVariable Long competitionId) {
        Leaderboard frozen = leaderboardService.getFrozenLeaderboard(competitionId);

        if (frozen == null) {
            return Result.success(null);
        }

        FrozenLeaderboardResponse response = new FrozenLeaderboardResponse();
        response.setSnapshotId(frozen.getSnapshotId());
        response.setMerkleRoot(frozen.getMerkleRoot());
        response.setChainTxHash(frozen.getChainTxHash());
        response.setBlockHeight(frozen.getBlockHeight());
        response.setFrozenAt(frozen.getFrozenAt());

        try {
            // 解析榜单数据
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

            @SuppressWarnings("unchecked")
            List<LeaderboardService.LeaderboardEntry> data = mapper.readValue(
                    frozen.getLeaderboardData(),
                    mapper.getTypeFactory().constructCollectionType(List.class, LeaderboardService.LeaderboardEntry.class)
            );
            response.setLeaderboardData(data);

        } catch (Exception e) {
            // 解析失败时返回null
            response.setLeaderboardData(null);
        }

        return Result.success(response);
    }

    /**
     * 验证榜单完整性
     */
    @GetMapping("/{competitionId}/verify")
    public Result<Boolean> verifyLeaderboard(@PathVariable Long competitionId) {
        boolean valid = leaderboardService.verifyLeaderboard(competitionId);
        return Result.success(valid);
    }

    /**
     * 解冻榜单 (仅管理员)
     */
    @PostMapping("/snapshot/{leaderboardId}/unfreeze")
    @RequireRole(UserRole.ADMIN)
    public Result<Void> unfreezeLeaderboard(
            @PathVariable Long leaderboardId,
            @RequestHeader("Authorization") String token) {

        String jwtToken = token.substring(7);
        Long userId = jwtUtil.getUserIdFromToken(jwtToken);

        leaderboardService.unfreezeLeaderboard(leaderboardId, userId);
        return Result.success(null);
    }

    /**
     * 确认榜单（公示期结束后）(仅管理员)
     */
    @PostMapping("/snapshot/{leaderboardId}/confirm")
    @RequireRole(UserRole.ADMIN)
    public Result<Void> confirmLeaderboard(
            @PathVariable Long leaderboardId,
            @RequestHeader("Authorization") String token) {

        String jwtToken = token.substring(7);
        Long userId = jwtUtil.getUserIdFromToken(jwtToken);

        leaderboardService.confirmLeaderboard(leaderboardId, userId);
        return Result.success(null);
    }

    /**
     * 获取榜单历史快照列表
     */
    @GetMapping("/{competitionId}/history")
    public Result<List<Leaderboard>> getLeaderboardHistory(
            @PathVariable Long competitionId,
            @RequestParam(defaultValue = "PUBLIC") String type) {
        List<Leaderboard> history = leaderboardService.getLeaderboardHistory(competitionId, type);
        return Result.success(history);
    }

    /**
     * 根据快照ID获取榜单
     */
    @GetMapping("/snapshot/{snapshotId}")
    public Result<FrozenLeaderboardResponse> getLeaderboardBySnapshotId(@PathVariable String snapshotId) {
        Leaderboard snapshot = leaderboardService.getLeaderboardBySnapshotId(snapshotId);

        if (snapshot == null) {
            return Result.success(null);
        }

        FrozenLeaderboardResponse response = new FrozenLeaderboardResponse();
        response.setSnapshotId(snapshot.getSnapshotId());
        response.setMerkleRoot(snapshot.getMerkleRoot());
        response.setChainTxHash(snapshot.getChainTxHash());
        response.setBlockHeight(snapshot.getBlockHeight());
        response.setFrozenAt(snapshot.getFrozenAt());
        response.setLeaderboardType(snapshot.getLeaderboardType());
        response.setPublicityStatus(snapshot.getPublicityStatus());
        response.setPublicityEndTime(snapshot.getPublicityEndTime());

        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

            @SuppressWarnings("unchecked")
            List<LeaderboardService.LeaderboardEntry> data = mapper.readValue(
                    snapshot.getLeaderboardData(),
                    mapper.getTypeFactory().constructCollectionType(List.class, LeaderboardService.LeaderboardEntry.class)
            );
            response.setLeaderboardData(data);

        } catch (Exception e) {
            response.setLeaderboardData(null);
        }

        return Result.success(response);
    }

    @Data
    public static class FrozenLeaderboardResponse {
        private String snapshotId;
        private String merkleRoot;
        private String chainTxHash;
        private Long blockHeight;
        private java.time.LocalDateTime frozenAt;
        private String leaderboardType;
        private String publicityStatus;
        private java.time.LocalDateTime publicityEndTime;
        private List<LeaderboardService.LeaderboardEntry> leaderboardData;
    }
}
