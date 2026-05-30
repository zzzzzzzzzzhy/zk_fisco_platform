package com.wereen.competitionplatform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wereen.competitionplatform.mapper.ChainProofMapper;
import com.wereen.competitionplatform.model.entity.ChainProof;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 模拟区块链服务 (用于测试环境)
 * 生产环境使用真实的BlockchainService
 */
@Slf4j
//@Service("mockBlockchainService")  // 禁用模拟服务，使用真实的 BlockchainService
@RequiredArgsConstructor
public class MockBlockchainService {

    private final ChainProofMapper chainProofMapper;
    private final ObjectMapper objectMapper;

    // 模拟的区块高度计数器
    private static Long mockBlockHeight = 1000000L;

    /**
     * 上链存证 (模拟实现)
     *
     * @param bizType 业务类型 (SUBMISSION/EVALUATION/LEADERBOARD/PRIZE_BATCH/WITHDRAW)
     * @param bizId   业务ID
     * @param data    上链数据
     * @return ChainProof 存证记录
     */
    @Transactional(rollbackFor = Exception.class)
    public ChainProof storeEvidence(String bizType, Long bizId, Map<String, Object> data) {
        try {
            // 1. 序列化数据并计算哈希
            String jsonData = objectMapper.writeValueAsString(data);
            String dataHash = calculateSHA256(jsonData);

            // 2. 模拟区块链调用
            ChainProof chainProof = createMockChainProof(bizType, bizId, dataHash, jsonData);

            // 3. 保存存证记录
            chainProofMapper.insert(chainProof);

            log.info("[模拟] 区块链上链成功: bizType={}, bizId={}, txHash={}, blockHeight={}",
                    bizType, bizId, chainProof.getTxHash(), chainProof.getBlockHeight());

            return chainProof;

        } catch (Exception e) {
            log.error("区块链上链失败: bizType={}, bizId={}", bizType, bizId, e);
            throw new RuntimeException("区块链上链失败: " + e.getMessage());
        }
    }

    /**
     * 创建模拟的链上存证记录
     */
    private ChainProof createMockChainProof(String bizType, Long bizId, String dataHash, String metadata) {
        ChainProof chainProof = new ChainProof();
        chainProof.setBizType(bizType);
        chainProof.setBizId(bizId);
        chainProof.setDataHash(dataHash);

        // 模拟交易哈希 (0x + 64位十六进制字符)
        String txHash = "0x" + UUID.randomUUID().toString().replace("-", "") +
                UUID.randomUUID().toString().replace("-", "").substring(0, 32);
        chainProof.setTxHash(txHash);

        // 模拟区块高度 (递增)
        synchronized (MockBlockchainService.class) {
            chainProof.setBlockHeight(++mockBlockHeight);
        }

        // 模拟区块时间
        chainProof.setBlockTime(LocalDateTime.now());

        // 存储元数据
        chainProof.setMetadata(metadata);

        // 上链成功
        chainProof.setStatus(2);

        return chainProof;
    }

    /**
     * 验证存证
     */
    public boolean verifyEvidence(String txHash) {
        try {
            ChainProof chainProof = chainProofMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ChainProof>()
                            .eq(ChainProof::getTxHash, txHash)
            );

            boolean exists = chainProof != null;
            log.info("[模拟] 存证验证: txHash={}, exists={}", txHash, exists);
            return exists;

        } catch (Exception e) {
            log.error("存证验证失败: txHash={}", txHash, e);
            return false;
        }
    }

    /**
     * 计算SHA256哈希
     */
    private String calculateSHA256(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("计算哈希失败", e);
        }
    }
}
