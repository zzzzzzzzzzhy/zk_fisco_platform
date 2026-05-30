package com.wereen.competitionplatform.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wereen.competitionplatform.mapper.CompetitionMapper;
import com.wereen.competitionplatform.model.entity.Competition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 竞赛状态自动转换定时任务
 * 每分钟执行一次，自动更新竞赛状态
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompetitionStatusTask {

    private final CompetitionMapper competitionMapper;

    /**
     * 自动转换竞赛状态
     * 0-草稿 1-报名中 2-进行中 3-已结束 4-已取消
     *
     * 转换规则：
     * 1. 报名中(1) → 进行中(2): 当前时间 >= 提交开始时间
     * 2. 进行中(2) → 已结束(3): 当前时间 > 提交结束时间
     */
    @Scheduled(cron = "0 * * * * ?") // 每分钟执行一次
    public void updateCompetitionStatus() {
        LocalDateTime now = LocalDateTime.now();

        try {
            // 1. 报名中 → 进行中
            LambdaQueryWrapper<Competition> wrapper1 = new LambdaQueryWrapper<>();
            wrapper1.eq(Competition::getStatus, 1) // 报名中
                    .le(Competition::getSubmissionStartTime, now); // 提交开始时间 <= 当前时间

            List<Competition> toOngoingList = competitionMapper.selectList(wrapper1);
            for (Competition competition : toOngoingList) {
                competition.setStatus(2); // 进行中
                competitionMapper.updateById(competition);
                log.info("竞赛状态自动更新: id={}, title={}, 报名中 → 进行中",
                        competition.getId(), competition.getTitle());
            }

            // 2. 进行中 → 已结束
            LambdaQueryWrapper<Competition> wrapper2 = new LambdaQueryWrapper<>();
            wrapper2.eq(Competition::getStatus, 2) // 进行中
                    .lt(Competition::getSubmissionEndTime, now); // 提交结束时间 < 当前时间

            List<Competition> toEndedList = competitionMapper.selectList(wrapper2);
            for (Competition competition : toEndedList) {
                competition.setStatus(3); // 已结束
                competitionMapper.updateById(competition);
                log.info("竞赛状态自动更新: id={}, title={}, 进行中 → 已结束",
                        competition.getId(), competition.getTitle());
            }

            if (!toOngoingList.isEmpty() || !toEndedList.isEmpty()) {
                log.info("竞赛状态自动更新完成: 报名中→进行中 {} 个, 进行中→已结束 {} 个",
                        toOngoingList.size(), toEndedList.size());
            }

        } catch (Exception e) {
            log.error("竞赛状态自动更新失败", e);
        }
    }
}
