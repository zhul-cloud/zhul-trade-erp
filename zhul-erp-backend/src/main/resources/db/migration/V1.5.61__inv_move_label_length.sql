-- BUS-092：库存流水业务说明放宽到 128 字。商品合并的说明带原商品「品牌 / 型号」，常超过原来的 32 字，
-- 导致商品候选批量通过（并入已有商品）写流水失败。
ALTER TABLE `inv_move`
    MODIFY COLUMN `biz_label` varchar(128) NOT NULL DEFAULT '' COMMENT '业务补充说明（如其他出库类型「报废」、商品合并的原商品）';

-- 回退（确认没有超过 32 字的说明后再执行，否则会截断失败）：
-- ALTER TABLE `inv_move` MODIFY COLUMN `biz_label` varchar(32) NOT NULL DEFAULT '' COMMENT '业务补充说明（如其他出库类型「报废」）';
