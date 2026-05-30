package com.wereen.competitionplatform.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.wereen.competitionplatform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 提交记录实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("submissions")
public class Submission extends BaseEntity {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 竞赛ID
     */
    private Long competitionId;

    /**
     * 文件路径（MinIO）
     */
    private String filePath;

    /**
     * 文件哈希算法 (SHA256/MD5)
     */
    private String hashAlgorithm;

    /**
     * 文件哈希值
     */
    private String fileHash;

    /**
     * 预检状态 (0-待检查 1-检查中 2-通过 3-不通过)
     */
    private Integer precheckStatus;

    /**
     * 预检失败原因
     */
    private String precheckReason;

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
    private LocalDateTime blockTime;

    /**
     * 链上状态 (0-未上链 1-上链中 2-已上链 3-上链失败)
     */
    private Integer chainStatus;
}
