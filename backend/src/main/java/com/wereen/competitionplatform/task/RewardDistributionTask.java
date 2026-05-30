package com.wereen.competitionplatform.task;

import com.wereen.competitionplatform.service.RewardDistributionService;
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
public class RewardDistributionTask {

    private final RewardDistributionService distributionService;
    private final RollupTaskLogService rollupTaskLogService;

    @Value("${reward.rollup.enabled:true}")
    private boolean enabled;

    @Scheduled(cron = "${reward.rollup.distribute-cron:0 */10 * * * ?}")
    public void distribute() {
        runDistribute(false);
    }

    public void distributeForce() {
        runDistribute(true);
    }

    private void runDistribute(boolean force) {
        if (!enabled && !force) {
            return;
        }
        rollupTaskLogService.log("rollup_distribute_start", Map.of());
        distributionService.distributePendingBatches();
        rollupTaskLogService.log("rollup_distribute_finish", Map.of());
    }
}
