package com.wereen.competitionplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wereen.competitionplatform.exception.BusinessException;
import com.wereen.competitionplatform.mapper.LeaderboardAppealMapper;
import com.wereen.competitionplatform.mapper.LeaderboardMapper;
import com.wereen.competitionplatform.model.entity.Leaderboard;
import com.wereen.competitionplatform.model.entity.LeaderboardAppeal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 榜单异议服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeaderboardAppealService {

    private final LeaderboardAppealMapper appealMapper;
    private final LeaderboardMapper leaderboardMapper;
    private final LeaderboardNotificationService notificationService;

    /**
     * 创建榜单异议
     */
    @Transactional(rollbackFor = Exception.class)
    public LeaderboardAppeal createAppeal(Long leaderboardId, Long userId, String appealType,
                                         String appealReason, String evidenceFiles) {
        // 检查榜单是否存在
        Leaderboard leaderboard = leaderboardMapper.selectById(leaderboardId);
        if (leaderboard == null) {
            throw new BusinessException("榜单不存在");
        }

        // 检查榜单是否在公示期
        if (!"IN_PUBLICITY".equals(leaderboard.getPublicityStatus())) {
            throw new BusinessException("该榜单不在公示期，无法提交异议");
        }

        // 检查公示期是否已过
        if (LocalDateTime.now().isAfter(leaderboard.getPublicityEndTime())) {
            throw new BusinessException("公示期已结束，无法提交异议");
        }

        // 检查用户是否已提交过异议
        LambdaQueryWrapper<LeaderboardAppeal> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LeaderboardAppeal::getLeaderboardId, leaderboardId)
                .eq(LeaderboardAppeal::getUserId, userId)
                .eq(LeaderboardAppeal::getDeleted, 0);

        long count = appealMapper.selectCount(wrapper);
        if (count > 0) {
            throw new BusinessException("您已对该榜单提交过异议，请勿重复提交");
        }

        // 创建异议记录
        LeaderboardAppeal appeal = new LeaderboardAppeal();
        appeal.setLeaderboardId(leaderboardId);
        appeal.setCompetitionId(leaderboard.getCompetitionId());
        appeal.setUserId(userId);
        appeal.setAppealType(appealType);
        appeal.setAppealReason(appealReason);
        appeal.setEvidenceFiles(evidenceFiles);
        appeal.setStatus("PENDING");
        appeal.setDeleted(0);

        appealMapper.insert(appeal);

        log.info("用户 {} 对榜单 {} 提交异议，类型: {}", userId, leaderboardId, appealType);

        return appeal;
    }

    /**
     * 获取竞赛的所有异议（管理员）
     */
    public List<LeaderboardAppeal> getAppealsByCompetition(Long competitionId) {
        LambdaQueryWrapper<LeaderboardAppeal> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LeaderboardAppeal::getCompetitionId, competitionId)
                .eq(LeaderboardAppeal::getDeleted, 0)
                .orderByDesc(LeaderboardAppeal::getCreatedAt);

        return appealMapper.selectList(wrapper);
    }

    /**
     * 获取用户的异议列表
     */
    public List<LeaderboardAppeal> getUserAppeals(Long userId) {
        LambdaQueryWrapper<LeaderboardAppeal> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LeaderboardAppeal::getUserId, userId)
                .eq(LeaderboardAppeal::getDeleted, 0)
                .orderByDesc(LeaderboardAppeal::getCreatedAt);

        return appealMapper.selectList(wrapper);
    }

    /**
     * 审核异议（管理员）
     */
    @Transactional(rollbackFor = Exception.class)
    public void reviewAppeal(Long appealId, Long reviewerId, String status,
                           String reviewResult, String reviewNotes) {
        LeaderboardAppeal appeal = appealMapper.selectById(appealId);
        if (appeal == null) {
            throw new BusinessException("异议记录不存在");
        }

        if (!"PENDING".equals(appeal.getStatus()) && !"REVIEWING".equals(appeal.getStatus())) {
            throw new BusinessException("该异议已处理完毕，无法重复审核");
        }

        // 更新异议状态
        appeal.setStatus(status);
        appeal.setReviewerId(reviewerId);
        appeal.setReviewResult(reviewResult);
        appeal.setReviewNotes(reviewNotes);
        appeal.setReviewedAt(LocalDateTime.now());

        appealMapper.updateById(appeal);

        // 发送通知给申诉用户
        String notificationTitle = "榜单异议处理结果";
        String notificationContent = String.format(
                "您对竞赛 %d 的榜单异议已被%s。审核结果：%s",
                appeal.getCompetitionId(),
                "ACCEPTED".equals(status) ? "接受" : "拒绝",
                reviewResult
        );

        notificationService.createNotification(
                appeal.getUserId(),
                appeal.getCompetitionId(),
                "APPEAL_RESULT",
                notificationTitle,
                notificationContent,
                null
        );

        log.info("管理员 {} 审核异议 {}，结果: {}", reviewerId, appealId, status);
    }
}
