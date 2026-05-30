package com.wereen.competitionplatform.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.DynamicArray;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
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

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RollupRewardDistributorService {

    private static final BigInteger DEFAULT_GAS_LIMIT = BigInteger.valueOf(900_000L);
    private static final int RECEIPT_ATTEMPTS = 30;
    private static final long RECEIPT_SLEEP_MS = 1000L;
    private static final String ZERO_ADDRESS = "0x0000000000000000000000000000000000000000";

    @Value("${blockchain.rollup.reward-distributor-address:}")
    private String rewardDistributorAddress;

    @Value("${blockchain.admin.private-key:}")
    private String adminPrivateKey;

    @Value("${blockchain.polygon.rpc-url:https://polygon-rpc.com}")
    private String polygonRpcUrl;

    @Value("${blockchain.polygon.chain-id:137}")
    private long polygonChainId;

    @Value("${blockchain.polygon.gas-price-gwei:30}")
    private long polygonGasPriceGwei;

    private Web3j web3j;
    private Credentials adminCredentials;
    private TransactionManager transactionManager;

    public enum TransactionState {
        MINED_SUCCESS,
        MINED_FAILED,
        PENDING,
        NOT_FOUND,
        UNKNOWN
    }

    @PostConstruct
    public void init() {
        if (!StringUtils.hasText(rewardDistributorAddress) || !StringUtils.hasText(adminPrivateKey)) {
            log.warn("RollupRewardDistributorService 未配置完整，跳过初始化");
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

    public String distributeBatch(long batchId, List<String> recipients, List<BigInteger> amounts) throws Exception {
        if (!isReady()) {
            throw new IllegalStateException("RollupRewardDistributorService not ready");
        }
        if (recipients == null || amounts == null || recipients.size() != amounts.size()) {
            throw new IllegalArgumentException("recipients/amounts 不一致");
        }

        List<Address> addressList = new ArrayList<>();
        List<Uint256> amountList = new ArrayList<>();
        for (int i = 0; i < recipients.size(); i++) {
            addressList.add(new Address(recipients.get(i)));
            amountList.add(new Uint256(amounts.get(i)));
        }

        Function function = new Function(
            "distributeBatch",
            List.of(
                new Uint256(batchId),
                new DynamicArray<>(Address.class, addressList),
                new DynamicArray<>(Uint256.class, amountList)
            ),
            List.of()
        );

        return sendTransaction(function);
    }

    public boolean isBatchDistributed(long batchId) {
        if (!StringUtils.hasText(rewardDistributorAddress) || web3j == null) {
            return false;
        }
        try {
            Function function = new Function(
                "batchDistributed",
                List.of(new Uint256(BigInteger.valueOf(batchId))),
                List.of(new TypeReference<Bool>() {})
            );
            String data = FunctionEncoder.encode(function);
            String from = adminCredentials != null ? adminCredentials.getAddress() : ZERO_ADDRESS;
            var response = web3j.ethCall(
                Transaction.createEthCallTransaction(from, rewardDistributorAddress, data),
                DefaultBlockParameterName.LATEST
            ).send();
            if (response == null) {
                return false;
            }
            if (response.isReverted()) {
                log.warn("Rollup 批次发放查询 reverted: batchId={}, reason={}", batchId, response.getRevertReason());
                return false;
            }
            if (response.getError() != null) {
                log.warn("Rollup 批次发放查询失败: batchId={}, err={}", batchId, response.getError().getMessage());
                return false;
            }
            List<Type> outputs = FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());
            if (outputs == null || outputs.isEmpty()) {
                return false;
            }
            Object value = outputs.get(0).getValue();
            return Boolean.TRUE.equals(value);
        } catch (Exception e) {
            log.warn("Rollup 批次发放查询异常: batchId={}, err={}", batchId, e.getMessage());
            return false;
        }
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
            log.warn("交易状态查询失败: txHash={}, err={}", txHash, e.getMessage());
            return TransactionState.UNKNOWN;
        }
    }

    private boolean isReady() {
        return web3j != null
            && adminCredentials != null
            && transactionManager != null
            && StringUtils.hasText(rewardDistributorAddress);
    }

    private String sendTransaction(Function function) throws Exception {
        String data = FunctionEncoder.encode(function);
        EthSendTransaction response = transactionManager.sendTransaction(
            gasPriceWei(),
            DEFAULT_GAS_LIMIT,
            rewardDistributorAddress,
            data,
            BigInteger.ZERO
        );
        if (response.hasError()) {
            throw new Exception("交易失败: " + response.getError().getMessage());
        }
        return response.getTransactionHash();
    }

    public boolean isTransactionMined(String txHash) {
        if (!StringUtils.hasText(txHash) || web3j == null) {
            return false;
        }
        try {
            return web3j.ethGetTransactionReceipt(txHash).send()
                .getTransactionReceipt()
                .map(receipt -> "0x1".equalsIgnoreCase(receipt.getStatus()))
                .orElse(false);
        } catch (Exception e) {
            log.warn("交易回执查询失败: txHash={}, err={}", txHash, e.getMessage());
            return false;
        }
    }

    public String cancelPendingTransaction(String txHash) throws Exception {
        return replacePendingTransaction(txHash, true);
    }

    public String replacePendingTransaction(String txHash) throws Exception {
        return replacePendingTransaction(txHash, false);
    }

    public String sendCancelTransaction(BigInteger nonce) throws Exception {
        if (nonce == null || web3j == null || adminCredentials == null) {
            return null;
        }
        RawTransaction raw = RawTransaction.createTransaction(
            nonce,
            gasPriceWei(),
            BigInteger.valueOf(21_000L),
            adminCredentials.getAddress(),
            BigInteger.ZERO,
            "0x"
        );
        byte[] signed = TransactionEncoder.signMessage(raw, polygonChainId, adminCredentials);
        EthSendTransaction response = web3j.ethSendRawTransaction(Numeric.toHexString(signed)).send();
        if (response.hasError()) {
            throw new Exception("发送取消交易失败: " + response.getError().getMessage());
        }
        return response.getTransactionHash();
    }

    private String replacePendingTransaction(String txHash, boolean cancel) throws Exception {
        if (!StringUtils.hasText(txHash) || web3j == null || adminCredentials == null) {
            return null;
        }
        EthTransaction txResponse = web3j.ethGetTransactionByHash(txHash).send();
        Optional<org.web3j.protocol.core.methods.response.Transaction> txOpt = txResponse.getTransaction();
        if (txOpt.isEmpty()) {
            return null;
        }
        org.web3j.protocol.core.methods.response.Transaction tx = txOpt.get();
        String blockNumberRaw = tx.getBlockNumberRaw();
        if (StringUtils.hasText(blockNumberRaw)) {
            return null;
        }
        if (!adminCredentials.getAddress().equalsIgnoreCase(tx.getFrom())) {
            return null;
        }
        BigInteger nonce = tx.getNonce();
        BigInteger gasLimit = cancel ? BigInteger.valueOf(21_000L) : tx.getGas();
        BigInteger value = cancel ? BigInteger.ZERO : tx.getValue();
        String to = cancel ? adminCredentials.getAddress() : tx.getTo();
        if (StringUtils.hasText(to) && !to.startsWith("0x")) {
            to = "0x" + to;
        }
        String data = cancel ? "0x" : tx.getInput();
        if (!StringUtils.hasText(data)) {
            data = "0x";
        }
        if (!data.startsWith("0x")) {
            data = "0x" + data;
        }

        RawTransaction raw = RawTransaction.createTransaction(
            nonce,
            gasPriceWei(),
            gasLimit,
            to,
            value,
            data
        );
        byte[] signed = TransactionEncoder.signMessage(raw, polygonChainId, adminCredentials);
        EthSendTransaction response = web3j.ethSendRawTransaction(Numeric.toHexString(signed)).send();
        if (response.hasError()) {
            throw new Exception("替换交易失败: " + response.getError().getMessage());
        }
        return response.getTransactionHash();
    }

    private BigInteger gasPriceWei() {
        if (polygonGasPriceGwei <= 0) {
            return BigInteger.ZERO;
        }
        return BigInteger.valueOf(polygonGasPriceGwei).multiply(BigInteger.valueOf(1_000_000_000L));
    }
}
