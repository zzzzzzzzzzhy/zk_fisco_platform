package com.wereen.competitionplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wereen.competitionplatform.mapper.GovernanceProposalMapper;
import com.wereen.competitionplatform.mapper.GovernanceVoteMapper;
import com.wereen.competitionplatform.model.entity.GovernanceProposal;
import com.wereen.competitionplatform.model.entity.GovernanceVote;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

/**
 * DAO 治理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GovernorService {

    private final GovernanceProposalMapper proposalMapper;
    private final GovernanceVoteMapper voteMapper;

    @Value("${blockchain.reward-governor.address}")
    private String governorAddress;

    @Value("${blockchain.forum-token.extension-address}")
    private String forumExtensionAddress;
    
    @Value("${blockchain.polygon.rpc-url}")
    private String rpcUrl;
    
    private Web3j web3j;

    /**
     * 获取提案列表（分页）
     */
    public Page<GovernanceProposal> getProposals(int page, int size, String status) {
        Page<GovernanceProposal> pageParam = new Page<>(page, size);
        QueryWrapper<GovernanceProposal> wrapper = new QueryWrapper<>();
        
        if (status != null && !status.isEmpty()) {
            wrapper.eq("status", status);
        }
        
        wrapper.orderByDesc("created_at");
        return proposalMapper.selectPage(pageParam, wrapper);
    }

    /**
     * 根据 proposalId 获取提案详情
     */
    public GovernanceProposal getProposalById(String proposalId) {
        QueryWrapper<GovernanceProposal> wrapper = new QueryWrapper<>();
        wrapper.eq("proposal_id", proposalId);
        return proposalMapper.selectOne(wrapper);
    }

    /**
     * 获取提案的投票记录
     */
    public List<GovernanceVote> getProposalVotes(String proposalId) {
        QueryWrapper<GovernanceVote> wrapper = new QueryWrapper<>();
        wrapper.eq("proposal_id", proposalId);
        wrapper.orderByDesc("voted_at");
        return voteMapper.selectList(wrapper);
    }

    /**
     * 查询用户在某个提案上的投票
     */
    public GovernanceVote getUserVote(String proposalId, String voter) {
        QueryWrapper<GovernanceVote> wrapper = new QueryWrapper<>();
        wrapper.eq("proposal_id", proposalId);
        wrapper.eq("voter", voter.toLowerCase());
        return voteMapper.selectOne(wrapper);
    }

    /**
     * 保存或更新提案
     */
    @Transactional
    public void saveProposal(GovernanceProposal proposal) {
        // 调试日志：观察 proposalId 实际长度和值，排查 Data too long 问题
        if (proposal.getProposalId() != null) {
            log.info("Saving proposal, proposalId={}, length={}",
                proposal.getProposalId(), proposal.getProposalId().length());
        } else {
            log.warn("Saving proposal with null proposalId!");
        }

        QueryWrapper<GovernanceProposal> wrapper = new QueryWrapper<>();
        wrapper.eq("proposal_id", proposal.getProposalId());
        
        GovernanceProposal existing = proposalMapper.selectOne(wrapper);
        if (existing != null) {
            proposal.setId(existing.getId());
            proposalMapper.updateById(proposal);
        } else {
            proposalMapper.insert(proposal);
        }
    }

    /**
     * 记录投票
     */
    @Transactional
    public void saveVote(GovernanceVote vote) {
        // 检查是否已经投票
        QueryWrapper<GovernanceVote> wrapper = new QueryWrapper<>();
        wrapper.eq("proposal_id", vote.getProposalId());
        wrapper.eq("voter", vote.getVoter().toLowerCase());
        
        GovernanceVote existing = voteMapper.selectOne(wrapper);
        if (existing != null) {
            // 更新投票
            vote.setId(existing.getId());
            voteMapper.updateById(vote);
        } else {
            // 新增投票
            voteMapper.insert(vote);
        }
        
        // 更新提案的票数统计
        updateProposalVoteCount(vote.getProposalId());
    }

    /**
     * 更新提案的票数统计
     */
    private void updateProposalVoteCount(String proposalId) {
        List<GovernanceVote> votes = getProposalVotes(proposalId);
        
        BigDecimal forVotes = BigDecimal.ZERO;
        BigDecimal againstVotes = BigDecimal.ZERO;
        BigDecimal abstainVotes = BigDecimal.ZERO;
        
        for (GovernanceVote vote : votes) {
            switch (vote.getSupport()) {
                case 1: // For
                    forVotes = forVotes.add(vote.getWeight());
                    break;
                case 0: // Against
                    againstVotes = againstVotes.add(vote.getWeight());
                    break;
                case 2: // Abstain
                    abstainVotes = abstainVotes.add(vote.getWeight());
                    break;
            }
        }
        
        GovernanceProposal proposal = getProposalById(proposalId);
        if (proposal != null) {
            proposal.setForVotes(forVotes);
            proposal.setAgainstVotes(againstVotes);
            proposal.setAbstainVotes(abstainVotes);
            proposalMapper.updateById(proposal);
        }
    }

    /**
     * 从链上同步提案状态
     */
    public void syncProposalStatus(String proposalId) {
        try {
            // 先从数据库中取出提案信息（以便获取所属治理合约地址）
            GovernanceProposal proposal = getProposalById(proposalId);
            if (proposal == null) {
                log.warn("同步提案状态时未找到本地记录, proposalId={}", proposalId);
                return;
            }

            // 初始化 Web3j（如果还没有）
            if (web3j == null) {
                web3j = Web3j.build(new org.web3j.protocol.http.HttpService(rpcUrl));
            }

            // 根据提案记录选择正确的治理合约地址（支持多合约并存）
            String targetGovernorAddress = governorAddress;
            if (proposal.getGovernorAddress() != null && !proposal.getGovernorAddress().isEmpty()) {
                targetGovernorAddress = proposal.getGovernorAddress();
            }
            
            // 调用 Governor.state(proposalId)
            // 注意：前端保存到数据库的 proposalId 是十进制字符串（BigNumber.toString()），
            // 合约 state(uint256) 也使用相同的十进制数值，因此这里按十进制解析。
            BigInteger idValue;
            if (proposalId.startsWith("0x") || proposalId.startsWith("0X")) {
                // 兼容将来可能存成 0x 前缀的十六进制形式
                idValue = new BigInteger(proposalId.substring(2), 16);
            } else {
                idValue = new BigInteger(proposalId); // 十进制
            }
            
            Function function = new Function(
                "state",
                Arrays.asList(new Uint256(idValue)),
                Arrays.asList(new TypeReference<Uint256>() {})
            );
            
            String encodedFunction = FunctionEncoder.encode(function);
            EthCall response = web3j.ethCall(
                Transaction.createEthCallTransaction(null, targetGovernorAddress, encodedFunction),
                DefaultBlockParameterName.LATEST
            ).send();
            
            if (response.hasError()) {
                String errorMessage = response.getError().getMessage();
                // 这里不再强制把提案标记为 Defeated，避免因为网络错误或合约切换导致
                // 「待激活」的提案在很短时间内被错误地标记为「未通过」。
                log.error("同步提案状态失败, proposalId={}, governor={}, error={}",
                    proposalId, targetGovernorAddress, errorMessage);
                return;
            }
            
            List<Type> result = FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());
            if (!result.isEmpty()) {
                int stateValue = ((Uint256) result.get(0)).getValue().intValue();
                String status = getProposalStatusString(stateValue);
                if (proposal != null && !proposal.getStatus().equals(status)) {
                    proposal.setStatus(status);
                    proposalMapper.updateById(proposal);
                    log.info("提案 {} 状态更新为: {}", proposalId, status);
                }
            }
        } catch (Exception e) {
            log.error("同步提案状态异常", e);
        }
    }

    /**
     * 将提案状态码转为字符串
     */
    private String getProposalStatusString(int state) {
        switch (state) {
            case 0: return "Pending";
            case 1: return "Active";
            case 2: return "Canceled";
            case 3: return "Defeated";
            case 4: return "Succeeded";
            case 5: return "Queued";
            case 6: return "Expired";
            case 7: return "Executed";
            default: return "Unknown";
        }
    }

    /**
     * 生成修改奖励参数的 calldata
     */
    public String encodeUpdateRewardConfig(
        BigInteger postReward,
        BigInteger commentReward,
        BigInteger shareReward,
        BigInteger checkinReward,
        BigInteger dailyLimit
    ) {
        Function function = new Function(
            "updateRewardConfig",
            Arrays.asList(
                new Uint256(postReward),
                new Uint256(commentReward),
                new Uint256(shareReward),
                new Uint256(checkinReward),
                new Uint256(dailyLimit)
            ),
            Collections.emptyList()
        );
        
        return FunctionEncoder.encode(function);
    }
}

