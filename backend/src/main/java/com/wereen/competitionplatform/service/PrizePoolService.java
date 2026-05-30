package com.wereen.competitionplatform.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.SecureUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wereen.competitionplatform.exception.BusinessException;
import com.wereen.competitionplatform.mapper.PrizeAllocationSchemeMapper;
import com.wereen.competitionplatform.mapper.PrizePoolFundingMapper;
import com.wereen.competitionplatform.mapper.PrizePoolMapper;
import com.wereen.competitionplatform.model.entity.PrizeAllocationScheme;
import com.wereen.competitionplatform.model.entity.PrizePool;
import com.wereen.competitionplatform.model.entity.PrizePoolFunding;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 奖金池管理服务
 *
 * 功能：
 * 1. 创建奖金池
 * 2. 奖金池注资（主办方、赞助商、平台）
 * 3. 配置分配方案（排名区间、奖金金额、占比）
 * 4. 计算Merkle树根（用于验证分配方案完整性）
 * 5. 锁定/解锁奖金池
 * 6. 查询奖金池状态
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrizePoolService {

    private final PrizePoolMapper prizePoolMapper;
    private final PrizePoolFundingMapper fundingMapper;
    private final PrizeAllocationSchemeMapper schemeMapper;

    /**
     * 创建奖金池
     *
     * @param leaderboardId 榜单ID
     * @param poolName 奖金池名称
     * @param description 描述
     * @return 奖金池
     */
    @Transactional(rollbackFor = Exception.class)
    public PrizePool createPrizePool(Long leaderboardId, String poolName, String description) {
        // 检查该榜单是否已有奖金池
        PrizePool existing = getPrizePoolByLeaderboard(leaderboardId);
        if (existing != null) {
            throw new BusinessException("该榜单已存在奖金池: " + existing.getPoolNo());
        }

        // 生成奖金池编号
        String poolNo = generatePoolNo(leaderboardId);

        PrizePool pool = new PrizePool();
        pool.setLeaderboardId(leaderboardId);
        pool.setPoolNo(poolNo);
        pool.setPoolName(poolName);
        pool.setDescription(description);
        pool.setTotalAmount(0L);
        pool.setAllocatedAmount(0L);
        pool.setDisbursedAmount(0L);
        pool.setStatus("DRAFT"); // 草稿状态
        pool.setLocked(0);
        pool.setDeleted(0);

        prizePoolMapper.insert(pool);

        log.info("创建奖金池成功: poolNo={}, leaderboardId={}", poolNo, leaderboardId);
        return pool;
    }

    /**
     * 奖金池注资
     *
     * @param poolId 奖金池ID
     * @param sourceType 资金来源类型（ORGANIZER-主办方 SPONSOR-赞助商 PLATFORM-平台）
     * @param sourceName 资金来源名称
     * @param fundingAmount 注资金额（分）
     * @param funderId 注资人ID
     * @return 注资记录
     */
    @Transactional(rollbackFor = Exception.class)
    public PrizePoolFunding addFunding(Long poolId, String sourceType, String sourceName,
                                       Long fundingAmount, Long funderId) {
        PrizePool pool = prizePoolMapper.selectById(poolId);
        if (pool == null) {
            throw new BusinessException("奖金池不存在");
        }

        if (pool.getLocked() == 1) {
            throw new BusinessException("奖金池已锁定，无法注资");
        }

        // 验证资金来源类型
        if (!List.of("ORGANIZER", "SPONSOR", "PLATFORM").contains(sourceType)) {
            throw new BusinessException("无效的资金来源类型");
        }

        // 创建注资记录
        PrizePoolFunding funding = new PrizePoolFunding();
        funding.setPoolId(poolId);
        funding.setSourceType(sourceType);
        funding.setSourceName(sourceName);
        funding.setFundingAmount(fundingAmount);
        funding.setFunderId(funderId);
        funding.setFundingTime(LocalDateTime.now());
        funding.setDeleted(0);

        fundingMapper.insert(funding);

        // 更新奖金池总额
        pool.setTotalAmount(pool.getTotalAmount() + fundingAmount);
        prizePoolMapper.updateById(pool);

        log.info("奖金池注资成功: poolId={}, sourceType={}, amount={}", poolId, sourceType, fundingAmount);
        return funding;
    }

    /**
     * 创建分配方案
     *
     * @param poolId 奖金池ID
     * @param schemeName 方案名称（如"冠军奖"）
     * @param rankStart 起始排名
     * @param rankEnd 结束排名
     * @param prizeAmountPerUser 每人奖金金额（分）
     * @param percentage 占比（可选）
     * @return 分配方案
     */
    @Transactional(rollbackFor = Exception.class)
    public PrizeAllocationScheme createAllocationScheme(Long poolId, String schemeName,
                                                        Integer rankStart, Integer rankEnd,
                                                        Long prizeAmountPerUser, BigDecimal percentage) {
        PrizePool pool = prizePoolMapper.selectById(poolId);
        if (pool == null) {
            throw new BusinessException("奖金池不存在");
        }

        if (pool.getLocked() == 1) {
            throw new BusinessException("奖金池已锁定，无法修改分配方案");
        }

        // 验证排名区间
        if (rankStart == null || rankEnd == null || rankStart > rankEnd || rankStart < 1) {
            throw new BusinessException("无效的排名区间");
        }

        // 检查排名区间是否重叠
        LambdaQueryWrapper<PrizeAllocationScheme> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PrizeAllocationScheme::getPoolId, poolId)
                .eq(PrizeAllocationScheme::getDeleted, 0);

        List<PrizeAllocationScheme> existingSchemes = schemeMapper.selectList(wrapper);
        for (PrizeAllocationScheme existing : existingSchemes) {
            if (isRankOverlap(rankStart, rankEnd, existing.getRankStart(), existing.getRankEnd())) {
                throw new BusinessException(
                        String.format("排名区间与现有方案重叠: [%d-%d] vs [%d-%d]",
                                rankStart, rankEnd, existing.getRankStart(), existing.getRankEnd())
                );
            }
        }

        // 计算总金额
        int userCount = rankEnd - rankStart + 1;
        Long totalAmount = prizeAmountPerUser * userCount;

        // 创建分配方案
        PrizeAllocationScheme scheme = new PrizeAllocationScheme();
        scheme.setPoolId(poolId);
        scheme.setSchemeName(schemeName);
        scheme.setRankStart(rankStart);
        scheme.setRankEnd(rankEnd);
        scheme.setPrizeAmountPerUser(prizeAmountPerUser);
        scheme.setTotalAmount(totalAmount);
        scheme.setPercentage(percentage);
        scheme.setDeleted(0);

        schemeMapper.insert(scheme);

        log.info("创建分配方案成功: poolId={}, schemeName={}, rank=[{}-{}], amount={}",
                poolId, schemeName, rankStart, rankEnd, prizeAmountPerUser);

        return scheme;
    }

    /**
     * 批量创建分配方案
     *
     * @param poolId 奖金池ID
     * @param schemes 分配方案列表
     * @return 创建的方案列表
     */
    @Transactional(rollbackFor = Exception.class)
    public List<PrizeAllocationScheme> batchCreateSchemes(Long poolId, List<SchemeDTO> schemes) {
        List<PrizeAllocationScheme> result = new ArrayList<>();

        for (SchemeDTO dto : schemes) {
            PrizeAllocationScheme scheme = createAllocationScheme(
                    poolId,
                    dto.getSchemeName(),
                    dto.getRankStart(),
                    dto.getRankEnd(),
                    dto.getPrizeAmountPerUser(),
                    dto.getPercentage()
            );
            result.add(scheme);
        }

        return result;
    }

    /**
     * 锁定奖金池
     *
     * 锁定后：
     * 1. 不能修改分配方案
     * 2. 不能注资
     * 3. 计算Merkle树根
     * 4. 存证到区块链
     *
     * @param poolId 奖金池ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void lockPrizePool(Long poolId) {
        PrizePool pool = prizePoolMapper.selectById(poolId);
        if (pool == null) {
            throw new BusinessException("奖金池不存在");
        }

        if (pool.getLocked() == 1) {
            throw new BusinessException("奖金池已锁定");
        }

        // 验证分配方案总额不超过奖金池总额
        Long totalSchemeAmount = getTotalSchemeAmount(poolId);
        if (totalSchemeAmount > pool.getTotalAmount()) {
            throw new BusinessException(
                    String.format("分配方案总额(%d)超过奖金池总额(%d)",
                            totalSchemeAmount, pool.getTotalAmount())
            );
        }

        // 计算Merkle树根
        String merkleRoot = calculateAllocationMerkleRoot(poolId);

        // TODO: 后期扩展 - 存证到区块链
        // String chainTxHash = storeToBlockchain(poolId, merkleRoot);

        pool.setLocked(1);
        pool.setStatus("LOCKED");
        pool.setAllocationMerkleRoot(merkleRoot);
        pool.setLockedAt(LocalDateTime.now());
        // pool.setChainTxHash(chainTxHash);

        prizePoolMapper.updateById(pool);

        log.info("锁定奖金池成功: poolId={}, merkleRoot={}", poolId, merkleRoot);
    }

    /**
     * 解锁奖金池（仅管理员可操作）
     *
     * @param poolId 奖金池ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void unlockPrizePool(Long poolId) {
        PrizePool pool = prizePoolMapper.selectById(poolId);
        if (pool == null) {
            throw new BusinessException("奖金池不存在");
        }

        if (pool.getLocked() == 0) {
            throw new BusinessException("奖金池未锁定");
        }

        // 检查是否已有分配记录
        if (pool.getAllocatedAmount() > 0) {
            throw new BusinessException("奖金池已有分配记录，无法解锁");
        }

        pool.setLocked(0);
        pool.setStatus("DRAFT");
        pool.setLockedAt(null);

        prizePoolMapper.updateById(pool);

        log.info("解锁奖金池成功: poolId={}", poolId);
    }

    /**
     * 获取奖金池
     *
     * @param poolId 奖金池ID
     * @return 奖金池
     */
    public PrizePool getPrizePool(Long poolId) {
        return prizePoolMapper.selectById(poolId);
    }

    /**
     * 根据榜单ID获取奖金池
     *
     * @param leaderboardId 榜单ID
     * @return 奖金池
     */
    public PrizePool getPrizePoolByLeaderboard(Long leaderboardId) {
        LambdaQueryWrapper<PrizePool> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PrizePool::getLeaderboardId, leaderboardId)
                .eq(PrizePool::getDeleted, 0)
                .orderByDesc(PrizePool::getCreatedAt)
                .last("LIMIT 1");

        return prizePoolMapper.selectOne(wrapper);
    }

    /**
     * 获取奖金池的所有注资记录
     *
     * @param poolId 奖金池ID
     * @return 注资记录列表
     */
    public List<PrizePoolFunding> getFundingList(Long poolId) {
        LambdaQueryWrapper<PrizePoolFunding> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PrizePoolFunding::getPoolId, poolId)
                .eq(PrizePoolFunding::getDeleted, 0)
                .orderByDesc(PrizePoolFunding::getFundingTime);

        return fundingMapper.selectList(wrapper);
    }

    /**
     * 获取奖金池的所有分配方案
     *
     * @param poolId 奖金池ID
     * @return 分配方案列表
     */
    public List<PrizeAllocationScheme> getSchemeList(Long poolId) {
        LambdaQueryWrapper<PrizeAllocationScheme> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PrizeAllocationScheme::getPoolId, poolId)
                .eq(PrizeAllocationScheme::getDeleted, 0)
                .orderByAsc(PrizeAllocationScheme::getRankStart);

        return schemeMapper.selectList(wrapper);
    }

    /**
     * 更新奖金池已分配金额
     *
     * @param poolId 奖金池ID
     * @param allocatedAmount 新增分配金额
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateAllocatedAmount(Long poolId, Long allocatedAmount) {
        PrizePool pool = prizePoolMapper.selectById(poolId);
        if (pool == null) {
            throw new BusinessException("奖金池不存在");
        }

        pool.setAllocatedAmount(pool.getAllocatedAmount() + allocatedAmount);
        prizePoolMapper.updateById(pool);
    }

    /**
     * 更新奖金池已发放金额
     *
     * @param poolId 奖金池ID
     * @param disbursedAmount 新增发放金额
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateDisbursedAmount(Long poolId, Long disbursedAmount) {
        PrizePool pool = prizePoolMapper.selectById(poolId);
        if (pool == null) {
            throw new BusinessException("奖金池不存在");
        }

        pool.setDisbursedAmount(pool.getDisbursedAmount() + disbursedAmount);
        prizePoolMapper.updateById(pool);
    }

    // ==================== 私有方法 ====================

    /**
     * 生成奖金池编号
     *
     * 格式: POOL-{leaderboardId}-{yyyyMMddHHmmss}-{随机4位}
     */
    private String generatePoolNo(Long leaderboardId) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String randomStr = IdUtil.randomUUID().substring(0, 4).toUpperCase();
        return String.format("POOL-%d-%s-%s", leaderboardId, timestamp, randomStr);
    }

    /**
     * 检查排名区间是否重叠
     */
    private boolean isRankOverlap(Integer start1, Integer end1, Integer start2, Integer end2) {
        return !(end1 < start2 || end2 < start1);
    }

    /**
     * 获取分配方案总金额
     */
    private Long getTotalSchemeAmount(Long poolId) {
        List<PrizeAllocationScheme> schemes = getSchemeList(poolId);
        return schemes.stream()
                .mapToLong(PrizeAllocationScheme::getTotalAmount)
                .sum();
    }

    /**
     * 计算分配方案的Merkle树根
     *
     * 用于验证分配方案的完整性和不可篡改性
     */
    private String calculateAllocationMerkleRoot(Long poolId) {
        List<PrizeAllocationScheme> schemes = getSchemeList(poolId);

        if (schemes.isEmpty()) {
            return SecureUtil.sha256("");
        }

        // 将每个方案哈希化
        List<String> leaves = schemes.stream()
                .map(scheme -> SecureUtil.sha256(
                        String.format("%d|%s|%d|%d|%d",
                                scheme.getId(),
                                scheme.getSchemeName(),
                                scheme.getRankStart(),
                                scheme.getRankEnd(),
                                scheme.getPrizeAmountPerUser())
                ))
                .collect(Collectors.toList());

        // 构建Merkle树
        return buildMerkleTree(leaves);
    }

    /**
     * 构建Merkle树（简化版）
     *
     * TODO: 后期扩展 - 使用更完善的Merkle树实现
     */
    private String buildMerkleTree(List<String> leaves) {
        if (leaves.isEmpty()) {
            return "";
        }

        if (leaves.size() == 1) {
            return leaves.get(0);
        }

        List<String> newLevel = new ArrayList<>();

        // 两两配对哈希
        for (int i = 0; i < leaves.size(); i += 2) {
            String left = leaves.get(i);
            String right = (i + 1 < leaves.size()) ? leaves.get(i + 1) : left;
            String combined = SecureUtil.sha256(left + right);
            newLevel.add(combined);
        }

        // 递归构建上层
        return buildMerkleTree(newLevel);
    }

    // ==================== DTO类 ====================

    /**
     * 分配方案DTO
     */
    @lombok.Data
    public static class SchemeDTO {
        private String schemeName;
        private Integer rankStart;
        private Integer rankEnd;
        private Long prizeAmountPerUser;
        private BigDecimal percentage;
    }
}
