package com.wereen.competitionplatform.task;

import com.wereen.competitionplatform.service.RollupRewardDistributorService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PendingTxCleanupRunner {

    @Value("${reward.rollup.cancel-tx-hashes:}")
    private String cancelTxHashes;

    @Value("${reward.rollup.replace-tx-hashes:}")
    private String replaceTxHashes;

    @Value("${reward.rollup.cancel-nonce-range:}")
    private String cancelNonceRange;

    private final RollupRewardDistributorService distributorService;

    @PostConstruct
    public void runOnce() {
        List<String> replaceHashes = parseHashes(replaceTxHashes);
        for (String txHash : replaceHashes) {
            try {
                String newHash = distributorService.replacePendingTransaction(txHash);
                if (StringUtils.hasText(newHash)) {
                    log.warn("已发送替换交易: oldTx={}, newTx={}", txHash, newHash);
                } else {
                    log.warn("未找到可替换交易或已上链: {}", txHash);
                }
            } catch (Exception e) {
                log.warn("替换交易失败: txHash={}", txHash, e);
            }
        }

        List<String> hashes = parseHashes(cancelTxHashes);
        for (String txHash : hashes) {
            try {
                String newHash = distributorService.cancelPendingTransaction(txHash);
                if (StringUtils.hasText(newHash)) {
                    log.warn("已发送取消交易: oldTx={}, cancelTx={}", txHash, newHash);
                } else {
                    log.warn("未找到可取消交易或已上链: {}", txHash);
                }
            } catch (Exception e) {
                log.warn("取消交易失败: txHash={}, err={}", txHash, e.getMessage());
            }
        }

        Range nonceRange = parseNonceRange(cancelNonceRange);
        if (nonceRange == null) {
            return;
        }
        for (BigInteger nonce = nonceRange.start; nonce.compareTo(nonceRange.end) <= 0; nonce = nonce.add(BigInteger.ONE)) {
            try {
                String newHash = distributorService.sendCancelTransaction(nonce);
                if (StringUtils.hasText(newHash)) {
                    log.warn("已发送取消交易: nonce={}, cancelTx={}", nonce, newHash);
                } else {
                    log.warn("取消交易跳过: nonce={}", nonce);
                }
            } catch (Exception e) {
                log.warn("取消交易失败: nonce={}, err={}", nonce, e.getMessage());
            }
        }
    }

    private List<String> parseHashes(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        String[] parts = raw.split("[,\\s]+");
        List<String> out = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return out;
    }

    private Range parseNonceRange(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String[] parts = raw.split("-");
        if (parts.length != 2) {
            return null;
        }
        BigInteger start = parseNonce(parts[0].trim());
        BigInteger end = parseNonce(parts[1].trim());
        if (start == null || end == null || start.compareTo(end) > 0) {
            return null;
        }
        return new Range(start, end);
    }

    private BigInteger parseNonce(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        try {
            if (trimmed.startsWith("0x") || trimmed.startsWith("0X")) {
                return new BigInteger(trimmed.substring(2), 16);
            }
            return new BigInteger(trimmed, 10);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static class Range {
        private final BigInteger start;
        private final BigInteger end;

        private Range(BigInteger start, BigInteger end) {
            this.start = start;
            this.end = end;
        }
    }
}
