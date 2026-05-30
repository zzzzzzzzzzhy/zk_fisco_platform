package com.wereen.competitionplatform.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.wereen.competitionplatform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 发放批次实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("disbursement_batches")
public class DisbursementBatch extends BaseEntity {

    /**
     * 批次号 (BATCH_YYYYMMDD_序号)
     */
    private String batchNo;

    /**
     * 奖金池ID
     */
    private Long poolId;

    /**
     * 竞赛ID
     */
    private Long competitionId;

    /**
     * 总人数
     */
    private Integer totalCount;

    /**
     * 总金额（分，税前）
     */
    private Long totalAmount;

    /**
     * 总税额（分，预留字段）
     */
    private Long totalTax;

    /**
     * 总实发（分，税后）
     */
    private Long totalActualAmount;

    /**
     * 成功人数
     */
    private Integer successCount;

    /**
     * 失败人数
     */
    private Integer failedCount;

    /**
     * 状态 (CREATED-已创建 PROCESSING-处理中 COMPLETED-已完成 FAILED-失败)
     */
    private String status;

    /**
     * 银行批次号
     */
    private String bankBatchNo;

    /**
     * 银行返回结果
     */
    private String bankResponse;

    /**
     * 发放清单Merkle根
     */
    private String merkleRoot;

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
     * 执行人ID
     */
    private Long executorId;

    /**
     * 操作员ID
     */
    private Long operatorId;

    /**
     * 提交时间
     */
    private LocalDateTime submittedAt;

    /**
     * 执行时间
     */
    private LocalDateTime executedAt;

    /**
     * 完成时间
     */
    private LocalDateTime completedAt;

    /**
     * 备注
     */
    private String remark;
}
