-- 应付的收款方按供应商关联刷新（问题 BUS-023）：供应商改名后，已生成的应付仍显示旧名称。
-- 今后改名由程序同步（SupplierRenamedEvent）；这里一次性把已经过时的名称刷成供应商当前的显示名
-- （有简称用简称，否则用名称，最长 128 字）。只涉及收款方为供应商（1）、货代（3）且关联了供应商的应付。
UPDATE `fin_payable` p
    JOIN `supplier` s ON s.`id` = p.`supplier_id` AND s.`tenant_id` = p.`tenant_id`
SET p.`payee_name`  = LEFT(IF(TRIM(IFNULL(s.`short_name`, '')) <> '', s.`short_name`, s.`name`), 128),
    p.`update_time` = NOW(),
    p.`update_by`   = 'sys'
WHERE p.`payee_type` IN (1, 3)
  AND p.`deleted_at` IS NULL
  AND p.`payee_name` <> LEFT(IF(TRIM(IFNULL(s.`short_name`, '')) <> '', s.`short_name`, s.`name`), 128);

-- 回退：无需回退。只刷新显示用的名称，不影响金额与核销；旧名称本就是供应商改名前的快照。
