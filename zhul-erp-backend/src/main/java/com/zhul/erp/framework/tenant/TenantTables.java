package com.zhul.erp.framework.tenant;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 租户拦截器管辖的表及其隔离方式（audit SEC-011，方案 C：分类租户拦截器、按模块逐步启用）。
 * 不在清单里的表暂不由拦截器加租户条件，仍靠代码里手写的条件；按模块逐步加入。
 */
public final class TenantTables {

    /** 隔离方式 */
    public enum Policy {
        /** 本租户私有：查询、修改、删除都只限当前租户 */
        PRIVATE,
        /** 平台共享、租户只读：查询可见平台（0）与本租户，修改、删除只限当前租户 */
        SHARED_READ,
        /** 平台共享、租户可改（如字典项，PRD 允许租户维护全局内置项）：查询、修改、删除都可见平台与本租户 */
        SHARED_WRITE
    }

    private static final Map<String, Policy> ENFORCED = Map.ofEntries(
            // 系统：用户与组织
            Map.entry("user_basic", Policy.PRIVATE),
            Map.entry("account", Policy.PRIVATE),
            Map.entry("department", Policy.PRIVATE),
            Map.entry("position", Policy.PRIVATE),
            // 平台内置角色（如管理员）被各租户的用户引用，租户只读
            Map.entry("role", Policy.SHARED_READ),
            // 系统参数：平台默认值 + 租户覆盖值，租户不能改平台行（平台模板由服务复制到本租户后再改）
            Map.entry("sys_config", Policy.SHARED_READ),
            Map.entry("dict_type", Policy.SHARED_WRITE),
            Map.entry("dict_item", Policy.SHARED_WRITE),
            // 供应商档案
            Map.entry("supplier", Policy.PRIVATE),
            Map.entry("supplier_attachment", Policy.PRIVATE),
            Map.entry("supplier_bank_account", Policy.PRIVATE),
            Map.entry("supplier_product_scope", Policy.PRIVATE),
            // 财务
            Map.entry("fin_customer_refund", Policy.PRIVATE),
            Map.entry("fin_customer_credit_consumption", Policy.PRIVATE),
            Map.entry("fin_customer_credit_entry", Policy.PRIVATE),
            Map.entry("fin_expense", Policy.PRIVATE),
            Map.entry("fin_expense_allocation", Policy.PRIVATE),
            Map.entry("fin_fx_settlement", Policy.PRIVATE),
            Map.entry("fin_fx_settlement_allocation", Policy.PRIVATE),
            Map.entry("fin_other_income", Policy.PRIVATE),
            Map.entry("fin_payable", Policy.PRIVATE),
            Map.entry("fin_payable_installment", Policy.PRIVATE),
            Map.entry("fin_payment", Policy.PRIVATE),
            Map.entry("fin_payment_allocation", Policy.PRIVATE),
            Map.entry("fin_refund_request", Policy.PRIVATE),
            Map.entry("fin_reimbursement", Policy.PRIVATE),
            Map.entry("fin_reimbursement_line", Policy.PRIVATE),
            // 客户、商机（商机阶段由平台统一定义，租户只读）
            Map.entry("customer", Policy.PRIVATE),
            Map.entry("customer_party", Policy.PRIVATE),
            Map.entry("opportunity", Policy.PRIVATE),
            Map.entry("opportunity_attachment", Policy.PRIVATE),
            Map.entry("opportunity_stage", Policy.SHARED_READ),
            Map.entry("opportunity_stage_log", Policy.PRIVATE),
            // 询盘与询价
            Map.entry("customer_inquiry", Policy.PRIVATE),
            Map.entry("customer_inquiry_append", Policy.PRIVATE),
            Map.entry("customer_inquiry_attachment", Policy.PRIVATE),
            Map.entry("inquiry_item", Policy.PRIVATE),
            Map.entry("inquiry_order", Policy.PRIVATE),
            Map.entry("inquiry_order_item", Policy.PRIVATE),
            Map.entry("inquiry_order_item_quote", Policy.PRIVATE),
            Map.entry("inquiry_order_supplier", Policy.PRIVATE),
            Map.entry("sourcing_assign_rule", Policy.PRIVATE),
            Map.entry("sourcing_import", Policy.PRIVATE),
            Map.entry("sourcing_item_handoff", Policy.PRIVATE),
            Map.entry("sourcing_quote", Policy.PRIVATE),
            Map.entry("sourcing_task", Policy.PRIVATE),
            Map.entry("sourcing_task_assignee", Policy.PRIVATE),
            // 报价、PI、销售订单、收款、售后
            // 定价规则：平台默认值 + 租户自己的规则（有则用自己的）
            Map.entry("pricing_amount_tier", Policy.SHARED_READ),
            Map.entry("pricing_condition_margin", Policy.SHARED_READ),
            Map.entry("quotation", Policy.PRIVATE),
            Map.entry("quotation_fee", Policy.PRIVATE),
            Map.entry("quotation_item", Policy.PRIVATE),
            Map.entry("quotation_send_log", Policy.PRIVATE),
            Map.entry("quotation_version", Policy.PRIVATE),
            Map.entry("proforma_invoice", Policy.PRIVATE),
            Map.entry("proforma_invoice_fee", Policy.PRIVATE),
            Map.entry("proforma_invoice_item", Policy.PRIVATE),
            Map.entry("proforma_invoice_send_log", Policy.PRIVATE),
            Map.entry("proforma_invoice_version", Policy.PRIVATE),
            Map.entry("sales_order", Policy.PRIVATE),
            Map.entry("sales_order_cost_snapshot", Policy.PRIVATE),
            Map.entry("sales_order_fee", Policy.PRIVATE),
            Map.entry("sales_order_item", Policy.PRIVATE),
            Map.entry("sales_order_partial_cancel", Policy.PRIVATE),
            Map.entry("payment_receipt", Policy.PRIVATE),
            Map.entry("after_sales", Policy.PRIVATE),
            Map.entry("after_sales_item", Policy.PRIVATE),
            Map.entry("after_sales_log", Policy.PRIVATE),
            Map.entry("sales_return", Policy.PRIVATE),
            Map.entry("sales_return_disposition", Policy.PRIVATE),
            Map.entry("sales_return_item", Policy.PRIVATE)
    );

    /** SQL 里是否出现已纳入管辖的表名（整词匹配，{@code account} 不匹配 {@code account_access_token}） */
    private static final Pattern MENTIONED = Pattern.compile(
            "(?i)(?<![A-Za-z0-9_])(" + String.join("|", ENFORCED.keySet()) + ")(?![A-Za-z0-9_])");

    private TenantTables() {
    }

    /** SQL 是否可能涉及纳入管辖的表；误判为涉及只会多解析一次，不影响结果 */
    public static boolean mentions(String sql) {
        return sql != null && MENTIONED.matcher(sql).find();
    }

    /** 表的隔离方式；未纳入拦截器管辖时为 null */
    public static Policy policy(String table) {
        return table == null ? null : ENFORCED.get(normalize(table));
    }

    static Map<String, Policy> enforced() {
        return ENFORCED;
    }

    private static String normalize(String table) {
        String t = table.replace("`", "");
        int dot = t.lastIndexOf('.');
        return (dot >= 0 ? t.substring(dot + 1) : t).toLowerCase(Locale.ROOT);
    }
}
