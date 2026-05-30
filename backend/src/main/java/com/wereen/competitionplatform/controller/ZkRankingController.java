package com.wereen.competitionplatform.controller;

import com.wereen.competitionplatform.annotation.RequireRole;
import com.wereen.competitionplatform.common.Result;
import com.wereen.competitionplatform.common.UserRole;
import com.wereen.competitionplatform.model.entity.SubmissionCommitment;
import com.wereen.competitionplatform.model.entity.ZkRankingProof;
import com.wereen.competitionplatform.service.ZkRankingService;
import com.wereen.competitionplatform.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * ZK 排名证明接口。
 *
 * <pre>
 * POST /zk/competitions/{id}/commit          管理员提交得分承诺
 * POST /zk/competitions/{id}/prove           管理员生成 ZK 排名证明
 * GET  /zk/competitions/{id}/proof           获取最新 ZK 证明（含排名）
 * GET  /zk/competitions/{id}/commitments     查看所有公开承诺哈希
 * GET  /zk/competitions/{id}/commitments/me  查看本人承诺（含盐，需登录）
 * </pre>
 */
@RestController
@RequestMapping("/zk/competitions")
@RequiredArgsConstructor
public class ZkRankingController {

    private final ZkRankingService zkRankingService;
    private final JwtUtil          jwtUtil;

    /**
     * 管理员为竞赛参赛者批量提交得分承诺。
     *
     * @param scoreMap  请求体 JSON：{ "userId": score, ... }，score 为整数（×100 表示两位小数）
     */
    @PostMapping("/{id}/commit")
    @RequireRole(UserRole.ADMIN)
    public Result<List<SubmissionCommitment>> commitScores(
            @PathVariable Long id,
            @RequestBody Map<Long, Long> scoreMap) {
        List<SubmissionCommitment> result = zkRankingService.commitScores(id, scoreMap);
        return Result.success(result);
    }

    /**
     * 管理员触发 ZK 证明生成，验证承诺并得出最终排名。
     */
    @PostMapping("/{id}/prove")
    @RequireRole(UserRole.ADMIN)
    public Result<ZkRankingProof> generateProof(@PathVariable Long id) throws Exception {
        ZkRankingProof proof = zkRankingService.generateRankingProof(id);
        return Result.success(proof);
    }

    /**
     * 获取竞赛的最新 ZK 排名证明（任何人可查）。
     */
    @GetMapping("/{id}/proof")
    public Result<ZkRankingProof> getProof(@PathVariable Long id) {
        ZkRankingProof proof = zkRankingService.getProof(id);
        if (proof == null) return Result.error("该竞赛暂无 ZK 排名证明");
        return Result.success(proof);
    }

    /**
     * 获取竞赛所有参赛者的公开承诺哈希（盐不公开，得分在揭示后可见）。
     */
    @GetMapping("/{id}/commitments")
    public Result<List<Map<String, Object>>> getCommitments(@PathVariable Long id) {
        return Result.success(zkRankingService.getPublicCommitments(id));
    }

    /**
     * 参赛者查看自己的承诺记录（含盐值，用于自行验证）。
     */
    @GetMapping("/{id}/commitments/me")
    public Result<SubmissionCommitment> getMyCommitment(
            @PathVariable Long id,
            HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (bearer == null || !bearer.startsWith("Bearer ")) {
            return Result.error("请先登录");
        }
        Long userId = jwtUtil.getUserIdFromToken(bearer.substring(7));
        SubmissionCommitment c = zkRankingService.getCommitment(id, userId);
        if (c == null) return Result.error("未找到您的承诺记录");
        return Result.success(c);
    }
}
