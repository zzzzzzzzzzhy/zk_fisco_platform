package com.wereen.competitionplatform.controller;

import com.wereen.competitionplatform.common.Result;
import com.wereen.competitionplatform.model.entity.LeaderboardNotification;
import com.wereen.competitionplatform.service.LeaderboardNotificationService;
import com.wereen.competitionplatform.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 榜单通知控制器
 */
@RestController
@RequestMapping("/leaderboard-notifications")
@RequiredArgsConstructor
public class LeaderboardNotificationController {

    private final LeaderboardNotificationService notificationService;
    private final JwtUtil jwtUtil;

    /**
     * 获取用户通知列表
     */
    @GetMapping
    public Result<List<LeaderboardNotification>> getUserNotifications(
            @RequestParam(required = false) Boolean onlyUnread,
            @RequestHeader("Authorization") String token) {

        String jwtToken = token.substring(7);
        Long userId = jwtUtil.getUserIdFromToken(jwtToken);

        List<LeaderboardNotification> notifications = notificationService.getUserNotifications(userId, onlyUnread);
        return Result.success(notifications);
    }

    /**
     * 获取未读通知数量
     */
    @GetMapping("/unread-count")
    public Result<Long> getUnreadCount(@RequestHeader("Authorization") String token) {
        String jwtToken = token.substring(7);
        Long userId = jwtUtil.getUserIdFromToken(jwtToken);

        long count = notificationService.getUnreadCount(userId);
        return Result.success(count);
    }

    /**
     * 标记通知为已读
     */
    @PostMapping("/{notificationId}/read")
    public Result<Void> markAsRead(
            @PathVariable Long notificationId,
            @RequestHeader("Authorization") String token) {

        String jwtToken = token.substring(7);
        Long userId = jwtUtil.getUserIdFromToken(jwtToken);

        notificationService.markAsRead(notificationId, userId);
        return Result.success(null);
    }

    /**
     * 标记所有通知为已读
     */
    @PostMapping("/read-all")
    public Result<Void> markAllAsRead(@RequestHeader("Authorization") String token) {
        String jwtToken = token.substring(7);
        Long userId = jwtUtil.getUserIdFromToken(jwtToken);

        notificationService.markAllAsRead(userId);
        return Result.success(null);
    }
}
