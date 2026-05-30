package com.wereen.competitionplatform.controller;

import com.wereen.competitionplatform.annotation.RequireRole;
import com.wereen.competitionplatform.common.PageResult;
import com.wereen.competitionplatform.common.Result;
import com.wereen.competitionplatform.common.UserRole;
import com.wereen.competitionplatform.model.entity.ContentReport;
import com.wereen.competitionplatform.service.ContentReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 内容举报管理
 */
@RestController
@RequestMapping("/content-reports")
@RequiredArgsConstructor
public class ContentReportController {

    private final ContentReportService contentReportService;

    /**
     * 管理员分页查看举报列表
     */
    @GetMapping
    @RequireRole(UserRole.ADMIN)
    public Result<PageResult<ContentReport>> listReports(
        @RequestParam(defaultValue = "1") Long current,
        @RequestParam(defaultValue = "20") Long size,
        @RequestParam(required = false) Integer status
    ) {
        PageResult<ContentReport> page = contentReportService.listReports(current, size, status);
        return Result.success(page);
    }

    /**
     * 管理员处理举报
     */
    @PutMapping("/{id}/handle")
    @RequireRole(UserRole.ADMIN)
    public Result<Boolean> handleReport(
        @PathVariable Long id,
        @RequestBody Map<String, String> request
    ) {
        Integer status;
        try {
            status = Integer.valueOf(request.getOrDefault("status", "1"));
        } catch (NumberFormatException e) {
            return Result.error("无效的处理状态");
        }
        String resultNote = request.get("resultNote");

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long handlerId = null;
        if (auth != null && auth.getPrincipal() instanceof Long) {
            handlerId = (Long) auth.getPrincipal();
        }

        contentReportService.handleReport(id, status, handlerId, resultNote);
        return Result.success(true);
    }
}


