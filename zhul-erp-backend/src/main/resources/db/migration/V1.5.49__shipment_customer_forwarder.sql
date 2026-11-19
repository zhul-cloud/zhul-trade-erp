-- V1.5.49：出运单交客户指定货代（openspec feedback-1119）
-- 货交给客户自己的货代、国际运输由客户安排时，登记出运只要求交货日期，运费只记送货运费，不进货代运费账单。
-- 以新建与登记出运时货代的归属为准记快照；已出运的历史单据不改（保持 0，不影响已有对账）。

ALTER TABLE `logistics_shipment`
    ADD COLUMN `customer_forwarder` tinyint(2) NOT NULL DEFAULT 0 COMMENT '是否交客户指定货代（0-否、1-是）：国际运输由客户安排，运单号选填，运费为送货运费，不进货代运费账单' AFTER `forwarder_id`;

UPDATE `logistics_shipment` s
    JOIN `supplier` f ON f.`id` = s.`forwarder_id`
SET s.`customer_forwarder` = 1
WHERE s.`status` = 1
  AND s.`deleted_at` IS NULL
  AND f.`customer_id` IS NOT NULL;

-- 回退：ALTER TABLE `logistics_shipment` DROP COLUMN `customer_forwarder`;
