package com.wereen.competitionplatform.service;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wereen.competitionplatform.exception.BusinessException;
import com.wereen.competitionplatform.mapper.PrizeAllocationMapper;
import com.wereen.competitionplatform.model.entity.*;
import com.wereen.competitionplatform.util.TaxCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 奖金分配服务
 *
 * 功能：
 * 1. 根据榜单排名和分配方案分配奖金
 * 2. 触发KYC通知
 * 3. 管理奖金分配状态流转
 * 4. 处理KYC完成回调
 * 5. 处理风控完成回调
 * 6. 奖金作废（超时未完成KYC）
 *
 * 状态流转：
 * ALLOCATED → KYC_NOTIFIED → KYC_PENDING → KYC_APPROVED →
 * RISK_PENDING → RISK_PASSED → QUEUE_BATCH → DISBURSING →
 * COMPLETED / FAILED / FORFEITED
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrizeAllocationService {

    private final PrizeAllocationMapper allocationMapper;
    private final PrizePoolService prizePoolService;
    private final UserKycService kycService;
    private final RiskControlService riskControlService;

    /**
     * KYC截止天数（榜单公示结束后）
     */
    private static final int KYC_DEADLINE_DAYS = 30;

    /**
     * 根据榜单排名批量分配奖金
     *
     * @param poolId 奖金池ID
     * @param userRankList 用户排名列表
     * @return 分配记录列表
     */
    @Transactional(rollbackFor = Exception.class)
    public List<PrizeAllocation> batchAllocate(Long poolId, List<UserRankDTO> userRankList) {
        // 获取奖金池
        PrizePool pool = prizePoolService.getPrizePool(poolId);
        if (pool == null) {
            throw new BusinessException("奖金池不存在");
        }

        if (pool.getLocked() != 1) {
            throw new BusinessException("奖金池未锁定，无法分配");
        }

        // 获取分配方案
        List<PrizeAllocationScheme> schemes = prizePoolService.getSchemeList(poolId);
        if (schemes.isEmpty()) {
            throw new BusinessException("未配置分配方案");
        }

        // 构建排名区间映射
        Map<Integer, PrizeAllocationScheme> rankSchemeMap = buildRankSchemeMap(schemes);

        List<PrizeAllocation> allocations = new ArrayList<>();
        Long totalAllocatedAmount = 0L;

        for (UserRankDTO userRank : userRankList) {
            // 查找对应的分配方案
            PrizeAllocationScheme scheme = rankSchemeMap.get(userRank.getRank());
            if (scheme == null) {
                log.warn("排名 {} 未找到对应的分配方案，跳过", userRank.getRank());
                continue;
            }

            // 检查是否已分配
            PrizeAllocation existing = getAllocationByPoolAndUser(poolId, userRank.getUserId());
            if (existing != null) {
                log.warn("用户 {} 已分配奖金，跳过", userRank.getUserId());
                continue;
            }

            // 计算税后金额
            Long prizeAmount = scheme.getPrizeAmountPerUser();
            TaxCalculator.TaxResult taxResult = TaxCalculator.calculate(prizeAmount);

            // 创建分配记录
            PrizeAllocation allocation = new PrizeAllocation();
            allocation.setPoolId(poolId);
            allocation.setSchemeId(scheme.getId());
            allocation.setUserId(userRank.getUserId());
            allocation.setLeaderboardId(pool.getLeaderboardId());
            allocation.setAllocationNo(generateAllocationNo(poolId, userRank.getUserId()));
            allocation.setRank(userRank.getRank());
            allocation.setPrizeAmount(prizeAmount);
            allocation.setTaxAmount(taxResult.getTaxAmount());
            allocation.setActualAmount(taxResult.getActualAmount());
            allocation.setStatus("ALLOCATED");
            allocation.setKycDeadline(LocalDateTime.now().plusDays(KYC_DEADLINE_DAYS));
            allocation.setDeleted(0);

            allocationMapper.insert(allocation);
            allocations.add(allocation);

            totalAllocatedAmount += prizeAmount;

            log.info("分配奖金: userId={}, rank={}, prizeAmount={}, actualAmount={}",
                    userRank.getUserId(), userRank.getRank(), prizeAmount, taxResult.getActualAmount());
        }

        // 更新奖金池已分配金额
        if (totalAllocatedAmount > 0) {
            prizePoolService.updateAllocatedAmount(poolId, totalAllocatedAmount);
        }

        log.info("批量分配奖金完成: poolId={}, count={}, totalAmount={}", poolId, allocations.size(), totalAllocatedAmount);

        return allocations;
    }

    /**
     * 批量发送KYC通知
     *
     * @param poolId 奖金池ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void batchNotifyKyc(Long poolId) {
        LambdaQueryWrapper<PrizeAllocation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PrizeAllocation::getPoolId, poolId)
                .eq(PrizeAllocation::getStatus, "ALLOCATED")
                .eq(PrizeAllocation::getDeleted, 0);

        List<PrizeAllocation> allocations = allocationMapper.selectList(wrapper);

        for (PrizeAllocation allocation : allocations) {
            notifyKyc(allocation.getId());
        }

        log.info("批量发送KYC通知完成: poolId={}, count={}", poolId, allocations.size());
    }

    /**
     * 发送KYC通知（单个）
     *
     * @param allocationId 分配记录ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void notifyKyc(Long allocationId) {
        PrizeAllocation allocation = allocationMapper.selectById(allocationId);
        if (allocation == null) {
            throw new BusinessException("分配记录不存在");
        }

        if (!"ALLOCATED".equals(allocation.getStatus())) {
            throw new BusinessException("分配记录状态不正确，无法发送KYC通知");
        }

        // TODO: 后期扩展 - 发送站内信/邮件/短信通知
        // notificationService.sendKycNotification(allocation.getUserId(), allocation);

        allocation.setStatus("KYC_NOTIFIED");
        allocation.setKycNotifiedAt(LocalDateTime.now());
        allocationMapper.updateById(allocation);

        log.info("发送KYC通知: allocationId={}, userId={}", allocationId, allocation.getUserId());
    }

    /**
     * 用户提交KYC（回调）
     *
     * @param allocationId 分配记录ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void onKycSubmitted(Long allocationId) {
        PrizeAllocation allocation = allocationMapper.selectById(allocationId);
        if (allocation == null) {
            throw new BusinessException("分配记录不存在");
        }

        if (!List.of("KYC_NOTIFIED", "ALLOCATED").contains(allocation.getStatus())) {
            throw new BusinessException("分配记录状态不正确");
        }

        allocation.setStatus("KYC_PENDING");
        allocationMapper.updateById(allocation);

        log.info("用户提交KYC: allocationId={}, userId={}", allocationId, allocation.getUserId());
    }

    /**
     * KYC审核通过回调
     *
     * @param allocationId 分配记录ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void onKycApproved(Long allocationId) {
        PrizeAllocation allocation = allocationMapper.selectById(allocationId);
        if (allocation == null) {
            throw new BusinessException("分配记录不存在");
        }

        if (!"KYC_PENDING".equals(allocation.getStatus())) {
            log.warn("分配记录状态不是KYC_PENDING，跳过: allocationId={}, status={}",
                    allocationId, allocation.getStatus());
            return;
        }

        allocation.setStatus("KYC_APPROVED");
        allocationMapper.updateById(allocation);

        log.info("KYC审核通过: allocationId={}, userId={}", allocationId, allocation.getUserId());

        // 自动触发风控检查
        performRiskCheck(allocationId);
    }

    /**
     * 执行风控检查
     *
     * @param allocationId 分配记录ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void performRiskCheck(Long allocationId) {
        PrizeAllocation allocation = allocationMapper.selectById(allocationId);
        if (allocation == null) {
            throw new BusinessException("分配记录不存在");
        }

        if (!"KYC_APPROVED".equals(allocation.getStatus())) {
            throw new BusinessException("KYC未通过，无法进行风控检查");
        }

        // 执行风控检查
        RiskControlRecord riskRecord = riskControlService.performRiskCheck(
                allocationId,
                allocation.getUserId(),
                allocation.getActualAmount()
        );

        allocation.setStatus("RISK_PENDING");
        allocation.setRiskCheckAt(LocalDateTime.now());
        allocationMapper.updateById(allocation);

        // 如果是自动决策，立即处理结果
        if ("APPROVED".equals(riskRecord.getDecision())) {
            onRiskPassed(allocationId);
        } else if ("REJECTED".equals(riskRecord.getDecision())) {
            onRiskRejected(allocationId, riskRecord.getRiskReason());
        }
        // PENDING状态等待人工审核
    }

    /**
     * 风控通过回调
     *
     * @param allocationId 分配记录ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void onRiskPassed(Long allocationId) {
        PrizeAllocation allocation = allocationMapper.selectById(allocationId);
        if (allocation == null) {
            throw new BusinessException("分配记录不存在");
        }

        if (!"RISK_PENDING".equals(allocation.getStatus())) {
            log.warn("分配记录状态不是RISK_PENDING，跳过: allocationId={}, status={}",
                    allocationId, allocation.getStatus());
            return;
        }

        allocation.setStatus("RISK_PASSED");
        allocation.setRiskPassedAt(LocalDateTime.now());
        allocationMapper.updateById(allocation);

        log.info("风控通过: allocationId={}, userId={}", allocationId, allocation.getUserId());

        // 自动加入发放队列
        queueForDisbursement(allocationId);
    }

    /**
     * 风控拒绝回调
     *
     * @param allocationId 分配记录ID
     * @param reason 拒绝原因
     */
    @Transactional(rollbackFor = Exception.class)
    public void onRiskRejected(Long allocationId, String reason) {
        PrizeAllocation allocation = allocationMapper.selectById(allocationId);
        if (allocation == null) {
            throw new BusinessException("分配记录不存在");
        }

        allocation.setStatus("FAILED");
        allocation.setFailureReason(reason);
        allocationMapper.updateById(allocation);

        log.warn("风控拒绝: allocationId={}, userId={}, reason={}", allocationId, allocation.getUserId(), reason);
    }

    /**
     * 加入发放队列
     *
     * @param allocationId 分配记录ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void queueForDisbursement(Long allocationId) {
        PrizeAllocation allocation = allocationMapper.selectById(allocationId);
        if (allocation == null) {
            throw new BusinessException("分配记录不存在");
        }

        if (!"RISK_PASSED".equals(allocation.getStatus())) {
            throw new BusinessException("风控未通过，无法加入发放队列");
        }

        allocation.setStatus("QUEUE_BATCH");
        allocationMapper.updateById(allocation);

        log.info("加入发放队列: allocationId={}, userId={}", allocationId, allocation.getUserId());
    }

    /**
     * 处理超时未完成KYC的记录（定时任务）
     */
    @Transactional(rollbackFor = Exception.class)
    public void forfeitExpiredAllocations() {
        LambdaQueryWrapper<PrizeAllocation> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(PrizeAllocation::getStatus, "ALLOCATED", "KYC_NOTIFIED")
                .le(PrizeAllocation::getKycDeadline, LocalDateTime.now())
                .eq(PrizeAllocation::getDeleted, 0);

        List<PrizeAllocation> expiredAllocations = allocationMapper.selectList(wrapper);

        for (PrizeAllocation allocation : expiredAllocations) {
            allocation.setStatus("FORFEITED");
            allocation.setFailureReason("超过KYC截止时间未完成认证");
            allocationMapper.updateById(allocation);

            log.warn("奖金作废: allocationId={}, userId={}, deadline={}",
                    allocation.getId(), allocation.getUserId(), allocation.getKycDeadline());
        }

        log.info("处理超时未完成KYC的记录: count={}", expiredAllocations.size());
    }

    /**
     * 获取用户的奖金分配记录
     *
     * @param userId 用户ID
     * @return 分配记录列表
     */
    public List<PrizeAllocation> getUserAllocations(Long userId) {
        LambdaQueryWrapper<PrizeAllocation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PrizeAllocation::getUserId, userId)
                .eq(PrizeAllocation::getDeleted, 0)
                .orderByDesc(PrizeAllocation::getCreatedAt);

        return allocationMapper.selectList(wrapper);
    }

    /**
     * 获取单个分配记录
     *
     * @param allocationId 分配记录ID
     * @return 分配记录
     */
    public PrizeAllocation getAllocation(Long allocationId) {
        return allocationMapper.selectById(allocationId);
    }

    /**
     * 根据奖金池和用户获取分配记录
     *
     * @param poolId 奖金池ID
     * @param userId 用户ID
     * @return 分配记录
     */
    public PrizeAllocation getAllocationByPoolAndUser(Long poolId, Long userId) {
        LambdaQueryWrapper<PrizeAllocation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PrizeAllocation::getPoolId, poolId)
                .eq(PrizeAllocation::getUserId, userId)
                .eq(PrizeAllocation::getDeleted, 0)
                .orderByDesc(PrizeAllocation::getCreatedAt)
                .last("LIMIT 1");

        return allocationMapper.selectOne(wrapper);
    }

    /**
     * 获取待发放的分配记录
     *
     * @param poolId 奖金池ID
     * @return 分配记录列表
     */
    public List<PrizeAllocation> getPendingDisbursements(Long poolId) {
        LambdaQueryWrapper<PrizeAllocation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PrizeAllocation::getPoolId, poolId)
                .eq(PrizeAllocation::getStatus, "QUEUE_BATCH")
                .eq(PrizeAllocation::getDeleted, 0)
                .orderByAsc(PrizeAllocation::getRiskPassedAt);

        return allocationMapper.selectList(wrapper);
    }

    /**
     * 根据批次ID获取分配记录
     *
     * @param batchId 批次ID
     * @return 分配记录列表
     */
    public List<PrizeAllocation> getAllocationsByBatch(Long batchId) {
        LambdaQueryWrapper<PrizeAllocation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PrizeAllocation::getBatchId, batchId)
                .eq(PrizeAllocation::getDeleted, 0)
                .orderByAsc(PrizeAllocation::getId);

        return allocationMapper.selectList(wrapper);
    }

    /**
     * 获取批次下失败的分配记录
     *
     * @param batchId 批次ID
     * @return 分配记录列表
     */
    public List<PrizeAllocation> getFailedAllocationsByBatch(Long batchId) {
        LambdaQueryWrapper<PrizeAllocation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PrizeAllocation::getBatchId, batchId)
                .eq(PrizeAllocation::getStatus, "FAILED")
                .eq(PrizeAllocation::getDeleted, 0);

        return allocationMapper.selectList(wrapper);
    }

    /**
     * 更新分配记录的批次ID
     *
     * @param allocationIds 分配记录ID列表
     * @param batchId 批次ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateBatchId(List<Long> allocationIds, Long batchId) {
        for (Long allocationId : allocationIds) {
            PrizeAllocation allocation = allocationMapper.selectById(allocationId);
            if (allocation != null) {
                allocation.setBatchId(batchId);
                allocation.setStatus("DISBURSING");
                allocationMapper.updateById(allocation);
            }
        }
    }

    /**
     * 标记发放成功
     *
     * @param allocationId 分配记录ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void markDisbursed(Long allocationId) {
        PrizeAllocation allocation = allocationMapper.selectById(allocationId);
        if (allocation == null) {
            throw new BusinessException("分配记录不存在");
        }

        allocation.setStatus("COMPLETED");
        allocation.setDisbursedAt(LocalDateTime.now());
        allocationMapper.updateById(allocation);

        log.info("标记发放成功: allocationId={}, userId={}", allocationId, allocation.getUserId());
    }

    /**
     * 标记发放失败
     *
     * @param allocationId 分配记录ID
     * @param failureReason 失败原因
     */
    @Transactional(rollbackFor = Exception.class)
    public void markFailed(Long allocationId, String failureReason) {
        PrizeAllocation allocation = allocationMapper.selectById(allocationId);
        if (allocation == null) {
            throw new BusinessException("分配记录不存在");
        }

        allocation.setStatus("FAILED");
        allocation.setFailureReason(failureReason);
        allocationMapper.updateById(allocation);

        log.warn("标记发放失败: allocationId={}, userId={}, reason={}", allocationId, allocation.getUserId(), failureReason);
    }

    // ==================== 私有方法 ====================

    /**
     * 构建排名-方案映射
     *
     * @param schemes 分配方案列表
     * @return 排名-方案映射
     */
    private Map<Integer, PrizeAllocationScheme> buildRankSchemeMap(List<PrizeAllocationScheme> schemes) {
        Map<Integer, PrizeAllocationScheme> map = new java.util.HashMap<>();

        for (PrizeAllocationScheme scheme : schemes) {
            for (int rank = scheme.getRankStart(); rank <= scheme.getRankEnd(); rank++) {
                map.put(rank, scheme);
            }
        }

        return map;
    }

    /**
     * 生成分配编号
     *
     * 格式: ALLOC-{poolId}-{userId}-{yyyyMMddHHmmss}-{随机4位}
     */
    private String generateAllocationNo(Long poolId, Long userId) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String randomStr = IdUtil.randomUUID().substring(0, 4).toUpperCase();
        return String.format("ALLOC-%d-%d-%s-%s", poolId, userId, timestamp, randomStr);
    }

    // ==================== DTO类 ====================

    /**
     * 用户排名DTO
     */
    @lombok.Data
    public static class UserRankDTO {
        private Long userId;
        private Integer rank;
    }
}
