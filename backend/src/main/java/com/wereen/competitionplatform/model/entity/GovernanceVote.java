package com.wereen.competitionplatform.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DAO 投票记录实体
 */
@Data
@TableName("governance_vote")
public class GovernanceVote {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 提案ID
     */
    @TableField("proposal_id")
    private String proposalId;
    
    /**
     * 投票人地址
     */
    private String voter;
    
    /**
     * 投票类型
     * 0 = Against (反对)
     * 1 = For (赞成)
     * 2 = Abstain (弃权)
     */
    private Integer support;
    
    /**
     * 投票权重（代币数量）
     */
    private BigDecimal weight;
    
    /**
     * 投票理由
     */
    private String reason;
    
    /**
     * 投票交易哈希
     */
    @TableField("tx_hash")
    private String txHash;
    
    /**
     * 投票区块号
     */
    @TableField("block_number")
    private Long blockNumber;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime votedAt;
}

