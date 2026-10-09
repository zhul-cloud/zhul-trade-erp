package com.zhul.erp.modules.document.support;

import com.zhul.erp.modules.document.constants.DocTypes;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/** 各类单据可用的占位符；上传模版时据此校验 */
public final class Placeholders {

    /** ${xxx.yyy}；名称只允许字母，便于把拼错的也识别出来再报错 */
    public static final Pattern TOKEN = Pattern.compile("\\$\\{([A-Za-z]+(?:\\.[A-Za-z]+)?)}");

    /** 报价单 / 文字报价 */
    public static final Set<String> HEADER = Set.of(
            "quotation.no", "quotation.date", "quotation.validUntil", "quotation.currency", "quotation.currencySymbol",
            "quotation.incoterm", "quotation.remark", "quotation.itemTotal", "quotation.feeTotal", "quotation.total",
            "customer.name", "customer.contact", "customer.country", "customer.address", "customer.email", "customer.phone",
            "seller.name", "seller.email", "seller.phone");

    public static final Set<String> ITEM = Set.of(
            "item.no", "item.model", "item.brand", "item.category", "item.description", "item.condition", "item.conditionEn",
            "item.leadTime", "item.leadTimeEn", "item.warranty", "item.qty", "item.unitPrice", "item.unitPriceShort", "item.amount");

    public static final Set<String> FEE = Set.of("fee.no", "fee.name", "fee.amount");

    /** PI 表头：PI 信息、买方、收货人、卖方、收款账户 */
    public static final Set<String> PI_HEADER = Set.of(
            "pi.no", "pi.date", "pi.currency", "pi.currencySymbol", "pi.deliveryTime", "pi.paymentTerm", "pi.incoterm",
            "pi.portOfShipment", "pi.remark", "pi.itemTotal", "pi.feeTotal", "pi.discount", "pi.total",
            "customer.name",
            "buyer.name", "buyer.address", "buyer.country", "buyer.taxId", "buyer.contact", "buyer.phone", "buyer.email",
            "consignee.name", "consignee.address", "consignee.country", "consignee.taxId", "consignee.contact", "consignee.phone",
            "consignee.email",
            "seller.name", "seller.email", "seller.phone",
            "bank.name", "bank.accountName", "bank.accountNo", "bank.swift", "bank.country", "bank.address", "bank.bankCode",
            "bank.branchCode");

    public static final Set<String> PI_ITEM;
    public static final Set<String> PI_FEE = Set.of("fee.no", "fee.name", "fee.amount", "fee.remark");

    static {
        Set<String> s = new HashSet<>(ITEM);
        s.addAll(Set.of("item.hsCode", "item.origin", "item.remark"));
        PI_ITEM = Set.copyOf(s);
    }

    private static final Set<String> PARTIES = Set.of(
            "buyer.name", "buyer.address", "buyer.country", "buyer.taxId", "buyer.contact", "buyer.phone", "buyer.email",
            "consignee.name", "consignee.address", "consignee.country", "consignee.taxId", "consignee.contact", "consignee.phone",
            "consignee.email", "customer.name", "seller.name", "seller.email", "seller.phone");

    /** 商业发票 CI */
    public static final Set<String> CI_HEADER;
    public static final Set<String> CI_ITEM = Set.of("item.no", "item.model", "item.brand", "item.description", "item.hsCode", "item.origin",
            "item.qty", "item.unitPrice", "item.amount");
    public static final Set<String> CI_FEE = FEE;

    /** 装箱单 PL：明细行按箱输出，箱的字段写在箱内第一行并合并单元格 */
    public static final Set<String> PL_HEADER;
    public static final Set<String> PL_ITEM = Set.of("item.no", "item.model", "item.brand", "item.description", "item.qty",
            "box.no", "box.netWeight", "box.grossWeight", "box.dimensions", "box.volume");

    static {
        Set<String> ci = new HashSet<>(PARTIES);
        ci.addAll(Set.of("ci.no", "ci.date", "ci.currency", "ci.currencySymbol", "ci.total", "ci.itemTotal", "ci.feeTotal", "ci.paymentRef",
                "pi.no", "so.no", "shipment.no", "shipment.waybill"));
        CI_HEADER = Set.copyOf(ci);
        Set<String> pl = new HashSet<>(PARTIES);
        pl.addAll(Set.of("pl.no", "pl.date", "pl.priceTerm", "pl.totalBoxes", "pl.totalQty", "pl.totalNetWeight", "pl.totalGrossWeight",
                "pl.totalVolume", "ci.no", "pi.no", "so.no", "shipment.no", "shipment.waybill"));
        PL_HEADER = Set.copyOf(pl);
    }

    public static final String ITEM_PREFIX = "item.";
    /** PL 箱字段，与明细放在同一行 */
    public static final String BOX_PREFIX = "box.";
    public static final String FEE_PREFIX = "fee.";

    private Placeholders() {
    }

    public static boolean known(String name) {
        return known(DocTypes.QUOTATION, name);
    }

    public static boolean known(int docType, String name) {
        if (docType == DocTypes.PI) {
            return PI_HEADER.contains(name) || PI_ITEM.contains(name) || PI_FEE.contains(name);
        }
        if (docType == DocTypes.CI) {
            return CI_HEADER.contains(name) || CI_ITEM.contains(name) || CI_FEE.contains(name);
        }
        if (docType == DocTypes.PL) {
            return PL_HEADER.contains(name) || PL_ITEM.contains(name);
        }
        return HEADER.contains(name) || ITEM.contains(name) || FEE.contains(name);
    }
}
