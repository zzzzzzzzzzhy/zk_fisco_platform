package com.wereen.competitionplatform.consumer;

import com.wereen.competitionplatform.mapper.ChainProofMapper;
import com.wereen.competitionplatform.mapper.PrizeBatchItemMapper;
import com.wereen.competitionplatform.mapper.PrizeBatchMapper;
import com.wereen.competitionplatform.model.entity.ChainProof;
import com.wereen.competitionplatform.model.entity.PrizeBatch;
import com.wereen.competitionplatform.model.entity.PrizeBatchItem;
import com.wereen.competitionplatform.service.BlockchainService;
import com.wereen.competitionplatform.service.WalletService;
import com.wereen.competitionplatform.util.MerkleTreeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fisco.bcos.sdk.model.TransactionReceipt;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 奖金入账消费者
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PrizeBookkeepingConsumer implements StreamListener<String, ObjectRecord<String, Map<String, String>>> {

    private final WalletService walletService;
    private final PrizeBatchItemMapper prizeBatchItemMapper;
    private final PrizeBatchMapper prizeBatchMapper;
    private final BlockchainService blockchainService;
    private final ChainProofMapper chainProofMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onMessage(ObjectRecord<String, Map<String, String>> record) {
        try {
            Map<String, String> taskData = record.getValue();
            Long batchId = Long.parseLong(taskData.get("batchId"));

            log.info("处理奖金入账任务: batchId={}", batchId);

            // 查询批次记录
            PrizeBatch batch = prizeBatchMapper.selectById(batchId);
            if (batch == null) {
                log.error("奖金批次不存在: batchId={}", batchId);
                return;
            }

            // 查询批次明细
            List<PrizeBatchItem> items = prizeBatchItemMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PrizeBatchItem>()
                            .eq(PrizeBatchItem::getBatchId, batchId)
                            .eq(PrizeBatchItem::getStatus, "PENDING")
            );

            if (items.isEmpty()) {
                log.info("没有待处理的奖金明细: batchId={}", batchId);
                return;
            }

            // 逐个处理入账
            int successCount = 0;
            int failedCount = 0;

            for (PrizeBatchItem item : items) {
                try {
                    // 调用钱包服务增加余额
                    walletService.increaseBalance(
                            item.getUserId(),
                            "CNY",
                            item.getAmount(),
                            "batch:" + batchId,
                            String.format("竞赛奖金入账，排名：%d", item.getRank())
                    );

                    // 更新明细状态为成功
                    item.setStatus("SUCCESS");
                    prizeBatchItemMapper.updateById(item);
                    successCount++;

                    log.info("奖金入账成功: userId={}, amount={}", item.getUserId(), item.getAmount());

                } catch (Exception e) {
                    log.error("奖金入账失败: itemId={}", item.getId(), e);
                    item.setStatus("FAILED");
                    item.setReason(e.getMessage());
                    prizeBatchItemMapper.updateById(item);
                    failedCount++;
                }
            }

            log.info("奖金入账处理完成: batchId={}, 成功={}, 失败={}", batchId, successCount, failedCount);

            // ========== 奖金批次上链处理 ==========
            try {
                // 1. 查询该批次的所有明细（包括成功和失败的）
                List<PrizeBatchItem> allItems = prizeBatchItemMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PrizeBatchItem>()
                        .eq(PrizeBatchItem::getBatchId, batchId)
                        .orderByAsc(PrizeBatchItem::getRank)
                );

                // 2. 计算获奖者列表的 Merkle Root（确保数据不可篡改）
                List<String> winnerData = allItems.stream()
                    .map(item -> String.format("%d|%d|%d|%s",
                        item.getUserId(),
                        item.getRank(),
                        item.getAmount(),
                        item.getStatus()))
                    .collect(Collectors.toList());

                String winnersRoot = MerkleTreeUtil.calculateMerkleRoot(winnerData);
                log.info("计算奖金批次Merkle Root: batchId={}, merkleRoot={}", batchId, winnersRoot);

                // 3. 更新批次的 Merkle Root
                batch.setMerkleRoot(winnersRoot);

                // 4. 构造元数据（JSON格式）
                String metadata = String.format(
                    "{\"batchNo\":\"%s\",\"competitionId\":%d,\"totalAmount\":%d,\"winnersCount\":%d,\"successCount\":%d,\"failedCount\":%d,\"timestamp\":\"%s\"}",
                    batch.getBatchNo(),
                    batch.getCompetitionId(),
                    batch.getTotalAmount(),
                    batch.getWinnersCount(),
                    successCount,
                    failedCount,
                    LocalDateTime.now().toString()
                );

                // 5. 调用区块链服务上链
                TransactionReceipt receipt = blockchainService.recordPrizeBatch(
                    batch.getBatchNo(),
                    winnersRoot,
                    batch.getTotalAmount(),
                    "CNY",
                    metadata
                );

                // 6. 解析交易回执
                String txHash = receipt.getTransactionHash();
                Long blockHeight = Long.valueOf(receipt.getBlockNumber());
                LocalDateTime blockTime = LocalDateTime.now();

                // 7. 保存链上存证记录
                ChainProof chainProof = new ChainProof();
                chainProof.setBizType("PRIZE_BATCH");
                chainProof.setBizId(batchId);
                chainProof.setDataHash(winnersRoot); // 使用 Merkle Root 作为数据哈希
                chainProof.setTxHash(txHash);
                chainProof.setBlockHeight(blockHeight);
                chainProof.setBlockTime(blockTime);
                chainProof.setMetadata(metadata);
                chainProof.setStatus(2); // 已上链
                chainProofMapper.insert(chainProof);

                // 8. 更新批次记录的链上状态
                batch.setChainTxHash(txHash);
                batch.setBlockHeight(blockHeight);
                batch.setBlockTime(blockTime);
                batch.setStatus("ACCOUNTED"); // 更新批次状态为已入账
                batch.setAccountedAt(LocalDateTime.now());
                prizeBatchMapper.updateById(batch);

                log.info("奖金批次上链成功: batchId={}, txHash={}, blockHeight={}",
                    batchId, txHash, blockHeight);

            } catch (Exception e) {
                log.error("奖金批次上链失败: batchId={}", batchId, e);
                // 即使上链失败，入账已完成，不影响用户余额
            }

            log.info("奖金入账任务完成: batchId={}", batchId);

        } catch (Exception e) {
            log.error("处理奖金入账任务失败", e);
            throw new RuntimeException("奖金入账任务失败: " + e.getMessage(), e);
        }
    }
}
