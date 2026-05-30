package com.wereen.competitionplatform.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.wereen.competitionplatform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 评测结果实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("evaluations")
public class Evaluation extends BaseEntity {

    /**
     * 提交记录ID
     */
    private Long submissionId;

    /**
     * 竞赛ID
     */
    private Long competitionId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 得分
     */
    private BigDecimal score;

    /**
     * 排名（旧字段，兼容性保留）
     */
    private Integer rank;

    /**
     * 榜单类型 (PUBLIC-公榜 PRIVATE-私榜 BOTH-双榜)
     */
    private String leaderboardType;

    /**
     * 公榜得分（公开测试集）
     */
    private BigDecimal publicScore;

    /**
     * 私榜得分（隐藏测试集）
     */
    private BigDecimal privateScore;

    /**
     * 公榜排名
     */
    private Integer publicRank;

    /**
     * 私榜排名
     */
    private Integer privateRank;

    /**
     * 评测日志路径（MinIO）
     */
    private String logPath;

    /**
     * 资源使用情况（JSON格式）
     */
    private String resourceUsage;

    /**
     * 评测状态 (0-待评测 1-评测中 2-成功 3-失败)
     */
    private Integer status;

    /**
     * 失败原因
     */
    private String failureReason;

    /**
     * 是否复评 (0-否 1-是)
     */
    private Integer isReview;

    /**
     * 评测结果哈希（用于上链）
     */
    private String resultHash;

    /**
     * 链上交易哈希
     */
    private String chainTxHash;

    /**
     * 区块高度
     */
    private Long blockHeight;

    /**
     * 区块时间
     */
    private java.time.LocalDateTime blockTime;

    /**
     * 上链状态 (0-未上链 1-上链中 2-已上链 3-失败)
     */
    private Integer chainStatus;
}
