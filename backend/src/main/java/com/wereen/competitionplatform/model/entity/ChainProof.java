package com.wereen.competitionplatform.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.wereen.competitionplatform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 链上存证实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("chain_proofs")
public class ChainProof extends BaseEntity {

    /**
     * 业务类型 (SUBMISSION/EVALUATION/LEADERBOARD/PRIZE_BATCH/WITHDRAW)
     */
    private String bizType;

    /**
     * 业务ID
     */
    private Long bizId;

    /**
     * 数据哈希
     */
    private String dataHash;

    /**
     * 链上交易哈希
     */
    private String txHash;

    /**
     * 区块高度
     */
    private Long blockHeight;

    /**
     * 区块时间
     */
    private LocalDateTime blockTime;

    /**
     * 元数据（JSON格式）
     */
    private String metadata;

    /**
     * 链网络 (FISCO / POLYGON / etc)
     */
    private String chainNetwork;

    /**
     * 上链状态 (0-待上链 1-上链中 2-已上链 3-上链失败)
     */
    private Integer status;
}
