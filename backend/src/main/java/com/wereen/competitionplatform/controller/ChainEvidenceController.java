package com.wereen.competitionplatform.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wereen.competitionplatform.common.Result;
import com.wereen.competitionplatform.mapper.ChainProofMapper;
import com.wereen.competitionplatform.model.entity.ChainProof;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 链上存证查询接口
 *
 * <p>提供按业务类型和业务 ID 查询 ChainProof 存证记录的 REST 接口，
 * 用于演示完整的链上存证查询链路。
 *
 * <p>业务类型 (bizType) 示例：SUBMISSION / EVALUATION / LEADERBOARD / PRIZE_BATCH / WITHDRAW
 */
@Slf4j
@RestController
@RequestMapping("/chain")
@RequiredArgsConstructor
public class ChainEvidenceController {

    private final ChainProofMapper chainProofMapper;

    /**
     * 查询指定业务类型与业务 ID 对应的所有链上存证记录。
     *
     * <p>同一业务 ID 可能因重试或补链存在多条记录，接口返回列表供调用方自行判断
     * 最新/最终状态。通常取列表中 status=2（已上链）的最近一条即可。
     *
     * @param bizType 业务类型，如 SUBMISSION、EVALUATION 等（大写，不区分大小写均可查到）
     * @param bizId   业务主键 ID
     * @return 匹配的存证记录列表，按创建时间倒序排列；不存在时返回空列表
     */
    @GetMapping("/evidence/{bizType}/{bizId}")
    public Result<List<ChainProof>> getEvidence(
            @PathVariable String bizType,
            @PathVariable Long bizId) {

        log.info("查询链上存证: bizType={}, bizId={}", bizType, bizId);

        LambdaQueryWrapper<ChainProof> wrapper = new LambdaQueryWrapper<ChainProof>()
                .eq(ChainProof::getBizType, bizType.toUpperCase())
                .eq(ChainProof::getBizId, bizId)
                .orderByDesc(ChainProof::getCreatedAt);

        List<ChainProof> proofs = chainProofMapper.selectList(wrapper);

        if (proofs.isEmpty()) {
            log.warn("未找到链上存证记录: bizType={}, bizId={}", bizType, bizId);
            return Result.success("未找到存证记录", proofs);
        }

        log.info("查询到 {} 条存证记录: bizType={}, bizId={}", proofs.size(), bizType, bizId);
        return Result.success(proofs);
    }
}
