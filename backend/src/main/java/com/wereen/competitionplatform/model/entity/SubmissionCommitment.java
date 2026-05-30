package com.wereen.competitionplatform.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.wereen.competitionplatform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 提交得分承诺记录。
 *
 * 竞赛结束评分后、公布排名前，主办方为每位参赛者生成：
 *   commitment = SHA-256(score_le64 ‖ salt_32bytes)
 * 并将其上链。之后公布 (score, salt) + ZK 证明，任何人可验证得分从未被篡改。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("submission_commitments")
public class SubmissionCommitment extends BaseEntity {

    /** 关联竞赛 */
    private Long competitionId;

    /** 参赛者 */
    private Long userId;

    /** 关联的提交记录 */
    private Long submissionId;

    /** SHA-256(score_le64 ‖ salt)，hex 编码，已上链 */
    private String commitmentHash;

    /** 32 字节随机盐，hex 编码；承诺阶段不公开 */
    private String saltHex;

    /** 得分（整数，×100 表示两位小数），揭示后才写入 */
    private Long score;

    /** 是否已揭示 (0-未揭示 1-已揭示) */
    private Integer revealed;

    /** 揭示时间 */
    private LocalDateTime revealedAt;

    /**
     * 链上交易哈希（commitScores 调用产生）
     */
    private String chainTxHash;
}
