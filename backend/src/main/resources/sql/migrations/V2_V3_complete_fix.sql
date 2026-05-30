-- ============================================
-- 完整修复缺失字段
-- 版本: V2+V3 Complete Fix
-- 日期: 2025-10-24
-- 说明: 添加所有 V2 和 V3 迁移脚本中的缺失字段
-- ============================================

USE `competition-platform`;

-- ============================================
-- 1. 修复 competitions 表
-- ============================================

ALTER TABLE `competitions`
ADD COLUMN IF NOT EXISTS `use_ab_leaderboard` TINYINT DEFAULT 0 COMMENT '是否启用公私榜 (0-否 1-是)' AFTER `status`,
ADD COLUMN IF NOT EXISTS `public_test_ratio` DECIMAL(3,2) DEFAULT 0.30 COMMENT '公开测试集比例（0.30表示30%）' AFTER `use_ab_leaderboard`,
ADD COLUMN IF NOT EXISTS `private_leaderboard_publish_time` DATETIME COMMENT '私榜公布时间（通常=竞赛结束时间）' AFTER `public_test_ratio`,
ADD COLUMN IF NOT EXISTS `max_daily_submissions` INT DEFAULT 5 COMMENT '每日最大提交次数' AFTER `private_leaderboard_publish_time`,
ADD COLUMN IF NOT EXISTS `max_total_submissions` INT DEFAULT 100 COMMENT '总最大提交次数' AFTER `max_daily_submissions`;

-- 添加索引
CREATE INDEX IF NOT EXISTS `idx_use_ab_leaderboard` ON `competitions` (`use_ab_leaderboard`);

-- ============================================
-- 2. 修复 evaluations 表
-- ============================================

ALTER TABLE `evaluations`
ADD COLUMN IF NOT EXISTS `leaderboard_type` VARCHAR(10) DEFAULT 'PUBLIC' COMMENT '榜单类型 (PUBLIC-公榜 PRIVATE-私榜 BOTH-双榜)' AFTER `rank`,
ADD COLUMN IF NOT EXISTS `public_score` DECIMAL(10,4) COMMENT '公榜得分（公开测试集）' AFTER `leaderboard_type`,
ADD COLUMN IF NOT EXISTS `private_score` DECIMAL(10,4) COMMENT '私榜得分（隐藏测试集）' AFTER `public_score`,
ADD COLUMN IF NOT EXISTS `public_rank` INT COMMENT '公榜排名' AFTER `private_score`,
ADD COLUMN IF NOT EXISTS `private_rank` INT COMMENT '私榜排名' AFTER `public_rank`,
ADD COLUMN IF NOT EXISTS `result_hash` VARCHAR(255) COMMENT '评测结果哈希' AFTER `private_rank`,
ADD COLUMN IF NOT EXISTS `chain_tx_hash` VARCHAR(255) COMMENT '链上交易哈希' AFTER `result_hash`,
ADD COLUMN IF NOT EXISTS `block_height` BIGINT COMMENT '区块高度' AFTER `chain_tx_hash`,
ADD COLUMN IF NOT EXISTS `block_time` DATETIME COMMENT '区块时间' AFTER `block_height`,
ADD COLUMN IF NOT EXISTS `chain_status` INT DEFAULT 0 COMMENT '上链状态 (0-未上链 1-上链中 2-已上链 3-失败)' AFTER `block_time`;

-- 添加索引
CREATE INDEX IF NOT EXISTS `idx_public_rank` ON `evaluations` (`competition_id`, `public_rank`);
CREATE INDEX IF NOT EXISTS `idx_private_rank` ON `evaluations` (`competition_id`, `private_rank`);
CREATE INDEX IF NOT EXISTS `idx_leaderboard_type` ON `evaluations` (`leaderboard_type`);

-- ============================================
-- 3. 修复 leaderboards 表
-- ============================================

ALTER TABLE `leaderboards`
ADD COLUMN IF NOT EXISTS `leaderboard_type` VARCHAR(10) DEFAULT 'PUBLIC' COMMENT '榜单类型 (PUBLIC-公榜 PRIVATE-私榜)' AFTER `competition_id`,
ADD COLUMN IF NOT EXISTS `publicity_status` VARCHAR(20) DEFAULT 'IN_PUBLICITY' COMMENT '公示状态 (IN_PUBLICITY-公示中 CONFIRMED-已确认 CANCELLED-已取消)' AFTER `frozen`,
ADD COLUMN IF NOT EXISTS `publicity_start_time` DATETIME COMMENT '公示开始时间' AFTER `publicity_status`,
ADD COLUMN IF NOT EXISTS `publicity_end_time` DATETIME COMMENT '公示结束时间' AFTER `publicity_start_time`,
ADD COLUMN IF NOT EXISTS `publicity_days` INT DEFAULT 7 COMMENT '公示天数（默认7天）' AFTER `publicity_end_time`,
ADD COLUMN IF NOT EXISTS `confirmed_by` BIGINT COMMENT '确认操作人ID' AFTER `publicity_days`,
ADD COLUMN IF NOT EXISTS `confirmed_at` DATETIME COMMENT '确认时间' AFTER `confirmed_by`,
ADD COLUMN IF NOT EXISTS `remark` TEXT COMMENT '榜单备注' AFTER `confirmed_at`;

-- 添加索引
CREATE INDEX IF NOT EXISTS `idx_leaderboard_type` ON `leaderboards` (`leaderboard_type`);
CREATE INDEX IF NOT EXISTS `idx_publicity_status` ON `leaderboards` (`publicity_status`);
CREATE INDEX IF NOT EXISTS `idx_publicity_end_time` ON `leaderboards` (`publicity_end_time`);

-- ============================================
-- 4. 修复 disbursement_batches 表
-- ============================================

ALTER TABLE `disbursement_batches`
ADD COLUMN IF NOT EXISTS `operator_id` BIGINT COMMENT '操作员ID' AFTER `executor_id`,
ADD COLUMN IF NOT EXISTS `submitted_at` DATETIME COMMENT '提交时间' AFTER `operator_id`;

-- ============================================
-- 5. 修复 prize_allocations 表
-- ============================================

ALTER TABLE `prize_allocations`
ADD COLUMN IF NOT EXISTS `scheme_id` BIGINT COMMENT '分配方案ID' AFTER `pool_id`,
ADD COLUMN IF NOT EXISTS `risk_check_at` DATETIME COMMENT '风控检查时间' AFTER `risk_status`,
ADD COLUMN IF NOT EXISTS `risk_passed_at` DATETIME COMMENT '风控通过时间' AFTER `risk_check_at`,
ADD COLUMN IF NOT EXISTS `failure_reason` TEXT COMMENT '失败原因' AFTER `failed_reason`,
ADD COLUMN IF NOT EXISTS `disbursed_at` DATETIME COMMENT '发放时间' AFTER `failure_reason`;

-- 如果 failed_reason 字段存在，将其内容迁移到 failure_reason
UPDATE `prize_allocations`
SET `failure_reason` = `failed_reason`
WHERE `failed_reason` IS NOT NULL AND `failure_reason` IS NULL;

-- ============================================
-- 6. 修复 prize_pool_fundings 表
-- ============================================

ALTER TABLE `prize_pool_fundings`
ADD COLUMN IF NOT EXISTS `source_type` VARCHAR(50) COMMENT '资金来源类型 (ORGANIZER-主办方 SPONSOR-赞助商 PLATFORM-平台)' AFTER `funding_no`,
ADD COLUMN IF NOT EXISTS `source_name` VARCHAR(200) COMMENT '资金来源名称' AFTER `source_type`,
ADD COLUMN IF NOT EXISTS `funder_id` BIGINT COMMENT '注资人ID' AFTER `source_name`,
ADD COLUMN IF NOT EXISTS `funding_amount` BIGINT COMMENT '注资金额（分）' AFTER `funder_id`,
ADD COLUMN IF NOT EXISTS `funding_time` DATETIME COMMENT '注资时间' AFTER `funding_amount`;

-- 如果旧字段存在，迁移数据
UPDATE `prize_pool_fundings`
SET
    `source_type` = COALESCE(`source_type`, `sponsor_type`),
    `source_name` = COALESCE(`source_name`, `sponsor_name`),
    `funder_id` = COALESCE(`funder_id`, `sponsor_id`),
    `funding_amount` = COALESCE(`funding_amount`, `amount`)
WHERE 1=1;

-- ============================================
-- 7. 修复 prize_pools 表
-- ============================================

ALTER TABLE `prize_pools`
ADD COLUMN IF NOT EXISTS `pool_name` VARCHAR(200) COMMENT '奖金池名称' AFTER `pool_no`,
ADD COLUMN IF NOT EXISTS `description` TEXT COMMENT '描述' AFTER `pool_name`,
ADD COLUMN IF NOT EXISTS `locked` TINYINT DEFAULT 0 COMMENT '是否锁定 (0-否 1-是)' AFTER `status`;

-- ============================================
-- 8. 创建缺失的表（如果不存在）
-- ============================================

-- 创建 schema_migrations 表
CREATE TABLE IF NOT EXISTS `schema_migrations` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `version` VARCHAR(50) NOT NULL COMMENT '迁移版本号',
    `description` VARCHAR(255) COMMENT '迁移描述',
    `executed_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '执行时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_version` (`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据库迁移记录表';

-- 创建 leaderboard_appeals 表
CREATE TABLE IF NOT EXISTS `leaderboard_appeals` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `leaderboard_id` BIGINT NOT NULL COMMENT '榜单快照ID',
    `competition_id` BIGINT NOT NULL COMMENT '竞赛ID',
    `user_id` BIGINT NOT NULL COMMENT '申诉用户ID',
    `appeal_type` VARCHAR(50) NOT NULL COMMENT '申诉类型 (SCORE_ERROR-分数错误 RANK_ERROR-排名错误 DATA_ERROR-数据错误 OTHER-其他)',
    `appeal_reason` TEXT NOT NULL COMMENT '申诉理由',
    `evidence_files` JSON COMMENT '证据文件列表（MinIO路径）',
    `status` VARCHAR(20) DEFAULT 'PENDING' COMMENT '处理状态 (PENDING-待处理 REVIEWING-审核中 ACCEPTED-已接受 REJECTED-已拒绝)',
    `reviewer_id` BIGINT COMMENT '审核人ID',
    `review_result` TEXT COMMENT '审核结果',
    `review_notes` TEXT COMMENT '审核备注',
    `reviewed_at` DATETIME COMMENT '审核时间',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除 (0-未删除 1-已删除)',
    PRIMARY KEY (`id`),
    KEY `idx_leaderboard_id` (`leaderboard_id`),
    KEY `idx_competition_id` (`competition_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='榜单异议表';

-- 创建 leaderboard_history 表
CREATE TABLE IF NOT EXISTS `leaderboard_history` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `leaderboard_id` BIGINT NOT NULL COMMENT '榜单快照ID',
    `competition_id` BIGINT NOT NULL COMMENT '竞赛ID',
    `operation_type` VARCHAR(50) NOT NULL COMMENT '操作类型 (CREATE-创建 UPDATE-更新 FREEZE-冻结 UNFREEZE-解冻 CONFIRM-确认)',
    `operator_id` BIGINT NOT NULL COMMENT '操作人ID',
    `before_data` JSON COMMENT '操作前数据',
    `after_data` JSON COMMENT '操作后数据',
    `remark` TEXT COMMENT '备注',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_leaderboard_id` (`leaderboard_id`),
    KEY `idx_competition_id` (`competition_id`),
    KEY `idx_operation_type` (`operation_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='榜单历史表';

-- ============================================
-- 9. 添加索引优化
-- ============================================

CREATE INDEX IF NOT EXISTS `idx_status_kyc_deadline` ON `prize_allocations` (`status`, `kyc_deadline`);
CREATE INDEX IF NOT EXISTS `idx_pool_status` ON `prize_allocations` (`pool_id`, `status`);

-- ============================================
-- 10. 插入迁移记录
-- ============================================

INSERT INTO `schema_migrations` (`version`, `description`)
VALUES
    ('V2_2025_10_24', '榜单管理功能：公私榜、公示期、异议处理'),
    ('V3_2025_10_24', '奖金发放功能：KYC认证、奖金池管理、奖金分配、批次发放')
ON DUPLICATE KEY UPDATE `executed_at` = CURRENT_TIMESTAMP;

-- ============================================
-- 完成提示
-- ============================================

SELECT '✓ 数据库修复完成！已添加所有缺失字段和表' AS status;
