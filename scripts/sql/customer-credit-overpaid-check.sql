-- 客户余额上线前检查（只读）：列出「已到账 + 手续费差额 > 合计」的 PI 与手动订单。
-- 上线后这些多收的部分不再计入订单收入（spec finance/order-cost），经营统计对应月份的销售收入随之减少。
-- 用法：mysql ... zhul_erp < scripts/sql/customer-credit-overpaid-check.sql
-- 多收人民币按该 PI / 订单有效到账的加权到账汇率估算（Σ 毛额 × 到账汇率 ÷ Σ 毛额），HALF_UP 两位。

SELECT x.tenant_id                                              AS 租户,
       x.doc_type                                               AS 单据,
       x.doc_no                                                 AS 编号,
       x.so_no                                                  AS 销售订单,
       DATE_FORMAT(x.sales_date, '%Y-%m')                       AS 销售月份,
       c.name                                                   AS 客户,
       x.currency_code                                          AS 币种,
       x.total_amount                                           AS 合计,
       x.received_amount + x.fee_diff_amount                    AS 已到账含差额,
       x.received_amount + x.fee_diff_amount - x.total_amount   AS 多收,
       ROUND((x.received_amount + x.fee_diff_amount - x.total_amount) * IFNULL(
           (SELECT SUM(r.amount * r.exchange_rate) / NULLIF(SUM(r.amount), 0)
            FROM payment_receipt r
            WHERE r.kind = 2 AND r.status = 1 AND r.deleted_at IS NULL
              AND ((x.doc_type = 'PI' AND r.pi_id = x.id) OR (x.doc_type = '手动订单' AND r.so_id = x.id))), 0), 2)
                                                                AS 收入减少人民币估算
FROM (
    SELECT p.id, p.tenant_id, 'PI' AS doc_type, p.pi_no AS doc_no, s.so_no, s.sales_date, p.customer_id, p.currency_code,
           p.total_amount, p.received_amount, p.fee_diff_amount
    FROM proforma_invoice p
    LEFT JOIN sales_order s ON s.pi_id = p.id AND s.status = 1 AND s.deleted_at IS NULL
    WHERE p.deleted_at IS NULL AND p.status <> 4
    UNION ALL
    SELECT s.id, s.tenant_id, '手动订单', s.so_no, s.so_no, s.sales_date, s.customer_id, s.currency_code,
           s.total_amount, s.received_amount, s.fee_diff_amount
    FROM sales_order s
    WHERE s.deleted_at IS NULL AND s.status = 1 AND s.pi_id IS NULL
) x
LEFT JOIN customer c ON c.id = x.customer_id
WHERE x.received_amount + x.fee_diff_amount > x.total_amount
ORDER BY x.tenant_id, x.sales_date, x.doc_no;
