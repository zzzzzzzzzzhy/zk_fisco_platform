package com.wereen.competitionplatform.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.SecureUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wereen.competitionplatform.exception.BusinessException;
import com.wereen.competitionplatform.mapper.DisbursementBatchMapper;
import com.wereen.competitionplatform.model.entity.DisbursementBatch;
import com.wereen.competitionplatform.model.entity.PrizeAllocation;
import com.wereen.competitionplatform.model.entity.PrizePool;
import com.wereen.competitionplatform.model.entity.UserKyc;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 发放批次服务
 *
 * 功能：
 * 1. 创建发放批次（从待发放队列）
 * 2. 提交银行转账批次
 * 3. 查询批次状态
 * 4. 处理批次完成回调
 * 5. 重试失败发放
 * 6. 生成Merkle树根（用于验证批次完整性）
 * 7. 存证到区块链
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DisbursementService {

    private final DisbursementBatchMapper batchMapper;
    private final PrizeAllocationService allocationService;
    private final PrizePoolService prizePoolService;
    private final UserKycService kycService;

    /**
     * 创建发放批次
     *
     * @param poolId 奖金池ID
     * @param operatorId 操作员ID
     * @return 批次记录
     */
    @Transactional(rollbackFor = Exception.class)
    public DisbursementBatch createBatch(Long poolId, Long operatorId) {
        // 获取奖金池
        PrizePool pool = prizePoolService.getPrizePool(poolId);
        if (pool == null) {
            throw new BusinessException("奖金池不存在");
        }

        // 获取待发放的分配记录
        List<PrizeAllocation> pendingAllocations = allocationService.getPendingDisbursements(poolId);
        if (pendingAllocations.isEmpty()) {
            throw new BusinessException("没有待发放的奖金记录");
        }

        // 生成批次编号
        String batchNo = generateBatchNo(poolId);

        // 计算批次总额和总数
        int totalCount = pendingAllocations.size();
        Long totalAmount = pendingAllocations.stream()
                .mapToLong(PrizeAllocation::getActualAmount)
                .sum();

        // 创建批次记录
        DisbursementBatch batch = new DisbursementBatch();
        batch.setPoolId(poolId);
        batch.setBatchNo(batchNo);
        batch.setTotalCount(totalCount);
        batch.setTotalAmount(totalAmount);
        batch.setSuccessCount(0);
        batch.setFailedCount(0);
        batch.setStatus("PENDING");
        batch.setOperatorId(operatorId);
        batch.setDeleted(0);

        batchMapper.insert(batch);

        // 更新分配记录的批次ID
        List<Long> allocationIds = pendingAllocations.stream()
                .map(PrizeAllocation::getId)
                .collect(Collectors.toList());
        allocationService.updateBatchId(allocationIds, batch.getId());

        log.info("创建发放批次: batchNo={}, poolId={}, totalCount={}, totalAmount={}",
                batchNo, poolId, totalCount, totalAmount);

        return batch;
    }

    /**
     * 提交批次到银行
     *
     * @param batchId 批次ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void submitBatch(Long batchId) {
        DisbursementBatch batch = batchMapper.selectById(batchId);
        if (batch == null) {
            throw new BusinessException("批次不存在");
        }

        if (!"PENDING".equals(batch.getStatus())) {
            throw new BusinessException("批次状态不正确，无法提交");
        }

        // 获取批次下的所有分配记录
        List<PrizeAllocation> allocations = getAllocationsByBatch(batchId);
        if (allocations.isEmpty()) {
            throw new BusinessException("批次下没有分配记录");
        }

        // 构建银行转账明细
        List<BankTransferItem> transferItems = buildBankTransferItems(allocations);

        // 计算Merkle树根
        String merkleRoot = calculateBatchMerkleRoot(transferItems);

        // TODO: 后期扩展 - 提交到银行批量转账接口
        // BankBatchResponse response = bankService.submitBatch(transferItems);
        // String bankBatchNo = response.getBatchNo();

        // 模拟银行批次号
        String bankBatchNo = "BANK-" + IdUtil.simpleUUID().substring(0, 12).toUpperCase();

        batch.setStatus("PROCESSING");
        batch.setSubmittedAt(LocalDateTime.now());
        batch.setBankBatchNo(bankBatchNo);
        batch.setMerkleRoot(merkleRoot);

        batchMapper.updateById(batch);

        log.info("提交批次到银行: batchId={}, batchNo={}, bankBatchNo={}, merkleRoot={}",
                batchId, batch.getBatchNo(), bankBatchNo, merkleRoot);

        // TODO: 后期扩展 - 存证到区块链
        // String chainTxHash = storeToBlockchain(batch);
        // batch.setChainTxHash(chainTxHash);
        // batchMapper.updateById(batch);
    }

    /**
     * 查询批次状态（从银行）
     *
     * @param batchId 批次ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void queryBatchStatus(Long batchId) {
        DisbursementBatch batch = batchMapper.selectById(batchId);
        if (batch == null) {
            throw new BusinessException("批次不存在");
        }

        if (!"PROCESSING".equals(batch.getStatus())) {
            log.warn("批次状态不是PROCESSING，无需查询: batchId={}, status={}", batchId, batch.getStatus());
            return;
        }

        // TODO: 后期扩展 - 调用银行API查询批次状态
        // BankBatchStatusResponse response = bankService.queryBatchStatus(batch.getBankBatchNo());
        // processBankResponse(batchId, response);

        log.info("查询批次状态: batchId={}, bankBatchNo={}", batchId, batch.getBankBatchNo());
    }

    /**
     * 处理批次完成回调
     *
     * @param batchId 批次ID
     * @param successCount 成功数量
     * @param failedCount 失败数量
     * @param failedAllocationIds 失败的分配记录ID列表
     */
    @Transactional(rollbackFor = Exception.class)
    public void onBatchCompleted(Long batchId, Integer successCount, Integer failedCount,
                                 List<Long> failedAllocationIds) {
        DisbursementBatch batch = batchMapper.selectById(batchId);
        if (batch == null) {
            throw new BusinessException("批次不存在");
        }

        batch.setSuccessCount(successCount);
        batch.setFailedCount(failedCount);
        batch.setCompletedAt(LocalDateTime.now());

        // 确定最终状态
        if (failedCount == 0) {
            batch.setStatus("COMPLETED");
        } else if (successCount == 0) {
            batch.setStatus("FAILED");
        } else {
            batch.setStatus("PARTIAL");
        }

        batchMapper.updateById(batch);

        // 更新分配记录状态
        List<PrizeAllocation> allocations = getAllocationsByBatch(batchId);
        for (PrizeAllocation allocation : allocations) {
            if (failedAllocationIds != null && failedAllocationIds.contains(allocation.getId())) {
                allocationService.markFailed(allocation.getId(), "银行转账失败");
            } else {
                allocationService.markDisbursed(allocation.getId());

                // 更新奖金池已发放金额
                prizePoolService.updateDisbursedAmount(batch.getPoolId(), allocation.getActualAmount());
            }
        }

        log.info("批次完成: batchId={}, status={}, success={}, failed={}",
                batchId, batch.getStatus(), successCount, failedCount);
    }

    /**
     * 手动标记批次完成（用于测试/模拟）
     *
     * @param batchId 批次ID
     * @param allSuccess 是否全部成功
     */
    @Transactional(rollbackFor = Exception.class)
    public void manualCompleteBatch(Long batchId, boolean allSuccess) {
        DisbursementBatch batch = batchMapper.selectById(batchId);
        if (batch == null) {
            throw new BusinessException("批次不存在");
        }

        List<PrizeAllocation> allocations = getAllocationsByBatch(batchId);
        int totalCount = allocations.size();

        if (allSuccess) {
            onBatchCompleted(batchId, totalCount, 0, null);
        } else {
            // 模拟部分失败（第一条记录失败）
            List<Long> failedIds = allocations.isEmpty() ? null :
                    List.of(allocations.get(0).getId());
            onBatchCompleted(batchId, totalCount - 1, 1, failedIds);
        }

        log.info("手动标记批次完成: batchId={}, allSuccess={}", batchId, allSuccess);
    }

    /**
     * 重试失败的发放
     *
     * @param batchId 原批次ID
     * @param operatorId 操作员ID
     * @return 新批次记录
     */
    @Transactional(rollbackFor = Exception.class)
    public DisbursementBatch retryFailedDisbursements(Long batchId, Long operatorId) {
        // 获取原批次
        DisbursementBatch originalBatch = batchMapper.selectById(batchId);
        if (originalBatch == null) {
            throw new BusinessException("原批次不存在");
        }

        // 获取失败的分配记录
        List<PrizeAllocation> failedAllocations = getFailedAllocationsByBatch(batchId);
        if (failedAllocations.isEmpty()) {
            throw new BusinessException("没有失败的发放记录");
        }

        // 重置分配记录状态为待发放
        for (PrizeAllocation allocation : failedAllocations) {
            allocation.setStatus("QUEUE_BATCH");
            allocation.setBatchId(null);
            // 通过allocationMapper更新，这里简化处理
        }

        // 创建新批次
        return createBatch(originalBatch.getPoolId(), operatorId);
    }

    /**
     * 获取批次记录
     *
     * @param batchId 批次ID
     * @return 批次记录
     */
    public DisbursementBatch getBatch(Long batchId) {
        return batchMapper.selectById(batchId);
    }

    /**
     * 获取奖金池的所有批次
     *
     * @param poolId 奖金池ID
     * @return 批次列表
     */
    public List<DisbursementBatch> getBatchesByPool(Long poolId) {
        LambdaQueryWrapper<DisbursementBatch> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DisbursementBatch::getPoolId, poolId)
                .eq(DisbursementBatch::getDeleted, 0)
                .orderByDesc(DisbursementBatch::getCreatedAt);

        return batchMapper.selectList(wrapper);
    }

    /**
     * 获取处理中的批次（定时任务用于查询状态）
     *
     * @return 批次列表
     */
    public List<DisbursementBatch> getProcessingBatches() {
        LambdaQueryWrapper<DisbursementBatch> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DisbursementBatch::getStatus, "PROCESSING")
                .eq(DisbursementBatch::getDeleted, 0);

        return batchMapper.selectList(wrapper);
    }

    // ==================== 私有方法 ====================

    /**
     * 生成批次编号
     *
     * 格式: BATCH-{poolId}-{yyyyMMddHHmmss}-{随机4位}
     */
    private String generateBatchNo(Long poolId) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String randomStr = IdUtil.randomUUID().substring(0, 4).toUpperCase();
        return String.format("BATCH-%d-%s-%s", poolId, timestamp, randomStr);
    }

    /**
     * 获取批次下的所有分配记录
     */
    private List<PrizeAllocation> getAllocationsByBatch(Long batchId) {
        return allocationService.getAllocationsByBatch(batchId);
    }

    /**
     * 获取批次下失败的分配记录
     */
    private List<PrizeAllocation> getFailedAllocationsByBatch(Long batchId) {
        return allocationService.getFailedAllocationsByBatch(batchId);
    }

    /**
     * 构建银行转账明细
     */
    private List<BankTransferItem> buildBankTransferItems(List<PrizeAllocation> allocations) {
        List<BankTransferItem> items = new ArrayList<>();

        for (PrizeAllocation allocation : allocations) {
            // 获取用户KYC信息
            UserKyc kyc = kycService.getUserKyc(allocation.getUserId());
            if (kyc == null) {
                log.error("用户KYC信息不存在: userId={}", allocation.getUserId());
                continue;
            }

            BankTransferItem item = new BankTransferItem();
            item.setAllocationId(allocation.getId());
            item.setUserId(allocation.getUserId());
            item.setRealName(kyc.getRealName());
            // 注意：实际使用时需要解密银行卡号
            item.setBankCardNumber(kyc.getBankCardNumber()); // 加密的
            item.setBankName(kyc.getBankName());
            item.setBankBranch(kyc.getBankBranch());
            item.setAmount(allocation.getActualAmount());
            item.setRemark(String.format("奖金发放-%s", allocation.getAllocationNo()));

            items.add(item);
        }

        return items;
    }

    /**
     * 计算批次Merkle树根
     */
    private String calculateBatchMerkleRoot(List<BankTransferItem> items) {
        if (items.isEmpty()) {
            return SecureUtil.sha256("");
        }

        // 将每个转账项哈希化
        List<String> leaves = items.stream()
                .map(item -> SecureUtil.sha256(
                        String.format("%d|%s|%s|%d",
                                item.getAllocationId(),
                                item.getRealName(),
                                item.getBankCardNumber(),
                                item.getAmount())
                ))
                .collect(Collectors.toList());

        // 构建Merkle树
        return buildMerkleTree(leaves);
    }

    /**
     * 构建Merkle树（简化版）
     */
    private String buildMerkleTree(List<String> leaves) {
        if (leaves.isEmpty()) {
            return "";
        }

        if (leaves.size() == 1) {
            return leaves.get(0);
        }

        List<String> newLevel = new ArrayList<>();

        for (int i = 0; i < leaves.size(); i += 2) {
            String left = leaves.get(i);
            String right = (i + 1 < leaves.size()) ? leaves.get(i + 1) : left;
            String combined = SecureUtil.sha256(left + right);
            newLevel.add(combined);
        }

        return buildMerkleTree(newLevel);
    }

    // ==================== 内部类 ====================

    /**
     * 银行转账明细项
     */
    @lombok.Data
    private static class BankTransferItem {
        /**
         * 分配记录ID
         */
        private Long allocationId;

        /**
         * 用户ID
         */
        private Long userId;

        /**
         * 真实姓名
         */
        private String realName;

        /**
         * 银行卡号
         */
        private String bankCardNumber;

        /**
         * 开户行
         */
        private String bankName;

        /**
         * 开户支行
         */
        private String bankBranch;

        /**
         * 转账金额（分）
         */
        private Long amount;

        /**
         * 备注
         */
        private String remark;
    }
}
