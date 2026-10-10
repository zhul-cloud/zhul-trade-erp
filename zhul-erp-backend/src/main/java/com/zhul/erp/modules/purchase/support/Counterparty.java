package com.zhul.erp.modules.purchase.support;

import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.purchase.constants.PurchaseConstants;
import com.zhul.erp.modules.purchase.entity.PurchaseOrderDO;
import com.zhul.erp.modules.purchase.entity.PurchaseRequirementDO;
import org.springframework.util.StringUtils;

/** 采购对象：老供应商，或线上店铺（平台 + 店铺名，同一平台下店铺名去掉首尾空格后相同即同一家） */
public record Counterparty(Long supplierId, int channel, String shopName) {

    private static final int SHOP_NAME_MAX = 100;

    public static Counterparty supplier(Long supplierId) {
        return new Counterparty(supplierId, PurchaseConstants.CHANNEL_SUPPLIER, "");
    }

    public static Counterparty shop(Integer channel, String shopName) {
        if (channel == null || channel == PurchaseConstants.CHANNEL_SUPPLIER || !PurchaseConstants.CHANNEL_NAMES.containsKey(channel)) {
            throw new BizException("请选择平台");
        }
        String name = shopName == null ? "" : shopName.trim();
        if (name.isEmpty()) {
            throw new BizException("请填写店铺名称");
        }
        if (name.length() > SHOP_NAME_MAX) {
            throw new BizException("店铺名称不能超过 " + SHOP_NAME_MAX + " 个字符");
        }
        return new Counterparty(0L, channel, name);
    }

    /** 请求里的采购对象：有供应商用供应商，否则用平台 + 店铺 */
    public static Counterparty of(Long supplierId, Integer channel, String shopName) {
        return supplierId != null ? supplier(supplierId) : shop(channel, shopName);
    }

    public static Counterparty of(PurchaseOrderDO po) {
        return po.getChannel() == null || po.getChannel() == PurchaseConstants.CHANNEL_SUPPLIER
                ? supplier(po.getSupplierId()) : new Counterparty(0L, po.getChannel(), po.getShopName());
    }

    /** 需求的建议渠道；还没定时为 null */
    public static Counterparty suggestedBy(PurchaseRequirementDO r) {
        if (r.getSuggestedSupplierId() != null) {
            return supplier(r.getSuggestedSupplierId());
        }
        if (StringUtils.hasText(r.getSuggestedShopName()) && PurchaseConstants.CHANNEL_NAMES.containsKey(r.getSuggestedChannel())
                && r.getSuggestedChannel() != PurchaseConstants.CHANNEL_SUPPLIER) {
            return new Counterparty(0L, r.getSuggestedChannel(), r.getSuggestedShopName().trim());
        }
        return null;
    }

    public boolean isShop() {
        return channel != PurchaseConstants.CHANNEL_SUPPLIER;
    }

    public String key() {
        return isShop() ? "shop:" + channel + ":" + shopName : "supplier:" + supplierId;
    }

    /** 店铺显示名：淘宝 · 工控优选店 */
    public String shopTitle() {
        return PurchaseConstants.CHANNEL_NAMES.get(channel) + " · " + shopName;
    }

    /** 写到需求的建议渠道上 */
    public void applyTo(PurchaseRequirementDO r) {
        r.setSuggestedSupplierId(isShop() ? null : supplierId);
        r.setSuggestedChannel(channel);
        r.setSuggestedShopName(isShop() ? shopName : "");
    }
}
