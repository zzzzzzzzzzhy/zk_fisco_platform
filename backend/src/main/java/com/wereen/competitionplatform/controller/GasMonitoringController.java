package com.wereen.competitionplatform.controller;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.wereen.competitionplatform.common.Result;
import com.wereen.competitionplatform.model.entity.GasTransaction;
import com.wereen.competitionplatform.service.AlchemyGasMonitoringService;
import com.wereen.competitionplatform.service.GasTransactionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Gas费监控控制器
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/gas")
@Tag(name = "Gas费监控", description = "Gas费实时监控相关接口")
public class GasMonitoringController {

    private final AlchemyGasMonitoringService alchemyGasMonitoringService;
    private final GasTransactionService gasTransactionService;

    /**
     * 接收链上 Gas Webhook 事件（目前主要使用 Ankr RPC 查询）
     */
    @PostMapping("/webhook")
    @Operation(summary = "Gas Webhook", description = "接收链上交易事件用于Gas统计（预留接口）")
    public ResponseEntity<String> handleWebhook(@RequestBody JsonNode webhookEvent) {
        try {
            log.info("收到 Gas Webhook 事件: {}", webhookEvent);

            alchemyGasMonitoringService.handleWebhookEvent(webhookEvent);

            return ResponseEntity.ok("success");
        } catch (Exception e) {
            log.error("处理Webhook事件失败", e);
            return ResponseEntity.status(500).body("error");
        }
    }

    /**
     * 获取当前Gas费信息
     */
    @GetMapping("/current")
    @Operation(summary = "当前Gas费", description = "获取当前网络Gas费信息")
    public Result<Map<String, Object>> getCurrentGasInfo() {
        try {
            AlchemyGasMonitoringService.GasInfo gasInfo = alchemyGasMonitoringService.getCurrentGasInfo();

            if (gasInfo != null) {
                Map<String, Object> result = new HashMap<>();
                result.put("gasPrice", gasInfo.getGasPrice());
                result.put("timestamp", gasInfo.getTimestamp());
                result.put("network", gasInfo.getNetwork());

                log.info("成功获取Gas费信息: {}", result);
                return Result.success(result);
            } else {
                return Result.error("Gas费信息获取失败：返回空值");
            }
        } catch (Exception e) {
            log.error("获取Gas费信息失败", e);
            return Result.error("获取Gas费信息失败: " + e.getMessage());
        }
    }

    /**
     * 估算交易Gas费
     */
    @PostMapping("/estimate")
    @Operation(summary = "估算Gas费", description = "估算指定交易的Gas费用")
    public Result<Map<String, Object>> estimateGas(@RequestBody Map<String, String> request) {
        try {
            String from = request.get("from");
            String to = request.get("to");
            String data = request.get("data");

            AlchemyGasMonitoringService.GasEstimate estimate =
                alchemyGasMonitoringService.estimateGas(from, to, data);

            Map<String, Object> result = new HashMap<>();
            result.put("gasLimit", estimate.getGasLimit());
            result.put("estimatedGasFee", estimate.getEstimatedGasFee());
            result.put("timestamp", estimate.getTimestamp());

            return Result.success(result);
        } catch (Exception e) {
            log.error("估算Gas费失败", e);
            return Result.error("估算Gas费失败: " + e.getMessage());
        }
    }

    /**
     * 最近 Gas 交易记录（用于表格展示）
     */
    @GetMapping("/transactions")
    @Operation(summary = "最近 Gas 交易记录", description = "获取最近的 Polygon Gas 交易记录")
    public Result<List<Map<String, Object>>> listRecentTransactions() {
        List<GasTransaction> list = gasTransactionService.listRecentTransactions(50);
        List<Map<String, Object>> result = list.stream().map(tx -> {
            Map<String, Object> m = new HashMap<>();
            m.put("txHash", tx.getTxHash());
            m.put("fromAddress", tx.getFromAddress());
            m.put("toAddress", tx.getToAddress());
            m.put("gasPrice", tx.getGasPriceGwei());
            m.put("gasUsed", tx.getGasUsed());
            m.put("gasFeeEth", tx.getGasFeeMatic());
            m.put("success", tx.getSuccess());
            m.put("timestamp", tx.getCreateTime());
            m.put("bizType", tx.getBizType());
            return m;
        }).collect(Collectors.toList());
        return Result.success(result);
    }

    /**
     * 获取交易详情
     */
    @GetMapping("/transaction/{txHash}")
    @Operation(summary = "交易详情", description = "获取指定交易的详细信息，包含Gas费用")
    public Result<Map<String, Object>> getTransactionDetail(@PathVariable String txHash) {
        try {
            AlchemyGasMonitoringService.TransactionDetail detail =
                alchemyGasMonitoringService.getTransactionDetail(txHash);

            if (detail == null) {
                return Result.error("交易不存在或未确认");
            }

            Map<String, Object> result = new HashMap<>();
            result.put("txHash", detail.getTxHash());
            result.put("gasUsed", detail.getGasUsed());
            result.put("gasPrice", detail.getGasPrice());
            result.put("gasFeeEth", detail.getGasFeeEth());
            result.put("blockNumber", detail.getBlockNumber());
            result.put("success", detail.getSuccess());
            result.put("timestamp", detail.getTimestamp());

            return Result.success(result);
        } catch (Exception e) {
            log.error("获取交易详情失败: {}", txHash, e);
            return Result.error("获取交易详情失败: " + e.getMessage());
        }
    }

    /**
     * 24 小时 Gas 费用趋势
     */
    @GetMapping("/stats/24h-trend")
    @Operation(summary = "24小时Gas趋势", description = "最近24小时内每小时的Gas费用")
    public Result<Map<String, Object>> get24hTrend() {
        List<GasTransactionService.HourlyGasStat> stats = gasTransactionService.getLast24HoursTrend();
        List<String> labels = stats.stream()
            .map(GasTransactionService.HourlyGasStat::getHourLabel)
            .collect(Collectors.toList());
        List<BigDecimal> values = stats.stream()
            .map(GasTransactionService.HourlyGasStat::getTotalGasFee)
            .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("labels", labels);
        result.put("values", values);
        return Result.success(result);
    }

    /**
     * Gas 费用按业务类型分布
     */
    @GetMapping("/stats/distribution")
    @Operation(summary = "Gas费用分布", description = "最近24小时内不同业务类型的Gas费用分布")
    public Result<List<Map<String, Object>>> getDistribution() {
        List<GasTransactionService.BizTypeStat> stats = gasTransactionService.getBizTypeDistribution(24);
        List<Map<String, Object>> result = stats.stream().map(s -> {
            Map<String, Object> m = new HashMap<>();
            m.put("bizType", s.getBizType());
            m.put("label", mapBizTypeLabel(s.getBizType()));
            m.put("value", s.getTotalGasFee());
            return m;
        }).collect(Collectors.toList());
        return Result.success(result);
    }

    private String mapBizTypeLabel(String bizType) {
        if ("CHECKIN".equalsIgnoreCase(bizType)) {
            return "签到";
        }
        if ("POST".equalsIgnoreCase(bizType)) {
            return "发帖/内容奖励";
        }
        if ("COMMENT".equalsIgnoreCase(bizType)) {
            return "评论奖励";
        }
        if ("TIP".equalsIgnoreCase(bizType)) {
            return "打赏";
        }
        if ("PIN".equalsIgnoreCase(bizType)) {
            return "置顶";
        }
        return "其他";
    }

    /**
     * 测试 Ankr Polygon RPC 连接
     */
    @GetMapping("/test")
    @Operation(summary = "测试连接", description = "测试 Ankr Polygon RPC 连接")
    public Result<String> testConnection() {
        try {
            log.info("开始测试 Ankr Polygon RPC 连接...");

            AlchemyGasMonitoringService.GasInfo gasInfo = alchemyGasMonitoringService.getCurrentGasInfo();
            log.info("GasInfo获取结果: {}", gasInfo);

            if (gasInfo != null) {
                String message = "Ankr Polygon RPC 连接正常，当前Gas费: " + gasInfo.getGasPrice() + " Gwei, 网络: " + gasInfo.getNetwork();
                log.info("连接测试成功: {}", message);
                return Result.success(message);
            } else {
                String error = "Ankr Polygon RPC 返回空结果";
                log.error("连接测试失败: {}", error);
                return Result.error(error);
            }
        } catch (Exception e) {
            log.error("测试 Ankr Polygon RPC 连接失败，异常详情: ", e);
            return Result.error("连接测试失败: " + e.getMessage());
        }
    }
}