package com.wereen.competitionplatform.service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthGasPrice;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.response.PollingTransactionReceiptProcessor;
import org.web3j.utils.Numeric;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Polygon Forum Token 奖励链路封装
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ForumTokenRewardService {

    private static final BigInteger DEFAULT_GAS_LIMIT = BigInteger.valueOf(350_000L);
    private static final Duration RECEIPT_WAIT = Duration.ofSeconds(1);
    private static final int RECEIPT_ATTEMPTS = 30;
    private static final BigDecimal DECIMAL = BigDecimal.TEN.pow(18);

    @Value("${blockchain.forum-token.extension-address:}")
    private String forumTokenExtensionAddress;

    @Value("${blockchain.admin.private-key:}")
    private String adminPrivateKey;

    @Value("${blockchain.polygon.rpc-urls:}")
    private String polygonRpcUrls;

    @Value("${blockchain.polygon.rpc-url:https://rpc-amoy.polygon.technology/}")
    private String polygonRpcUrl;

    @Value("${blockchain.polygon.chain-id:80002}")
    private long polygonChainId;

    private final GasTransactionService gasTransactionService;

    private volatile Web3j web3j;
    private volatile Credentials adminCredentials;
    private volatile TransactionManager transactionManager;

    @PostConstruct
    public void init() {
        try {
            if (!StringUtils.hasText(forumTokenExtensionAddress)) {
                log.warn("ForumTokenExtension地址未配置，链上奖励功能不可用");
                return;
            }
            if (!StringUtils.hasText(adminPrivateKey)) {
                log.warn("Polygon管理员私钥未配置，链上奖励功能不可用");
                return;
            }
            log.info("读取到的ForumTokenExtension地址: {}", forumTokenExtensionAddress);
            this.forumTokenExtensionAddress = normalizeAddress(forumTokenExtensionAddress);
            log.info("规范化后的ForumTokenExtension地址: {} (长度: {})", 
                this.forumTokenExtensionAddress, this.forumTokenExtensionAddress.length());
            
            this.adminCredentials = Credentials.create(normalizePrivateKey(adminPrivateKey));
            log.info("管理员地址: {} (长度: {})", 
                adminCredentials.getAddress(), adminCredentials.getAddress().length());
            
            initializeWeb3();
            log.info("ForumTokenRewardService初始化成功");
        } catch (Exception e) {
            log.error("ForumTokenRewardService初始化失败，跳过链上奖励功能: {}", e.getMessage(), e);
            // 不抛出异常，允许应用继续启动
        }
    }

    /**
     * 以下旧版「管理员推模式」奖励接口已在链上废弃（对应的 Solidity 函数全部 revert），
     * 为避免继续发起失败交易，这里统一改为本地 no-op，并打印日志说明。
     *
     * 新版奖励全部走「用户自己拿签名上链 claimXXXReward」的 Pull 模式。
     */
    public Optional<String> rewardPost(String walletAddress, String postId) {
        log.info("rewardPost 已废弃（管理员推模式），跳过链上调用: wallet={}, postId={}", walletAddress, postId);
        return Optional.empty();
    }

    public Optional<String> rewardComment(String walletAddress, String commentId) {
        log.info("rewardComment 已废弃（管理员推模式），跳过链上调用: wallet={}, commentId={}", walletAddress, commentId);
        return Optional.empty();
    }

    public Optional<String> rewardFeaturedPost(String walletAddress, String postId) {
        log.info("rewardFeaturedPost 已废弃（管理员推模式），跳过链上调用: wallet={}, postId={}", walletAddress, postId);
        return Optional.empty();
    }

    public Optional<String> rewardContentShare(String walletAddress, String contentId, boolean isVideo) {
        log.info("rewardContentShare 已废弃（管理员推模式），跳过链上调用: wallet={}, contentId={}, isVideo={}",
            walletAddress, contentId, isVideo);
        return Optional.empty();
    }

    public Optional<String> dailyCheckin(String walletAddress) {
        log.info("dailyCheckin 已废弃（管理员推模式），跳过链上调用: wallet={}", walletAddress);
        return Optional.empty();
    }

    /**
     * 打赏内容创作者
     */
    public String tipContent(String tipperAddress, String creatorAddress, String contentType, String contentId, BigDecimal amount) throws Exception {
        if (!isReady()) {
            throw new Exception("Web3 service not ready");
        }

        BigInteger amountWei = amount.multiply(DECIMAL).toBigInteger();

        Function function = new Function(
            "tipContent",
            Arrays.asList(
                new Address(creatorAddress),
                new Uint256(amountWei),
                new Utf8String(contentType),
                new Utf8String(contentId)
            ),
            List.of()
        );

        return sendTransactionAndWaitReceipt(function, "ForumTokenExtension.tipContent");
    }

    /**
     * 购买置顶功能
     */
    public String purchasePinPost(String userAddress, String postId, BigDecimal amount) throws Exception {
        if (!isReady()) {
            throw new Exception("Web3 service not ready");
        }

        BigInteger amountWei = amount.multiply(DECIMAL).toBigInteger();

        Function function = new Function(
            "purchasePinPost",
            Arrays.asList(
                new Address(userAddress),
                new Uint256(amountWei),
                new Utf8String(postId)
            ),
            List.of()
        );

        return sendTransactionAndWaitReceipt(function, "ForumTokenExtension.purchasePinPost");
    }

    /**
     * 燃烧代币
     */
    public String burnTokens(String userAddress, BigDecimal amount) throws Exception {
        if (!isReady()) {
            throw new Exception("Web3 service not ready");
        }

        BigInteger amountWei = amount.multiply(DECIMAL).toBigInteger();

        Function function = new Function(
            "burnTokens",
            Arrays.asList(new Uint256(amountWei)),
            List.of()
        );

        return sendTransactionAndWaitReceipt(function, "ForumTokenExtension.burnTokens");
    }

    public BigDecimal getTokenBalance(String walletAddress) {
        if (!isReady() || !StringUtils.hasText(walletAddress)) {
            return BigDecimal.ZERO;
        }
        List<Type> outputs = callFunction(new Function(
            "getUserTokenBalance",
            List.of(new Address(walletAddress)),
            List.of(new TypeReference<Uint256>() {})
        ));
        if (outputs.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigInteger value = ((Uint256) outputs.get(0)).getValue();
        return new BigDecimal(value).divide(DECIMAL);
    }

    public OnChainRewardInfo getUserRewardInfo(String walletAddress) {
        if (!isReady() || !StringUtils.hasText(walletAddress)) {
            return null;
        }
        List<Type> outputs = callFunction(new Function(
            "getUserRewardInfo",
            List.of(new Address(walletAddress)),
            Arrays.asList(
                new TypeReference<Uint256>() {},
                new TypeReference<Uint256>() {},
                new TypeReference<Uint256>() {},
                new TypeReference<Uint256>() {}
            )
        ));
        if (outputs.size() < 4) {
            return null;
        }
        BigInteger ts = ((Uint256) outputs.get(0)).getValue();
        BigInteger consecutive = ((Uint256) outputs.get(1)).getValue();
        BigInteger daily = ((Uint256) outputs.get(2)).getValue();
        BigInteger total = ((Uint256) outputs.get(3)).getValue();
        LocalDateTime lastCheckin = ts.signum() == 0
            ? null
            : LocalDateTime.ofInstant(Instant.ofEpochSecond(ts.longValue()), ZoneOffset.UTC);

        return new OnChainRewardInfo(
            lastCheckin,
            consecutive.intValue(),
            new BigDecimal(daily),
            new BigDecimal(total)
        );
    }

    public boolean canCheckin(String walletAddress) {
        if (!isReady() || !StringUtils.hasText(walletAddress)) {
            return false;
        }
        List<Type> outputs = callFunction(new Function(
            "canCheckinToday",
            List.of(new Address(walletAddress)),
            List.of(new TypeReference<org.web3j.abi.datatypes.Bool>() {})
        ));
        if (outputs.isEmpty()) {
            return false;
        }
        return ((org.web3j.abi.datatypes.Bool) outputs.get(0)).getValue();
    }

    public RewardConfig fetchRewardConfig() {
        if (!isReady()) {
            log.warn("Web3j 未就绪，无法获取奖励配置");
            return null;
        }
        try {
            // 调试：打印地址信息
            String fromAddr = adminCredentials.getAddress();
            String toAddr = forumTokenExtensionAddress;
            log.debug("调用 rewardConfig - From: {} (len: {}), To: {} (len: {})", 
                fromAddr, fromAddr.length(), toAddr, toAddr.length());
            
            // 使用原始的 eth_call 直接获取数据，然后手动解码
            String data = FunctionEncoder.encode(new Function(
                "rewardConfig",
                List.of(),
                List.of()
            ));
            
            EthCall response = web3j.ethCall(
                Transaction.createEthCallTransaction(
                    fromAddr,
                    toAddr,
                    data
                ),
                DefaultBlockParameterName.LATEST
            ).send();
            
            if (response.hasError()) {
                log.error("调用 rewardConfig 失败: {}", response.getError().getMessage());
                return null;
            }
            
            String result = response.getResult();
            if (!StringUtils.hasText(result) || result.equals("0x")) {
                log.error("rewardConfig 返回空数据");
                return null;
            }
            
            // 手动解码返回值
            // 去掉 "0x" 前缀
            String hex = result.substring(2);
            
            // 每个 uint256 是 64 个十六进制字符（32 字节）
            if (hex.length() < 64 * 7) {
                log.error("rewardConfig 返回数据长度不足: {} 字符，期望至少 {} 字符", 
                    hex.length(), 64 * 7);
                return null;
            }
            
            // 解析 7 个 uint256 值
            BigInteger postReward = new BigInteger(hex.substring(0, 64), 16);
            BigInteger commentReward = new BigInteger(hex.substring(64, 128), 16);
            BigInteger dailyCheckinReward = new BigInteger(hex.substring(128, 192), 16);
            BigInteger featuredPostReward = new BigInteger(hex.substring(192, 256), 16);
            BigInteger consecutiveBonus = new BigInteger(hex.substring(256, 320), 16);
            BigInteger contentImageReward = new BigInteger(hex.substring(320, 384), 16);
            BigInteger contentVideoReward = new BigInteger(hex.substring(384, 448), 16);
            
            log.info("成功获取奖励配置: postReward={}, commentReward={}, dailyCheckinReward={}", 
                postReward, commentReward, dailyCheckinReward);
            
            return RewardConfig.builder()
                .postReward(postReward)
                .commentReward(commentReward)
                .dailyCheckinReward(dailyCheckinReward)
                .featuredPostReward(featuredPostReward)
                .consecutiveBonus(consecutiveBonus)
                .contentImageReward(contentImageReward)
                .contentVideoReward(contentVideoReward)
                .build();
                
        } catch (Exception e) {
            log.error("获取奖励配置失败", e);
            return null;
        }
    }

    private Optional<String> executeRewardFunction(String functionName, List<Type> input) {
        if (!isReady()) {
            return Optional.empty();
        }
        try {
            Function function = new Function(functionName, input, List.of());
            String txHash = sendTransactionAndWaitReceipt(function, "ForumTokenExtension." + functionName);
            return Optional.ofNullable(txHash);
        } catch (Exception e) {
            log.error("执行链上函数失败: {}", functionName, e);
            refreshWeb3();
            return Optional.empty();
        }
    }

    private boolean isReady() {
        return web3j != null && transactionManager != null && adminCredentials != null;
    }

    private synchronized void initializeWeb3() {
        if (this.web3j != null) {
            return;
        }
        List<String> candidates = new ArrayList<>();
        if (StringUtils.hasText(polygonRpcUrls)) {
            for (String entry : polygonRpcUrls.split(",")) {
                if (StringUtils.hasText(entry)) {
                    candidates.add(entry.trim());
                }
            }
        }
        if (StringUtils.hasText(polygonRpcUrl)) {
            candidates.add(polygonRpcUrl.trim());
        }
        if (candidates.isEmpty()) {
            candidates.add("https://rpc-amoy.polygon.technology/");
        }

        for (String endpoint : candidates) {
            try {
                Web3j client = Web3j.build(new HttpService(endpoint));
                client.ethChainId().send();
                this.web3j = client;
                this.transactionManager = new RawTransactionManager(
                    client,
                    adminCredentials,
                    polygonChainId,
                    new PollingTransactionReceiptProcessor(client, RECEIPT_WAIT.toMillis(), RECEIPT_ATTEMPTS)
                );
                log.info("已连接 Polygon RPC: {}", endpoint);
                return;
            } catch (Exception e) {
                log.warn("连接 Polygon RPC 失败: {}", endpoint, e);
            }
        }
        log.error("全部 Polygon RPC 均无法连接，链上奖励功能不可用");
    }

    private synchronized void refreshWeb3() {
        closeQuietly();
        this.web3j = null;
        this.transactionManager = null;
        initializeWeb3();
    }

    private List<Type> callFunction(Function function) {
        if (!isReady()) {
            return List.of();
        }
        try {
            String data = FunctionEncoder.encode(function);
            EthCall response = web3j.ethCall(
                Transaction.createEthCallTransaction(adminCredentials.getAddress(), forumTokenExtensionAddress, data),
                DefaultBlockParameterName.LATEST
            ).send();
            if (response.hasError()) {
                throw new IllegalStateException(response.getError().getMessage());
            }
            return FunctionReturnDecoder.decode(response.getResult(), function.getOutputParameters());
        } catch (Exception e) {
            log.error("调用链上函数失败: {}", function.getName(), e);
            return List.of();
        }
    }

    /**
     * 发送交易但只校验发送是否成功（不等待回执）
     */
    private String sendTransaction(Function function) throws Exception {
        BigInteger gasPrice = fetchGasPrice();
        String data = FunctionEncoder.encode(function);
        EthSendTransaction response = transactionManager.sendTransaction(
            gasPrice,
            DEFAULT_GAS_LIMIT,
            forumTokenExtensionAddress,
            data,
            BigInteger.ZERO
        );
        if (response.hasError()) {
            throw new IllegalStateException("链上交易失败: " + response.getError().getMessage());
        }
        return response.getTransactionHash();
    }

    /**
     * 发送交易并严格等待回执，只有 status=0x1 才算成功
     */
    private String sendTransactionAndWaitReceipt(Function function, String action) throws Exception {
        String txHash = sendTransaction(function);
        if (!StringUtils.hasText(txHash)) {
            throw new IllegalStateException(action + " 交易失败: 未获得交易哈希");
        }

        org.web3j.protocol.core.methods.response.TransactionReceipt receipt = null;
        int attempts = 0;
        int maxAttempts = 20; // 总共轮询约 100 秒
        long sleepMillis = 5000L;

        while (attempts < maxAttempts && receipt == null) {
            try {
                Thread.sleep(sleepMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(action + " 交易回执等待被中断", e);
            }

            try {
                var opt = web3j.ethGetTransactionReceipt(txHash).send().getTransactionReceipt();
                if (opt.isPresent()) {
                    receipt = opt.get();
                    String status = receipt.getStatus();
                    log.info("{} 获取到交易回执: txHash={}, status={}, block={}",
                        action, txHash, status, receipt.getBlockNumber());
                    if (!"0x1".equalsIgnoreCase(status)) {
                        // 业务执行失败，直接抛出异常，不再继续轮询
                        throw new IllegalStateException(action + " 交易执行失败, status=" + status);
                    }
                    // 记录本次交易的 Gas 消耗，用于后台 Gas 面板统计
                    try {
                        BigInteger gasUsed = receipt.getGasUsed();
                        BigInteger blockNumber = receipt.getBlockNumber();
                        BigInteger gasPriceWei = null;
                        String effectiveGasPrice = receipt.getEffectiveGasPrice();
                        if (StringUtils.hasText(effectiveGasPrice)) {
                            gasPriceWei = Numeric.decodeQuantity(effectiveGasPrice);
                        }

                        gasTransactionService.recordForumTx(
                            action,
                            txHash,
                            receipt.getFrom(),
                            receipt.getTo(),
                            receipt.getTo(), // ForumTokenExtension 合约地址
                            gasUsed,
                            gasPriceWei,
                            blockNumber,
                            "0x1".equalsIgnoreCase(status)
                        );
                    } catch (Exception recordEx) {
                        log.warn("记录 Gas 交易信息失败: txHash={}", txHash, recordEx);
                    }
                    break;
                }
            } catch (IllegalStateException e) {
                // 明确的业务失败，向上抛出，让调用方按失败处理
                log.warn("{} 查询交易回执失败: txHash={}, error={}", action, txHash, e.getMessage());
                throw e;
            } catch (Exception e) {
                // 网络等临时错误，记录日志后继续重试
                log.warn("{} 查询交易回执失败: txHash={}, error={}", action, txHash, e.getMessage());
            }
            attempts++;
        }

        if (receipt == null) {
            throw new IllegalStateException(action + " 交易执行失败: 一直未获取到交易回执");
        }

        return txHash;
    }

    private BigInteger fetchGasPrice() {
        if (web3j == null) {
            return BigInteger.valueOf(30_000_000_000L);
        }
        try {
            EthGasPrice gasPrice = web3j.ethGasPrice().send();
            BigInteger price = gasPrice.getGasPrice();
            if (price == null) {
                return BigInteger.valueOf(30_000_000_000L);
            }
            // 增加50%的gas price以避免"replacement transaction underpriced"错误
            BigInteger adjustedPrice = price.multiply(BigInteger.valueOf(15)).divide(BigInteger.TEN);
            log.info("ForumTokenRewardService使用的Gas Price: {} Gwei", adjustedPrice.divide(BigInteger.valueOf(1_000_000_000L)));
            return adjustedPrice;
        } catch (Exception e) {
            log.warn("获取 gasPrice 失败，使用默认值", e);
            return BigInteger.valueOf(30_000_000_000L);
        }
    }

    private String normalizePrivateKey(String raw) {
        String trimmed = raw.trim();
        if (trimmed.startsWith("0x") || trimmed.startsWith("0X")) {
            return trimmed;
        }
        return Numeric.prependHexPrefix(trimmed);
    }

    private void closeQuietly() {
        if (this.web3j != null) {
            try {
                this.web3j.shutdown();
            } catch (Exception ignore) {
            }
        }
    }

    private String normalizeAddress(String address) {
        if (!StringUtils.hasText(address)) {
            return null;
        }
        String cleaned = address.trim().replace("\"", "").replace("'", "");
        String body = cleaned;
        if (cleaned.startsWith("0x") || cleaned.startsWith("0X")) {
            body = cleaned.substring(2);
        }
        body = body.trim();
        
        // 以太坊地址必须是 40 个十六进制字符（20 字节）
        if (body.length() != 40) {
            log.error("无效的合约地址长度: {}, 期望40个字符，实际{}个字符", address, body.length());
            throw new IllegalArgumentException("ForumTokenExtension address must be 40 hex characters (20 bytes)");
        }
        
        String normalized = "0x" + body.toLowerCase();
        log.info("ForumTokenExtension address normalized: {} (length: {})", normalized, normalized.length());
        return normalized;
    }

    @Data
    public static class OnChainRewardInfo {
        private final LocalDateTime lastCheckinTime;
        private final Integer consecutiveDays;
        private final BigDecimal dailyRewardAmount;
        private final BigDecimal totalRewarded;
    }

    @Data
    @lombok.Builder
    public static class RewardConfig {
        private BigInteger postReward;
        private BigInteger commentReward;
        private BigInteger dailyCheckinReward;
        private BigInteger featuredPostReward;
        private BigInteger consecutiveBonus;
        private BigInteger contentImageReward;
        private BigInteger contentVideoReward;
    }

}
