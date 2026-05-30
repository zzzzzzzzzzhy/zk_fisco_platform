-- ============================================
-- 榜单管理功能增强 - 数据库迁移脚本
-- 版本: V2
-- 日期: 2025-10-23
-- 功能: 公私榜、公示期、异议处理、历史快照
-- ============================================

USE `competition-platform`;

-- ============================================
-- 1. 扩展 competitions 表（竞赛表）
-- ============================================

-- 添加公私榜相关字段
ALTER TABLE `competitions`
ADD COLUMN `use_ab_leaderboard` TINYINT DEFAULT 0 COMMENT '是否启用公私榜 (0-否 1-是)' AFTER `status`,
ADD COLUMN `public_test_ratio` DECIMAL(3,2) DEFAULT 0.30 COMMENT '公开测试集比例（0.30表示30%）' AFTER `use_ab_leaderboard`,
ADD COLUMN `private_leaderboard_publish_time` DATETIME COMMENT '私榜公布时间（通常=竞赛结束时间）' AFTER `public_test_ratio`,
ADD COLUMN `max_daily_submissions` INT DEFAULT 5 COMMENT '每日最大提交次数' AFTER `private_leaderboard_publish_time`,
ADD COLUMN `max_total_submissions` INT DEFAULT 100 COMMENT '总最大提交次数' AFTER `max_daily_submissions`,
ADD COLUMN `cover_image` VARCHAR(500) COMMENT '封面图片URL' AFTER `max_total_submissions`,
ADD COLUMN `detail_image` VARCHAR(500) COMMENT '详情图片URL' AFTER `cover_image`;

-- 添加索引
CREATE INDEX `idx_use_ab_leaderboard` ON `competitions` (`use_ab_leaderboard`);

-- ============================================
-- 2. 扩展 evaluations 表（评测结果表）
-- ============================================

-- 添加公私榜得分和排名字段
ALTER TABLE `evaluations`
ADD COLUMN `leaderboard_type` VARCHAR(10) DEFAULT 'PUBLIC' COMMENT '榜单类型 (PUBLIC-公榜 PRIVATE-私榜 BOTH-双榜)' AFTER `rank`,
ADD COLUMN `public_score` DECIMAL(10,4) COMMENT '公榜得分（公开测试集）' AFTER `leaderboard_type`,
ADD COLUMN `private_score` DECIMAL(10,4) COMMENT '私榜得分（隐藏测试集）' AFTER `public_score`,
ADD COLUMN `public_rank` INT COMMENT '公榜排名' AFTER `private_score`,
ADD COLUMN `private_rank` INT COMMENT '私榜排名' AFTER `public_rank`,
ADD COLUMN `result_hash` VARCHAR(255) COMMENT '评测结果哈希' AFTER `private_rank`,
ADD COLUMN `chain_tx_hash` VARCHAR(255) COMMENT '链上交易哈希' AFTER `result_hash`,
ADD COLUMN `block_height` BIGINT COMMENT '区块高度' AFTER `chain_tx_hash`,
ADD COLUMN `block_time` DATETIME COMMENT '区块时间' AFTER `block_height`,
ADD COLUMN `chain_status` INT DEFAULT 0 COMMENT '上链状态 (0-未上链 1-上链中 2-已上链 3-失败)' AFTER `block_time`;

-- 添加索引
CREATE INDEX `idx_public_rank` ON `evaluations` (`competition_id`, `public_rank`);
CREATE INDEX `idx_private_rank` ON `evaluations` (`competition_id`, `private_rank`);
CREATE INDEX `idx_leaderboard_type` ON `evaluations` (`leaderboard_type`);

-- ============================================
-- 3. 扩展 leaderboards 表（榜单快照表）
-- ============================================

-- 添加公示期和榜单类型字段
ALTER TABLE `leaderboards`
ADD COLUMN `leaderboard_type` VARCHAR(10) DEFAULT 'PUBLIC' COMMENT '榜单类型 (PUBLIC-公榜 PRIVATE-私榜)' AFTER `competition_id`,
ADD COLUMN `publicity_status` VARCHAR(20) DEFAULT 'IN_PUBLICITY' COMMENT '公示状态 (IN_PUBLICITY-公示中 CONFIRMED-已确认 CANCELLED-已取消)' AFTER `frozen`,
ADD COLUMN `publicity_start_time` DATETIME COMMENT '公示开始时间' AFTER `publicity_status`,
ADD COLUMN `publicity_end_time` DATETIME COMMENT '公示结束时间' AFTER `publicity_start_time`,
ADD COLUMN `publicity_days` INT DEFAULT 7 COMMENT '公示天数（默认7天）' AFTER `publicity_end_time`,
ADD COLUMN `confirmed_by` BIGINT COMMENT '确认操作人ID' AFTER `publicity_days`,
ADD COLUMN `confirmed_at` DATETIME COMMENT '确认时间' AFTER `confirmed_by`,
ADD COLUMN `remark` TEXT COMMENT '榜单备注' AFTER `confirmed_at`;

-- 添加索引
CREATE INDEX `idx_leaderboard_type` ON `leaderboards` (`leaderboard_type`);
CREATE INDEX `idx_publicity_status` ON `leaderboards` (`publicity_status`);
CREATE INDEX `idx_publicity_end_time` ON `leaderboards` (`publicity_end_time`);

-- ============================================
-- 4. 创建 leaderboard_appeals 表（榜单异议表）
-- ============================================

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
    KEY `idx_status` (`status`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='榜单异议表';

-- ============================================
-- 5. 创建 leaderboard_history 表（榜单历史表）
-- ============================================

CREATE TABLE IF NOT EXISTS `leaderboard_history` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `competition_id` BIGINT NOT NULL COMMENT '竞赛ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `leaderboard_type` VARCHAR(10) DEFAULT 'PUBLIC' COMMENT '榜单类型 (PUBLIC-公榜 PRIVATE-私榜)',
    `rank` INT NOT NULL COMMENT '排名',
    `score` DECIMAL(10,4) NOT NULL COMMENT '得分',
    `submission_id` BIGINT COMMENT '提交ID',
    `snapshot_time` DATETIME NOT NULL COMMENT '快照时间',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_competition_user` (`competition_id`, `user_id`, `leaderboard_type`),
    KEY `idx_snapshot_time` (`snapshot_time`),
    KEY `idx_rank` (`competition_id`, `rank`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='榜单历史表';

-- ============================================
-- 6. 创建 leaderboard_notifications 表（榜单通知表）
-- ============================================

CREATE TABLE IF NOT EXISTS `leaderboard_notifications` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `competition_id` BIGINT NOT NULL COMMENT '竞赛ID',
    `notification_type` VARCHAR(50) NOT NULL COMMENT '通知类型 (RANK_CHANGE-排名变化 LEADERBOARD_FROZEN-榜单冻结 PUBLICITY_START-公示开始 APPEAL_RESULT-异议结果)',
    `title` VARCHAR(200) NOT NULL COMMENT '通知标题',
    `content` TEXT NOT NULL COMMENT '通知内容',
    `data` JSON COMMENT '附加数据（如旧排名、新排名）',
    `is_read` TINYINT DEFAULT 0 COMMENT '是否已读 (0-未读 1-已读)',
    `read_at` DATETIME COMMENT '阅读时间',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除 (0-未删除 1-已删除)',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`, `is_read`),
    KEY `idx_competition_id` (`competition_id`),
    KEY `idx_notification_type` (`notification_type`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='榜单通知表';

-- ============================================
-- 7. 扩展 prize_batches 表（奖金批次表）
-- ============================================

-- 添加缺失的区块链字段
ALTER TABLE `prize_batches`
ADD COLUMN `block_height` BIGINT COMMENT '区块高度' AFTER `chain_tx_hash`,
ADD COLUMN `block_time` DATETIME COMMENT '区块时间' AFTER `block_height`;

-- ============================================
-- 8. 扩展 withdraw_requests 表（提现申请表）
-- ============================================

-- 添加缺失的区块链字段
ALTER TABLE `withdraw_requests`
ADD COLUMN `request_hash` VARCHAR(255) COMMENT '提现申请哈希' AFTER `risk_score`,
ADD COLUMN `chain_tx_hash` VARCHAR(255) COMMENT '链上交易哈希' AFTER `request_hash`,
ADD COLUMN `block_height` BIGINT COMMENT '区块高度' AFTER `chain_tx_hash`,
ADD COLUMN `block_time` DATETIME COMMENT '区块时间' AFTER `block_height`,
ADD COLUMN `chain_status` INT DEFAULT 0 COMMENT '上链状态 (0-未上链 1-上链中 2-已上链 3-失败)' AFTER `block_time`;

-- ============================================
-- 9. 创建视图：实时榜单视图
-- ============================================

-- 公榜实时排名视图
CREATE OR REPLACE VIEW `v_public_leaderboard` AS
SELECT
    e.competition_id,
    e.user_id,
    u.username,
    e.public_score AS score,
    e.public_rank AS rank,
    e.submission_id,
    s.created_at AS submit_time,
    e.updated_at AS last_update_time
FROM evaluations e
JOIN users u ON e.user_id = u.id
JOIN submissions s ON e.submission_id = s.id
WHERE e.status = 2  -- 评测成功
  AND e.public_rank IS NOT NULL
  AND e.deleted = 0
ORDER BY e.competition_id, e.public_rank;

-- 私榜实时排名视图
CREATE OR REPLACE VIEW `v_private_leaderboard` AS
SELECT
    e.competition_id,
    e.user_id,
    u.username,
    e.private_score AS score,
    e.private_rank AS rank,
    e.submission_id,
    s.created_at AS submit_time,
    e.updated_at AS last_update_time
FROM evaluations e
JOIN users u ON e.user_id = u.id
JOIN submissions s ON e.submission_id = s.id
WHERE e.status = 2  -- 评测成功
  AND e.private_rank IS NOT NULL
  AND e.deleted = 0
ORDER BY e.competition_id, e.private_rank;

-- ============================================
-- 10. 数据初始化
-- ============================================

-- 更新现有竞赛的公私榜配置（默认不启用）
UPDATE `competitions`
SET
    `use_ab_leaderboard` = 0,
    `public_test_ratio` = 0.30,
    `private_leaderboard_publish_time` = `submission_end_time`,
    `max_daily_submissions` = 5,
    `max_total_submissions` = 100
WHERE `use_ab_leaderboard` IS NULL;

-- 更新现有评测记录的榜单类型（默认公榜）
UPDATE `evaluations`
SET
    `leaderboard_type` = 'PUBLIC',
    `public_score` = `score`,
    `public_rank` = `rank`
WHERE `leaderboard_type` IS NULL OR `leaderboard_type` = '';

-- 更新现有榜单快照的类型（默认公榜）
UPDATE `leaderboards`
SET
    `leaderboard_type` = 'PUBLIC',
    `publicity_status` = IF(`frozen` = 1, 'CONFIRMED', 'IN_PUBLICITY'),
    `publicity_days` = 7
WHERE `leaderboard_type` IS NULL OR `leaderboard_type` = '';

-- ============================================
-- 11. 触发器：自动更新榜单历史
-- ============================================

DELIMITER $$

-- 评测结果插入时，自动记录到榜单历史
CREATE TRIGGER `tr_evaluation_insert_history`
AFTER INSERT ON `evaluations`
FOR EACH ROW
BEGIN
    -- 记录公榜历史
    IF NEW.public_rank IS NOT NULL THEN
        INSERT INTO `leaderboard_history` (
            `competition_id`, `user_id`, `leaderboard_type`, `rank`, `score`,
            `submission_id`, `snapshot_time`
        ) VALUES (
            NEW.competition_id, NEW.user_id, 'PUBLIC', NEW.public_rank, NEW.public_score,
            NEW.submission_id, NOW()
        );
    END IF;

    -- 记录私榜历史
    IF NEW.private_rank IS NOT NULL THEN
        INSERT INTO `leaderboard_history` (
            `competition_id`, `user_id`, `leaderboard_type`, `rank`, `score`,
            `submission_id`, `snapshot_time`
        ) VALUES (
            NEW.competition_id, NEW.user_id, 'PRIVATE', NEW.private_rank, NEW.private_score,
            NEW.submission_id, NOW()
        );
    END IF;
END$$

-- 评测结果更新时，自动记录到榜单历史
CREATE TRIGGER `tr_evaluation_update_history`
AFTER UPDATE ON `evaluations`
FOR EACH ROW
BEGIN
    -- 如果公榜排名变化，记录历史
    IF OLD.public_rank <> NEW.public_rank OR OLD.public_score <> NEW.public_score THEN
        INSERT INTO `leaderboard_history` (
            `competition_id`, `user_id`, `leaderboard_type`, `rank`, `score`,
            `submission_id`, `snapshot_time`
        ) VALUES (
            NEW.competition_id, NEW.user_id, 'PUBLIC', NEW.public_rank, NEW.public_score,
            NEW.submission_id, NOW()
        );
    END IF;

    -- 如果私榜排名变化，记录历史
    IF OLD.private_rank <> NEW.private_rank OR OLD.private_score <> NEW.private_score THEN
        INSERT INTO `leaderboard_history` (
            `competition_id`, `user_id`, `leaderboard_type`, `rank`, `score`,
            `submission_id`, `snapshot_time`
        ) VALUES (
            NEW.competition_id, NEW.user_id, 'PRIVATE', NEW.private_rank, NEW.private_score,
            NEW.submission_id, NOW()
        );
    END IF;
END$$

DELIMITER ;

-- ============================================
-- 12. 清理和优化
-- ============================================

-- 分析表以优化查询性能
ANALYZE TABLE `competitions`;
ANALYZE TABLE `evaluations`;
ANALYZE TABLE `leaderboards`;
ANALYZE TABLE `leaderboard_appeals`;
ANALYZE TABLE `leaderboard_history`;
ANALYZE TABLE `leaderboard_notifications`;

-- ============================================
-- 迁移完成
-- ============================================

-- 插入迁移记录
CREATE TABLE IF NOT EXISTS `schema_migrations` (
    `version` VARCHAR(50) PRIMARY KEY,
    `description` VARCHAR(200),
    `executed_at` DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `schema_migrations` (`version`, `description`)
VALUES ('V2_2025_10_23', '榜单管理功能增强：公私榜、公示期、异议处理');

-- 完成提示
SELECT '✓ 数据库迁移完成！' AS status,
       'V2_add_leaderboard_features' AS migration_name,
       NOW() AS completed_at;
