package com.wereen.competitionplatform.task;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.wereen.competitionplatform.mapper.GovernanceProposalMapper;
import com.wereen.competitionplatform.model.entity.GovernanceProposal;
import com.wereen.competitionplatform.service.GovernorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 治理提案状态同步定时任务
 * 定期从链上同步提案状态，确保数据库状态与链上一致
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GovernanceProposalSyncTask {

    private final GovernanceProposalMapper proposalMapper;
    private final GovernorService governorService;

    /**
     * 每30秒同步一次待激活和投票中的提案状态
     * 避免频繁查询链上，但保证状态及时更新
     */
    @Scheduled(fixedRate = 30000) // 30秒
    public void syncPendingAndActiveProposals() {
        try {
            QueryWrapper<GovernanceProposal> wrapper = new QueryWrapper<>();
            // 只同步待激活和投票中的提案（这些状态会变化）
            wrapper.in("status", "Pending", "Active");
            wrapper.orderByDesc("created_at");
            wrapper.last("LIMIT 20"); // 每次最多同步20个，避免超时

            List<GovernanceProposal> proposals = proposalMapper.selectList(wrapper);
            
            if (proposals.isEmpty()) {
                return;
            }

            log.debug("开始同步 {} 个提案状态", proposals.size());
            int syncedCount = 0;

            for (GovernanceProposal proposal : proposals) {
                try {
                    String oldStatus = proposal.getStatus();
                    governorService.syncProposalStatus(proposal.getProposalId());
                    
                    // 重新查询以获取最新状态
                    GovernanceProposal updated = proposalMapper.selectById(proposal.getId());
                    if (updated != null && !oldStatus.equals(updated.getStatus())) {
                        syncedCount++;
                        log.info("提案 {} 状态已更新: {} -> {}", 
                            proposal.getProposalId().substring(0, 20) + "...", 
                            oldStatus, 
                            updated.getStatus());
                    }
                } catch (Exception e) {
                    log.warn("同步提案 {} 状态失败: {}", 
                        proposal.getProposalId().substring(0, 20) + "...", 
                        e.getMessage());
                }
            }

            if (syncedCount > 0) {
                log.info("提案状态同步完成，共更新 {} 个提案", syncedCount);
            }
        } catch (Exception e) {
            log.error("定时同步提案状态失败", e);
        }
    }
}

