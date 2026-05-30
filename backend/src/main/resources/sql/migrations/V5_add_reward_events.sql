CREATE TABLE IF NOT EXISTS `reward_events` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `event_type` VARCHAR(32) NOT NULL COMMENT '事件类型',
    `biz_id` VARCHAR(64) NOT NULL COMMENT '业务ID',
    `signature` VARCHAR(255) COMMENT '用户签名',
    `payload` JSON COMMENT '事件载荷',
    `batch_id` BIGINT COMMENT '批次ID',
    `status` TINYINT DEFAULT 0 COMMENT '处理状态 (0-待打包 1-已打包 2-已发放)',
    `tx_hash` VARCHAR(128) COMMENT '发放交易哈希',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除 (0-未删除 1-已删除)',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_type_biz` (`user_id`, `event_type`, `biz_id`),
    KEY `idx_event_type` (`event_type`),
    KEY `idx_batch_id` (`batch_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='奖励事件表';
