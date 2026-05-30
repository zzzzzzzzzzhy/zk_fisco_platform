-- ============================================
-- 奖金发放功能 - 数据库迁移脚本
-- 版本: V3
-- 日期: 2025-10-24
-- 功能: KYC认证、奖金池管理、奖金分配、批次发放
-- ============================================

USE `competition-platform`;

-- ============================================
-- 0. 创建迁移记录表（如果不存在）
-- ============================================

CREATE TABLE IF NOT EXISTS `schema_migrations` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `version` VARCHAR(50) NOT NULL COMMENT '迁移版本号',
    `description` VARCHAR(255) COMMENT '迁移描述',
    `executed_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '执行时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_version` (`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据库迁移记录表';

-- ============================================
-- 1. 用户KYC认证表（简化版）
-- ============================================

CREATE TABLE IF NOT EXISTS `user_kyc` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `real_name` VARCHAR(100) NOT NULL COMMENT '真实姓名',
    `id_card_number` VARCHAR(64) NOT NULL COMMENT '身份证号（加密存储）',
    `id_card_hash` VARCHAR(64) NOT NULL COMMENT '身份证号哈希（用于索引）',
    `mobile_phone` VARCHAR(32) NOT NULL COMMENT '手机号（加密存储）',
    `mobile_hash` VARCHAR(64) NOT NULL COMMENT '手机号哈希（用于索引）',
    `bank_card_number` VARCHAR(64) COMMENT '银行卡号（加密存储）',
    `bank_card_hash` VARCHAR(64) COMMENT '银行卡号哈希（用于索引）',
    `bank_name` VARCHAR(100) COMMENT '开户行',
    `bank_branch` VARCHAR(200) COMMENT '开户支行',

    -- 第三方验证信息
    `face_similarity` DECIMAL(5,2) COMMENT '人脸相似度（预留）',
    `liveness_passed` TINYINT DEFAULT 0 COMMENT '活体检测是否通过（预留）',
    `third_party_kyc_id` VARCHAR(100) COMMENT '第三方KYC请求ID（阿里云等）',
    `third_party_result` JSON COMMENT '第三方返回结果',

    -- 审核信息
    `status` VARCHAR(20) DEFAULT 'PENDING' COMMENT '状态 (PENDING-待审核 APPROVED-已通过 REJECTED-已拒绝)',
    `reject_reason` TEXT COMMENT '拒绝原因',
    `reviewer_id` BIGINT COMMENT '审核员ID',
    `reviewed_at` DATETIME COMMENT '审核时间',
    `retry_count` INT DEFAULT 0 COMMENT '重试次数',

    -- 时间戳
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除 (0-未删除 1-已删除)',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_id` (`user_id`, `deleted`),
    KEY `idx_id_card_hash` (`id_card_hash`),
    KEY `idx_mobile_hash` (`mobile_hash`),
    KEY `idx_bank_card_hash` (`bank_card_hash`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户KYC认证表（简化版）';

-- ============================================
-- 2. 奖金池表
-- ============================================

CREATE TABLE IF NOT EXISTS `prize_pools` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `pool_no` VARCHAR(50) UNIQUE NOT NULL COMMENT '奖金池编号',
    `competition_id` BIGINT NOT NULL COMMENT '竞赛ID',
    `leaderboard_id` BIGINT COMMENT '榜单ID',
    `total_amount` BIGINT NOT NULL DEFAULT 0 COMMENT '总金额（分）',
    `allocated_amount` BIGINT DEFAULT 0 COMMENT '已分配金额（分）',
    `disbursed_amount` BIGINT DEFAULT 0 COMMENT '已发放金额（分）',
    `reserved_amount` BIGINT DEFAULT 0 COMMENT '储备金（分）',

    -- 状态管理
    `status` VARCHAR(20) DEFAULT 'CREATED' COMMENT '状态 (CREATED-已创建 FUNDING-募集中 LOCKED-已锁定 ALLOCATED-已分配 DISBURSING-发放中 COMPLETED-已完成 SETTLED-已结算)',

    -- 区块链存证
    `funding_merkle_root` VARCHAR(255) COMMENT '注资记录Merkle根',
    `allocation_merkle_root` VARCHAR(255) COMMENT '分配方案Merkle根',
    `allocation_hash` VARCHAR(255) COMMENT '分配方案哈希',
    `chain_tx_hash` VARCHAR(255) COMMENT '链上交易哈希',
    `block_height` BIGINT COMMENT '区块高度',
    `block_time` DATETIME COMMENT '区块时间',

    -- 操作记录
    `created_by` BIGINT COMMENT '创建人ID',
    `locked_by` BIGINT COMMENT '锁定人ID',
    `locked_at` DATETIME COMMENT '锁定时间',
    `allocated_at` DATETIME COMMENT '分配时间',

    -- 时间戳
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除 (0-未删除 1-已删除)',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_pool_no` (`pool_no`),
    KEY `idx_competition_id` (`competition_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='奖金池表';

-- ============================================
-- 3. 奖金池注资记录表
-- ============================================

CREATE TABLE IF NOT EXISTS `prize_pool_fundings` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `pool_id` BIGINT NOT NULL COMMENT '奖金池ID',
    `funding_no` VARCHAR(50) UNIQUE COMMENT '注资编号',

    -- 注资方信息
    `sponsor_type` VARCHAR(20) NOT NULL COMMENT '注资方类型 (ORGANIZER-主办方 SPONSOR-赞助商 PLATFORM-平台)',
    `sponsor_name` VARCHAR(200) NOT NULL COMMENT '注资方名称',
    `sponsor_id` BIGINT COMMENT '注资方ID',
    `amount` BIGINT NOT NULL COMMENT '注资金额（分）',

    -- 状态管理
    `status` VARCHAR(20) DEFAULT 'PENDING' COMMENT '状态 (PENDING-待确认 CONFIRMED-已确认 CANCELLED-已取消)',

    -- 支付信息
    `bank_account` VARCHAR(100) COMMENT '付款账号（加密）',
    `transaction_id` VARCHAR(100) COMMENT '银行流水号',
    `transfer_voucher` VARCHAR(500) COMMENT '转账凭证URL（MinIO）',

    -- 审核信息
    `confirmed_by` BIGINT COMMENT '确认人ID',
    `confirmed_at` DATETIME COMMENT '确认时间',

    -- 区块链存证
    `chain_tx_hash` VARCHAR(255) COMMENT '链上交易哈希',
    `block_height` BIGINT COMMENT '区块高度',
    `block_time` DATETIME COMMENT '区块时间',

    `remark` TEXT COMMENT '备注',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除',

    PRIMARY KEY (`id`),
    KEY `idx_pool_id` (`pool_id`),
    KEY `idx_funding_no` (`funding_no`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='奖金池注资记录表';

-- ============================================
-- 4. 奖金分配方案表
-- ============================================

CREATE TABLE IF NOT EXISTS `prize_allocation_schemes` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `pool_id` BIGINT NOT NULL COMMENT '奖金池ID',
    `scheme_name` VARCHAR(100) COMMENT '方案名称（如：冠军奖、优秀奖）',

    -- 排名范围
    `rank_start` INT NOT NULL COMMENT '起始排名',
    `rank_end` INT NOT NULL COMMENT '结束排名',

    -- 奖金配置
    `prize_amount_per_user` BIGINT NOT NULL COMMENT '每人奖金（分）',
    `total_amount` BIGINT NOT NULL COMMENT '该奖项总金额（分）',
    `total_users` INT NOT NULL COMMENT '获奖人数',
    `percentage` DECIMAL(5,2) COMMENT '占奖金池百分比',

    `description` VARCHAR(500) COMMENT '奖项说明',
    `sort_order` INT DEFAULT 0 COMMENT '排序',

    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除',

    PRIMARY KEY (`id`),
    KEY `idx_pool_id` (`pool_id`),
    KEY `idx_rank_range` (`rank_start`, `rank_end`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='奖金分配方案表';

-- ============================================
-- 5. 奖金分配表（用户奖金记录）
-- ============================================

CREATE TABLE IF NOT EXISTS `prize_allocations` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `allocation_no` VARCHAR(50) UNIQUE COMMENT '分配编号',
    `pool_id` BIGINT NOT NULL COMMENT '奖金池ID',
    `batch_id` BIGINT COMMENT '发放批次ID',
    `competition_id` BIGINT NOT NULL COMMENT '竞赛ID',
    `leaderboard_id` BIGINT NOT NULL COMMENT '榜单ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `rank` INT NOT NULL COMMENT '排名',

    -- 奖金金额
    `prize_amount` BIGINT NOT NULL COMMENT '奖金金额（分，税前）',
    `tax_amount` BIGINT DEFAULT 0 COMMENT '个税（分，预留字段）',
    `actual_amount` BIGINT NOT NULL COMMENT '实发金额（分，税后）',

    -- 状态流转
    `status` VARCHAR(20) DEFAULT 'ALLOCATED' COMMENT '状态 (ALLOCATED-已分配 KYC_NOTIFIED-已通知 KYC_PENDING-KYC待审 KYC_APPROVED-KYC通过 RISK_PENDING-风控中 RISK_PASSED-风控通过 QUEUE_BATCH-待发放 DISBURSING-发放中 COMPLETED-已完成 FAILED-失败 FORFEITED-已作废)',

    -- KYC相关
    `kyc_id` BIGINT COMMENT 'KYC记录ID',
    `kyc_notified_at` DATETIME COMMENT 'KYC通知时间',
    `kyc_deadline` DATETIME COMMENT 'KYC截止时间（15天）',
    `kyc_completed_at` DATETIME COMMENT 'KYC完成时间',

    -- 风控相关
    `risk_score` INT DEFAULT 0 COMMENT '风险评分 (0-100)',
    `risk_status` VARCHAR(20) COMMENT '风控状态 (PENDING-待审 PASSED-通过 REJECTED-拒绝)',
    `risk_checked_at` DATETIME COMMENT '风控检查时间',

    -- 发放信息
    `disbursement_account` VARCHAR(100) COMMENT '收款银行卡（加密）',
    `disbursement_bank` VARCHAR(100) COMMENT '开户行',
    `transaction_id` VARCHAR(100) COMMENT '银行流水号',
    `failed_reason` TEXT COMMENT '失败原因',
    `retry_count` INT DEFAULT 0 COMMENT '重试次数',

    -- 区块链存证
    `chain_tx_hash` VARCHAR(255) COMMENT '链上交易哈希',
    `block_height` BIGINT COMMENT '区块高度',
    `block_time` DATETIME COMMENT '区块时间',

    `completed_at` DATETIME COMMENT '发放完成时间',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_allocation_no` (`allocation_no`),
    KEY `idx_pool_id` (`pool_id`),
    KEY `idx_batch_id` (`batch_id`),
    KEY `idx_competition_user` (`competition_id`, `user_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='奖金分配表';

-- ============================================
-- 6. 发放批次表
-- ============================================

CREATE TABLE IF NOT EXISTS `disbursement_batches` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `batch_no` VARCHAR(50) UNIQUE NOT NULL COMMENT '批次号 (BATCH_YYYYMMDD_序号)',
    `pool_id` BIGINT NOT NULL COMMENT '奖金池ID',
    `competition_id` BIGINT COMMENT '竞赛ID',

    -- 批次统计
    `total_count` INT DEFAULT 0 COMMENT '总人数',
    `total_amount` BIGINT DEFAULT 0 COMMENT '总金额（分，税前）',
    `total_tax` BIGINT DEFAULT 0 COMMENT '总税额（分，预留字段）',
    `total_actual_amount` BIGINT DEFAULT 0 COMMENT '总实发（分，税后）',
    `success_count` INT DEFAULT 0 COMMENT '成功人数',
    `failed_count` INT DEFAULT 0 COMMENT '失败人数',

    -- 状态管理
    `status` VARCHAR(20) DEFAULT 'CREATED' COMMENT '状态 (CREATED-已创建 PROCESSING-处理中 COMPLETED-已完成 FAILED-失败)',

    -- 银行信息
    `bank_batch_no` VARCHAR(100) COMMENT '银行批次号',
    `bank_response` JSON COMMENT '银行返回结果',

    -- 区块链存证
    `merkle_root` VARCHAR(255) COMMENT '发放清单Merkle根',
    `chain_tx_hash` VARCHAR(255) COMMENT '链上交易哈希',
    `block_height` BIGINT COMMENT '区块高度',
    `block_time` DATETIME COMMENT '区块时间',

    -- 操作记录
    `executor_id` BIGINT COMMENT '执行人ID',
    `executed_at` DATETIME COMMENT '执行时间',
    `completed_at` DATETIME COMMENT '完成时间',

    `remark` TEXT COMMENT '备注',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_batch_no` (`batch_no`),
    KEY `idx_pool_id` (`pool_id`),
    KEY `idx_competition_id` (`competition_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='发放批次表';

-- ============================================
-- 7. 风控记录表
-- ============================================

CREATE TABLE IF NOT EXISTS `risk_control_records` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `allocation_id` BIGINT NOT NULL COMMENT '奖金分配ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',

    -- 用户信息哈希（用于风控检测）
    `id_card_hash` VARCHAR(64) COMMENT '身份证号哈希',
    `mobile_hash` VARCHAR(64) COMMENT '手机号哈希',
    `bank_card_hash` VARCHAR(64) COMMENT '银行卡号哈希',
    `prize_amount` BIGINT COMMENT '奖金金额（分）',

    -- 检测结果
    `blacklist_hit` TINYINT DEFAULT 0 COMMENT '是否命中黑名单 (0-否 1-是)',
    `fraud_score` INT DEFAULT 0 COMMENT '欺诈分数 (0-100)',
    `aml_score` INT DEFAULT 0 COMMENT 'AML风险分数 (0-100)',
    `risk_score` INT DEFAULT 0 COMMENT '综合风险评分 (0-100)',
    `risk_level` VARCHAR(20) COMMENT '风险等级 (LOW-低 MEDIUM-中 HIGH-高)',
    `risk_reason` TEXT COMMENT '风险原因',

    -- 决策结果
    `decision` VARCHAR(20) NOT NULL COMMENT '决策 (APPROVED-通过 PENDING-待审核 REJECTED-拒绝)',

    -- 人工复核
    `reviewer_id` BIGINT COMMENT '复核人ID',
    `review_remark` TEXT COMMENT '复核备注',
    `reviewed_at` DATETIME COMMENT '复核时间',

    `deleted` TINYINT DEFAULT 0 COMMENT '是否删除 (0-否 1-是)',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    PRIMARY KEY (`id`),
    KEY `idx_allocation_id` (`allocation_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_id_card_hash` (`id_card_hash`),
    KEY `idx_mobile_hash` (`mobile_hash`),
    KEY `idx_bank_card_hash` (`bank_card_hash`),
    KEY `idx_decision` (`decision`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='风控记录表';

-- ============================================
-- 8. 添加索引优化
-- ============================================

-- 奖金分配表联合索引
ALTER TABLE `prize_allocations` ADD INDEX `idx_status_kyc_deadline` (`status`, `kyc_deadline`);
ALTER TABLE `prize_allocations` ADD INDEX `idx_pool_status` (`pool_id`, `status`);

-- 发放批次表联合索引
ALTER TABLE `disbursement_batches` ADD INDEX `idx_pool_status` (`pool_id`, `status`);

-- ============================================
-- 9. 插入迁移记录
-- ============================================

INSERT INTO `schema_migrations` (`version`, `description`)
VALUES ('V3_2025_10_24', '奖金发放功能：KYC认证、奖金池管理、奖金分配、批次发放')
ON DUPLICATE KEY UPDATE `executed_at` = CURRENT_TIMESTAMP;

-- ============================================
-- 10. 完成提示
-- ============================================

SELECT '✓ 奖金发放数据库迁移完成！' AS status,
       'V3_add_prize_disbursement_features' AS migration_name,
       NOW() AS completed_at;
