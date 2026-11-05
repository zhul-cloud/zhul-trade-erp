-- V1.5.16：采购订单其他费用增加类型，支持记供应商或平台优惠（2026-11-05 反馈第 13 条）。
-- 优惠金额存正数，合计里减去；已有数据都是费用，行为不变。
-- 回退：确认没有 fee_type = 2 的行后执行 ALTER TABLE `purchase_order_fee` DROP COLUMN `fee_type`;
--       有优惠行时直接删列会把优惠当成费用，合计变大。
ALTER TABLE `purchase_order_fee`
    ADD COLUMN `fee_type` tinyint(2) NOT NULL DEFAULT 1 COMMENT '类型（1-费用、2-优惠）' AFTER `fee_name`;
