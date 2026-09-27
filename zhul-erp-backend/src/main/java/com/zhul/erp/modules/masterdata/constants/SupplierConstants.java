package com.zhul.erp.modules.masterdata.constants;

import java.util.Map;

/** 供应商主数据常量，码值与 V1.2.8__supplier_basic_info.sql 的字段注释一致。 */
public final class SupplierConstants {

    private SupplierConstants() {
    }

    /** 自动生成编码的前缀：SUP + 5 位补零主键 */
    public static final String CODE_PREFIX = "SUP";

    /** 供应商类型、所属行业的"未设置"（存量数据和内联创建的记录） */
    public static final int UNSET = 0;

    public static final Map<Integer, String> SUPPLIER_TYPE_LABELS = Map.of(
            1, "生产商",
            2, "经销商",
            3, "服务商",
            4, "代理商",
            5, "其他");

    public static final Map<Integer, String> INDUSTRY_LABELS = Map.of(
            1, "制造业",
            2, "原材料",
            3, "信息技术",
            4, "物流运输",
            5, "金融服务",
            6, "其他");

    /** 收款账户类型 */
    public static final int ACCOUNT_CORPORATE = 1;
    public static final int ACCOUNT_PERSONAL = 2;
    public static final Map<Integer, String> ACCOUNT_TYPE_LABELS = Map.of(
            ACCOUNT_CORPORATE, "对公",
            ACCOUNT_PERSONAL, "对私");
    public static final int MAX_ACCOUNTS = 10;

    /** 附件类型 */
    public static final int ATTACHMENT_BUSINESS_LICENSE = 1;
    public static final Map<Integer, String> ATTACHMENT_CATEGORY_LABELS = Map.of(
            ATTACHMENT_BUSINESS_LICENSE, "营业执照",
            2, "开户许可证",
            3, "资质证书",
            4, "合同",
            5, "其他");
    public static final int MAX_ATTACHMENTS = 20;
    public static final long ATTACHMENT_MAX_BYTES = 10L * 1024 * 1024;

    /** 导出行数上限，超出提示缩小筛选范围 */
    public static final int EXPORT_MAX_ROWS = 5000;

    public static final String ERROR_CREDIT_CODE_DUPLICATE = "SUPPLIER_CREDIT_CODE_DUPLICATE";
}
