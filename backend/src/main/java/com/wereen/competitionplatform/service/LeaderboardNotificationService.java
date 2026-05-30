package com.wereen.competitionplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wereen.competitionplatform.exception.BusinessException;
import com.wereen.competitionplatform.mapper.LeaderboardNotificationMapper;
import com.wereen.competitionplatform.model.entity.LeaderboardNotification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 榜单通知服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeaderboardNotificationService {

    private final LeaderboardNotificationMapper notificationMapper;

    /**
     * 创建通知
     */
    @Transactional(rollbackFor = Exception.class)
    public LeaderboardNotification createNotification(Long userId, Long competitionId,
                                                     String notificationType, String title,
                                                     String content, String data) {
        LeaderboardNotification notification = new LeaderboardNotification();
        notification.setUserId(userId);
        notification.setCompetitionId(competitionId);
        notification.setNotificationType(notificationType);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setData(data);
        notification.setIsRead(0);
        notification.setDeleted(0);

        notificationMapper.insert(notification);

        log.info("创建榜单通知 - 用户: {}, 竞赛: {}, 类型: {}", userId, competitionId, notificationType);

        return notification;
    }

    /**
     * 获取用户通知列表
     */
    public List<LeaderboardNotification> getUserNotifications(Long userId, Boolean onlyUnread) {
        LambdaQueryWrapper<LeaderboardNotification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LeaderboardNotification::getUserId, userId)
                .eq(LeaderboardNotification::getDeleted, 0);

        if (onlyUnread != null && onlyUnread) {
            wrapper.eq(LeaderboardNotification::getIsRead, 0);
        }

        wrapper.orderByDesc(LeaderboardNotification::getCreatedAt);

        return notificationMapper.selectList(wrapper);
    }

    /**
     * 标记通知为已读
     */
    @Transactional(rollbackFor = Exception.class)
    public void markAsRead(Long notificationId, Long userId) {
        LeaderboardNotification notification = notificationMapper.selectById(notificationId);
        if (notification == null) {
            throw new BusinessException("通知不存在");
        }

        if (!notification.getUserId().equals(userId)) {
            throw new BusinessException("无权访问该通知");
        }

        if (notification.getIsRead() == 1) {
            return; // 已读，无需更新
        }

        notification.setIsRead(1);
        notification.setReadAt(LocalDateTime.now());
        notificationMapper.updateById(notification);

        log.info("用户 {} 标记通知 {} 为已读", userId, notificationId);
    }

    /**
     * 批量标记已读
     */
    @Transactional(rollbackFor = Exception.class)
    public void markAllAsRead(Long userId) {
        LambdaQueryWrapper<LeaderboardNotification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LeaderboardNotification::getUserId, userId)
                .eq(LeaderboardNotification::getIsRead, 0)
                .eq(LeaderboardNotification::getDeleted, 0);

        List<LeaderboardNotification> notifications = notificationMapper.selectList(wrapper);

        LocalDateTime now = LocalDateTime.now();
        for (LeaderboardNotification notification : notifications) {
            notification.setIsRead(1);
            notification.setReadAt(now);
            notificationMapper.updateById(notification);
        }

        log.info("用户 {} 标记所有通知为已读，共 {} 条", userId, notifications.size());
    }

    /**
     * 获取未读通知数量
     */
    public long getUnreadCount(Long userId) {
        LambdaQueryWrapper<LeaderboardNotification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LeaderboardNotification::getUserId, userId)
                .eq(LeaderboardNotification::getIsRead, 0)
                .eq(LeaderboardNotification::getDeleted, 0);

        return notificationMapper.selectCount(wrapper);
    }
}
