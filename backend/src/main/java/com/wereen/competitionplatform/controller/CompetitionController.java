package com.wereen.competitionplatform.controller;

import com.wereen.competitionplatform.annotation.RequireRole;
import com.wereen.competitionplatform.common.PageResult;
import com.wereen.competitionplatform.common.Result;
import com.wereen.competitionplatform.common.UserRole;
import com.wereen.competitionplatform.model.entity.Competition;
import com.wereen.competitionplatform.service.CompetitionService;
import com.wereen.competitionplatform.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 竞赛控制器
 */
@RestController
@RequestMapping("/competitions")
@RequiredArgsConstructor
public class CompetitionController {

    private final CompetitionService competitionService;
    private final JwtUtil jwtUtil;

    /**
     * 获取竞赛列表（分页）
     */
    @GetMapping
    public Result<PageResult<Competition>> getCompetitionPage(
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "10") Long size,
            @RequestParam(required = false) Integer status) {

        PageResult<Competition> page = competitionService.getCompetitionPage(current, size, status);
        return Result.success(page);
    }

    /**
     * 获取竞赛详情
     */
    @GetMapping("/{id}")
    public Result<Competition> getCompetitionById(@PathVariable Long id) {
        Competition competition = competitionService.getCompetitionById(id);
        return Result.success(competition);
    }

    /**
     * 获取进行中的竞赛
     */
    @GetMapping("/ongoing")
    public Result<List<Competition>> getOngoingCompetitions() {
        List<Competition> competitions = competitionService.getOngoingCompetitions();
        return Result.success(competitions);
    }

    /**
     * 创建竞赛 (仅管理员)
     */
    @PostMapping
    @RequireRole(UserRole.ADMIN)
    public Result<Competition> createCompetition(@RequestBody Competition competition,
                                                  HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (bearer != null && bearer.startsWith("Bearer ")) {
            Long userId = jwtUtil.getUserIdFromToken(bearer.substring(7));
            competition.setCreatorId(userId);
        }
        Competition created = competitionService.createCompetition(competition);
        return Result.success(created);
    }

    /**
     * 更新竞赛 (仅管理员)
     */
    @PutMapping("/{id}")
    @RequireRole(UserRole.ADMIN)
    public Result<Competition> updateCompetition(@PathVariable Long id, @RequestBody Competition competition) {
        competition.setId(id);
        Competition updated = competitionService.updateCompetition(competition);
        return Result.success(updated);
    }

    /**
     * 发布竞赛 (仅管理员)
     */
    @PostMapping("/{id}/publish")
    @RequireRole(UserRole.ADMIN)
    public Result<Void> publishCompetition(@PathVariable Long id) {
        competitionService.publishCompetition(id);
        return Result.success();
    }

    /**
     * 删除竞赛 (仅管理员)
     */
    @DeleteMapping("/{id}")
    @RequireRole(UserRole.ADMIN)
    public Result<Void> deleteCompetition(@PathVariable Long id) {
        competitionService.deleteCompetition(id);
        return Result.success();
    }
}
