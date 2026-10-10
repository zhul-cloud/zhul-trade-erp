-- ===========================
-- 报价单与 PI 的下拉字典：贸易术语地点、质保、交期、付款条件、起运港（平台字典，各租户共用；英文名显示在单据上）
-- 见 openspec/changes/polish-quotation-pi-documents
-- ===========================

INSERT INTO `dict_type` (`tenant_id`, `dict_type`, `dict_name`, `is_builtin`, `status`, `remark`, `create_by`, `update_by`)
SELECT 0, v.t, v.n, 1, 1, v.r, 'sys', 'sys'
FROM (
    SELECT 'trade_term_place' AS t, '贸易术语地点' AS n, '报价单、PI 的贸易术语地点下拉；DAP 默认取客户国家，不在此字典中；FOB 默认 Hong Kong，EXW 默认 Fuzhou' AS r
    UNION ALL SELECT 'warranty', '质保', '报价单、PI 型号行的质保下拉，默认项为新行的默认值'
    UNION ALL SELECT 'pi_delivery_time', '交期', 'PI 交期下拉；编码与货期字典 inquiry_lead_time 一一对应，新建 PI 时按型号中最长的货期带出'
    UNION ALL SELECT 'pi_payment_term', '付款条件', 'PI 付款条件下拉，默认项为新建 PI 的默认值'
    UNION ALL SELECT 'port_of_shipment', '起运港', 'PI 起运港下拉，默认项为新建 PI 的默认值'
) v
WHERE NOT EXISTS (SELECT 1 FROM `dict_type` d WHERE d.`dict_type` = v.t AND d.`tenant_id` = 0);

INSERT INTO `dict_item` (`tenant_id`, `dict_type_id`, `dict_type`, `item_code`, `item_name`, `item_name_en`, `item_value`, `sort_order`, `is_default`, `status`, `remark`, `create_by`, `update_by`)
SELECT 0, t.`id`, t.`dict_type`, v.code, v.name, v.en, v.code, v.s, v.d, 1, '', 'sys', 'sys'
FROM `dict_type` t
JOIN (
              SELECT 'trade_term_place' AS dt, 'HONG_KONG' AS code, '香港' AS name, 'Hong Kong' AS en, 1 AS s, 0 AS d
    UNION ALL SELECT 'trade_term_place', 'FUZHOU',    '福州', 'Fuzhou',    2, 0
    UNION ALL SELECT 'trade_term_place', 'XIAMEN',    '厦门', 'Xiamen',    3, 0
    UNION ALL SELECT 'trade_term_place', 'SHENZHEN',  '深圳', 'Shenzhen',  4, 0
    UNION ALL SELECT 'trade_term_place', 'SHANGHAI',  '上海', 'Shanghai',  5, 0
    UNION ALL SELECT 'trade_term_place', 'GUANGZHOU', '广州', 'Guangzhou', 6, 0

    UNION ALL SELECT 'warranty', 'Y1',   '1 年',   '1 year',      1, 1
    UNION ALL SELECT 'warranty', 'M6',   '6 个月', '6 months',    2, 0
    UNION ALL SELECT 'warranty', 'M3',   '3 个月', '3 months',    3, 0
    UNION ALL SELECT 'warranty', 'Y2',   '2 年',   '2 years',     4, 0
    UNION ALL SELECT 'warranty', 'NONE', '不质保', 'No warranty', 5, 0

    UNION ALL SELECT 'pi_delivery_time', 'IN_STOCK',     '现货：付款后 3-5 天', '3-5 days after payment',    1, 1
    UNION ALL SELECT 'pi_delivery_time', 'DAYS_1_2',     '付款后 1-2 天',       '1-2 days after payment',    2, 0
    UNION ALL SELECT 'pi_delivery_time', 'DAYS_2_3',     '付款后 2-3 天',       '2-3 days after payment',    3, 0
    UNION ALL SELECT 'pi_delivery_time', 'DAYS_3_5',     '付款后 3-5 天',       '3-5 days after payment',    4, 0
    UNION ALL SELECT 'pi_delivery_time', 'DAYS_5_7',     '付款后 5-7 天',       '5-7 days after payment',    5, 0
    UNION ALL SELECT 'pi_delivery_time', 'WEEKS_1_2',    '付款后 1-2 周',       '1-2 weeks after payment',   6, 0
    UNION ALL SELECT 'pi_delivery_time', 'WEEKS_2_4',    '付款后 2-4 周',       '2-4 weeks after payment',   7, 0
    UNION ALL SELECT 'pi_delivery_time', 'WEEKS_4_8',    '付款后 4-8 周',       '4-8 weeks after payment',   8, 0
    UNION ALL SELECT 'pi_delivery_time', 'WEEKS_8_PLUS', '付款后 8 周以上',     'Over 8 weeks after payment', 9, 0

    UNION ALL SELECT 'pi_payment_term', 'TT_100',       'T/T 100% 预付',          'T/T 100% in advance',                         1, 1
    UNION ALL SELECT 'pi_payment_term', 'TT_30_70',     'T/T 30% 定金，发货前付尾款', 'T/T 30% deposit, balance before shipment',  2, 0
    UNION ALL SELECT 'pi_payment_term', 'TT_50_50',     'T/T 50% 定金，发货前付尾款', 'T/T 50% deposit, balance before shipment',  3, 0
    UNION ALL SELECT 'pi_payment_term', 'TT_30_BL',     'T/T 30% 定金，见提单副本付尾款', 'T/T 30% deposit, balance against copy of B/L', 4, 0
    UNION ALL SELECT 'pi_payment_term', 'LC_SIGHT',     '即期信用证',             'L/C at sight',                                5, 0
    UNION ALL SELECT 'pi_payment_term', 'TRADE_ASSURANCE', '阿里巴巴信用保障',     'Alibaba Trade Assurance',                     6, 0
    UNION ALL SELECT 'pi_payment_term', 'PAYPAL',       'PayPal 100% 预付',      'PayPal 100% in advance',                      7, 0

    UNION ALL SELECT 'port_of_shipment', 'HONG_KONG', '香港', 'Hong Kong', 1, 1
    UNION ALL SELECT 'port_of_shipment', 'SHENZHEN',  '深圳', 'Shenzhen',  2, 0
    UNION ALL SELECT 'port_of_shipment', 'XIAMEN',    '厦门', 'Xiamen',    3, 0
    UNION ALL SELECT 'port_of_shipment', 'FUZHOU',    '福州', 'Fuzhou',    4, 0
    UNION ALL SELECT 'port_of_shipment', 'SHANGHAI',  '上海', 'Shanghai',  5, 0
    UNION ALL SELECT 'port_of_shipment', 'GUANGZHOU', '广州', 'Guangzhou', 6, 0
) v ON v.dt = t.`dict_type`
WHERE t.`tenant_id` = 0
  AND NOT EXISTS (SELECT 1 FROM `dict_item` i WHERE i.`dict_type_id` = t.`id` AND i.`item_code` = v.code);
