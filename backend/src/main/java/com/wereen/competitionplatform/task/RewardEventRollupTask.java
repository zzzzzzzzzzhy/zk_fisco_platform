package com.wereen.competitionplatform.task;

import com.wereen.competitionplatform.service.RewardEventRollupService;
import com.wereen.competitionplatform.service.RollupTaskLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class RewardEventRollupTask {

    private final RewardEventRollupService rollupService;
    private final RollupTaskLogService rollupTaskLogService;

    @Value("${reward.rollup.enabled:true}")
    private boolean enabled;

    @Value("${reward.rollup.window-minutes:120}")
    private long windowMinutes;

    @Scheduled(cron = "${reward.rollup.cron:0 0 * * * ?}")
    public void rollupHourly() {
        runRollup(false);
    }

    public void rollupHourlyForce() {
        runRollup(true);
    }

    private void runRollup(boolean force) {
        if (!enabled && !force) {
            return;
        }
        if (windowMinutes <= 0) {
            log.warn("Reward Rollup 跳过: window-minutes 配置无效 {}", windowMinutes);
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime windowEnd = now.truncatedTo(ChronoUnit.MINUTES);
        LocalDateTime windowStart = windowEnd.minusMinutes(windowMinutes);

        rollupTaskLogService.log("rollup_window_start", Map.of(
            "windowStart", windowStart.toString(),
            "windowEnd", windowEnd.toString(),
            "windowMinutes", String.valueOf(windowMinutes)
        ));

        rollupService.rollupEvents("CONTENT_SHARE", 1, "CONTENT_SHARE_ROLLUP", windowStart, windowEnd);
        rollupService.rollupEvents("COMMENT", 2, "COMMENT_ROLLUP", windowStart, windowEnd);
        rollupService.rollupEvents("CHECKIN", 3, "CHECKIN_ROLLUP", windowStart, windowEnd);

        rollupTaskLogService.log("rollup_window_finish", Map.of(
            "windowStart", windowStart.toString(),
            "windowEnd", windowEnd.toString()
        ));
    }
}
