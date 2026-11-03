package com.zhul.erp.modules.masterdata.support;

import com.zhul.erp.modules.masterdata.entity.SupplierDO;

/**
 * 供应商显示名（有简称用简称，否则用名称）变了。同步发布、与改名在同一事务内处理，
 * 引用供应商的单据据此刷新保存的名称（如应付的收款方）。
 */
public record SupplierRenamedEvent(int tenantId, long supplierId, String displayName) {

    /** 单据上显示的供应商名：有简称用简称，否则用名称；最长 128 字 */
    public static String displayName(SupplierDO s) {
        String name = s.getShortName() != null && !s.getShortName().isBlank() ? s.getShortName() : s.getName();
        if (name == null) {
            return "";
        }
        return name.length() > 128 ? name.substring(0, 128) : name;
    }
}
