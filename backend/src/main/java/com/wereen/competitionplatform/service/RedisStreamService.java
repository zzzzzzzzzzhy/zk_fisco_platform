package com.wereen.competitionplatform.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Redis Streams 服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisStreamService {

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 发送任务到Stream
     */
    public void sendTask(String streamKey, Map<String, String> taskData) {
        try {
            ObjectRecord<String, Map<String, String>> record = StreamRecords.newRecord()
                    .ofObject(taskData)
                    .withStreamKey(streamKey);

            stringRedisTemplate.opsForStream().add(record);
            log.info("发送任务到Stream成功: stream={}, data={}", streamKey, taskData);
        } catch (Exception e) {
            log.error("发送任务到Stream失败: stream={}", streamKey, e);
            throw new RuntimeException("发送任务失败", e);
        }
    }

    /**
     * 发送上传预检任务
     */
    public void sendUploadPrecheckTask(Long submissionId, String filePath) {
        Map<String, String> taskData = new HashMap<>();
        taskData.put("submissionId", String.valueOf(submissionId));
        taskData.put("filePath", filePath);
        taskData.put("taskType", "upload_precheck");

        sendTask("tasks:upload_precheck", taskData);
    }

    /**
     * 发送链上存证任务
     */
    public void sendChainEvidenceTask(String bizType, Long bizId, String dataHash) {
        Map<String, String> taskData = new HashMap<>();
        taskData.put("bizType", bizType);
        taskData.put("bizId", String.valueOf(bizId));
        taskData.put("dataHash", dataHash);
        taskData.put("taskType", "chain_evidence");

        sendTask("tasks:chain_evidence", taskData);
    }

    /**
     * 发送评测任务
     */
    public void sendEvaluateTask(Long submissionId, Long competitionId) {
        Map<String, String> taskData = new HashMap<>();
        taskData.put("submissionId", String.valueOf(submissionId));
        taskData.put("competitionId", String.valueOf(competitionId));
        taskData.put("taskType", "evaluate");

        sendTask("tasks:evaluate", taskData);
    }

    /**
     * 发送榜单冻结任务
     */
    public void sendLeaderboardFreezeTask(Long competitionId, Long leaderboardId) {
        Map<String, String> taskData = new HashMap<>();
        taskData.put("competitionId", String.valueOf(competitionId));
        taskData.put("leaderboardId", String.valueOf(leaderboardId));
        taskData.put("taskType", "leaderboard_freeze");

        sendTask("tasks:leaderboard_freeze", taskData);
    }

    /**
     * 发送奖金入账任务
     */
    public void sendPrizeBookkeepingTask(Long batchId) {
        Map<String, String> taskData = new HashMap<>();
        taskData.put("batchId", String.valueOf(batchId));
        taskData.put("taskType", "prize_bookkeeping");

        sendTask("tasks:prize_bookkeeping", taskData);
    }

    /**
     * 发送提现申请任务
     */
    public void sendWithdrawApplyTask(Long withdrawId) {
        Map<String, String> taskData = new HashMap<>();
        taskData.put("withdrawId", String.valueOf(withdrawId));
        taskData.put("taskType", "withdraw_apply");

        sendTask("tasks:withdraw_apply", taskData);
    }

    /**
     * 发送提现出款任务
     */
    public void sendWithdrawPayoutTask(Long withdrawId) {
        Map<String, String> taskData = new HashMap<>();
        taskData.put("withdrawId", String.valueOf(withdrawId));
        taskData.put("taskType", "withdraw_payout");

        sendTask("tasks:withdraw_payout", taskData);
    }
}
