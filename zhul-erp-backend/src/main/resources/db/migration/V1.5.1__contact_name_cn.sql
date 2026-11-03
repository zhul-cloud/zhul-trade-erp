-- 联系人中文名（spec master-data/customer-profile「客户联系人与决策人画像」）：系统内显示用，空时保存后由 AI 翻译回填
ALTER TABLE `customer_contact`
    ADD COLUMN `name_cn` varchar(100) NOT NULL DEFAULT '' COMMENT '中文名（系统内显示，空时由 AI 翻译回填）' AFTER `name`;
ALTER TABLE `customer`
    ADD COLUMN `contact_name_cn` varchar(100) NOT NULL DEFAULT '' COMMENT '主联系人中文名（随主联系人同步）' AFTER `contact_name`;

-- 回退：
-- ALTER TABLE `customer_contact` DROP COLUMN `name_cn`;
-- ALTER TABLE `customer` DROP COLUMN `contact_name_cn`;
