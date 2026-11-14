-- 财务收付退单据化（change finance-cash-docs）：收款单新增单号 SK + 年月日 + 3 位流水，备注放宽到 500 字
-- 历史到账（kind = 2，含已作废）按租户、登记日期、ID 顺序补号，并写入单据编号流水，新号接着往下排；水单（kind = 1）不编号
-- 回退：删除 receipt_no 列与索引、删除 document_sequence 中 doc_type = 'SK' 的行；note 改回 300 前先确认没有超过 300 字的备注

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'payment_receipt' AND COLUMN_NAME = 'receipt_no');
SET @s := IF(@c = 0,
    'ALTER TABLE `payment_receipt` ADD COLUMN `receipt_no` varchar(24) NOT NULL DEFAULT '''' COMMENT ''收款单号（SK + 年月日 + 3 位流水；水单为空）'' AFTER `tenant_id`, ADD KEY `idx_receipt_no` (`tenant_id`, `receipt_no`)',
    'SELECT 1');
PREPARE stmt FROM @s;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE `payment_receipt`
    MODIFY COLUMN `note` varchar(500) NOT NULL DEFAULT '' COMMENT '说明';

UPDATE `payment_receipt` t
JOIN (SELECT `id`, DATE(`create_time`) AS d,
             ROW_NUMBER() OVER (PARTITION BY `tenant_id`, DATE(`create_time`) ORDER BY `id`) AS n
      FROM `payment_receipt`
      WHERE `kind` = 2 AND `deleted_at` IS NULL AND `receipt_no` = '') z ON z.`id` = t.`id`
SET t.`receipt_no` = CONCAT('SK', DATE_FORMAT(z.d, '%Y%m%d'), LPAD(z.n, 3, '0'));

INSERT INTO `document_sequence` (`tenant_id`, `doc_type`, `biz_date`, `last_no`)
SELECT `tenant_id`, 'SK', DATE(`create_time`), COUNT(*)
FROM `payment_receipt`
WHERE `kind` = 2 AND `deleted_at` IS NULL AND `receipt_no` <> ''
GROUP BY `tenant_id`, DATE(`create_time`)
ON DUPLICATE KEY UPDATE `last_no` = GREATEST(`last_no`, VALUES(`last_no`));
