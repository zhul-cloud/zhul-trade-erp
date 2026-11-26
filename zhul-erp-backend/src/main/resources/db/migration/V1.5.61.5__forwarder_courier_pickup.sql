-- 客户自己约 DHL、FedEx 上门取件（openspec customer-courier-pickup）：客户指定货代记交接方式与快递信息，出运单记是否上门取件。

ALTER TABLE `supplier`
    ADD COLUMN `handover_mode`   tinyint(2)  NOT NULL DEFAULT 1  COMMENT '交接方式（1-送到货代、2-上门取件），只对客户指定货代有意义' AFTER `customer_id`,
    ADD COLUMN `courier_carrier` varchar(32) NOT NULL DEFAULT '' COMMENT '上门取件的承运商（如 DHL、FedEx）' AFTER `handover_mode`,
    ADD COLUMN `courier_account` varchar(32) NOT NULL DEFAULT '' COMMENT '上门取件的客户快递账号' AFTER `courier_carrier`;

ALTER TABLE `logistics_shipment`
    ADD COLUMN `pickup` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否上门取件（0-否、1-是），登记出运时按货代交接方式记快照' AFTER `customer_forwarder`;
