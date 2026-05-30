package com.wereen.competitionplatform.service;

import com.wereen.competitionplatform.conig.BlockchainProperties;
import com.wereen.competitionplatform.exception.BusinessException;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.DynamicBytes;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Hash;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.Sign;
import org.web3j.crypto.SignedRawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.crypto.Keys;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthBlockNumber;
import org.web3j.protocol.core.methods.response.EthGasPrice;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.gas.DefaultGasProvider;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * Polygon 上链存证服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PolygonContentProofService {

    private static final BigInteger DEFAULT_GAS_LIMIT = BigInteger.valueOf(600_000L);

    private final BlockchainProperties blockchainProperties;
    private volatile Web3j web3j;
    private volatile Credentials adminCredentials;

    private void initIfNecessary() {
        if (web3j == null) {
            synchronized (this) {
                if (web3j == null) {
                    String rpcUrl = blockchainProperties.getPolygon().getRpcUrl();
                    log.info("🔧 初始化 Polygon Web3j: rpcUrl={}", rpcUrl);
                    if (!StringUtils.hasText(rpcUrl)) {
                        throw new BusinessException("Polygon RPC 未配置");
                    }
                    web3j = Web3j.build(new HttpService(rpcUrl));
                    log.info("✅ Polygon Web3j 初始化成功");
                }
            }
        }
        if (adminCredentials == null) {
            synchronized (this) {
                if (adminCredentials == null) {
                    String privateKey = blockchainProperties.getAdmin().getPrivateKey();
                    if (!StringUtils.hasText(privateKey)) {
                        throw new BusinessException("Polygon Admin私钥未配置");
                    }
                    adminCredentials = Credentials.create(privateKey);
                    log.info("✅ Polygon Admin 凭证初始化成功: address={}", adminCredentials.getAddress());
                }
            }
        }
    }

    /**
     * 生成前端签名所需的 EIP-712 数据结构
     */
    public Map<String, Object> generateEIP712Data(Long shareId, String dataHash, String metadata, String publisherAddress) {
        initIfNecessary();
        
        String contractAddress = blockchainProperties.getContentShare().getRegistryAddress();
        long chainId = blockchainProperties.getPolygon().getChainId() != null
            ? blockchainProperties.getPolygon().getChainId()
            : 137L;
        
        // EIP-712 Domain
        Map<String, Object> domain = new HashMap<>();
        domain.put("name", "ContentShareRegistry");
        domain.put("version", "1");
        domain.put("chainId", chainId);
        domain.put("verifyingContract", contractAddress);
        
        // EIP-712 Types
        Map<String, Object> types = new HashMap<>();
        types.put("EIP712Domain", Arrays.asList(
            Map.of("name", "name", "type", "string"),
            Map.of("name", "version", "type", "string"),
            Map.of("name", "chainId", "type", "uint256"),
            Map.of("name", "verifyingContract", "type", "address")
        ));
        types.put("ContentShare", Arrays.asList(
            Map.of("name", "dataHash", "type", "bytes32"),
            Map.of("name", "publisher", "type", "address"),
            Map.of("name", "shareId", "type", "uint256"),
            Map.of("name", "metadata", "type", "string")
        ));
        
        // Primary Type
        String primaryType = "ContentShare";
        
        // Message
        Map<String, Object> message = new HashMap<>();
        message.put("dataHash", dataHash.startsWith("0x") ? dataHash : "0x" + dataHash);
        message.put("publisher", publisherAddress);
        message.put("shareId", shareId.toString());
        message.put("metadata", metadata);
        
        Map<String, Object> result = new HashMap<>();
        result.put("domain", domain);
        result.put("types", types);
        result.put("primaryType", primaryType);
        result.put("message", message);
        result.put("contractAddress", contractAddress);
        
        log.info("生成 EIP-712 签名数据: shareId={}, publisher={}", shareId, publisherAddress);
        return result;
    }
    
    /**
     * 验证前端提交的 Polygon 交易
     */
    public boolean verifyTransaction(String txHash, Long expectedShareId, String expectedDataHash) {
        initIfNecessary();

        log.info("🔍 开始验证 Polygon 交易: txHash={}, expectedShareId={}, rpcUrl={}",
            txHash, expectedShareId, blockchainProperties.getPolygon().getRpcUrl());

        try {
            // 1. 获取交易回执
            EthGetTransactionReceipt receiptResp = web3j.ethGetTransactionReceipt(txHash).send();

            log.info("📡 RPC 响应: hasError={}, resultPresent={}",
                receiptResp.hasError(), receiptResp.getTransactionReceipt().isPresent());

            if (receiptResp.hasError()) {
                log.error("❌ RPC 返回错误: code={}, message={}",
                    receiptResp.getError().getCode(), receiptResp.getError().getMessage());
                return false;
            }

            if (!receiptResp.getTransactionReceipt().isPresent()) {
                log.error("❌ 交易回执不存在: txHash={}", txHash);
                log.error("提示: 请检查交易是否已确认，或尝试在区块浏览器查询: https://polygonscan.com/tx/{}", txHash);
                return false;
            }
            
            TransactionReceipt receipt = receiptResp.getTransactionReceipt().get();
            
            // 2. 检查交易状态
            if (!"0x1".equalsIgnoreCase(receipt.getStatus())) {
                log.error("❌ 交易执行失败: txHash={}, status={}", txHash, receipt.getStatus());
                return false;
            }
            
            // 3. 检查合约地址
            String contractAddress = blockchainProperties.getContentShare().getRegistryAddress();
            if (!receipt.getTo().equalsIgnoreCase(contractAddress)) {
                log.error("❌ 交易目标合约不匹配: txHash={}, expected={}, actual={}", 
                    txHash, contractAddress, receipt.getTo());
                return false;
            }
            
            // 4. 解析事件日志，验证 shareId 和 dataHash
            log.info("🔍 开始解析交易日志: txHash={}, 日志数量={}", txHash, receipt.getLogs().size());
            
            for (org.web3j.protocol.core.methods.response.Log eventLog : receipt.getLogs()) {
                if (eventLog.getTopics().isEmpty()) continue;
                
                String actualTopic = eventLog.getTopics().get(0);
                log.info("📋 日志 Topic[0]: {}, 地址: {}", actualTopic, eventLog.getAddress());
                
                // ShareRecorded 事件的签名（注意：metadata 是 string 类型，timestamp 是 uint256）
                String eventSignature = "ShareRecorded(bytes32,address,uint256,string,uint256)";
                String expectedTopic = "0x" + Numeric.toHexStringNoPrefix(Hash.sha3(eventSignature.getBytes()));
                log.info("🎯 期望的 Topic (ShareRecorded): {}", expectedTopic);
                
                if (eventLog.getTopics().get(0).equals(expectedTopic)) {
                    // ShareRecorded 事件结构：
                    // topic[0] = 事件签名哈希
                    // topic[1] = dataHash (indexed)
                    // topic[2] = publisher (indexed)
                    // topic[3] = shareId (indexed)
                    // data = metadata (string) + timestamp (uint256)
                    
                    if (eventLog.getTopics().size() < 4) {
                        log.error("❌ 事件 topics 数量不足: 期望4个，实际{}个", eventLog.getTopics().size());
                        continue;
                    }
                    
                    String logDataHash = eventLog.getTopics().get(1);
                    String logPublisher = eventLog.getTopics().get(2);
                    String logShareIdHex = eventLog.getTopics().get(3);
                    
                    // 验证 dataHash
                    String normalizedExpectedHash = expectedDataHash.startsWith("0x") 
                        ? expectedDataHash 
                        : "0x" + expectedDataHash;
                    
                    if (!logDataHash.equalsIgnoreCase(normalizedExpectedHash)) {
                        log.error("❌ 交易中的 dataHash 不匹配: expected={}, actual={}", 
                            normalizedExpectedHash, logDataHash);
                        return false;
                    }
                    
                    // 验证 shareId（从 topic[3] 解析）
                    BigInteger logShareId = Numeric.toBigInt(logShareIdHex);
                    if (!logShareId.equals(BigInteger.valueOf(expectedShareId))) {
                        log.error("❌ 交易中的 shareId 不匹配: expected={}, actual={}", 
                            expectedShareId, logShareId);
                        return false;
                    }
                    
                    log.info("✅ 交易验证成功: txHash={}, shareId={}, dataHash={}, publisher={}", 
                        txHash, expectedShareId, logDataHash, logPublisher);
                    return true;
                }
            }
            
            log.error("❌ 交易中未找到 ShareRecorded 事件: txHash={}", txHash);
            return false;
            
        } catch (Exception e) {
            log.error("❌ 交易验证异常: txHash={}", txHash, e);
            return false;
        }
    }
    
    /**
     * 获取交易详情
     */
    public PolygonProofResult getTransactionDetails(String txHash) {
        initIfNecessary();
        
        try {
            EthGetTransactionReceipt receiptResp = web3j.ethGetTransactionReceipt(txHash).send();
            if (!receiptResp.getTransactionReceipt().isPresent()) {
                throw new BusinessException("交易不存在");
            }
            
            TransactionReceipt receipt = receiptResp.getTransactionReceipt().get();
            
            // 从事件日志中提取 publisher
            String publisher = null;
            for (org.web3j.protocol.core.methods.response.Log eventLog : receipt.getLogs()) {
                if (eventLog.getTopics().size() >= 3) {
                    // topic[2] = publisher (indexed address)
                    String publisherTopic = eventLog.getTopics().get(2);
                    publisher = "0x" + publisherTopic.substring(26); // 去掉前面的 padding
                    break;
                }
            }
            
            return PolygonProofResult.builder()
                .txHash(txHash)
                .blockNumber(receipt.getBlockNumber().longValue())
                .blockTime(LocalDateTime.now()) // 可以通过 eth_getBlockByNumber 获取精确时间
                .publisher(publisher)
                .payload(null)
                .build();
            
        } catch (Exception e) {
            log.error("获取交易详情失败: txHash={}", txHash, e);
            throw new BusinessException("获取交易详情失败: " + e.getMessage());
        }
    }
    
    /**
     * 将内容分享记录存证到Polygon（已废弃，改为前端用户自己签名）
     * @deprecated 现在由前端用户自己签名和发送交易
     */
    @Deprecated
    public PolygonProofResult recordContentShare(Long shareId, String dataHash, String metadataJson, String publisherAddress) {
        initIfNecessary();
        try {
            BigInteger nonce = web3j.ethGetTransactionCount(
                adminCredentials.getAddress(),
                DefaultBlockParameterName.PENDING
            ).send().getTransactionCount();

            EthGasPrice gasPriceResponse = web3j.ethGasPrice().send();
            BigInteger gasPrice = gasPriceResponse.getGasPrice();
            if (gasPrice == null || gasPrice.equals(BigInteger.ZERO)) {
                gasPrice = DefaultGasProvider.GAS_PRICE;
            }
            // 增加20%的gas price以避免"replacement transaction underpriced"错误
            gasPrice = gasPrice.multiply(BigInteger.valueOf(120)).divide(BigInteger.valueOf(100));
            log.info("使用的Gas Price: {} Gwei", gasPrice.divide(BigInteger.valueOf(1_000_000_000L)));

            String contractAddress = blockchainProperties.getContentShare().getRegistryAddress();
            if (!StringUtils.hasText(contractAddress)) {
                throw new BusinessException("Polygon content-share 合约地址未配置");
            }

            // 处理可能包含引号的地址
            if (contractAddress.startsWith("\"") && contractAddress.endsWith("\"")) {
                contractAddress = contractAddress.substring(1, contractAddress.length() - 1);
                log.info("去除引号后的合约地址: {}", contractAddress);
            }
            // 业务含义约定：
            // - publisherAddress：前端/业务侧传入的“用户钱包地址”（用于记录在 metadata 中）
            // - 真正上链的 publisher 字段：统一使用平台 Admin 地址（也是签名者）
            String userWalletAddress = StringUtils.hasText(publisherAddress)
                ? publisherAddress
                : null;
            String publisherOnChain = adminCredentials.getAddress();

            // 生成 EIP-712 签名
            byte[] signature = generateEIP712Signature(
                contractAddress,
                dataHash,
                publisherOnChain,
                shareId,
                metadataJson,
                adminCredentials
            );

            Bytes32 hashParam = new Bytes32(hexToBytes32(dataHash));
            Address publisherParam = new Address(publisherOnChain);
            Uint256 shareIdParam = new Uint256(BigInteger.valueOf(shareId));
            Utf8String metadataParam = new Utf8String(metadataJson);
            DynamicBytes signatureParam = new DynamicBytes(signature);

            Function function = new Function(
                "recordShare",
                java.util.Arrays.asList(hashParam, publisherParam, shareIdParam, metadataParam, signatureParam),
                java.util.Collections.emptyList()
            );
            String encodedFunction = FunctionEncoder.encode(function);

            // 确保合约地址格式正确
            String normalizedContractAddress = contractAddress;
            if (!normalizedContractAddress.startsWith("0x")) {
                normalizedContractAddress = "0x" + normalizedContractAddress;
            }
            if (normalizedContractAddress.length() != 42) {
                throw new BusinessException("合约地址格式错误: " + normalizedContractAddress);
            }

            RawTransaction rawTransaction = RawTransaction.createTransaction(
                nonce,
                gasPrice,
                DEFAULT_GAS_LIMIT,
                normalizedContractAddress,
                BigInteger.ZERO,
                encodedFunction
            );

            long chainId = blockchainProperties.getPolygon().getChainId() != null
                ? blockchainProperties.getPolygon().getChainId()
                : 80002L;

            // 使用正确的签名方法
            byte[] signedMessage = TransactionEncoder.signMessage(rawTransaction, chainId, adminCredentials);
            String hexValue = Numeric.toHexString(signedMessage);
            log.info("📤 准备发送 Polygon 交易: nonce={}, gasPrice={} Gwei, contract={}", 
                nonce, gasPrice.divide(BigInteger.valueOf(1_000_000_000L)), normalizedContractAddress);
            
            EthSendTransaction response = web3j.ethSendRawTransaction(hexValue).send();

            if (response.hasError()) {
                log.error("❌ Polygon RPC 返回错误: code={}, message={}", 
                    response.getError().getCode(), response.getError().getMessage());
                throw new BusinessException("Polygon交易失败: " + response.getError().getMessage());
            }

            String txHash = response.getTransactionHash();
            log.info("✅ Polygon 内容存证交易已发送, txHash={}", txHash);

            // 轮询交易回执，严格检查 status 是否为 0x1
            TransactionReceipt receipt = null;
            int maxAttempts = 20;
            int intervalSeconds = 5;

            for (int i = 0; i < maxAttempts; i++) {
                EthGetTransactionReceipt receiptResp =
                    web3j.ethGetTransactionReceipt(txHash).send();
                if (receiptResp.getTransactionReceipt().isPresent()) {
                    receipt = receiptResp.getTransactionReceipt().get();
                    log.info("✅ 第 {} 次查询到回执: txHash={}, status={}", i+1, txHash, receipt.getStatus());
                    break;
                } else {
                    log.info("⏳ 第 {} 次未查到回执，继续等待... (txHash={})", i+1, txHash);
                }
                try {
                    Thread.sleep(intervalSeconds * 1000L);
                } catch (InterruptedException ignore) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            if (receipt == null) {
                log.error("❌ 轮询 {} 次后仍未获取到回执: txHash={}", maxAttempts, txHash);
                throw new BusinessException("Polygon交易失败: 一直未获取到交易回执");
            }

            String status = receipt.getStatus();
            if (!"0x1".equalsIgnoreCase(status)) {
                log.error("❌ 交易已上链但执行失败: txHash={}, status={}, blockNumber={}", 
                    txHash, status, receipt.getBlockNumber());
                throw new BusinessException("Polygon交易执行失败, status=" + status);
            }

            BigInteger blockNumber = receipt.getBlockNumber();

            return PolygonProofResult.builder()
                .txHash(txHash)
                .blockNumber(blockNumber != null ? blockNumber.longValue() : null)
                .blockTime(LocalDateTime.now())
                .payload(metadataJson)
                // 这里记录链上真正的 publisher（平台 Admin 地址）
                .publisher(publisherOnChain)
                .build();

        } catch (Exception e) {
            log.error("Polygon 内容存证失败: shareId={}, hash={}", shareId, dataHash, e);
            throw new BusinessException("Polygon 内容存证失败: " + e.getMessage());
        }
    }

    /**
     * 生成 EIP-712 签名
     */
    private byte[] generateEIP712Signature(
        String verifyingContract,
        String dataHash,
        String publisher,
        Long shareId,
        String metadata,
        Credentials signer
    ) {
        try {
            // EIP-712 Domain Separator
            // keccak256("EIP712Domain(string name,string version,uint256 chainId,address verifyingContract)")
            byte[] domainTypeHash = Hash.sha3(
                "EIP712Domain(string name,string version,uint256 chainId,address verifyingContract)".getBytes(StandardCharsets.UTF_8)
            );
            
            byte[] nameHash = Hash.sha3("ContentShareRegistry".getBytes(StandardCharsets.UTF_8));
            byte[] versionHash = Hash.sha3("1".getBytes(StandardCharsets.UTF_8));
            
            long chainId = blockchainProperties.getPolygon().getChainId() != null
                ? blockchainProperties.getPolygon().getChainId()
                : 137L;
            
            String normalizedContract = verifyingContract.startsWith("0x")
                ? verifyingContract.substring(2)
                : verifyingContract;

            // 构建 domainSeparator，注意 verifyingContract 也必须是 32 字节的 left‑padded 值
            ByteBuffer domainBuffer = ByteBuffer.allocate(32 * 5);
            domainBuffer.put(domainTypeHash);
            domainBuffer.put(nameHash);
            domainBuffer.put(versionHash);
            domainBuffer.put(Numeric.toBytesPadded(BigInteger.valueOf(chainId), 32));
            domainBuffer.put(Numeric.toBytesPadded(new BigInteger(normalizedContract, 16), 32));
            
            byte[] domainSeparator = Hash.sha3(domainBuffer.array());
            
            // Struct Hash
            // keccak256("ContentShare(bytes32 dataHash,address publisher,uint256 shareId,string metadata)")
            byte[] structTypeHash = Hash.sha3(
                "ContentShare(bytes32 dataHash,address publisher,uint256 shareId,string metadata)".getBytes(StandardCharsets.UTF_8)
            );
            
            byte[] metadataHash = Hash.sha3(metadata.getBytes(StandardCharsets.UTF_8));
            
            String normalizedPublisher = publisher.startsWith("0x") 
                ? publisher.substring(2) 
                : publisher;
            
            String normalizedDataHash = dataHash.startsWith("0x")
                ? dataHash.substring(2)
                : dataHash;
            
            ByteBuffer structBuffer = ByteBuffer.allocate(32 * 5);
            structBuffer.put(structTypeHash);
            structBuffer.put(Numeric.hexStringToByteArray(normalizedDataHash));
            structBuffer.put(Numeric.toBytesPadded(new BigInteger(normalizedPublisher, 16), 32));
            structBuffer.put(Numeric.toBytesPadded(BigInteger.valueOf(shareId), 32));
            structBuffer.put(metadataHash);
            
            byte[] structHash = Hash.sha3(structBuffer.array());
            
            // EIP-712 最终消息
            ByteBuffer messageBuffer = ByteBuffer.allocate(2 + 32 + 32);
            messageBuffer.put((byte) 0x19);
            messageBuffer.put((byte) 0x01);
            messageBuffer.put(domainSeparator);
            messageBuffer.put(structHash);
            
            byte[] message = Hash.sha3(messageBuffer.array());
            
            // 调试：打印 EIP712 关键中间值
            log.info("Polygon EIP712 debug - domainSeparator: {}", Numeric.toHexString(domainSeparator));
            log.info("Polygon EIP712 debug - structHash     : {}", Numeric.toHexString(structHash));
            log.info("Polygon EIP712 debug - digest(message): {}", Numeric.toHexString(message));
            
            // 签名
            Sign.SignatureData sig = Sign.signMessage(message, signer.getEcKeyPair(), false);
            
            // 本地恢复签名者地址，确认与 Admin 一致
            try {
                BigInteger publicKey = Sign.signedMessageToKey(message, sig);
                String recoveredAddress = "0x" + Keys.getAddress(publicKey);
                log.info("Polygon EIP712 debug - expected signer: {}", signer.getAddress());
                log.info("Polygon EIP712 debug - recovered signer: {}", recoveredAddress);
            } catch (Exception recoverEx) {
                log.warn("Polygon EIP712 debug - 本地恢复签名者失败: {}", recoverEx.getMessage());
            }
            
            // 组合签名 (r + s + v)
            ByteBuffer signatureBuffer = ByteBuffer.allocate(65);
            signatureBuffer.put(sig.getR());
            signatureBuffer.put(sig.getS());
            signatureBuffer.put(sig.getV());
            
            return signatureBuffer.array();
            
        } catch (Exception e) {
            log.error("生成 EIP-712 签名失败", e);
            throw new BusinessException("生成签名失败: " + e.getMessage());
        }
    }

    private byte[] hexToBytes32(String hex) {
        if (!StringUtils.hasText(hex)) {
            throw new BusinessException("文件哈希不能为空，请先确保文件已正确上传并计算哈希");
        }
        String value = hex.startsWith("0x") ? hex.substring(2) : hex;
        if (value.length() != 64) {
            throw new BusinessException("文件哈希必须是32字节（64个十六进制字符）");
        }
        byte[] bytes = Numeric.hexStringToByteArray(value);
        if (bytes.length != 32) {
            throw new BusinessException("文件哈希长度错误");
        }
        return bytes;
    }

    @Data
    @Builder
    public static class PolygonProofResult {
        private String txHash;
        private Long blockNumber;
        private LocalDateTime blockTime;
        private String payload;
        private String publisher;
    }
}
