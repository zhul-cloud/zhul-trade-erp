package com.zhul.erp.modules.media.support;

import java.util.Arrays;
import java.util.Optional;

/**
 * 素材类别（规格「素材来源与分类」）：分组按业务流程排序；默认是否可发客户、能否标「用于发品」。
 * 类别由上传入口决定，不由用户选择。
 */
public enum MediaCategory {

    OFFICIAL_IMAGE(1, "官方图片", "官方资料", true, true),
    PRODUCT_DOCUMENT(2, "产品文档", "官方资料", true, false),
    CUSTOMER_IMAGE(3, "客户来图", "客户询盘", false, false),
    SUPPLIER_PHOTO(4, "供方实拍", "采购询价", false, true),
    NETWORK_IMAGE(5, "网络图片", "采购询价", true, true),
    SUPPLIER_SHIPMENT(6, "供方发货", "到货验收", false, true),
    INSPECTION(7, "验货记录", "到货验收", true, false),
    DEFECT(8, "到货异常", "到货验收", false, false),
    MARKETING(9, "营销实拍", "营销拍摄", true, true),
    PACKING(10, "打包发货", "仓库发货", true, false),
    FEEDBACK(11, "客户反馈", "售后反馈", false, false);

    public static final String BIZ_ATTACHMENT = "biz_attachment";
    public static final String INQUIRY_ATTACHMENT = "customer_inquiry_attachment";
    public static final String PRODUCT_MEDIA = "product_media";
    public static final String PRODUCT_DOCUMENT_TABLE = "product_document";

    private final int code;
    private final String label;
    private final String group;
    private final boolean canSendByDefault;
    private final boolean listingAllowed;

    MediaCategory(int code, String label, String group, boolean canSendByDefault, boolean listingAllowed) {
        this.code = code;
        this.label = label;
        this.group = group;
        this.canSendByDefault = canSendByDefault;
        this.listingAllowed = listingAllowed;
    }

    public int code() {
        return code;
    }

    public String label() {
        return label;
    }

    public String group() {
        return group;
    }

    public boolean canSendByDefault() {
        return canSendByDefault;
    }

    public boolean listingAllowed() {
        return listingAllowed;
    }

    public static Optional<MediaCategory> of(Integer code) {
        return Arrays.stream(values()).filter(c -> code != null && c.code == code).findFirst();
    }
}
