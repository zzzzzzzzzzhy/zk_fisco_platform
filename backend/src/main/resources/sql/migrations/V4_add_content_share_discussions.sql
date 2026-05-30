-- V4: 内容分享关联帖子表
CREATE TABLE IF NOT EXISTS `content_share_post_relations` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `content_share_id` BIGINT NOT NULL COMMENT '内容分享ID',
    `post_id` BIGINT NOT NULL COMMENT '论坛帖子ID',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除 (0-未删除 1-已删除)',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_content_share_post` (`content_share_id`, `post_id`),
    KEY `idx_post_id` (`post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='内容分享与帖子关联关系';
