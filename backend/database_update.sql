-- 数据库更新脚本
-- 添加用户角色字段

-- 1. 给 users 表添加 role 字段
ALTER TABLE users ADD COLUMN role VARCHAR(20) DEFAULT 'USER' COMMENT '用户角色 (USER-普通用户 ADMIN-管理员)';

-- 2. 更新现有用户，设置默认角色为 USER
UPDATE users SET role = 'USER' WHERE role IS NULL;

-- 3. 创建第一个管理员账号（可选）
-- 密码是 "admin123" 经过BCrypt加密后的结果
-- 实际使用时请修改为更安全的密码
-- INSERT INTO users (username, email, password, role, kyc_status, risk_flag, status, created_at, updated_at)
-- VALUES ('admin', 'admin@example.com', '$2a$10$...', 'ADMIN', 0, 0, 1, NOW(), NOW());

-- 提示：
-- 1. 执行此脚本前请备份数据库
-- 2. 如需创建管理员账号，建议通过应用程序注册后，手动更新role字段：
--    UPDATE users SET role = 'ADMIN' WHERE username = 'your_username';

-- 4. 给 competitions 表添加图片字段
ALTER TABLE competitions ADD COLUMN cover_image TEXT COMMENT '封面图片 (Position 1 - 用于列表页横幅展示)';
ALTER TABLE competitions ADD COLUMN detail_image TEXT COMMENT '详情图片 (Position 2 - 用于详情页头部展示)';

-- 提示：
-- 图片字段使用TEXT类型以支持base64编码的图片或图片URL
-- 如果不上传图片，将使用前端的默认图片：
--   - cover_image 默认: frontend/src/images/11.jpeg
--   - detail_image 默认: frontend/src/images/22.jpg

-- 5. 创建社区论坛相关表（如已存在可忽略）
CREATE TABLE IF NOT EXISTS forum_posts (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    author_id BIGINT NOT NULL COMMENT '作者ID',
    competition_id BIGINT NULL COMMENT '关联竞赛',
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    category VARCHAR(50) NULL,
    tags VARCHAR(255) NULL,
    status TINYINT DEFAULT 1 COMMENT '0-草稿 1-已发布 2-隐藏',
    is_pinned TINYINT DEFAULT 0 COMMENT '是否置顶',
    view_count INT DEFAULT 0,
    reply_count INT DEFAULT 0,
    like_count INT DEFAULT 0,
    last_reply_at DATETIME NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_forum_posts_competition_id (competition_id),
    INDEX idx_forum_posts_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS forum_comments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    post_id BIGINT NOT NULL,
    parent_id BIGINT NULL,
    author_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    status TINYINT DEFAULT 1 COMMENT '0-待审核 1-正常 2-屏蔽',
    like_count INT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_forum_comments_post_id (post_id),
    CONSTRAINT fk_forum_comment_post FOREIGN KEY (post_id) REFERENCES forum_posts (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 6. 链上存证表增加链网络字段（执行前请确认无同名字段）
ALTER TABLE chain_proofs
    ADD COLUMN chain_network VARCHAR(32) DEFAULT 'FISCO' COMMENT '链网络(FISCO/POLYGON/...)';

-- 7. 内容分享表
CREATE TABLE IF NOT EXISTS content_shares (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL COMMENT '发布者',
    title VARCHAR(120) NOT NULL,
    description TEXT,
    media_type VARCHAR(20) NOT NULL COMMENT 'IMAGE/VIDEO',
    media_url VARCHAR(512) NOT NULL,
    thumbnail_url VARCHAR(512),
    duration_seconds BIGINT,
    hash_algorithm VARCHAR(20),
    file_hash VARCHAR(128),
    metadata TEXT,
    visibility TINYINT DEFAULT 1,
    fisco_status TINYINT DEFAULT 0 COMMENT 'FISCO链状态',
    polygon_status TINYINT DEFAULT 0 COMMENT 'Polygon链状态',
    fisco_tx_hash VARCHAR(255),
    polygon_tx_hash VARCHAR(255),
    fisco_block_height BIGINT,
    polygon_block_number BIGINT,
    fisco_block_time DATETIME NULL,
    polygon_block_time DATETIME NULL,
    fisco_error VARCHAR(255),
    polygon_error VARCHAR(255),
    review_status TINYINT DEFAULT 1 COMMENT '审核状态(0-待审核 1-通过 2-拒绝)',
    reviewer_id BIGINT NULL COMMENT '审核人用户ID',
    reviewed_at DATETIME NULL COMMENT '审核时间',
    review_reason VARCHAR(255) NULL COMMENT '审核备注',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_content_share_user (user_id),
    INDEX idx_content_share_media (media_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 7.1 内容分享与论坛帖子关联表
CREATE TABLE IF NOT EXISTS content_share_post_relations (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    content_share_id BIGINT NOT NULL COMMENT '内容分享ID',
    post_id BIGINT NOT NULL COMMENT '帖子ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_content_share_post (content_share_id, post_id),
    KEY idx_cspr_post_id (post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='内容分享-帖子关联表';

-- 8. 用户表新增钱包地址字段（执行前请确认无同名字段）
ALTER TABLE users
    ADD COLUMN wallet_address VARCHAR(64) UNIQUE COMMENT '绑定钱包地址';

-- 9. Gas 交易记录表（用于后台 Gas 监控面板）
CREATE TABLE IF NOT EXISTS gas_transactions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tx_hash VARCHAR(66) NOT NULL COMMENT '交易哈希',
    from_address VARCHAR(64) NOT NULL COMMENT '发送方地址',
    to_address VARCHAR(64) NULL COMMENT '接收方地址',
    contract_address VARCHAR(64) NULL COMMENT '合约地址',
    biz_type VARCHAR(32) DEFAULT 'OTHER' COMMENT '业务类型(CHECKIN/POST/COMMENT/TIP/PIN/OTHER)',
    gas_used BIGINT NOT NULL COMMENT '消耗的Gas数量',
    gas_price_gwei DECIMAL(20, 8) NOT NULL COMMENT 'Gas单价(Gwei)',
    gas_fee_matic DECIMAL(38, 18) NOT NULL COMMENT '本次交易消耗的费用(MATIC)',
    block_number BIGINT NULL COMMENT '区块高度',
    success TINYINT DEFAULT 1 COMMENT '是否执行成功',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_gas_tx_hash (tx_hash),
    KEY idx_gas_tx_create_time (create_time),
    KEY idx_gas_tx_biz_type (biz_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Polygon Gas 交易记录表';

-- 10. 内容举报表（用户举报违规内容）
CREATE TABLE IF NOT EXISTS content_reports (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    content_share_id BIGINT NOT NULL COMMENT '被举报的内容分享ID',
    reporter_id BIGINT NULL COMMENT '举报人用户ID（未登录可为空）',
    reason_code VARCHAR(32) NOT NULL COMMENT '举报类型(SPAM/ILLEGAL/INFRINGE/OTHER)',
    reason_text VARCHAR(500) NULL COMMENT '举报说明',
    status TINYINT DEFAULT 0 COMMENT '处理状态(0-待处理 1-已处理 2-已忽略)',
    handler_id BIGINT NULL COMMENT '处理人ID',
    handled_at DATETIME NULL COMMENT '处理时间',
    result_note VARCHAR(500) NULL COMMENT '处理结果说明',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    KEY idx_content_reports_share (content_share_id),
    KEY idx_content_reports_status (status),
    KEY idx_content_reports_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='内容举报记录表';
