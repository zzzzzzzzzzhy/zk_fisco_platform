package com.wereen.competitionplatform.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.wereen.competitionplatform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * ZK 排名证明记录。
 *
 * 一条记录对应一次竞赛排名的 ZK 证明，包含证明本体和公开的 journal 输出。
 * 本地开发时由 MockZkRankingProver 生成占位证明；
 * 生产环境由 zk/host/prove 二进制产出真实 RISC Zero Groth16 证明。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("zk_ranking_proofs")
public class ZkRankingProof extends BaseEntity {

    /** 关联竞赛 */
    private Long competitionId;

    /** RISC Zero image ID（标识 guest 程序版本），hex 编码 */
    private String imageId;

    /** ZK 证明字节，hex 编码（真实时为 Groth16 seal，mock 时为全零） */
    private String sealHex;

    /** Guest journal 字节，hex 编码；链上验证时作为 journalDigest = sha256(journal) */
    private String journalHex;

    /** journal 的 sha256，hex 编码；提交到合约的参数 */
    private String journalDigest;

    /**
     * 最终排名，JSON 数组，userId 从高分到低分排列。
     * 例: "[3,1,2]" 表示 userId=3 第一，userId=1 第二，userId=2 第三。
     */
    private String rankingJson;

    /**
     * 证明状态：MOCK / REAL / SUBMITTED
     * MOCK     – 本地占位证明
     * REAL     – 真实 RISC Zero 证明，未提交合约
     * SUBMITTED – 已提交到 CompetitionCommitRegistry 合约
     */
    private String status;

    /** 链上交易哈希（SUBMITTED 时有值） */
    private String chainTxHash;
}
