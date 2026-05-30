package com.wereen.competitionplatform.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Polygon Gas监控服务（使用 Ankr Polygon 主网 RPC）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlchemyGasMonitoringService {

    @Value("${blockchain.polygon.rpc-url}")
    private String alchemyRpcUrl;

    /**
     * 主网 WEE 代币地址（用于识别“我们的合约”）
     */
    @Value("${blockchain.mtk.token-address}")
    private String weeTokenAddress;

    /**
     * ForumTokenExtension 合约地址（用于识别“我们的合约”）
     */
    @Value("${blockchain.forum-token.extension-address}")
    private String forumTokenExtensionAddress;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 获取当前Gas费信息
     */
    public GasInfo getCurrentGasInfo() {
        try {
            log.info("开始获取Gas费信息（Ankr Polygon RPC），URL: {}", alchemyRpcUrl);

            Map<String, Object> payload = new HashMap<>();
            payload.put("jsonrpc", "2.0");
            payload.put("method", "eth_gasPrice");
            payload.put("params", new Object[]{});
            payload.put("id", 1);

            // 设置正确的请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

            log.info("发送 Ankr Polygon RPC 请求: {}", payload);

            ResponseEntity<String> response = restTemplate.postForEntity(alchemyRpcUrl, entity, String.class);
            log.info("收到 Ankr Polygon RPC 响应: {}", response.getBody());

            JsonNode jsonNode = objectMapper.readTree(response.getBody());
            String gasPriceHex = jsonNode.path("result").asText();
            log.info("Gas Price Hex: {}", gasPriceHex);

            if (gasPriceHex == null || gasPriceHex.isEmpty()) {
                throw new RuntimeException("Gas price result is null or empty");
            }

            BigDecimal gasPriceWei = new BigDecimal(new java.math.BigInteger(gasPriceHex.substring(2), 16));
            BigDecimal gasPriceGwei = gasPriceWei.divide(BigDecimal.valueOf(1e9), 6, BigDecimal.ROUND_HALF_UP);

            GasInfo result = GasInfo.builder()
                .gasPrice(gasPriceGwei)
                .timestamp(LocalDateTime.now())
                // 这里统一展示为 Polygon 主网，RPC 已经指向 Polygon 主网（Ankr）
                .network("Polygon Mainnet")
                .build();

            log.info("Gas费信息获取成功: {} Gwei", gasPriceGwei);
            return result;

        } catch (Exception e) {
            log.error("获取Gas费信息失败，URL: {}, 异常详情: ", alchemyRpcUrl, e);
            return null;
        }
    }

    /**
     * 获取Gas费估算
     */
    public GasEstimate estimateGas(String from, String to, String data) {
        try {
            String url = alchemyRpcUrl; // URL已经包含了API Key

            Map<String, Object> payload = new HashMap<>();
            payload.put("jsonrpc", "2.0");
            payload.put("method", "eth_estimateGas");

            Map<String, Object> params = new HashMap<>();
            params.put("from", from);
            params.put("to", to);
            params.put("data", data);

            payload.put("params", new Object[]{params});
            payload.put("id", 1);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            JsonNode jsonNode = objectMapper.readTree(response.getBody());

            String gasLimitHex = jsonNode.path("result").asText();
            Long gasLimit = new java.math.BigInteger(gasLimitHex.substring(2), 16).longValue();

            BigDecimal gasPrice = getCurrentGasInfo().getGasPrice();
            BigDecimal estimatedFee = BigDecimal.valueOf(gasLimit).multiply(gasPrice);

            return GasEstimate.builder()
                .gasLimit(gasLimit)
                .estimatedGasFee(estimatedFee)
                .timestamp(LocalDateTime.now())
                .build();

        } catch (Exception e) {
            log.error("估算Gas费失败", e);
            return null;
        }
    }

    /**
     * 获取交易详情（包含Gas费信息）
     */
    public TransactionDetail getTransactionDetail(String txHash) {
        try {
            String url = alchemyRpcUrl; // URL已经包含了API Key

            Map<String, Object> payload = new HashMap<>();
            payload.put("jsonrpc", "2.0");
            payload.put("method", "eth_getTransactionReceipt");
            payload.put("params", new Object[]{txHash});
            payload.put("id", 1);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            JsonNode jsonNode = objectMapper.readTree(response.getBody());

            JsonNode result = jsonNode.path("result");
            if (result.isNull()) {
                return null;
            }

            String gasUsedHex = result.path("gasUsed").asText();
            String gasPriceHex = result.path("effectiveGasPrice").asText();

            Long gasUsed = new java.math.BigInteger(gasUsedHex.substring(2), 16).longValue();
            BigDecimal gasPriceWei = new BigDecimal(new java.math.BigInteger(gasPriceHex.substring(2), 16));
            BigDecimal gasPriceGwei = gasPriceWei.divide(BigDecimal.valueOf(1e9));
            BigDecimal gasFeeEth = gasPriceWei.multiply(BigDecimal.valueOf(gasUsed)).divide(BigDecimal.valueOf(1e18));

            return TransactionDetail.builder()
                .txHash(txHash)
                .gasUsed(gasUsed)
                .gasPrice(gasPriceGwei)
                .gasFeeEth(gasFeeEth)
                .blockNumber(new java.math.BigInteger(result.path("blockNumber").asText().substring(2), 16).longValue())
                .success(result.path("status").asText().equals("0x1"))
                .timestamp(LocalDateTime.now())
                .build();

        } catch (Exception e) {
            log.error("获取交易详情失败: {}", txHash, e);
            return null;
        }
    }

    /**
     * 处理 Gas 关联 Webhook 事件（保留接口占位，目前主要依赖 Ankr RPC 轮询）
     */
    public void handleWebhookEvent(JsonNode webhookEvent) {
        try {
            String eventType = webhookEvent.path("type").asText();
            JsonNode activity = webhookEvent.path("activity").get(0);

            if (activity != null) {
                String txHash = activity.path("hash").asText();
                String fromAddress = activity.path("fromAddress").asText();
                String toAddress = activity.path("toAddress").asText();
                String contractAddress = activity.has("rawContract") ?
                    activity.path("rawContract").path("address").asText() : null;

                log.info("收到 Gas Webhook 事件: type={}, txHash={}, from={}, to={}, contract={}",
                    eventType, txHash, fromAddress, toAddress, contractAddress);

                // 如果是我们的合约交易，获取详细Gas信息
                if (isOurContract(toAddress) || isOurContract(contractAddress)) {
                    TransactionDetail detail = getTransactionDetail(txHash);
                    if (detail != null) {
                        // 保存Gas费记录到数据库
                        saveGasTransaction(detail, fromAddress, toAddress, contractAddress);
                    }
                }
            }

        } catch (Exception e) {
            log.error("处理 Gas Webhook 事件失败", e);
        }
    }

    private boolean isOurContract(String address) {
        if (address == null || address.isEmpty()) return false;

        // 使用配置中的主网合约地址来判断是否为“我们关心的交易”
        if (weeTokenAddress != null && address.equalsIgnoreCase(weeTokenAddress)) {
            return true;
        }
        if (forumTokenExtensionAddress != null && address.equalsIgnoreCase(forumTokenExtensionAddress)) {
            return true;
        }
        return false;
    }

    private void saveGasTransaction(TransactionDetail detail, String from, String to, String contract) {
        try {
            // 这里可以保存到数据库
            // gasTransactionService.save(new GasTransaction(...));

            log.info("保存Gas交易记录: hash={}, gasFee={}, success={}",
                detail.getTxHash(), detail.getGasFeeEth(), detail.getSuccess());

        } catch (Exception e) {
            log.error("保存Gas交易记录失败", e);
        }
    }

    // 数据类定义
    @Data
    @lombok.Builder
    public static class GasInfo {
        private BigDecimal gasPrice;
        private LocalDateTime timestamp;
        private String network;
    }

    @Data
    @lombok.Builder
    public static class GasEstimate {
        private Long gasLimit;
        private BigDecimal estimatedGasFee;
        private LocalDateTime timestamp;
    }

    @Data
    @lombok.Builder
    public static class TransactionDetail {
        private String txHash;
        private Long gasUsed;
        private BigDecimal gasPrice;
        private BigDecimal gasFeeEth;
        private Long blockNumber;
        private Boolean success;
        private LocalDateTime timestamp;
    }
}
