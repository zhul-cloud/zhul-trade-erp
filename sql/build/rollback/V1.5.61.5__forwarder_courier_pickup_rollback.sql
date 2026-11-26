-- 上门取件 V1.5.61.5 的反向 SQL（备用，只在需要回退该版本时手工执行，执行后还需从 flyway_schema_history 删除 1.5.61.5 的记录）。
ALTER TABLE `logistics_shipment` DROP COLUMN `pickup`;
ALTER TABLE `supplier` DROP COLUMN `courier_account`, DROP COLUMN `courier_carrier`, DROP COLUMN `handover_mode`;
