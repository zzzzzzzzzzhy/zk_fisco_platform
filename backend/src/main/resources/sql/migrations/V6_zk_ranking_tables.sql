-- ZK 排名证明相关表

CREATE TABLE IF NOT EXISTS `submission_commitments` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `competition_id`   BIGINT       NOT NULL COMMENT '竞赛ID',
    `user_id`          BIGINT       NOT NULL COMMENT '参赛者ID',
    `submission_id`    BIGINT       COMMENT '关联提交ID',
    `commitment_hash`  VARCHAR(64)  NOT NULL COMMENT 'SHA-256(score_le64‖salt)，hex',
    `salt_hex`         VARCHAR(64)  NOT NULL COMMENT '32字节随机盐，hex',
    `score`            BIGINT       COMMENT '得分（×100），揭示后填写',
    `revealed`         TINYINT      DEFAULT 0 COMMENT '是否已揭示',
    `revealed_at`      DATETIME     COMMENT '揭示时间',
    `chain_tx_hash`    VARCHAR(128) COMMENT '上链交易哈希',
    `created_at`       DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `updated_at`       DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`          TINYINT      DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_competition_user` (`competition_id`, `user_id`),
    KEY `idx_competition` (`competition_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='提交得分承诺记录';

CREATE TABLE IF NOT EXISTS `zk_ranking_proofs` (
    `id`             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `competition_id` BIGINT        NOT NULL COMMENT '竞赛ID',
    `image_id`       VARCHAR(128)  COMMENT 'RISC Zero image ID，hex',
    `seal_hex`       MEDIUMTEXT    COMMENT 'Groth16 seal，hex',
    `journal_hex`    TEXT          COMMENT 'Guest journal，hex',
    `journal_digest` VARCHAR(64)   COMMENT 'sha256(journal)，hex',
    `ranking_json`   TEXT          COMMENT '最终排名 JSON 数组（userId 列表）',
    `status`         VARCHAR(16)   DEFAULT 'MOCK' COMMENT 'MOCK/REAL/SUBMITTED',
    `chain_tx_hash`  VARCHAR(128)  COMMENT '链上交易哈希',
    `created_at`     DATETIME      DEFAULT CURRENT_TIMESTAMP,
    `updated_at`     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`        TINYINT       DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_competition` (`competition_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ZK 排名证明记录';
