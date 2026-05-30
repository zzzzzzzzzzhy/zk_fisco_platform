package com.wereen.competitionplatform.consumer;

import com.wereen.competitionplatform.service.BlockchainEvidenceService;
import com.wereen.competitionplatform.service.BlockchainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fisco.bcos.sdk.model.TransactionReceipt;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

/**
 * 链上存证消费者
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChainEvidenceConsumer implements StreamListener<String, ObjectRecord<String, Map<String, String>>> {

    private final BlockchainService blockchainService;
    private final BlockchainEvidenceService blockchainEvidenceService;

    @Override
    public void onMessage(ObjectRecord<String, Map<String, String>> record) {
        try {
            Map<String, String> taskData = record.getValue();
            String bizType = taskData.get("bizType");
            String bizId = taskData.get("bizId");
            String dataHash = taskData.get("dataHash");

            log.info("处理链上存证任务: bizType={}, bizId={}, hash={}", bizType, bizId, dataHash);

            TransactionReceipt receipt = blockchainService.saveEvidence(bizType, bizId, dataHash);

            if (receipt != null && "0x0".equals(receipt.getStatus())) {
                if ("SUBMISSION".equals(bizType)) {
                    Long submissionId = Long.parseLong(bizId);
                    LocalDateTime blockTime = LocalDateTime.ofInstant(
                            Instant.ofEpochMilli(System.currentTimeMillis()),
                            ZoneId.systemDefault()
                    );

                    blockchainEvidenceService.updateChainStatus(
                            submissionId,
                            2,
                            receipt.getTransactionHash(),
                            parseBlockNumber(receipt.getBlockNumber()),
                            blockTime
                    );
                }

                log.info("链上存证任务完成: bizType={}, bizId={}, txHash={}",
                        bizType, bizId, receipt.getTransactionHash());
            } else {
                log.error("链上存证失败: bizType={}, bizId={}", bizType, bizId);
                if ("SUBMISSION".equals(bizType)) {
                    blockchainEvidenceService.updateChainStatus(Long.parseLong(bizId), 3, null, null, null);
                }
            }
        } catch (Exception e) {
            log.error("处理链上存证任务失败", e);
        }
    }

    /**
     * 解析区块高度，兼容0x前缀
     */
    private Long parseBlockNumber(String blockNumber) {
        if (blockNumber == null) {
            return null;
        }
        try {
            String value = blockNumber.trim();
            if (value.startsWith("0x") || value.startsWith("0X")) {
                return new java.math.BigInteger(value.substring(2), 16).longValue();
            }
            return new java.math.BigInteger(value).longValue();
        } catch (Exception e) {
            log.warn("解析区块高度失败: {}", blockNumber, e);
            return null;
        }
    }
}
