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
            Map.entry("customer_contact", Policy.PRIVATE),
            Map.entry("customer_field_source", Policy.PRIVATE),
            Map.entry("customer_dup_dismiss", Policy.PRIVATE),
            Map.entry("customer_background_report", Policy.PRIVATE),
            Map.entry("country_profile", Policy.PRIVATE),
            Map.entry("opportunity", Policy.PRIVATE),
            Map.entry("opportunity_attachment", Policy.PRIVATE),
            Map.entry("opportunity_stage", Policy.SHARED_READ),
            Map.entry("opportunity_stage_log", Policy.PRIVATE),
            Map.entry("followup_item", Policy.PRIVATE),
            Map.entry("customer_followup", Policy.PRIVATE),
            Map.entry("customer_followup_item", Policy.PRIVATE),
            Map.entry("customer_annual_plan", Policy.PRIVATE),
            Map.entry("sop_stage", Policy.SHARED_READ),
            Map.entry("sop_blocker", Policy.SHARED_READ),
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
            Map.entry("sales_return_item", Policy.PRIVATE),
            // 采购与收货
            Map.entry("purchase_order", Policy.PRIVATE),
            Map.entry("purchase_order_attachment", Policy.PRIVATE),
            Map.entry("purchase_order_fee", Policy.PRIVATE),
            Map.entry("purchase_order_item", Policy.PRIVATE),
            Map.entry("purchase_order_log", Policy.PRIVATE),
            Map.entry("purchase_receipt", Policy.PRIVATE),
            Map.entry("purchase_receipt_item", Policy.PRIVATE),
            Map.entry("purchase_requirement", Policy.PRIVATE),
            Map.entry("receiving_discrepancy", Policy.PRIVATE),
            Map.entry("supplier_shipment", Policy.PRIVATE),
            Map.entry("supplier_shipment_item", Policy.PRIVATE),
            // 仓储、出库与物流
            Map.entry("stock_hold", Policy.PRIVATE),
            Map.entry("outbound_box", Policy.PRIVATE),
            Map.entry("outbound_box_item", Policy.PRIVATE),
            Map.entry("outbound_order", Policy.PRIVATE),
            Map.entry("outbound_order_item", Policy.PRIVATE),
            Map.entry("courier_waybill", Policy.PRIVATE),
            Map.entry("logistics_shipment", Policy.PRIVATE),
            Map.entry("shipment_doc_group", Policy.PRIVATE),
            Map.entry("freight_allocation", Policy.PRIVATE),
            Map.entry("forwarder_statement", Policy.PRIVATE),
            Map.entry("forwarder_statement_line", Policy.PRIVATE),
            Map.entry("shoot_task", Policy.PRIVATE),
            // 附件与素材
            Map.entry("biz_attachment", Policy.PRIVATE),
            Map.entry("biz_attachment_model", Policy.PRIVATE),
            Map.entry("media_asset", Policy.PRIVATE),
            Map.entry("media_item", Policy.PRIVATE),
            // 商品内容与营销：按租户维护
            Map.entry("product_content_task", Policy.PRIVATE),
            Map.entry("product_keyword", Policy.PRIVATE),
            Map.entry("product_keyword_use", Policy.PRIVATE),
            Map.entry("product_marketing_skip", Policy.PRIVATE),
            Map.entry("product_platform_content", Policy.PRIVATE),
            Map.entry("product_platform_keyword", Policy.PRIVATE),
            Map.entry("product_seo", Policy.PRIVATE),
            Map.entry("product_seo_faq", Policy.PRIVATE),
            Map.entry("listing_task", Policy.PRIVATE),
            // AI：平台订阅设置供获授权的公司读取
            Map.entry("ai_task", Policy.PRIVATE),
            Map.entry("sys_ai_config", Policy.SHARED_READ),
            Map.entry("sys_ai_provider_config", Policy.SHARED_READ),
            // 其他系统数据
            Map.entry("sys_log", Policy.PRIVATE),
            Map.entry("document_sequence", Policy.PRIVATE),
            Map.entry("document_template", Policy.SHARED_READ),
            Map.entry("document_template_version", Policy.SHARED_READ),
            Map.entry("exchange_rate", Policy.SHARED_READ),
            Map.entry("exchange_rate_log", Policy.PRIVATE),
            Map.entry("tenant_bank_account", Policy.PRIVATE),
            Map.entry("transit_time", Policy.SHARED_READ)
    );

    /**
     * 有 tenant_id 但不由拦截器管辖的表及理由。它们本来就跨租户，代码里显式带租户条件；
     * 新建带 tenant_id 的表须加进上面的清单或这里（TenantIsolationContractTest 检查）。
     */
    private static final String PRODUCT_LIBRARY = "全局商品库：数据一律存平台（tenant 0），"
            + "租户能否修改看商品的所属租户（owner_tenant_id），由 ProductWriteGuard / PlatformScopeGuard 校验";

    private static final Map<String, String> EXCLUDED = Map.ofEntries(
            Map.entry("product_candidate", "平台候选池：由各公司的询盘与导入汇入平台（tenant 0），各公司查看自己的来源"),
            Map.entry("product_candidate_source", "候选来源：记录来自哪家公司，平台按来源归并候选"),
            Map.entry("migration_batch", "历史迁移是平台工具：平台账号查看、执行各公司的迁移批次"),
            Map.entry("migration_id_map", "历史迁移的 ID 对照：迁移在平台范围内按目标公司执行"),
            Map.entry("resource", "全局菜单与按钮定义，全部属于平台（tenant 0）；写操作在 MenuService 校验平台身份，权限计算按全局菜单进行"),
            Map.entry("product_content_import", "上传记录：资料包写入全局商品库，各公司都能看到（不显示其他公司的确认人）；"
                    + "营销包只看本公司的，ContentImportRecordService 显式带租户条件"),
            Map.entry("product", PRODUCT_LIBRARY),
            Map.entry("product_application", PRODUCT_LIBRARY),
            Map.entry("product_brand", PRODUCT_LIBRARY),
            Map.entry("product_brand_alias", PRODUCT_LIBRARY),
            Map.entry("product_category", PRODUCT_LIBRARY),
            Map.entry("product_customs", PRODUCT_LIBRARY),
            Map.entry("product_document", PRODUCT_LIBRARY),
            Map.entry("product_fact_note", PRODUCT_LIBRARY),
            Map.entry("product_fact_task", PRODUCT_LIBRARY),
            Map.entry("product_faq", PRODUCT_LIBRARY),
            Map.entry("product_listing_price", PRODUCT_LIBRARY),
            Map.entry("product_locale", PRODUCT_LIBRARY),
            Map.entry("product_logistics", PRODUCT_LIBRARY),
            Map.entry("product_media", PRODUCT_LIBRARY),
            Map.entry("product_order_code", PRODUCT_LIBRARY),
            Map.entry("product_reference_price", PRODUCT_LIBRARY),
            Map.entry("product_relationship", PRODUCT_LIBRARY),
            Map.entry("product_relationship_note", PRODUCT_LIBRARY),
            Map.entry("product_series", PRODUCT_LIBRARY),
            Map.entry("product_specification", PRODUCT_LIBRARY)
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

    static Map<String, String> excluded() {
        return EXCLUDED;
    }

    private static String normalize(String table) {
        String t = table.replace("`", "");
        int dot = t.lastIndexOf('.');
        return (dot >= 0 ? t.substring(dot + 1) : t).toLowerCase(Locale.ROOT);
    }
}
