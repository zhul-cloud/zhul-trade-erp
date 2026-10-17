package com.zhul.erp.framework.storage;

/**
 * 业务环节：决定文件在存储中的目录与可见性，对应规格「存储目录」表。新增环节先在这里登记。
 * legacyModule 为迁 OSS 之前本地私有目录下的模块名，用于识别、读取尚未迁移的旧文件键。
 */
public enum StorageArea {

    INQUIRY("inquiry", false, "customer-inquiry"),
    OPPORTUNITY("opportunity", false, "opportunity"),
    SOURCING("sourcing", false, "sourcing-import"),
    SUPPLIER("supplier", false, "supplier"),
    PURCHASE("purchase", false, "purchase"),
    SHIPMENT("shipment", false, "attachment"),
    RECEIPT("receipt", false, "attachment"),
    /** 按「品牌 / 型号」归档的实拍素材 */
    MEDIA("media", false, "attachment"),
    LOGISTICS("logistics", false, "attachment"),
    FINANCE("finance", false, "payment-slip"),
    DOCUMENT("document", false, "document-template"),
    /** 站点外观图片，公开存储，不分年月 */
    SYSTEM("system", true, null),
    /** 共享商品库的图片与视频，公开存储，按「品牌 / 型号」归档 */
    PRODUCT("product", true, null);

    private final String dir;
    private final boolean publicRead;
    private final String legacyModule;

    StorageArea(String dir, boolean publicRead, String legacyModule) {
        this.dir = dir;
        this.publicRead = publicRead;
        this.legacyModule = legacyModule;
    }

    public String dir() {
        return dir;
    }

    public boolean publicRead() {
        return publicRead;
    }

    public String legacyModule() {
        return legacyModule;
    }
}
