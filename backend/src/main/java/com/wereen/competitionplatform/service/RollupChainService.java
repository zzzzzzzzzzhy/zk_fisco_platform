package com.wereen.competitionplatform.service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import jakarta.annotation.PostConstruct;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.DynamicBytes;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.tx.RawTransactionManager;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.EthTransaction;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.response.PollingTransactionReceiptProcessor;
import org.web3j.utils.Numeric;

@Slf4j
@Service
@RequiredArgsConstructor
public class RollupChainService {

    private static final BigInteger DEFAULT_GAS_LIMIT = BigInteger.valueOf(600_000L);
    private static final BigDecimal WEI_IN_GWEI = BigDecimal.valueOf(1_000_000_000L);
    private static final int RECEIPT_ATTEMPTS = 30;
    private static final long RECEIPT_SLEEP_MS = 1000L;
    private static final String ZERO_ADDRESS = "0x0000000000000000000000000000000000000000";

    @Value("${blockchain.rollup.registry-address:}")
    private String rollupRegistryAddress;

    @Value("${blockchain.admin.private-key:}")
    private String adminPrivateKey;

    @Value("${blockchain.polygon.rpc-url:https://polygon-rpc.com}")
    private String polygonRpcUrl;

    @Value("${blockchain.polygon.chain-id:137}")
    private long polygonChainId;

    @Value("${blockchain.polygon.gas-price-gwei:30}")
    private long polygonGasPriceGwei;

    @Value("${blockchain.polygon.gas-mode:fixed}")
    private String gasMode;

    @Value("${blockchain.polygon.max-priority-fee-gwei:30}")
    private long maxPriorityFeeGwei;

    @Value("${blockchain.polygon.max-fee-cap-gwei:0}")
    private BigDecimal maxFeeCapGwei;

    @Value("${blockchain.polygon.max-priority-fee-cap-gwei:0}")
    private BigDecimal maxPriorityFeeCapGwei;

    @Value("${blockchain.polygon.gas-station-url:https://gasstation.polygon.technology/v2}")
    private String gasStationUrl;

    @Value("${blockchain.polygon.gas-station-tier:standard}")
    private String gasStationTier;

    @Value("${blockchain.polygon.gas-station-timeout-ms:2000}")
    private long gasStationTimeoutMs;

    @Value("${blockchain.polygon.gas-station-cache-seconds:15}")
    private long gasStationCacheSeconds;

    private Web3j web3j;
    private Credentials adminCredentials;
    private TransactionManager transactionManager;
    private final ObjectMapper objectMapper;

    private volatile GasStationQuote cachedQuote;
    private volatile long cachedQuoteAtMs;

    public enum TransactionState {
        MINED_SUCCESS,
        MINED_FAILED,
        PENDING,
        NOT_FOUND,
        UNKNOWN
    }

    @PostConstruct
    public void init() {
        if (!StringUtils.hasText(rollupRegistryAddress)
            || !StringUtils.hasText(adminPrivateKey)) {
            log.warn("RollupChainService 未配置完整，跳过初始化");
            return;
        }

        String normalizedKey = adminPrivateKey.startsWith("0x")
            ? adminPrivateKey
            : "0x" + adminPrivateKey;
        this.adminCredentials = Credentials.create(normalizedKey);
        this.web3j = Web3j.build(new HttpService(polygonRpcUrl));
        this.transactionManager = new RawTransactionManager(
            web3j,
            adminCredentials,
            polygonChainId,
            new PollingTransactionReceiptProcessor(web3j, RECEIPT_SLEEP_MS, RECEIPT_ATTEMPTS)
        );
    }

    public String submitBatch(byte[] proof, String journalDigestHex, Long batchId, String merkleRootHex,
                              long count, long windowStart, long windowEnd) throws Exception {
        if (!isReady()) {
            throw new IllegalStateException("RollupChainService not ready");
        }

        byte[] journalDigest = Numeric.hexStringToByteArray(journalDigestHex);
        byte[] rootBytes = Numeric.hexStringToByteArray("0x" + merkleRootHex);
        Function function = new Function(
            "submitBatch",
            Arrays.asList(
                new DynamicBytes(proof),
                new Bytes32(journalDigest),
                new Uint256(batchId),
                new Bytes32(rootBytes),
                new Uint256(count),
                new Uint256(windowStart),
                new Uint256(windowEnd)
            ),
            List.of()
        );

        return sendTransaction(function);
    }

    public TransactionState getTransactionState(String txHash) {
        if (!StringUtils.hasText(txHash) || web3j == null) {
            return TransactionState.UNKNOWN;
        }
        try {
            var receiptOpt = web3j.ethGetTransactionReceipt(txHash).send().getTransactionReceipt();
            if (receiptOpt.isPresent()) {
                String status = receiptOpt.get().getStatus();
                if ("0x1".equalsIgnoreCase(status)) {
                    return TransactionState.MINED_SUCCESS;
                }
                return TransactionState.MINED_FAILED;
            }
            EthTransaction txResponse = web3j.ethGetTransactionByHash(txHash).send();
            if (txResponse.getTransaction().isPresent()) {
                return TransactionState.PENDING;
            }
            return TransactionState.NOT_FOUND;
        } catch (Exception e) {
            log.warn("Rollup 交易状态查询失败: txHash={}, err={}", txHash, e.getMessage());
            return TransactionState.UNKNOWN;
        }
    }

    public boolean isBatchSubmitted(long batchId) {
        if (!StringUtils.hasText(rollupRegistryAddress) || web3j == null) {
            return false;
        }
        try {
            Function function = new Function(
                "isBatchSubmitted",
                List.of(new Uint256(BigInteger.valueOf(batchId))),
                List.of(new TypeReference<Bool>() {})
            );
            String data = FunctionEncoder.encode(function);
            String from = adminCredentials != null ? adminCredentials.getAddress() : ZERO_ADDRESS;
            var response = web3j.ethCall(
                Transaction.createEthCallTransaction(from, rollupRegistryAddress, data),
                DefaultBlockParameterName.LATEST
            ).send();
            if (response == null) {
                return false;
            }
            if (response.isReverted()) {
                log.warn("Rollup 批次查询 reverted: batchId={}, reason={}", batchId, response.getRevertReason());
                return false;
            }
            if (response.getError() != null) {
                log.warn("Rollup 批次查询失败: batchId={}, err={}", batchId, response.getError().getMessage());
                return false;
            }
            List<Type> outputs = FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());
            if (outputs == null || outputs.isEmpty()) {
                return false;
            }
            Object value = outputs.get(0).getValue();
            return Boolean.TRUE.equals(value);
        } catch (Exception e) {
            log.warn("Rollup 批次查询异常: batchId={}, err={}", batchId, e.getMessage());
            return false;
        }
    }

    private boolean isReady() {
        return web3j != null
            && adminCredentials != null
            && transactionManager != null
            && StringUtils.hasText(rollupRegistryAddress);
    }

    private String sendTransaction(Function function) throws Exception {
        String data = FunctionEncoder.encode(function);
        EthSendTransaction response;
        if (isEip1559Mode() && transactionManager instanceof RawTransactionManager rawManager) {
            Eip1559Fees fees = resolveEip1559Fees();
            if (fees != null) {
                log.info("EIP-1559 交易参数: gasLimit={}, maxPriorityFee={} gwei, maxFee={} gwei",
                    DEFAULT_GAS_LIMIT,
                    fees.maxPriorityFeeWei.divide(BigInteger.valueOf(1_000_000_000L)),
                    fees.maxFeeWei.divide(BigInteger.valueOf(1_000_000_000L))
                );
                response = rawManager.sendEIP1559Transaction(
                    polygonChainId,
                    fees.maxPriorityFeeWei,  // maxPriorityFeePerGas
                    fees.maxFeeWei,           // maxFeePerGas
                    DEFAULT_GAS_LIMIT,        // gasLimit
                    rollupRegistryAddress,
                    data,
                    BigInteger.ZERO,
                    false
                );
            } else {
                response = transactionManager.sendTransaction(
                    gasPriceWei(),
                    DEFAULT_GAS_LIMIT,
                    rollupRegistryAddress,
                    data,
                    BigInteger.ZERO
                );
            }
        } else {
            response = transactionManager.sendTransaction(
                gasPriceWei(),
                DEFAULT_GAS_LIMIT,
                rollupRegistryAddress,
                data,
                BigInteger.ZERO
            );
        }
        if (response.hasError()) {
            throw new Exception("交易失败: " + response.getError().getMessage());
        }
        return response.getTransactionHash();
    }

    private BigInteger gasPriceWei() {
        if ("gas-station".equalsIgnoreCase(gasMode)) {
            GasStationQuote quote = getGasStationQuote();
            if (quote != null && quote.maxFeeGwei != null) {
                BigDecimal cappedMaxFee = capMaxFee(quote.maxFeeGwei, "gas-station");
                return toWei(cappedMaxFee);
            }
        }
        if (polygonGasPriceGwei <= 0) {
            return BigInteger.ZERO;
        }
        BigDecimal fixedMaxFee = BigDecimal.valueOf(polygonGasPriceGwei);
        fixedMaxFee = capMaxFee(fixedMaxFee, "fixed");
        return toWei(fixedMaxFee);
    }

    private boolean isEip1559Mode() {
        return "eip1559-gas-station".equalsIgnoreCase(gasMode)
            || "eip1559-fixed".equalsIgnoreCase(gasMode);
    }

    private Eip1559Fees resolveEip1559Fees() {
        if ("eip1559-gas-station".equalsIgnoreCase(gasMode)) {
            GasStationQuote quote = getGasStationQuote();
            if (quote != null && quote.maxFeeGwei != null && quote.priorityFeeGwei != null) {
                BigDecimal maxFee = floorMaxFee(quote.maxFeeGwei, "gas-station");
                BigDecimal priorityFee = floorPriorityFee(quote.priorityFeeGwei, "gas-station");
                maxFee = capMaxFee(maxFee, "gas-station");
                priorityFee = capPriorityFee(priorityFee, maxFee, "gas-station");
                return new Eip1559Fees(toWei(priorityFee), toWei(maxFee));
            }
            Eip1559Fees baseFeeFees = resolveEip1559FromBaseFee();
            if (baseFeeFees != null) {
                log.info("Gas station unavailable, fallback to base fee for EIP-1559");
                return baseFeeFees;
            }
        }
        if ("eip1559-fixed".equalsIgnoreCase(gasMode)) {
            if (polygonGasPriceGwei <= 0 || maxPriorityFeeGwei <= 0) {
                Eip1559Fees baseFeeFees = resolveEip1559FromBaseFee();
                if (baseFeeFees != null) {
                    log.info("Fixed EIP-1559 fee invalid, fallback to base fee");
                }
                return baseFeeFees;
            }
            BigDecimal maxFee = capMaxFee(BigDecimal.valueOf(polygonGasPriceGwei), "fixed");
            BigDecimal priorityFee = capPriorityFee(BigDecimal.valueOf(maxPriorityFeeGwei), maxFee, "fixed");
            return new Eip1559Fees(toWei(priorityFee), toWei(maxFee));
        }
        return null;
    }

    private Eip1559Fees resolveEip1559FromBaseFee() {
        if (web3j == null) {
            return null;
        }
        try {
            var block = web3j.ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false).send();
            if (block == null || block.getBlock() == null) {
                return null;
            }
            BigInteger baseFee = block.getBlock().getBaseFeePerGas();
            if (baseFee == null || baseFee.signum() <= 0) {
                return null;
            }
            long priorityGwei = maxPriorityFeeGwei > 0 ? maxPriorityFeeGwei : 30;
            BigDecimal priorityFee = floorPriorityFee(BigDecimal.valueOf(priorityGwei), "base-fee");
            BigDecimal baseFeeGwei = new BigDecimal(baseFee)
                .divide(WEI_IN_GWEI, 9, RoundingMode.DOWN);
            BigDecimal maxFee = baseFeeGwei.multiply(BigDecimal.valueOf(2L)).add(priorityFee);
            maxFee = floorMaxFee(maxFee, "base-fee");
            maxFee = capMaxFee(maxFee, "base-fee");
            priorityFee = capPriorityFee(priorityFee, maxFee, "base-fee");
            return new Eip1559Fees(toWei(priorityFee), toWei(maxFee));
        } catch (Exception e) {
            log.warn("Base fee fetch failed: {}", e.getMessage());
            return null;
        }
    }

    private BigInteger toWei(BigDecimal gwei) {
        return gwei.multiply(WEI_IN_GWEI).toBigInteger();
    }

    private BigDecimal capMaxFee(BigDecimal maxFeeGwei, String source) {
        if (maxFeeGwei == null) {
            return BigDecimal.ZERO;
        }
        if (maxFeeCapGwei != null && maxFeeCapGwei.signum() > 0
            && maxFeeGwei.compareTo(maxFeeCapGwei) > 0) {
            log.warn("Max fee capped (source={}): {} gwei -> {} gwei", source, maxFeeGwei, maxFeeCapGwei);
            return maxFeeCapGwei;
        }
        return maxFeeGwei;
    }

    private BigDecimal floorMaxFee(BigDecimal maxFeeGwei, String source) {
        if (maxFeeGwei == null) {
            return BigDecimal.ZERO;
        }
        if (polygonGasPriceGwei > 0) {
            BigDecimal floor = BigDecimal.valueOf(polygonGasPriceGwei);
            if (maxFeeGwei.compareTo(floor) < 0) {
                log.warn("Max fee floored (source={}): {} gwei -> {} gwei", source, maxFeeGwei, floor);
                return floor;
            }
        }
        return maxFeeGwei;
    }

    private BigDecimal floorPriorityFee(BigDecimal priorityFeeGwei, String source) {
        if (priorityFeeGwei == null) {
            return BigDecimal.ZERO;
        }
        if (maxPriorityFeeGwei > 0) {
            BigDecimal floor = BigDecimal.valueOf(maxPriorityFeeGwei);
            if (priorityFeeGwei.compareTo(floor) < 0) {
                log.warn("Priority fee floored (source={}): {} gwei -> {} gwei", source, priorityFeeGwei, floor);
                return floor;
            }
        }
        return priorityFeeGwei;
    }

    private BigDecimal capPriorityFee(BigDecimal priorityFeeGwei, BigDecimal maxFeeGwei, String source) {
        if (priorityFeeGwei == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal cap = null;
        if (maxPriorityFeeCapGwei != null && maxPriorityFeeCapGwei.signum() > 0) {
            cap = maxPriorityFeeCapGwei;
        } else if (maxFeeCapGwei != null && maxFeeCapGwei.signum() > 0) {
            cap = maxFeeCapGwei;
        }
        if (cap != null && priorityFeeGwei.compareTo(cap) > 0) {
            log.warn("Priority fee capped (source={}): {} gwei -> {} gwei", source, priorityFeeGwei, cap);
            priorityFeeGwei = cap;
        }
        if (maxFeeGwei != null && maxFeeGwei.signum() > 0 && maxFeeGwei.compareTo(priorityFeeGwei) <= 0) {
            BigDecimal adjusted = maxFeeGwei.subtract(BigDecimal.ONE);
            if (adjusted.signum() < 0) {
                adjusted = BigDecimal.ZERO;
            }
            log.warn("Priority fee adjusted to stay below max fee (source={}): {} gwei -> {} gwei",
                source, priorityFeeGwei, adjusted);
            return adjusted;
        }
        return priorityFeeGwei;
    }

    private GasStationQuote getGasStationQuote() {
        long now = System.currentTimeMillis();
        if (cachedQuote != null && (now - cachedQuoteAtMs) < gasStationCacheSeconds * 1000) {
            return cachedQuote;
        }
        try {
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(gasStationTimeoutMs))
                .build();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(gasStationUrl))
                .timeout(Duration.ofMillis(gasStationTimeoutMs))
                .GET()
                .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Gas station response status: {}", response.statusCode());
                return null;
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode tierNode = root.path(gasStationTier);
            if (tierNode.isMissingNode()) {
                log.warn("Gas station tier not found: {}", gasStationTier);
                return null;
            }
            BigDecimal maxFee = tierNode.path("maxFee").decimalValue();
            BigDecimal priorityFee = tierNode.path("maxPriorityFee").decimalValue();
            GasStationQuote quote = new GasStationQuote(maxFee, priorityFee);
            cachedQuote = quote;
            cachedQuoteAtMs = now;
            return quote;
        } catch (Exception e) {
            log.warn("Gas station fetch failed: {}", e.getMessage());
            return null;
        }
    }

    private static class GasStationQuote {
        private final BigDecimal maxFeeGwei;
        private final BigDecimal priorityFeeGwei;

        private GasStationQuote(BigDecimal maxFeeGwei, BigDecimal priorityFeeGwei) {
            this.maxFeeGwei = maxFeeGwei;
            this.priorityFeeGwei = priorityFeeGwei;
        }
    }

    private static class Eip1559Fees {
        private final BigInteger maxPriorityFeeWei;
        private final BigInteger maxFeeWei;

        private Eip1559Fees(BigInteger maxPriorityFeeWei, BigInteger maxFeeWei) {
            this.maxPriorityFeeWei = maxPriorityFeeWei;
            this.maxFeeWei = maxFeeWei;
        }
    }
}
