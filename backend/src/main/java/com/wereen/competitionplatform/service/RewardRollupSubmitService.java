package com.wereen.competitionplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wereen.competitionplatform.mapper.ChainProofMapper;
import com.wereen.competitionplatform.model.entity.ChainProof;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RewardRollupSubmitService {

    private final ChainProofMapper chainProofMapper;
    private final ObjectMapper objectMapper;
    private final RollupChainService rollupChainService;
    private final RollupTaskLogService rollupTaskLogService;
    private final RewardProofGeneratorService proofGeneratorService;

    @Value("${reward.rollup.pending-grace-minutes:30}")
    private long pendingGraceMinutes;

    @Value("${reward.rollup.retry-minutes:30}")
    private long retryMinutes;

    public void submitPendingRollups() {
        List<ChainProof> pending = chainProofMapper.selectList(
            new LambdaQueryWrapper<ChainProof>()
                .in(ChainProof::getStatus, 0, 2, 3)
                .in(ChainProof::getBizType, "CONTENT_SHARE_ROLLUP", "COMMENT_ROLLUP", "CHECKIN_ROLLUP")
                .eq(ChainProof::getDeleted, 0)
                .orderByAsc(ChainProof::getCreatedAt)
                .last("limit 20")
        );

        for (ChainProof proof : pending) {
            trySubmit(proof);
        }
    }

    private void trySubmit(ChainProof proof) {
        Integer status = proof.getStatus();
        if (status != null && status == 2) {
            RollupChainService.TransactionState state = rollupChainService.getTransactionState(proof.getTxHash());
            if (state == RollupChainService.TransactionState.MINED_SUCCESS) {
                return;
            }
            if (state == RollupChainService.TransactionState.PENDING) {
                return;
            }
            if (withinGracePeriod(proof.getBlockTime(), pendingGraceMinutes)) {
                return;
            }
            proof.setStatus(3);
            chainProofMapper.updateById(proof);
        }
        if (status != null && status == 3) {
            if (withinGracePeriod(proof.getBlockTime(), retryMinutes)) {
                return;
            }
        }

        Map<String, Object> metadata = readMetadata(proof.getMetadata());
        if (metadata == null) {
            return;
        }
        Object batchIdTextValue = metadata.getOrDefault("batchIdText", metadata.get("batchId"));
        String batchIdText = String.valueOf(batchIdTextValue == null ? proof.getBizId() : batchIdTextValue);
        String merkleRoot = asString(metadata.get("merkleRoot"));
        String journalDigest = asString(metadata.get("journalDigest"));
        String proofFile = asString(metadata.get("proofFile"));
        Long count = asLong(metadata.get("count"));
        Long windowStart = asLong(metadata.get("windowStartEpoch"));
        Long windowEnd = asLong(metadata.get("windowEndEpoch"));

        if (!StringUtils.hasText(merkleRoot) || !StringUtils.hasText(journalDigest)
            || count == null || windowStart == null || windowEnd == null || !StringUtils.hasText(proofFile)) {
            log.warn("Rollup 元数据不完整，跳过提交: batchId={}", batchIdText);
            return;
        }

        Path proofPath = Path.of(proofFile);
        if (!Files.exists(proofPath)) {
            proofGeneratorService.generateProofIfConfigured(metadata);
        }
        if (!Files.exists(proofPath)) {
            log.info("Rollup 证明文件不存在，跳过: {}", proofPath);
            return;
        }

        try {
            byte[] proofBytes = Files.readAllBytes(proofPath);
            String txHash = rollupChainService.submitBatch(
                proofBytes,
                journalDigest,
                proof.getBizId(),
                merkleRoot,
                count,
                windowStart,
                windowEnd
            );

            proof.setTxHash(txHash);
            proof.setStatus(2);
            proof.setBlockTime(LocalDateTime.now());
            chainProofMapper.updateById(proof);
            rollupTaskLogService.log("rollup_submit_success", Map.of(
                "batchId", batchIdText,
                "txHash", txHash
            ));
            log.info("Rollup 上链成功: batchId={}, txHash={}", batchIdText, txHash);
        } catch (Exception e) {
            proof.setStatus(3);
            chainProofMapper.updateById(proof);
            rollupTaskLogService.log("rollup_submit_failed", Map.of(
                "batchId", batchIdText,
                "error", e.getMessage() == null ? "unknown" : e.getMessage()
            ));
            log.error("Rollup 上链失败: batchId={}", batchIdText, e);
        }
    }

    private Map<String, Object> readMetadata(String metadataJson) {
        if (!StringUtils.hasText(metadataJson)) {
            return null;
        }
        try {
            return objectMapper.readValue(metadataJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Rollup 元数据解析失败: {}", e.getMessage());
            return null;
        }
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean withinGracePeriod(LocalDateTime lastSubmit, long graceMinutes) {
        if (lastSubmit == null || graceMinutes <= 0) {
            return false;
        }
        Duration age = Duration.between(lastSubmit, LocalDateTime.now());
        return age.toMinutes() < graceMinutes;
    }
}
