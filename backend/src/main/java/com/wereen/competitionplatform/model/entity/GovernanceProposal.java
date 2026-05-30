package com.wereen.competitionplatform.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DAO 治理提案实体
 */
@Data
@TableName("governance_proposal")
public class GovernanceProposal {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 链上提案ID (uint256 转为字符串，当前使用十进制字符串)
     */
    @TableField("proposal_id")
    private String proposalId;
    
    /**
     * 所属治理合约地址（用于支持多治理合约并存）
     */
    @TableField("governor_address")
    private String governorAddress;
    
    /**
     * 提案发起人地址
     */
    private String proposer;
    
    /**
     * 提案标题
     */
    private String title;
    
    /**
     * 提案描述 (Markdown)
     */
    private String description;
    
    /**
     * 目标合约地址数组 (JSON)
     */
    private String targets;
    
    /**
     * 调用时发送的 ETH 数量数组 (JSON)
     */
    @TableField("`values`")
    private String values;
    
    /**
     * 调用数据数组 (JSON)
     */
    private String calldatas;
    
    /**
     * 提案状态
     * Pending(0) - 待激活
     * Active(1) - 投票中
     * Canceled(2) - 已取消
     * Defeated(3) - 未通过
     * Succeeded(4) - 已通过
     * Queued(5) - 排队中
     * Expired(6) - 已过期
     * Executed(7) - 已执行
     */
    private String status;
    
    /**
     * 赞成票数
     */
    @TableField("for_votes")
    private BigDecimal forVotes;
    
    /**
     * 反对票数
     */
    @TableField("against_votes")
    private BigDecimal againstVotes;
    
    /**
     * 弃权票数
     */
    @TableField("abstain_votes")
    private BigDecimal abstainVotes;
    
    /**
     * 投票开始区块
     */
    @TableField("start_block")
    private Long startBlock;
    
    /**
     * 投票结束区块
     */
    @TableField("end_block")
    private Long endBlock;
    
    /**
     * 创建提案的交易哈希
     */
    @TableField("create_tx_hash")
    private String createTxHash;
    
    /**
     * 执行提案的交易哈希
     */
    @TableField("execute_tx_hash")
    private String executeTxHash;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

