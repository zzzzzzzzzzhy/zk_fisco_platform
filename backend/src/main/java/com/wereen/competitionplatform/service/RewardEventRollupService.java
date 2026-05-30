package com.wereen.competitionplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wereen.competitionplatform.mapper.ChainProofMapper;
import com.wereen.competitionplatform.model.entity.ChainProof;
import com.wereen.competitionplatform.model.entity.RewardEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RewardEventRollupService {

    private static final DateTimeFormatter BATCH_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHH");
    private static final DateTimeFormatter TS_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final RewardEventService rewardEventService;
    private final ChainProofMapper chainProofMapper;
    private final ObjectMapper objectMapper;
    private final RewardProofGeneratorService proofGeneratorService;
    private final RollupTaskLogService rollupTaskLogService;

    @Value("${reward.rollup.proof-base-dir:proofs/rollup}")
    private String proofBaseDir;

    public void rollupEvents(String eventType, long typeId, String bizType,
                             LocalDateTime windowStart, LocalDateTime windowEnd) {
        if (windowStart == null || windowEnd == null || !windowEnd.isAfter(windowStart)) {
            log.warn("Reward Rollup 跳过: 无效时间窗口 eventType={}, windowStart={}, windowEnd={}",
                eventType, windowStart, windowEnd);
            return;
        }
        if (!StringUtils.hasText(eventType) || !StringUtils.hasText(bizType)) {
            log.warn("Reward Rollup 跳过: eventType/bizType 为空");
            return;
        }

        String batchIdText = windowStart.format(BATCH_FORMATTER);
        long batchId = typeId * 10_000_000_000L + Long.parseLong(batchIdText);

        if (isBatchProcessed(bizType, batchId)) {
            log.info("Reward Rollup 跳过: 批次已存在 eventType={}, batchId={}", eventType, batchId);
            return;
        }

        List<RewardEvent> events = rewardEventService.listPendingEvents(eventType, windowStart, windowEnd);
        if (events.isEmpty()) {
            log.info("Reward Rollup: 无事件 eventType={}, windowStart={}, windowEnd={}",
                eventType, windowStart, windowEnd);
            return;
        }

        List<byte[]> leaves = new ArrayList<>();
        List<Long> eventIds = new ArrayList<>();
        for (RewardEvent event : events) {
            if (!StringUtils.hasText(event.getSignature())) {
                continue;
            }
            String leafInput = buildLeafInput(event);
            leaves.add(hashBytes(leafInput.getBytes(StandardCharsets.UTF_8)));
            eventIds.add(event.getId());
        }

        if (leaves.isEmpty()) {
            log.info("Reward Rollup: 无有效签名事件 eventType={}, windowStart={}, windowEnd={}",
                eventType, windowStart, windowEnd);
            return;
        }

        byte[] merkleRoot = buildMerkleRoot(leaves);
        String merkleHex = toHex(merkleRoot);
        long windowStartEpoch = windowStart.toEpochSecond(ZoneOffset.UTC);
        long windowEndEpoch = windowEnd.toEpochSecond(ZoneOffset.UTC);
        String journalDigest = computeJournalDigest(batchId, merkleRoot, leaves.size(), windowStartEpoch, windowEndEpoch);
        String proofFile = buildProofFile(eventType, batchIdText);

        ChainProof proof = new ChainProof();
        proof.setBizType(bizType);
        proof.setBizId(batchId);
        proof.setDataHash(merkleHex);
        String metadataJson = buildMetadataJson(eventType, batchId, batchIdText, windowStart, windowEnd, windowStartEpoch,
            windowEndEpoch, leaves.size(), eventIds, merkleHex, journalDigest, proofFile);
        proof.setMetadata(metadataJson);
        proof.setStatus(0);
        proof.setChainNetwork("POLYGON");
        chainProofMapper.insert(proof);

        proofGeneratorService.generateProofIfConfigured(readMetadata(metadataJson));
        rewardEventService.markBatched(eventIds, batchId);
        rollupTaskLogService.log("rollup_batch_created", Map.of(
            "eventType", eventType,
            "batchId", batchIdText,
            "count", String.valueOf(leaves.size()),
            "merkleRoot", merkleHex
        ));
        log.info("Reward Rollup 完成: eventType={}, batchId={}, count={}, merkleRoot={}",
            eventType, batchIdText, leaves.size(), merkleHex);
    }

    private Map<String, Object> readMetadata(String metadataJson) {
        if (!StringUtils.hasText(metadataJson)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(metadataJson, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private boolean isBatchProcessed(String bizType, long batchId) {
        Long count = chainProofMapper.selectCount(
            new LambdaQueryWrapper<ChainProof>()
                .eq(ChainProof::getBizType, bizType)
                .eq(ChainProof::getBizId, batchId)
                .eq(ChainProof::getDeleted, 0)
        );
        return count != null && count > 0;
    }

    private String buildLeafInput(RewardEvent event) {
        String userId = event.getUserId() == null ? "" : event.getUserId().toString();
        String eventType = event.getEventType() == null ? "" : event.getEventType();
        String bizId = event.getBizId() == null ? "" : event.getBizId();
        String signature = event.getSignature() == null ? "" : event.getSignature();
        String payload = event.getPayload() == null ? "" : event.getPayload();
        long ts = event.getCreatedAt() == null ? 0L : event.getCreatedAt().toEpochSecond(ZoneOffset.UTC);
        String payloadHash = toHex(hashBytes(payload.getBytes(StandardCharsets.UTF_8)));
        return userId + "|" + eventType + "|" + bizId + "|" + signature + "|" + payloadHash + "|" + ts;
    }

    private String buildMetadataJson(String eventType, long batchId, String batchIdText, LocalDateTime windowStart,
                                     LocalDateTime windowEnd, long windowStartEpoch, long windowEndEpoch,
                                     int count, List<Long> eventIds, String merkleRoot,
                                     String journalDigest, String proofFile) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("eventType", eventType);
        metadata.put("batchId", batchId);
        metadata.put("batchIdText", batchIdText);
        metadata.put("windowStart", windowStart.format(TS_FORMATTER));
        metadata.put("windowEnd", windowEnd.format(TS_FORMATTER));
        metadata.put("windowStartEpoch", windowStartEpoch);
        metadata.put("windowEndEpoch", windowEndEpoch);
        metadata.put("count", count);
        metadata.put("eventIds", eventIds);
        metadata.put("merkleRoot", merkleRoot);
        metadata.put("journalDigest", journalDigest);
        metadata.put("proofFile", proofFile);
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            log.warn("Reward Rollup 元数据序列化失败: batchId={}, err={}", batchIdText, e.getMessage());
            return "{\"batchId\":\"" + batchIdText + "\",\"count\":" + count + "}";
        }
    }

    private String buildProofFile(String eventType, String batchIdText) {
        String safeType = eventType == null ? "unknown" : eventType.toLowerCase();
        return proofBaseDir + "/" + safeType + "/" + batchIdText + ".bin";
    }

    private byte[] buildMerkleRoot(List<byte[]> leaves) {
        List<byte[]> nodes = new ArrayList<>(leaves);
        while (nodes.size() > 1) {
            List<byte[]> next = new ArrayList<>();
            for (int i = 0; i < nodes.size(); i += 2) {
                byte[] left = nodes.get(i);
                byte[] right = (i + 1 < nodes.size()) ? nodes.get(i + 1) : left;
                next.add(hashPair(left, right));
            }
            nodes = next;
        }
        return nodes.get(0);
    }

    private byte[] hashPair(byte[] left, byte[] right) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(left);
            digest.update(right);
            return digest.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private byte[] hashBytes(byte[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private String computeJournalDigest(long batchId, byte[] merkleRoot, int count,
                                        long windowStartEpoch, long windowEndEpoch) {
        byte[] batchWord = toU256Bytes(batchId);
        byte[] countWord = toU256Bytes(count);
        byte[] startWord = toU256Bytes(windowStartEpoch);
        byte[] endWord = toU256Bytes(windowEndEpoch);
        byte[] journal = encodeJournalBytes(batchWord, merkleRoot, countWord, startWord, endWord);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return "0x" + toHex(digest.digest(journal));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private byte[] toU256Bytes(long value) {
        byte[] out = new byte[32];
        for (int i = 0; i < 8; i++) {
            out[31 - i] = (byte) ((value >>> (8 * i)) & 0xff);
        }
        return out;
    }

    private byte[] encodeJournalBytes(byte[]... values) {
        int total = values.length * 32 * 4;
        byte[] out = new byte[total];
        int idx = 0;
        for (byte[] value : values) {
            if (value == null || value.length != 32) {
                throw new IllegalArgumentException("Journal value must be 32 bytes");
            }
            for (byte b : value) {
                out[idx++] = b;
                out[idx++] = 0;
                out[idx++] = 0;
                out[idx++] = 0;
            }
        }
        return out;
    }
}
