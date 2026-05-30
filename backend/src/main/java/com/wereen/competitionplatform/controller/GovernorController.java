package com.wereen.competitionplatform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wereen.competitionplatform.common.Result;
import com.wereen.competitionplatform.model.entity.GovernanceProposal;
import com.wereen.competitionplatform.model.entity.GovernanceVote;
import com.wereen.competitionplatform.service.GovernorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DAO 治理控制器
 */
@Slf4j
@RestController
@RequestMapping("/governance")
@RequiredArgsConstructor
public class GovernorController {

    private final GovernorService governorService;

    @Value("${blockchain.reward-governor.address}")
    private String governorAddress;

    @Value("${blockchain.forum-token.extension-address}")
    private String forumExtensionAddress;

    @Value("${blockchain.mtk.token-address}")
    private String weeTokenAddress;

    /**
     * 获取提案列表
     */
    @GetMapping("/proposals")
    public Result<Page<GovernanceProposal>> getProposals(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status
    ) {
        try {
            Page<GovernanceProposal> proposals = governorService.getProposals(page, size, status);
            return Result.success(proposals);
        } catch (Exception e) {
            log.error("获取提案列表失败", e);
            return Result.error("获取提案列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取提案详情
     */
    @GetMapping("/proposals/{proposalId}")
    
    public Result<GovernanceProposal> getProposal(@PathVariable String proposalId) {
        try {
            GovernanceProposal proposal = governorService.getProposalById(proposalId);
            if (proposal == null) {
                return Result.error("提案不存在");
            }
            // 为避免前端详情页因链上 RPC 延迟而卡顿，这里不再同步链上状态，
            // 如需强制刷新可调用单独的 /governance/proposals/{proposalId}/sync 接口。
            return Result.success(proposal);
        } catch (Exception e) {
            log.error("获取提案详情失败", e);
            return Result.error("获取提案详情失败: " + e.getMessage());
        }
    }

    /**
     * 获取提案的投票记录
     */
    @GetMapping("/proposals/{proposalId}/votes")
    
    public Result<List<GovernanceVote>> getProposalVotes(@PathVariable String proposalId) {
        try {
            List<GovernanceVote> votes = governorService.getProposalVotes(proposalId);
            return Result.success(votes);
        } catch (Exception e) {
            log.error("获取投票记录失败", e);
            return Result.error("获取投票记录失败: " + e.getMessage());
        }
    }

    /**
     * 查询用户投票
     */
    @GetMapping("/proposals/{proposalId}/votes/{voter}")
    
    public Result<GovernanceVote> getUserVote(
            @PathVariable String proposalId,
            @PathVariable String voter
    ) {
        try {
            GovernanceVote vote = governorService.getUserVote(proposalId, voter);
            return Result.success(vote);
        } catch (Exception e) {
            log.error("查询用户投票失败", e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * 保存提案（由前端提交链上提案后调用）
     */
    @PostMapping("/proposals")
    
    public Result<String> saveProposal(@RequestBody GovernanceProposal proposal) {
        try {
            // 记录提案对应的治理合约地址，避免后续切换合约导致 Unknown proposal id
            proposal.setGovernorAddress(governorAddress);
            // 如果前端未显式传入状态，默认置为 Pending
            if (proposal.getStatus() == null || proposal.getStatus().isEmpty()) {
                proposal.setStatus("Pending");
            }
            governorService.saveProposal(proposal);
            return Result.success("提案已保存");
        } catch (Exception e) {
            log.error("保存提案失败", e);
            return Result.error("保存提案失败: " + e.getMessage());
        }
    }

    /**
     * 记录投票（由前端提交链上投票后调用）
     */
    @PostMapping("/votes")
    
    public Result<String> saveVote(@RequestBody GovernanceVote vote) {
        try {
            governorService.saveVote(vote);
            return Result.success("投票已记录");
        } catch (Exception e) {
            log.error("记录投票失败", e);
            return Result.error("记录投票失败: " + e.getMessage());
        }
    }

    /**
     * 同步提案状态
     */
    @PostMapping("/proposals/{proposalId}/sync")
    
    public Result<String> syncProposal(@PathVariable String proposalId) {
        try {
            governorService.syncProposalStatus(proposalId);
            return Result.success("状态已同步");
        } catch (Exception e) {
            log.error("同步状态失败", e);
            return Result.error("同步失败: " + e.getMessage());
        }
    }

    /**
     * 获取合约地址配置
     */
    @GetMapping("/config")
    
    public Result<Map<String, String>> getConfig() {
        Map<String, String> config = new HashMap<>();
        config.put("governorAddress", governorAddress);
        config.put("forumExtensionAddress", forumExtensionAddress);
        config.put("weeTokenAddress", weeTokenAddress);
        return Result.success(config);
    }

    /**
     * 生成修改奖励参数的 calldata（辅助工具）
     */
    @PostMapping("/calldata/update-reward")
    
    public Result<String> encodeUpdateReward(
            @RequestParam String postReward,
            @RequestParam String commentReward,
            @RequestParam String shareReward,
            @RequestParam String checkinReward,
            @RequestParam String dailyLimit
    ) {
        try {
            String calldata = governorService.encodeUpdateRewardConfig(
                new BigInteger(postReward),
                new BigInteger(commentReward),
                new BigInteger(shareReward),
                new BigInteger(checkinReward),
                new BigInteger(dailyLimit)
            );
            return Result.success(calldata);
        } catch (Exception e) {
            log.error("生成calldata失败", e);
            return Result.error("生成失败: " + e.getMessage());
        }
    }
}

