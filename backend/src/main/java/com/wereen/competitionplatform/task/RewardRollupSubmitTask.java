package com.wereen.competitionplatform.task;

import com.wereen.competitionplatform.service.RewardRollupSubmitService;
import com.wereen.competitionplatform.service.RollupTaskLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class RewardRollupSubmitTask {

    private final RewardRollupSubmitService submitService;
    private final RollupTaskLogService rollupTaskLogService;

    @Value("${reward.rollup.enabled:true}")
    private boolean enabled;

    @Scheduled(cron = "${reward.rollup.submit-cron:0 */5 * * * ?}")
    public void submitPending() {
        runSubmit(false);
    }

    public void submitPendingForce() {
        runSubmit(true);
    }

    private void runSubmit(boolean force) {
        if (!enabled && !force) {
            return;
        }
        rollupTaskLogService.log("rollup_submit_start", Map.of());
        submitService.submitPendingRollups();
        rollupTaskLogService.log("rollup_submit_finish", Map.of());
    }
}
