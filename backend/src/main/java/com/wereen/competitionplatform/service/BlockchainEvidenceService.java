package com.wereen.competitionplatform.service;

import com.wereen.competitionplatform.mapper.ChainProofMapper;
import com.wereen.competitionplatform.mapper.SubmissionMapper;
import com.wereen.competitionplatform.model.entity.ChainProof;
import com.wereen.competitionplatform.model.entity.Submission;
import com.wereen.competitionplatform.util.SM3HashUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fisco.bcos.sdk.model.TransactionReceipt;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 区块链存证异步处理服务
 * 将异步方法独立出来，避免Spring AOP代理问题
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BlockchainEvidenceService {

    private final BlockchainService blockchainService;
    private final SubmissionMapper submissionMapper;
    private final ChainProofMapper chainProofMapper;

    /**
     * 异步上链处理
     */
    @Async
    public void uploadSubmissionToBlockchainAsync(Long submissionId) {
        try {
            log.info("开始异步上链处理: submissionId={}", submissionId);

            // 更新状态为上链中
            updateChainStatus(submissionId, 1, null, null, null);

            Submission submission = getSubmissionById(submissionId);

            // 确保文件哈希已计算
            if (submission.getFileHash() == null || submission.getFileHash().isEmpty()) {
                log.warn("文件哈希未计算，跳过上链: submissionId={}", submissionId);
                updateChainStatus(submissionId, 3, null, null, null);
                return;
            }

            // 计算国密 SM3 哈希（对 SHA256 哈希值再做 SM3，保留双重哈希链）
            String sm3Hash = SM3HashUtil.hashHex(submission.getFileHash());

            // 构造元数据（JSON格式，含国密 SM3 哈希）
            String metadata = String.format(
                "{\"submissionId\":%d,\"userId\":%d,\"competitionId\":%d," +
                "\"hashAlgorithm\":\"%s\",\"fileHash\":\"%s\",\"sm3Hash\":\"%s\",\"timestamp\":\"%s\"}",
                submission.getId(),
                submission.getUserId(),
                submission.getCompetitionId(),
                submission.getHashAlgorithm(),
                submission.getFileHash(),
                sm3Hash,
                LocalDateTime.now().toString()
            );

            // 调用真实区块链服务上链
            TransactionReceipt receipt = blockchainService.saveEvidence(
                "SUBMISSION",
                String.valueOf(submission.getId()),
                submission.getFileHash()
            );

            // 检查交易回执是否有效
            if (receipt == null) {
                log.error("上链失败: 交易回执为null, submissionId={}", submissionId);
                updateChainStatus(submissionId, 3, null, null, null);
                return;
            }

            // 检查交易状态
            if (!"0x0".equals(receipt.getStatus())) {
                log.error("上链失败: 交易执行失败, submissionId={}, status={}, message={}",
                    submissionId, receipt.getStatus(), receipt.getMessage());
                updateChainStatus(submissionId, 3, null, null, null);
                return;
            }

            // 解析交易回执
            String txHash = receipt.getTransactionHash();
            Long blockHeight = parseBlockNumber(receipt.getBlockNumber());
            LocalDateTime blockTime = LocalDateTime.now();

            // 保存链上存证记录
            ChainProof chainProof = new ChainProof();
            chainProof.setBizType("SUBMISSION");
            chainProof.setBizId(submission.getId());
            chainProof.setDataHash(submission.getFileHash());
            chainProof.setTxHash(txHash);
            chainProof.setBlockHeight(blockHeight);
            chainProof.setBlockTime(blockTime);
            chainProof.setMetadata(metadata);
            chainProof.setStatus(2); // 已上链
            chainProof.setChainNetwork("FISCO");
            chainProofMapper.insert(chainProof);

            // 更新提交记录的链上状态
            updateChainStatus(
                submission.getId(),
                2, // 已上链
                txHash,
                blockHeight,
                blockTime
            );

            log.info("提交记录上链成功: submissionId={}, txHash={}, blockHeight={}",
                submissionId, txHash, blockHeight);

        } catch (Exception e) {
            log.error("提交记录上链失败: submissionId={}", submissionId, e);
            // 更新为上链失败状态
            try {
                updateChainStatus(submissionId, 3, null, null, null);
            } catch (Exception ex) {
                log.error("更新上链失败状态异常", ex);
            }
        }
    }

    /**
     * 根据ID查询提交记录
     */
    private Submission getSubmissionById(Long id) {
        Submission submission = submissionMapper.selectById(id);
        if (submission == null) {
            throw new RuntimeException("提交记录不存在: id=" + id);
        }
        return submission;
    }

    /**
     * 更新链上状态
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateChainStatus(Long submissionId, Integer chainStatus, String txHash, Long blockHeight, LocalDateTime blockTime) {
        Submission submission = getSubmissionById(submissionId);
        submission.setChainStatus(chainStatus);
        submission.setChainTxHash(txHash);
        submission.setBlockHeight(blockHeight);
        submission.setBlockTime(blockTime);

        submissionMapper.updateById(submission);
        log.info("更新提交链上状态: id={}, chainStatus={}, txHash={}", submissionId, chainStatus, txHash);
    }

    /**
     * 解析链上返回的区块高度（兼容0x十六进制格式）
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
