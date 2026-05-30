package com.wereen.competitionplatform.service;

import com.wereen.competitionplatform.conig.FiscoProperties;
import com.wereen.competitionplatform.contracts.EvidenceContract;
import com.wereen.competitionplatform.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fisco.bcos.sdk.abi.datatypes.generated.tuples.generated.Tuple6;
import org.fisco.bcos.sdk.client.Client;
import org.fisco.bcos.sdk.crypto.keypair.CryptoKeyPair;
import org.fisco.bcos.sdk.model.TransactionReceipt;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.util.List;

/**
 * 区块链服务 - FISCO BCOS
 */
@Slf4j
@Service
public class BlockchainService {

    private final Client fiscoBcosClient;
    private final CryptoKeyPair cryptoKeyPair;
    private final FiscoProperties fiscoProperties;
    
    // 使用构造函数注入，允许 fiscoBcosClient 和 cryptoKeyPair 为 null
    public BlockchainService(
            @org.springframework.beans.factory.annotation.Autowired(required = false) Client fiscoBcosClient,
            @org.springframework.beans.factory.annotation.Autowired(required = false) CryptoKeyPair cryptoKeyPair,
            FiscoProperties fiscoProperties
    ) {
        this.fiscoBcosClient = fiscoBcosClient;
        this.cryptoKeyPair = cryptoKeyPair;
        this.fiscoProperties = fiscoProperties;
        if (fiscoBcosClient == null) {
            log.warn("BlockchainService initialized without FISCO client - FISCO features will be unavailable");
        }
    }

    /**
     * 保存证据到区块链
     *
     * @param bizType 业务类型
     * @param bizId 业务ID
     * @param dataHash 数据哈希
     * @return 交易回执
     */
    public TransactionReceipt saveEvidence(String bizType, String bizId, String dataHash) {
        // 检查 FISCO 是否可用
        if (fiscoBcosClient == null || cryptoKeyPair == null) {
            log.warn("FISCO BCOS 不可用，跳过上链存证: bizType={}, bizId={}, hash={}", bizType, bizId, dataHash);
            return null;
        }
        
        try {
            log.info("开始上链存证: bizType={}, bizId={}, hash={}", bizType, bizId, dataHash);

            // 参数校验
            if (dataHash == null || dataHash.trim().isEmpty()) {
                throw new IllegalArgumentException("数据哈希不能为空");
            }
            if (bizType == null) {
                bizType = "UNKNOWN";
            }
            if (bizId == null) {
                bizId = "0";
            }

            // 加载合约
            String contractAddress = fiscoProperties.getContractAddress();
            log.info("加载合约: contractAddress={}", contractAddress);

            EvidenceContract contract = EvidenceContract.load(
                contractAddress,
                fiscoBcosClient,
                cryptoKeyPair
            );

            // 调用合约添加存证
            TransactionReceipt receipt = contract.addEvidence(
                dataHash,
                bizType,
                bizId,
                "SYSTEM",  // uploader - 上传者标识
                String.format("BizType: %s, BizId: %s", bizType, bizId)
            );

            log.info("区块链交易成功: transactionHash={}, blockNumber={}",
                    receipt.getTransactionHash(), receipt.getBlockNumber());

            return receipt;

        } catch (Exception e) {
            log.error("上链存证失败: bizType={}, bizId={}", bizType, bizId, e);
            throw new BusinessException("上链存证失败: " + e.getMessage());
        }
    }

    /**
     * 根据哈希查询存证详细信息
     *
     * @param dataHash 数据哈希
     * @return JSON格式的存证信息
     */
    public String getEvidenceByHash(String dataHash) {
        try {
            log.info("查询存证详细信息: {}", dataHash);

            EvidenceContract contract = EvidenceContract.load(
                fiscoProperties.getContractAddress(),
                fiscoBcosClient,
                cryptoKeyPair
            );

            // 调用合约查询方法
            Tuple6<String, String, String, String, String, BigInteger> result = contract.getEvidenceByHash(dataHash);

            if (result == null) {
                return null;
            }

            // 解析返回结果（顺序：dataHash, bizType, bizId, uploader, metadata, timestamp）
            String returnedHash = result.getValue1();
            String bizType = result.getValue2();
            String bizId = result.getValue3();
            String uploader = result.getValue4();
            String metadata = result.getValue5();
            BigInteger timestamp = result.getValue6();

            // 构造JSON结果
            String jsonResult = String.format(
                "{\"dataHash\":\"%s\",\"bizType\":\"%s\",\"bizId\":\"%s\",\"uploader\":\"%s\",\"metadata\":\"%s\",\"timestamp\":%s}",
                returnedHash, bizType, bizId, uploader, metadata, timestamp.toString()
            );

            log.info("查询存证成功: dataHash={}, bizType={}", dataHash, bizType);
            return jsonResult;

        } catch (Exception e) {
            log.error("查询存证失败: dataHash={}", dataHash, e);
            throw new BusinessException("查询存证失败: " + e.getMessage());
        }
    }

    /**
     * 验证存证是否存在
     */
    public boolean verifyEvidence(String dataHash) {
        try {
            log.info("验证存证: {}", dataHash);

            EvidenceContract contract = EvidenceContract.load(
                fiscoProperties.getContractAddress(),
                fiscoBcosClient,
                cryptoKeyPair
            );

            Boolean result = contract.verifyEvidence(dataHash);
            log.info("验证存证结果: dataHash={}, exists={}", dataHash, result);
            return result != null ? result : false;

        } catch (Exception e) {
            log.error("验证存证失败: dataHash={}", dataHash, e);
            return false;
        }
    }

    /**
     * 冻结榜单快照到区块链
     *
     * @param competitionId 竞赛ID
     * @param merkleRoot Merkle Root
     * @param uri 数据URI
     * @return 交易回执
     */
    public TransactionReceipt freezeLeaderboard(String competitionId, String merkleRoot, String uri) {
        try {
            log.info("冻结榜单上链: competitionId={}, merkleRoot={}", competitionId, merkleRoot);

            // 使用 saveEvidence 方法保存榜单冻结信息
            return saveEvidence("LEADERBOARD", competitionId, merkleRoot);

        } catch (Exception e) {
            log.error("榜单冻结上链失败: competitionId={}", competitionId, e);
            throw new BusinessException("榜单冻结上链失败: " + e.getMessage());
        }
    }

    /**
     * 记录奖金发放批次到区块链
     *
     * @param batchNo 批次号
     * @param winnersRoot 获奖者Merkle Root
     * @param amount 总金额
     * @param currency 币种
     * @param uri 数据URI
     * @return 交易回执
     */
    public TransactionReceipt recordPrizeBatch(String batchNo, String winnersRoot, Long amount, String currency, String uri) {
        try {
            log.info("奖金批次上链: batchNo={}, winnersRoot={}, amount={}", batchNo, winnersRoot, amount);

            // 使用 saveEvidence 方法保存奖金批次信息
            return saveEvidence("PRIZE_BATCH", batchNo, winnersRoot);

        } catch (Exception e) {
            log.error("奖金批次上链失败: batchNo={}", batchNo, e);
            throw new BusinessException("奖金批次上链失败: " + e.getMessage());
        }
    }

    /**
     * 获取区块高度
     */
    public Long getBlockNumber() {
        try {
            return fiscoBcosClient.getBlockNumber().getBlockNumber().longValue();
        } catch (Exception e) {
            log.error("获取区块高度失败", e);
            throw new BusinessException("获取区块高度失败");
        }
    }

    /**
     * 根据交易哈希获取交易回执
     */
    public TransactionReceipt getTransactionReceipt(String txHash) {
        try {
            return fiscoBcosClient.getTransactionReceipt(txHash).getTransactionReceipt().orElse(null);
        } catch (Exception e) {
            log.error("获取交易回执失败: txHash={}", txHash, e);
            throw new BusinessException("获取交易回执失败");
        }
    }

    /**
     * 查询用户的所有存证
     *
     * @param uploader 上传者标识（用户ID）
     * @return 存证哈希列表
     */
    public List<String> getEvidencesByUploader(String uploader) {
        try {
            log.info("查询用户存证: uploader={}", uploader);

            EvidenceContract contract = EvidenceContract.load(
                fiscoProperties.getContractAddress(),
                fiscoBcosClient,
                cryptoKeyPair
            );

            // 调用合约查询方法
            List<String> hashes = contract.getEvidencesByUploader(uploader);

            log.info("查询用户存证成功: uploader={}, count={}", uploader, hashes != null ? hashes.size() : 0);
            return hashes;

        } catch (Exception e) {
            log.error("查询用户存证失败: uploader={}", uploader, e);
            throw new BusinessException("查询用户存证失败: " + e.getMessage());
        }
    }

    /**
     * 根据业务类型查询存证列表
     *
     * @param bizType 业务类型（SUBMISSION/LEADERBOARD/PRIZE_BATCH等）
     * @return 存证哈希列表
     */
    public List<String> getEvidencesByBizType(String bizType) {
        try {
            log.info("查询业务类型存证: bizType={}", bizType);

            EvidenceContract contract = EvidenceContract.load(
                fiscoProperties.getContractAddress(),
                fiscoBcosClient,
                cryptoKeyPair
            );

            // 调用合约查询方法
            List<String> hashes = contract.getEvidencesByBizType(bizType);

            log.info("查询业务类型存证成功: bizType={}, count={}", bizType, hashes != null ? hashes.size() : 0);
            return hashes;

        } catch (Exception e) {
            log.error("查询业务类型存证失败: bizType={}", bizType, e);
            throw new BusinessException("查询业务类型存证失败: " + e.getMessage());
        }
    }

    /**
     * 获取存证总数
     *
     * @return 存证总数
     */
    public Long getEvidenceCount() {
        try {
            log.info("查询存证总数");

            EvidenceContract contract = EvidenceContract.load(
                fiscoProperties.getContractAddress(),
                fiscoBcosClient,
                cryptoKeyPair
            );

            // 调用合约查询方法
            java.math.BigInteger count = contract.getEvidenceCount();

            log.info("存证总数: {}", count);
            return count.longValue();

        } catch (Exception e) {
            log.error("查询存证总数失败", e);
            throw new BusinessException("查询存证总数失败: " + e.getMessage());
        }
    }

    /**
     * 分页查询存证列表
     *
     * @param offset 偏移量
     * @param limit 每页数量
     * @return 存证哈希列表
     */
    public List<String> getEvidenceList(Long offset, Long limit) {
        try {
            log.info("分页查询存证: offset={}, limit={}", offset, limit);

            EvidenceContract contract = EvidenceContract.load(
                fiscoProperties.getContractAddress(),
                fiscoBcosClient,
                cryptoKeyPair
            );

            // 调用合约查询方法
            List<String> hashes = contract.getEvidenceList(
                java.math.BigInteger.valueOf(offset),
                java.math.BigInteger.valueOf(limit)
            );

            log.info("分页查询存证成功: count={}", hashes != null ? hashes.size() : 0);
            return hashes;

        } catch (Exception e) {
            log.error("分页查询存证失败: offset={}, limit={}", offset, limit, e);
            throw new BusinessException("分页查询存证失败: " + e.getMessage());
        }
    }

    /**
     * 批量验证存证
     *
     * @param dataHashes 数据哈希列表
     * @return 验证结果列表（与输入顺序对应）
     */
    public List<Boolean> batchVerifyEvidence(List<String> dataHashes) {
        try {
            log.info("批量验证存证: count={}", dataHashes.size());

            EvidenceContract contract = EvidenceContract.load(
                fiscoProperties.getContractAddress(),
                fiscoBcosClient,
                cryptoKeyPair
            );

            // 调用合约查询方法
            List<Boolean> results = contract.batchVerifyEvidence(dataHashes);

            log.info("批量验证存证成功: total={}, exists={}",
                results.size(), results.stream().filter(b -> b).count());
            return results;

        } catch (Exception e) {
            log.error("批量验证存证失败", e);
            throw new BusinessException("批量验证存证失败: " + e.getMessage());
        }
    }

    /**
     * 更新存证元数据
     *
     * @param dataHash 数据哈希
     * @param metadata 新的元数据（JSON格式）
     * @return 交易回执
     */
    public TransactionReceipt updateEvidenceMetadata(String dataHash, String metadata) {
        try {
            log.info("更新存证元数据: dataHash={}", dataHash);

            EvidenceContract contract = EvidenceContract.load(
                fiscoProperties.getContractAddress(),
                fiscoBcosClient,
                cryptoKeyPair
            );

            // 调用合约更新方法
            TransactionReceipt receipt = contract.updateEvidenceMetadata(dataHash, metadata);

            log.info("更新存证元数据成功: dataHash={}, txHash={}",
                dataHash, receipt.getTransactionHash());
            return receipt;

        } catch (Exception e) {
            log.error("更新存证元数据失败: dataHash={}", dataHash, e);
            throw new BusinessException("更新存证元数据失败: " + e.getMessage());
        }
    }
}
