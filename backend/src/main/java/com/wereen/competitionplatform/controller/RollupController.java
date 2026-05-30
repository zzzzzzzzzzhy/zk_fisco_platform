package com.wereen.competitionplatform.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wereen.competitionplatform.common.Result;
import com.wereen.competitionplatform.mapper.ChainProofMapper;
import com.wereen.competitionplatform.model.entity.ChainProof;
import com.wereen.competitionplatform.task.RewardDistributionTask;
import com.wereen.competitionplatform.task.RewardEventRollupTask;
import com.wereen.competitionplatform.task.RewardRollupSubmitTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/rollup")
@RequiredArgsConstructor
public class RollupController {

    private final ChainProofMapper chainProofMapper;
    private final ObjectMapper objectMapper;
    private final RewardEventRollupTask rollupTask;
    private final RewardRollupSubmitTask submitTask;
    private final RewardDistributionTask distributionTask;

    @GetMapping("/batches")
    public Result<List<Map<String, Object>>> listBatches(
        @RequestParam(required = false) String eventType,
        @RequestParam(defaultValue = "20") int size
    ) {
        List<String> bizTypes = resolveBizTypes(eventType);
        if (bizTypes.isEmpty()) {
            return Result.success(List.of());
        }
        List<ChainProof> proofs = chainProofMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ChainProof>()
                .in(ChainProof::getBizType, bizTypes)
                .orderByDesc(ChainProof::getCreatedAt)
                .last("limit " + Math.max(1, size))
        );

        List<Map<String, Object>> result = new ArrayList<>();
        for (ChainProof proof : proofs) {
            Map<String, Object> data = new HashMap<>();
            data.put("bizType", proof.getBizType());
            data.put("batchId", proof.getBizId());
            data.put("status", proof.getStatus());
            data.put("txHash", proof.getTxHash());
            data.put("createdAt", proof.getCreatedAt());
            data.put("metadata", parseMetadata(proof.getMetadata()));
            result.add(data);
        }
        return Result.success(result);
    }

    @PostMapping("/run")
    public Result<Map<String, Object>> runRollup(@RequestParam(defaultValue = "all") String action) {
        String normalized = action == null ? "all" : action.trim().toLowerCase();
        Map<String, Object> response = new HashMap<>();
        switch (normalized) {
            case "window" -> rollupTask.rollupHourlyForce();
            case "submit" -> submitTask.submitPendingForce();
            case "distribute" -> distributionTask.distributeForce();
            case "all" -> {
                rollupTask.rollupHourlyForce();
                submitTask.submitPendingForce();
                distributionTask.distributeForce();
            }
            default -> {
                return Result.error("Unsupported action: " + action);
            }
        }
        response.put("action", normalized);
        response.put("status", "ok");
        return Result.success(response);
    }

    private List<String> resolveBizTypes(String eventType) {
        if (!StringUtils.hasText(eventType)) {
            return List.of("CONTENT_SHARE_ROLLUP", "COMMENT_ROLLUP", "CHECKIN_ROLLUP");
        }
        return switch (eventType.toUpperCase()) {
            case "CONTENT_SHARE" -> List.of("CONTENT_SHARE_ROLLUP");
            case "COMMENT" -> List.of("COMMENT_ROLLUP");
            case "CHECKIN" -> List.of("CHECKIN_ROLLUP");
            default -> List.of();
        };
    }

    private Map<String, Object> parseMetadata(String metadataJson) {
        if (!StringUtils.hasText(metadataJson)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(metadataJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }
}
