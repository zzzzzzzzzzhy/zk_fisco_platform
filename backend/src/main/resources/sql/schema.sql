-- 竞赛平台数据库初始化脚本

-- 创建数据库
CREATE DATABASE IF NOT EXISTS `competition_platform` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `competition_platform`;

-- 用户表
CREATE TABLE IF NOT EXISTS `users` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `email` VARCHAR(100) COMMENT '邮箱',
    `phone` VARCHAR(20) COMMENT '手机号',
    `password` VARCHAR(255) NOT NULL COMMENT '密码（加密）',
    `username` VARCHAR(50) NOT NULL COMMENT '用户名',
    `realname` VARCHAR(50) COMMENT '真实姓名',
    `id_number` VARCHAR(50) COMMENT '身份证号',
    `kyc_status` TINYINT DEFAULT 0 COMMENT 'KYC状态 (0-未认证 1-审核中 2-已通过 3-未通过)',
    `risk_flag` TINYINT DEFAULT 0 COMMENT '风控标志 (0-正常 1-风险用户)',
    `status` TINYINT DEFAULT 1 COMMENT '用户状态 (0-禁用 1-启用)',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除 (0-未删除 1-已删除)',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_email` (`email`),
    UNIQUE KEY `uk_phone` (`phone`),
    KEY `idx_username` (`username`),
    KEY `idx_kyc_status` (`kyc_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 竞赛表
CREATE TABLE IF NOT EXISTS `competitions` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `title` VARCHAR(200) NOT NULL DEFAULT '' COMMENT '竞赛标题',
    `description` TEXT COMMENT '竞赛简介',
    `detail` LONGTEXT COMMENT '赛题详情',
    `data_description` TEXT COMMENT '数据说明',
    `evaluation_standard` TEXT COMMENT '评测标准',
    `submission_requirement` TEXT COMMENT '提交要求',
    `prize_config` JSON COMMENT '奖金配置',
    `total_prize` BIGINT DEFAULT 0 COMMENT '总奖金池（单位：分）',
    `registration_start_time` DATETIME COMMENT '报名开始时间',
    `registration_end_time` DATETIME COMMENT '报名结束时间',
    `submission_start_time` DATETIME COMMENT '提交开始时间',
    `submission_end_time` DATETIME COMMENT '提交结束时间',
    `evaluation_image` VARCHAR(255) COMMENT '评测镜像',
    `dataset_path` VARCHAR(500) COMMENT '数据集路径（MinIO）',
    `status` TINYINT DEFAULT 0 COMMENT '竞赛状态 (0-草稿 1-报名中 2-进行中 3-已结束 4-已取消)',
    `creator_id` BIGINT NOT NULL COMMENT '创建人ID',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除 (0-未删除 1-已删除)',
    PRIMARY KEY (`id`),
    KEY `idx_status` (`status`),
    KEY `idx_creator_id` (`creator_id`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='竞赛表';

-- 报名记录表
CREATE TABLE IF NOT EXISTS `registrations` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `competition_id` BIGINT NOT NULL COMMENT '竞赛ID',
    `agreement_version` VARCHAR(50) COMMENT '协议版本',
    `status` TINYINT DEFAULT 0 COMMENT '报名状态 (0-待审核 1-已通过 2-已拒绝)',
    `remark` VARCHAR(500) COMMENT '审核备注',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除 (0-未删除 1-已删除)',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_competition` (`user_id`, `competition_id`),
    KEY `idx_competition_id` (`competition_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='报名记录表';

-- 提交记录表
CREATE TABLE IF NOT EXISTS `submissions` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `competition_id` BIGINT NOT NULL COMMENT '竞赛ID',
    `file_path` VARCHAR(500) NOT NULL COMMENT '文件路径（MinIO）',
    `hash_algorithm` VARCHAR(20) DEFAULT 'SHA256' COMMENT '文件哈希算法',
    `file_hash` VARCHAR(128) COMMENT '文件哈希值',
    `precheck_status` TINYINT DEFAULT 0 COMMENT '预检状态 (0-待检查 1-检查中 2-通过 3-不通过)',
    `precheck_reason` VARCHAR(500) COMMENT '预检失败原因',
    `chain_tx_hash` VARCHAR(128) COMMENT '链上交易哈希',
    `block_height` BIGINT COMMENT '区块高度',
    `block_time` DATETIME COMMENT '区块时间',
    `chain_status` TINYINT DEFAULT 0 COMMENT '链上状态 (0-未上链 1-上链中 2-已上链 3-上链失败)',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除 (0-未删除 1-已删除)',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_competition_id` (`competition_id`),
    KEY `idx_chain_tx_hash` (`chain_tx_hash`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='提交记录表';

-- 评测结果表
CREATE TABLE IF NOT EXISTS `evaluations` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `submission_id` BIGINT NOT NULL COMMENT '提交记录ID',
    `competition_id` BIGINT NOT NULL COMMENT '竞赛ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `score` DECIMAL(10,4) COMMENT '得分',
    `rank` INT COMMENT '排名',
    `log_path` VARCHAR(500) COMMENT '评测日志路径（MinIO）',
    `resource_usage` JSON COMMENT '资源使用情况',
    `status` TINYINT DEFAULT 0 COMMENT '评测状态 (0-待评测 1-评测中 2-成功 3-失败)',
    `failure_reason` VARCHAR(500) COMMENT '失败原因',
    `is_review` TINYINT DEFAULT 0 COMMENT '是否复评 (0-否 1-是)',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除 (0-未删除 1-已删除)',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_submission_id` (`submission_id`),
    KEY `idx_competition_id` (`competition_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_rank` (`rank`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评测结果表';

-- 榜单快照表
CREATE TABLE IF NOT EXISTS `leaderboards` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `competition_id` BIGINT NOT NULL COMMENT '竞赛ID',
    `snapshot_id` VARCHAR(50) NOT NULL COMMENT '快照ID（版本号）',
    `merkle_root` VARCHAR(128) COMMENT 'Merkle Root',
    `frozen` TINYINT DEFAULT 0 COMMENT '是否冻结 (0-否 1-是)',
    `frozen_by` BIGINT COMMENT '冻结操作人ID',
    `frozen_at` DATETIME COMMENT '冻结时间',
    `chain_tx_hash` VARCHAR(128) COMMENT '链上交易哈希',
    `block_height` BIGINT COMMENT '区块高度',
    `block_time` DATETIME COMMENT '区块时间',
    `leaderboard_data` LONGTEXT COMMENT '榜单数据（JSON格式）',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除 (0-未删除 1-已删除)',
    PRIMARY KEY (`id`),
    KEY `idx_competition_id` (`competition_id`),
    KEY `idx_snapshot_id` (`snapshot_id`),
    KEY `idx_frozen` (`frozen`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='榜单快照表';

-- 钱包余额表
CREATE TABLE IF NOT EXISTS `wallet_balance` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `currency` VARCHAR(10) DEFAULT 'CNY' COMMENT '币种',
    `balance` BIGINT DEFAULT 0 COMMENT '可用余额（单位：分）',
    `frozen_amount` BIGINT DEFAULT 0 COMMENT '冻结金额（单位：分）',
    `version` INT DEFAULT 0 COMMENT '乐观锁版本号',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除 (0-未删除 1-已删除)',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_currency` (`user_id`, `currency`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='钱包余额表';

-- 钱包交易记录表
CREATE TABLE IF NOT EXISTS `wallet_transactions` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `type` VARCHAR(50) NOT NULL COMMENT '交易类型',
    `amount` BIGINT NOT NULL COMMENT '交易金额（单位：分）',
    `balance_after` BIGINT NOT NULL COMMENT '交易后余额（单位：分）',
    `biz_ref` VARCHAR(100) COMMENT '业务关联ID',
    `status` VARCHAR(20) DEFAULT 'SUCCESS' COMMENT '交易状态',
    `remark` VARCHAR(500) COMMENT '备注',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除 (0-未删除 1-已删除)',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_type` (`type`),
    KEY `idx_biz_ref` (`biz_ref`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='钱包交易记录表';

-- 奖金发放批次表
CREATE TABLE IF NOT EXISTS `prize_batches` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `competition_id` BIGINT NOT NULL COMMENT '竞赛ID',
    `batch_no` VARCHAR(50) NOT NULL COMMENT '批次号',
    `total_amount` BIGINT NOT NULL COMMENT '总金额（单位：分）',
    `winners_count` INT NOT NULL COMMENT '获奖人数',
    `merkle_root` VARCHAR(128) COMMENT 'Merkle Root',
    `frozen_snapshot_id` BIGINT COMMENT '冻结快照ID',
    `status` VARCHAR(20) DEFAULT 'CREATED' COMMENT '批次状态',
    `chain_tx_hash` VARCHAR(128) COMMENT '链上交易哈希',
    `accounted_at` DATETIME COMMENT '入账时间',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除 (0-未删除 1-已删除)',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_batch_no` (`batch_no`),
    KEY `idx_competition_id` (`competition_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='奖金发放批次表';

-- 奖金发放批次明细表
CREATE TABLE IF NOT EXISTS `prize_batch_items` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `batch_id` BIGINT NOT NULL COMMENT '批次ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `rank` INT NOT NULL COMMENT '排名',
    `amount` BIGINT NOT NULL COMMENT '奖金金额（单位：分）',
    `item_hash` VARCHAR(128) COMMENT '项哈希',
    `status` VARCHAR(20) DEFAULT 'PENDING' COMMENT '状态',
    `reason` VARCHAR(500) COMMENT '失败原因',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除 (0-未删除 1-已删除)',
    PRIMARY KEY (`id`),
    KEY `idx_batch_id` (`batch_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='奖金发放批次明细表';

-- 提现申请表
CREATE TABLE IF NOT EXISTS `withdraw_requests` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `method` VARCHAR(50) NOT NULL COMMENT '提现方式',
    `account_payload` TEXT COMMENT '收款账号（加密）',
    `amount` BIGINT NOT NULL COMMENT '提现金额（单位：分）',
    `fee` BIGINT DEFAULT 0 COMMENT '手续费（单位：分）',
    `tax` BIGINT DEFAULT 0 COMMENT '税费（单位：分）',
    `status` VARCHAR(20) DEFAULT 'APPLIED' COMMENT '提现状态',
    `provider` VARCHAR(50) COMMENT '支付渠道',
    `provider_tx_id` VARCHAR(100) COMMENT '渠道交易ID',
    `failure_reason` VARCHAR(500) COMMENT '失败原因',
    `paid_at` DATETIME COMMENT '支付完成时间',
    `risk_score` INT COMMENT '风控评分',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除 (0-未删除 1-已删除)',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status` (`status`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='提现申请表';

-- 链上存证表
CREATE TABLE IF NOT EXISTS `chain_proofs` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `biz_type` VARCHAR(50) NOT NULL COMMENT '业务类型',
    `biz_id` BIGINT NOT NULL COMMENT '业务ID',
    `data_hash` VARCHAR(128) NOT NULL COMMENT '数据哈希',
    `tx_hash` VARCHAR(128) COMMENT '链上交易哈希',
    `block_height` BIGINT COMMENT '区块高度',
    `block_time` DATETIME COMMENT '区块时间',
    `metadata` JSON COMMENT '元数据',
    `status` TINYINT DEFAULT 0 COMMENT '上链状态 (0-待上链 1-上链中 2-已上链 3-上链失败)',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除 (0-未删除 1-已删除)',
    PRIMARY KEY (`id`),
    KEY `idx_biz_type_id` (`biz_type`, `biz_id`),
    KEY `idx_tx_hash` (`tx_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='链上存证表';

-- 奖励事件表
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
