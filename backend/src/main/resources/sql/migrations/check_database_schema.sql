-- ============================================
-- 检查数据库表结构
-- 用于验证所有必需的字段是否存在
-- ============================================

USE `competition-platform`;

-- 检查 competitions 表的字段
SELECT
    '检查 competitions 表字段' AS '检查项',
    CASE
        WHEN COUNT(*) = 7 THEN '✓ 所有字段都存在'
        ELSE CONCAT('✗ 缺少 ', 7 - COUNT(*), ' 个字段')
    END AS '结果'
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'competition-platform'
  AND TABLE_NAME = 'competitions'
  AND COLUMN_NAME IN (
    'use_ab_leaderboard',
    'public_test_ratio',
    'private_leaderboard_publish_time',
    'max_daily_submissions',
    'max_total_submissions',
    'cover_image',
    'detail_image'
  );

-- 列出缺失的字段
SELECT
    '缺失的字段' AS '类型',
    field_name AS '字段名'
FROM (
    SELECT 'use_ab_leaderboard' AS field_name
    UNION SELECT 'public_test_ratio'
    UNION SELECT 'private_leaderboard_publish_time'
    UNION SELECT 'max_daily_submissions'
    UNION SELECT 'max_total_submissions'
    UNION SELECT 'cover_image'
    UNION SELECT 'detail_image'
) AS required_fields
WHERE field_name NOT IN (
    SELECT COLUMN_NAME
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'competition-platform'
      AND TABLE_NAME = 'competitions'
);

-- 显示 competitions 表的所有字段
SELECT
    COLUMN_NAME AS '字段名',
    COLUMN_TYPE AS '类型',
    IS_NULLABLE AS '可空',
    COLUMN_DEFAULT AS '默认值',
    COLUMN_COMMENT AS '注释'
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'competition-platform'
  AND TABLE_NAME = 'competitions'
ORDER BY ORDINAL_POSITION;
