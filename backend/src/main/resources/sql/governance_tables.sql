-- DAO 治理相关表

USE `competition_platform`;

-- 治理提案表
CREATE TABLE IF NOT EXISTS `governance_proposal` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `proposal_id` VARCHAR(100) NOT NULL COMMENT '链上提案ID（uint256转字符串，最多78位）',
    `proposer` VARCHAR(42) NOT NULL COMMENT '提案发起人地址',
    `title` VARCHAR(200) NOT NULL COMMENT '提案标题',
    `description` TEXT COMMENT '提案描述（Markdown格式）',
    `targets` TEXT COMMENT '目标合约地址数组（JSON）',
    `values` TEXT COMMENT '调用时发送的ETH数量数组（JSON）',
    `calldatas` TEXT COMMENT '调用数据数组（JSON）',
    `status` VARCHAR(20) DEFAULT 'Pending' COMMENT '提案状态：Pending/Active/Canceled/Defeated/Succeeded/Queued/Expired/Executed',
    `for_votes` DECIMAL(40, 18) DEFAULT 0 COMMENT '赞成票数',
    `against_votes` DECIMAL(40, 18) DEFAULT 0 COMMENT '反对票数',
    `abstain_votes` DECIMAL(40, 18) DEFAULT 0 COMMENT '弃权票数',
    `start_block` BIGINT COMMENT '投票开始区块',
    `end_block` BIGINT COMMENT '投票结束区块',
    `create_tx_hash` VARCHAR(66) COMMENT '创建提案的交易哈希',
    `execute_tx_hash` VARCHAR(66) COMMENT '执行提案的交易哈希',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_proposal_id` (`proposal_id`),
    KEY `idx_proposer` (`proposer`),
    KEY `idx_status` (`status`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='DAO治理提案表';

-- 治理投票表
CREATE TABLE IF NOT EXISTS `governance_vote` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `proposal_id` VARCHAR(100) NOT NULL COMMENT '提案ID',
    `voter` VARCHAR(42) NOT NULL COMMENT '投票人地址',
    `support` TINYINT NOT NULL COMMENT '投票类型：0=反对，1=赞成，2=弃权',
    `weight` DECIMAL(40, 18) NOT NULL COMMENT '投票权重（代币数量）',
    `reason` TEXT COMMENT '投票理由',
    `tx_hash` VARCHAR(66) COMMENT '投票交易哈希',
    `block_number` BIGINT COMMENT '投票区块号',
    `voted_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '投票时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_proposal_voter` (`proposal_id`, `voter`),
    KEY `idx_voter` (`voter`),
    KEY `idx_proposal_id` (`proposal_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='DAO投票记录表';

