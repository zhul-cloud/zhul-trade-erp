-- 回价是否包邮（openspec sourcing-quote-enhancements）：默认包邮，回价附件存 biz_attachment（owner_type=SOURCING_QUOTE）
ALTER TABLE `sourcing_quote`
    ADD COLUMN `free_shipping` tinyint(2) NOT NULL DEFAULT 1 COMMENT '是否包邮（0-不包邮、1-包邮）' AFTER `lead_time`;
