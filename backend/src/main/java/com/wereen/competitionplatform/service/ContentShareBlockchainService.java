package com.wereen.competitionplatform.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wereen.competitionplatform.mapper.ChainProofMapper;
import com.wereen.competitionplatform.mapper.ContentShareMapper;
import com.wereen.competitionplatform.model.entity.ChainProof;
import com.wereen.competitionplatform.model.entity.ContentShare;
import com.wereen.competitionplatform.model.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fisco.bcos.sdk.model.TransactionReceipt;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 内容分享链上存证编排
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentShareBlockchainService {

    private final ContentShareMapper contentShareMapper;
    private final ChainProofMapper chainProofMapper;
    private final BlockchainService blockchainService;
    private final PolygonContentProofService polygonContentProofService;
    private final ObjectMapper objectMapper;
    private final UserService userService;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    @Async
    public void pushToChainsAsync(Long shareId, Long publisherId, String mediaType) {
        // FISCO 存证（企业内部链，后端主动存证）
        try {
            pushToFisco(shareId);
        } catch (Exception e) {
            log.error("FISCO 内容存证失败: shareId={}", shareId, e);
        }
        
        // Polygon 存证改为前端用户自己签名和发送交易
        // 后端只负责返回签名数据结构，并在前端提交 txHash 后验证和发放奖励
        log.info("⏩ Polygon 存证由前端用户自己完成，后端跳过: shareId={}", shareId);
    }
    
    /**
     * 前端提交 Polygon 存证交易后，后端验证并发放奖励
     */
    public void verifyAndRewardPolygonProof(Long shareId, String txHash, Long publisherId, String mediaType) {
        ContentShare share = getShare(shareId);
        updatePolygonStatus(shareId, 1, txHash, null, null, "验证中");
        
        try {
            // 验证交易是否真实存在且成功
            boolean isValid = polygonContentProofService.verifyTransaction(txHash, shareId, share.getFileHash());
            
            if (!isValid) {
                updatePolygonStatus(shareId, 3, txHash, null, null, "交易验证失败");
                throw new com.wereen.competitionplatform.exception.BusinessException("Polygon 交易验证失败");
            }
            
            // 获取交易详情
            PolygonContentProofService.PolygonProofResult result = 
                polygonContentProofService.getTransactionDetails(txHash);
            
            // 记录到 chain_proof 表
            ChainProof chainProof = new ChainProof();
            chainProof.setBizType("CONTENT_SHARE");
            chainProof.setBizId(shareId);
            chainProof.setDataHash(share.getFileHash());
            chainProof.setTxHash(txHash);
            chainProof.setBlockHeight(result.getBlockNumber());
            chainProof.setBlockTime(result.getBlockTime());
            chainProof.setMetadata(buildMetadata(share, result.getPublisher()));
            chainProof.setStatus(2);
            chainProof.setChainNetwork("POLYGON");
            chainProofMapper.insert(chainProof);
            
            updatePolygonStatus(shareId, 2, txHash, result.getBlockNumber(), result.getBlockTime(), null);
            
            log.info("✅ Polygon 交易验证成功，准备发放奖励: shareId={}, txHash={}", shareId, txHash);
            
            // 发放 WEE 奖励
            ContentRewardEvent event = new ContentRewardEvent(this, publisherId, shareId, mediaType);
            eventPublisher.publishEvent(event);
            log.info("📢 已发布内容奖励事件: shareId={}", shareId);
            
        } catch (Exception e) {
            log.error("Polygon 交易验证失败: shareId={}, txHash={}", shareId, txHash, e);
            updatePolygonStatus(shareId, 3, txHash, null, null, e.getMessage());
            throw e;
        }
    }

    private void pushToFisco(Long shareId) {
        ContentShare share = getShare(shareId);
        updateFiscoStatus(shareId, 1, null, null, null, null);
        try {
            // 对于FISCO，使用默认地址或从用户信息获取
            String walletAddress = "0x752d5728bc74270032dcc8d5b4b5748b6d4b0dff"; // 使用配置中的账户
            String metadata = buildMetadata(share, walletAddress);
            int maxAttempts = 12;
            long backoffMs = 10_000L;
            TransactionReceipt receipt = null;
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                try {
                    receipt = blockchainService.saveEvidence(
                        "CONTENT_SHARE",
                        String.valueOf(shareId),
                        share.getFileHash()
                    );
                    break;
                } catch (Exception e) {
                    if (attempt < maxAttempts && isRecoverableFiscoError(e)) {
                        log.warn("FISCO 节点未就绪，稍后重试: shareId={}, attempt={}/{}", shareId, attempt, maxAttempts);
                        updateFiscoStatus(shareId, 1, null, null, null, "FISCO节点启动中，重试中(" + attempt + "/" + maxAttempts + ")");
                        try {
                            Thread.sleep(backoffMs);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                        continue;
                    }
                    throw e;
                }
            }

            if (receipt == null) {
                // 可能是 FISCO client 未注入（或未启用）而被跳过：不标记失败，保持待上链
                updateFiscoStatus(shareId, 0, null, null, null, "FISCO不可用或节点未就绪，待重试");
                return;
            }
            if (!"0x0".equals(receipt.getStatus())) {
                Long blockHeight = parseBlockNumber(receipt.getBlockNumber());
                LocalDateTime blockTime = LocalDateTime.now();
                updateFiscoStatus(shareId, 3, receipt.getTransactionHash(), blockHeight, blockTime,
                    "交易执行失败: " + receipt.getStatus());
                return;
            }

            Long blockHeight = parseBlockNumber(receipt.getBlockNumber());
            LocalDateTime blockTime = LocalDateTime.now();

            ChainProof chainProof = new ChainProof();
            chainProof.setBizType("CONTENT_SHARE");
            chainProof.setBizId(shareId);
            chainProof.setDataHash(share.getFileHash());
            chainProof.setTxHash(receipt.getTransactionHash());
            chainProof.setBlockHeight(blockHeight);
            chainProof.setBlockTime(blockTime);
            chainProof.setMetadata(metadata);
            chainProof.setStatus(2);
            chainProof.setChainNetwork("FISCO");
            chainProofMapper.insert(chainProof);

            updateFiscoStatus(
                shareId,
                2,
                receipt.getTransactionHash(),
                blockHeight,
                blockTime,
                null
            );
        } catch (Exception e) {
            updateFiscoStatus(shareId, 3, null, null, null, e.getMessage());
            throw e;
        }
    }

    private boolean isRecoverableFiscoError(Exception e) {
        String msg = e.getMessage();
        if (msg == null) {
            return false;
        }
        String m = msg.toLowerCase();
        return m.contains("connection refused")
            || m.contains("connect")
            || m.contains("timeout")
            || m.contains("timed out")
            || m.contains("no route")
            || m.contains("unavailable")
            || m.contains("socket");
    }

    private void pushToPolygon(Long shareId) {
        ContentShare share = getShare(shareId);
        updatePolygonStatus(shareId, 1, null, null, null, null);
        try {
            String publisher = resolvePublisherAddress(share.getUserId());
            String metadata = buildMetadata(share, publisher);
            PolygonContentProofService.PolygonProofResult result =
                polygonContentProofService.recordContentShare(shareId, share.getFileHash(), metadata, publisher);

            ChainProof chainProof = new ChainProof();
            chainProof.setBizType("CONTENT_SHARE");
            chainProof.setBizId(shareId);
            chainProof.setDataHash(share.getFileHash());
            chainProof.setTxHash(result.getTxHash());
            chainProof.setBlockHeight(result.getBlockNumber());
            chainProof.setBlockTime(result.getBlockTime());
            chainProof.setMetadata(result.getPayload());
            chainProof.setStatus(2);
            chainProof.setChainNetwork("POLYGON");
            chainProofMapper.insert(chainProof);

            updatePolygonStatus(
                shareId,
                2,
                result.getTxHash(),
                result.getBlockNumber(),
                result.getBlockTime(),
                null
            );
        } catch (Exception e) {
            updatePolygonStatus(shareId, 3, null, null, null, e.getMessage());
            throw e;
        }
    }

    private ContentShare getShare(Long shareId) {
        ContentShare share = contentShareMapper.selectById(shareId);
        if (share == null) {
            throw new IllegalStateException("内容分享不存在: " + shareId);
        }
        return share;
    }

    private String buildMetadata(ContentShare share, String walletAddress) {
        try {
            // 规范化钱包地址：必须是 42 个字符 (0x + 40位十六进制)
            String normalizedAddress = normalizeEthereumAddress(walletAddress);
            
            Map<String, Object> meta = new HashMap<>();
            meta.put("title", share.getTitle());
            meta.put("userId", share.getUserId());
            meta.put("walletAddress", normalizedAddress);
            meta.put("mediaType", share.getMediaType());
            meta.put("mediaUrl", share.getMediaUrl());
            meta.put("hashAlgorithm", share.getHashAlgorithm());
            meta.put("fileHash", share.getFileHash());
            meta.put("createdAt", share.getCreatedAt() != null ? share.getCreatedAt().toString() : null);
            meta.put("extra", share.getMetadata());
            return objectMapper.writeValueAsString(meta);
        } catch (JsonProcessingException e) {
            log.warn("内容分享元数据序列化失败: id={}", share.getId(), e);
            return "{}";
        }
    }

    private String resolvePublisherAddress(Long userId) {
        try {
            if (userId == null) {
                return null;
            }
            User user = userService.getUserById(userId);
            if (user != null && user.getWalletAddress() != null) {
                return normalizeEthereumAddress(user.getWalletAddress());
            }
        } catch (Exception ignore) {
        }
        return null;
    }
    
    /**
     * 规范化以太坊地址格式
     * - 必须是 42 个字符 (0x + 40位十六进制)
     * - 强制小写（EIP-55 checksum 在合约层不强制）
     */
    private String normalizeEthereumAddress(String address) {
        if (address == null || address.isEmpty()) {
            log.warn("⚠️ 钱包地址为空");
            return null;
        }
        
        // 移除所有空白字符
        address = address.trim().replaceAll("\\s+", "");
        
        // 必须以 0x 开头
        if (!address.startsWith("0x") && !address.startsWith("0X")) {
            log.error("❌ 非法地址格式（缺少 0x 前缀）: {}", address);
            throw new IllegalArgumentException("钱包地址必须以 0x 开头: " + address);
        }
        
        // 统一转小写
        address = address.toLowerCase();
        
        // 检查长度（必须是 42 个字符）
        if (address.length() != 42) {
            log.error("❌ 非法地址长度（应为 42 个字符）: length={}, address={}", address.length(), address);
            throw new IllegalArgumentException(
                String.format("钱包地址长度错误（当前%d个字符，应为42个）: %s", address.length(), address)
            );
        }
        
        // 检查是否都是十六进制字符
        String hexPart = address.substring(2);
        if (!hexPart.matches("[0-9a-f]{40}")) {
            log.error("❌ 非法地址字符（必须为十六进制）: {}", address);
            throw new IllegalArgumentException("钱包地址包含非十六进制字符: " + address);
        }
        
        log.info("✅ 地址规范化成功: {}", address);
        return address;
    }

    private void updateFiscoStatus(Long shareId, Integer status, String txHash,
                                   Long blockHeight, LocalDateTime blockTime, String error) {
        ContentShare share = new ContentShare();
        share.setId(shareId);
        share.setFiscoStatus(status);
        share.setFiscoTxHash(txHash);
        share.setFiscoBlockHeight(blockHeight);
        share.setFiscoBlockTime(blockTime);
        share.setFiscoError(error);
        contentShareMapper.updateById(share);
    }

    private void updatePolygonStatus(Long shareId, Integer status, String txHash,
                                     Long blockNumber, LocalDateTime blockTime, String error) {
        ContentShare share = new ContentShare();
        share.setId(shareId);
        share.setPolygonStatus(status);
        share.setPolygonTxHash(txHash);
        share.setPolygonBlockNumber(blockNumber);
        share.setPolygonBlockTime(blockTime);
        share.setPolygonError(error);
        contentShareMapper.updateById(share);
    }

    private Long parseBlockNumber(String blockNumber) {
        if (blockNumber == null) {
            return null;
        }
        try {
            String value = blockNumber.trim();
            if (value.startsWith("0x")) {
                return new java.math.BigInteger(value.substring(2), 16).longValue();
            }
            return new java.math.BigInteger(value).longValue();
        } catch (Exception e) {
            log.warn("解析区块高度失败: {}", blockNumber, e);
            return null;
        }
    }
}
