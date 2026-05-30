-- ============================================
-- 完整修复缺失字段（动态SQL版本）
-- 版本: V2+V3 Complete Fix Dynamic
-- 日期: 2025-10-24
-- 说明: 使用动态SQL添加所有缺失字段，兼容所有MySQL版本
-- ============================================

USE `competition-platform`;

SET @dbname = 'competition-platform';

-- ============================================
-- 1. 修复 competitions 表
-- ============================================

SET @tablename = 'competitions';

-- use_ab_leaderboard
SET @colname = 'use_ab_leaderboard';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @colname) > 0,
  "SELECT '字段已存在' AS message",
  CONCAT("ALTER TABLE `", @tablename, "` ADD COLUMN `", @colname, "` TINYINT DEFAULT 0 COMMENT '是否启用公私榜 (0-否 1-是)' AFTER `status`")
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- public_test_ratio
SET @colname = 'public_test_ratio';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @colname) > 0,
  "SELECT '字段已存在' AS message",
  CONCAT("ALTER TABLE `", @tablename, "` ADD COLUMN `", @colname, "` DECIMAL(3,2) DEFAULT 0.30 COMMENT '公开测试集比例（0.30表示30%）' AFTER `use_ab_leaderboard`")
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- private_leaderboard_publish_time
SET @colname = 'private_leaderboard_publish_time';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @colname) > 0,
  "SELECT '字段已存在' AS message",
  CONCAT("ALTER TABLE `", @tablename, "` ADD COLUMN `", @colname, "` DATETIME COMMENT '私榜公布时间（通常=竞赛结束时间）' AFTER `public_test_ratio`")
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- max_daily_submissions
SET @colname = 'max_daily_submissions';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @colname) > 0,
  "SELECT '字段已存在' AS message",
  CONCAT("ALTER TABLE `", @tablename, "` ADD COLUMN `", @colname, "` INT DEFAULT 5 COMMENT '每日最大提交次数' AFTER `private_leaderboard_publish_time`")
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- max_total_submissions
SET @colname = 'max_total_submissions';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @colname) > 0,
  "SELECT '字段已存在' AS message",
  CONCAT("ALTER TABLE `", @tablename, "` ADD COLUMN `", @colname, "` INT DEFAULT 100 COMMENT '总最大提交次数' AFTER `max_daily_submissions`")
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 添加索引
SET @indexname = 'idx_use_ab_leaderboard';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
   WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND INDEX_NAME = @indexname) > 0,
  "SELECT '索引已存在' AS message",
  CONCAT("CREATE INDEX `", @indexname, "` ON `", @tablename, "` (`use_ab_leaderboard`)")
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================
-- 2. 修复 evaluations 表
-- ============================================

SET @tablename = 'evaluations';

-- leaderboard_type
SET @colname = 'leaderboard_type';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @colname) > 0,
  "SELECT '字段已存在' AS message",
  CONCAT("ALTER TABLE `", @tablename, "` ADD COLUMN `", @colname, "` VARCHAR(10) DEFAULT 'PUBLIC' COMMENT '榜单类型 (PUBLIC-公榜 PRIVATE-私榜 BOTH-双榜)' AFTER `rank`")
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- public_score
SET @colname = 'public_score';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @colname) > 0,
  "SELECT '字段已存在' AS message",
  CONCAT("ALTER TABLE `", @tablename, "` ADD COLUMN `", @colname, "` DECIMAL(10,4) COMMENT '公榜得分（公开测试集）' AFTER `leaderboard_type`")
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- private_score
SET @colname = 'private_score';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @colname) > 0,
  "SELECT '字段已存在' AS message",
  CONCAT("ALTER TABLE `", @tablename, "` ADD COLUMN `", @colname, "` DECIMAL(10,4) COMMENT '私榜得分（隐藏测试集）' AFTER `public_score`")
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- public_rank
SET @colname = 'public_rank';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @colname) > 0,
  "SELECT '字段已存在' AS message",
  CONCAT("ALTER TABLE `", @tablename, "` ADD COLUMN `", @colname, "` INT COMMENT '公榜排名' AFTER `private_score`")
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- private_rank
SET @colname = 'private_rank';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @colname) > 0,
  "SELECT '字段已存在' AS message",
  CONCAT("ALTER TABLE `", @tablename, "` ADD COLUMN `", @colname, "` INT COMMENT '私榜排名' AFTER `public_rank`")
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- result_hash
SET @colname = 'result_hash';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @colname) > 0,
  "SELECT '字段已存在' AS message",
  CONCAT("ALTER TABLE `", @tablename, "` ADD COLUMN `", @colname, "` VARCHAR(255) COMMENT '评测结果哈希' AFTER `private_rank`")
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- chain_tx_hash
SET @colname = 'chain_tx_hash';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @colname) > 0,
  "SELECT '字段已存在' AS message",
  CONCAT("ALTER TABLE `", @tablename, "` ADD COLUMN `", @colname, "` VARCHAR(255) COMMENT '链上交易哈希' AFTER `result_hash`")
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- block_height
SET @colname = 'block_height';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @colname) > 0,
  "SELECT '字段已存在' AS message",
  CONCAT("ALTER TABLE `", @tablename, "` ADD COLUMN `", @colname, "` BIGINT COMMENT '区块高度' AFTER `chain_tx_hash`")
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- block_time
SET @colname = 'block_time';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @colname) > 0,
  "SELECT '字段已存在' AS message",
  CONCAT("ALTER TABLE `", @tablename, "` ADD COLUMN `", @colname, "` DATETIME COMMENT '区块时间' AFTER `block_height`")
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- chain_status
SET @colname = 'chain_status';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @colname) > 0,
  "SELECT '字段已存在' AS message",
  CONCAT("ALTER TABLE `", @tablename, "` ADD COLUMN `", @colname, "` INT DEFAULT 0 COMMENT '上链状态 (0-未上链 1-上链中 2-已上链 3-失败)' AFTER `block_time`")
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 添加索引
SET @indexname = 'idx_public_rank';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
   WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND INDEX_NAME = @indexname) > 0,
  "SELECT '索引已存在' AS message",
  CONCAT("CREATE INDEX `", @indexname, "` ON `", @tablename, "` (`competition_id`, `public_rank`)")
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @indexname = 'idx_private_rank';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
   WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND INDEX_NAME = @indexname) > 0,
  "SELECT '索引已存在' AS message",
  CONCAT("CREATE INDEX `", @indexname, "` ON `", @tablename, "` (`competition_id`, `private_rank`)")
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @indexname = 'idx_leaderboard_type';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
   WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND INDEX_NAME = @indexname) > 0,
  "SELECT '索引已存在' AS message",
  CONCAT("CREATE INDEX `", @indexname, "` ON `", @tablename, "` (`leaderboard_type`)")
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================
-- 3. 修复 leaderboards 表
-- ============================================

SET @tablename = 'leaderboards';

-- leaderboard_type
SET @colname = 'leaderboard_type';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @colname) > 0,
  "SELECT '字段已存在' AS message",
  CONCAT("ALTER TABLE `", @tablename, "` ADD COLUMN `", @colname, "` VARCHAR(10) DEFAULT 'PUBLIC' COMMENT '榜单类型 (PUBLIC-公榜 PRIVATE-私榜)' AFTER `competition_id`")
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- publicity_status
SET @colname = 'publicity_status';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @colname) > 0,
  "SELECT '字段已存在' AS message",
  CONCAT("ALTER TABLE `", @tablename, "` ADD COLUMN `", @colname, "` VARCHAR(20) DEFAULT 'IN_PUBLICITY' COMMENT '公示状态 (IN_PUBLICITY-公示中 CONFIRMED-已确认 CANCELLED-已取消)' AFTER `frozen`")
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- publicity_start_time
SET @colname = 'publicity_start_time';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @colname) > 0,
  "SELECT '字段已存在' AS message",
  CONCAT("ALTER TABLE `", @tablename, "` ADD COLUMN `", @colname, "` DATETIME COMMENT '公示开始时间' AFTER `publicity_status`")
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- publicity_end_time
SET @colname = 'publicity_end_time';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @colname) > 0,
  "SELECT '字段已存在' AS message",
  CONCAT("ALTER TABLE `", @tablename, "` ADD COLUMN `", @colname, "` DATETIME COMMENT '公示结束时间' AFTER `publicity_start_time`")
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- publicity_days
SET @colname = 'publicity_days';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @colname) > 0,
  "SELECT '字段已存在' AS message",
  CONCAT("ALTER TABLE `", @tablename, "` ADD COLUMN `", @colname, "` INT DEFAULT 7 COMMENT '公示天数（默认7天）' AFTER `publicity_end_time`")
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- confirmed_by
SET @colname = 'confirmed_by';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @colname) > 0,
  "SELECT '字段已存在' AS message",
  CONCAT("ALTER TABLE `", @tablename, "` ADD COLUMN `", @colname, "` BIGINT COMMENT '确认操作人ID' AFTER `publicity_days`")
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- confirmed_at
SET @colname = 'confirmed_at';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @colname) > 0,
  "SELECT '字段已存在' AS message",
  CONCAT("ALTER TABLE `", @tablename, "` ADD COLUMN `", @colname, "` DATETIME COMMENT '确认时间' AFTER `confirmed_by`")
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- remark
SET @colname = 'remark';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @colname) > 0,
  "SELECT '字段已存在' AS message",
  CONCAT("ALTER TABLE `", @tablename, "` ADD COLUMN `", @colname, "` TEXT COMMENT '榜单备注' AFTER `confirmed_at`")
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 添加索引
SET @indexname = 'idx_leaderboard_type';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
   WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND INDEX_NAME = @indexname) > 0,
  "SELECT '索引已存在' AS message",
  CONCAT("CREATE INDEX `", @indexname, "` ON `", @tablename, "` (`leaderboard_type`)")
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @indexname = 'idx_publicity_status';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
   WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND INDEX_NAME = @indexname) > 0,
  "SELECT '索引已存在' AS message",
  CONCAT("CREATE INDEX `", @indexname, "` ON `", @tablename, "` (`publicity_status`)")
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @indexname = 'idx_publicity_end_time';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
   WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND INDEX_NAME = @indexname) > 0,
  "SELECT '索引已存在' AS message",
  CONCAT("CREATE INDEX `", @indexname, "` ON `", @tablename, "` (`publicity_end_time`)")
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================
-- 4. 修复 disbursement_batches 表
-- ============================================

SET @tablename = 'disbursement_batches';

-- operator_id
SET @colname = 'operator_id';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @colname) > 0,
  "SELECT '字段已存在' AS message",
  CONCAT("ALTER TABLE `", @tablename, "` ADD COLUMN `", @colname, "` BIGINT COMMENT '操作员ID' AFTER `executor_id`")
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- submitted_at
SET @colname = 'submitted_at';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @colname) > 0,
  "SELECT '字段已存在' AS message",
  CONCAT("ALTER TABLE `", @tablename, "` ADD COLUMN `", @colname, "` DATETIME COMMENT '提交时间' AFTER `operator_id`")
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================
-- 5. 修复 prize_allocations 表
-- ============================================

SET @tablename = 'prize_allocations';

-- scheme_id
SET @colname = 'scheme_id';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @colname) > 0,
  "SELECT '字段已存在' AS message",
  CONCAT("ALTER TABLE `", @tablename, "` ADD COLUMN `", @colname, "` BIGINT COMMENT '分配方案ID' AFTER `pool_id`")
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- risk_check_at
SET @colname = 'risk_check_at';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @colname) > 0,
  "SELECT '字段已存在' AS message",
  CONCAT("ALTER TABLE `", @tablename, "` ADD COLUMN `", @colname, "` DATETIME COMMENT '风控检查时间' AFTER `risk_status`")
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- risk_passed_at
SET @colname = 'risk_passed_at';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @colname) > 0,
  "SELECT '字段已存在' AS message",
  CONCAT("ALTER TABLE `", @tablename, "` ADD COLUMN `", @colname, "` DATETIME COMMENT '风控通过时间' AFTER `risk_check_at`")
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- failure_reason
SET @colname = 'failure_reason';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @colname) > 0,
  "SELECT '字段已存在' AS message",
  CONCAT("ALTER TABLE `", @tablename, "` ADD COLUMN `", @colname, "` TEXT COMMENT '失败原因' AFTER `failed_reason`")
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- disbursed_at
SET @colname = 'disbursed_at';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @colname) > 0,
  "SELECT '字段已存在' AS message",
  CONCAT("ALTER TABLE `", @tablename, "` ADD COLUMN `", @colname, "` DATETIME COMMENT '发放时间' AFTER `failure_reason`")
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 数据迁移：failed_reason -> failure_reason
UPDATE `prize_allocations`
SET `failure_reason` = `failed_reason`
WHERE `failed_reason` IS NOT NULL AND (`failure_reason` IS NULL OR `failure_reason` = '');

-- 添加索引
SET @indexname = 'idx_status_kyc_deadline';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
   WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND INDEX_NAME = @indexname) > 0,
  "SELECT '索引已存在' AS message",
  CONCAT("CREATE INDEX `", @indexname, "` ON `", @tablename, "` (`status`, `kyc_deadline`)")
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @indexname = 'idx_pool_status';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
   WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND INDEX_NAME = @indexname) > 0,
  "SELECT '索引已存在' AS message",
  CONCAT("CREATE INDEX `", @indexname, "` ON `", @tablename, "` (`pool_id`, `status`)")
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================
-- 6. 修复 prize_pool_fundings 表
-- ============================================

SET @tablename = 'prize_pool_fundings';

-- source_type
SET @colname = 'source_type';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @colname) > 0,
  "SELECT '字段已存在' AS message",
  CONCAT("ALTER TABLE `", @tablename, "` ADD COLUMN `", @colname, "` VARCHAR(50) COMMENT '资金来源类型 (ORGANIZER-主办方 SPONSOR-赞助商 PLATFORM-平台)' AFTER `funding_no`")
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- source_name
SET @colname = 'source_name';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @colname) > 0,
  "SELECT '字段已存在' AS message",
  CONCAT("ALTER TABLE `", @tablename, "` ADD COLUMN `", @colname, "` VARCHAR(200) COMMENT '资金来源名称' AFTER `source_type`")
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- funder_id
SET @colname = 'funder_id';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @colname) > 0,
  "SELECT '字段已存在' AS message",
  CONCAT("ALTER TABLE `", @tablename, "` ADD COLUMN `", @colname, "` BIGINT COMMENT '注资人ID' AFTER `source_name`")
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- funding_amount
SET @colname = 'funding_amount';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @colname) > 0,
  "SELECT '字段已存在' AS message",
  CONCAT("ALTER TABLE `", @tablename, "` ADD COLUMN `", @colname, "` BIGINT COMMENT '注资金额（分）' AFTER `funder_id`")
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- funding_time
SET @colname = 'funding_time';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @colname) > 0,
  "SELECT '字段已存在' AS message",
  CONCAT("ALTER TABLE `", @tablename, "` ADD COLUMN `", @colname, "` DATETIME COMMENT '注资时间' AFTER `funding_amount`")
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 数据迁移：从旧字段迁移到新字段
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

SET @tablename = 'prize_pools';

-- pool_name
SET @colname = 'pool_name';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @colname) > 0,
  "SELECT '字段已存在' AS message",
  CONCAT("ALTER TABLE `", @tablename, "` ADD COLUMN `", @colname, "` VARCHAR(200) COMMENT '奖金池名称' AFTER `pool_no`")
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- description
SET @colname = 'description';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @colname) > 0,
  "SELECT '字段已存在' AS message",
  CONCAT("ALTER TABLE `", @tablename, "` ADD COLUMN `", @colname, "` TEXT COMMENT '描述' AFTER `pool_name`")
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- locked
SET @colname = 'locked';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @colname) > 0,
  "SELECT '字段已存在' AS message",
  CONCAT("ALTER TABLE `", @tablename, "` ADD COLUMN `", @colname, "` TINYINT DEFAULT 0 COMMENT '是否锁定 (0-否 1-是)' AFTER `status`")
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

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
-- 9. 插入迁移记录
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
