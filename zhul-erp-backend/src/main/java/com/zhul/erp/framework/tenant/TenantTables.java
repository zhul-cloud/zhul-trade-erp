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
            Map.entry("fin_customer_refund", Policy.PRIVATE)
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
