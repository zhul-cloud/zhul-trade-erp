-- 客户阿里等级（openspec customer-ali-level）：登记或修改阿里巴巴国际站商机时带入客户资料，客户资料里也可以改
ALTER TABLE `customer`
    ADD COLUMN `ali_level` varchar(4) NOT NULL DEFAULT '' COMMENT '阿里等级（L0、L1、L1+、L2、L3、L4，空表示未填；客户来源为阿里巴巴国际站时）' AFTER `source_channel`;

-- 已登记的阿里商机：把最近一条带等级的商机的等级带入客户
UPDATE `customer` c
JOIN (
    SELECT o.`customer_id`, o.`ali_level`
    FROM `opportunity` o
    JOIN (SELECT `customer_id`, MAX(`id`) AS `id` FROM `opportunity`
          WHERE `ali_level` <> '' AND `deleted_at` IS NULL GROUP BY `customer_id`) last ON last.`id` = o.`id`
) x ON x.`customer_id` = c.`id`
SET c.`ali_level` = x.`ali_level`
WHERE c.`ali_level` = '';
