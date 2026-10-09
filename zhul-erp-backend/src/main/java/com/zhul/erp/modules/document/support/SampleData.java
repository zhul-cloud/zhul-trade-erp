package com.zhul.erp.modules.document.support;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

/** 模版预览用的示例数据：3 个型号 + 1 项费用 */
public final class SampleData {

    private SampleData() {
    }

    public static RenderModel quotation() {
        Map<String, Object> h = new LinkedHashMap<>();
        h.put("quotation.no", "QT20261004001");
        h.put("quotation.date", "2026-10-04");
        h.put("quotation.validUntil", "2026-10-19");
        h.put("quotation.currency", "USD");
        h.put("quotation.currencySymbol", "$");
        h.put("quotation.incoterm", "EXW");
        h.put("quotation.remark", "Prices are valid for 15 days.");
        h.put("customer.name", "Sample Trading LLC");
        h.put("customer.contact", "John Smith");
        h.put("customer.country", "United States");
        h.put("customer.address", "100 Main Street, Houston, TX");
        h.put("customer.email", "john@example.com");
        h.put("customer.phone", "+1 713 000 0000");
        h.put("seller.name", "Sales Rep");
        h.put("seller.email", "sales@example.com");
        h.put("seller.phone", "+86 591 0000 0000");
        List<Map<String, Object>> items = List.of(
                item(1, "6ES7214-1AG40-0XB0", "SIEMENS", "PLC", "New Original", "现货", "In stock", "1 year", 2, "268.50"),
                item(2, "1756-L83E", "Allen-Bradley", "PLC", "New Sealed", "1-2 周", "1-2 weeks", "1 year", 1, "6120"),
                item(3, "VPSH 61", "VEGA", "压力变送器", "Used", "现货", "In stock", "3 months", 4, "85.9"));
        List<Map<String, Object>> fees = List.of(fee("Shipping (DHL)", "65"));
        BigDecimal itemTotal = items.stream().map(i -> (BigDecimal) i.get("item.amount")).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal feeTotal = fees.stream().map(f -> (BigDecimal) f.get("fee.amount")).reduce(BigDecimal.ZERO, BigDecimal::add);
        h.put("quotation.itemTotal", itemTotal);
        h.put("quotation.feeTotal", feeTotal);
        h.put("quotation.total", itemTotal.add(feeTotal));
        return new RenderModel(h, items, fees);
    }

    /** PI 示例：3 个型号 + 运费、手续费 + 5% 折扣行 */
    public static RenderModel pi() {
        Map<String, Object> h = new LinkedHashMap<>();
        h.put("pi.no", "PI20261006001");
        h.put("pi.date", "2026-10-06");
        h.put("pi.currency", "USD");
        h.put("pi.currencySymbol", "$");
        h.put("pi.deliveryTime", "3-5 days after payment");
        h.put("pi.paymentTerm", "T/T 100% in advance");
        h.put("pi.incoterm", "FOB Xiamen");
        h.put("pi.portOfShipment", "Xiamen");
        h.put("pi.remark", "");
        h.put("customer.name", "Sample Trading LLC");
        h.put("buyer.name", "Sample Trading LLC");
        h.put("buyer.address", "100 Main Street, Houston, TX 77002, United States");
        h.put("buyer.country", "United States");
        h.put("buyer.taxId", "US-12-3456789");
        h.put("buyer.contact", "John Smith");
        h.put("buyer.phone", "+1 713 000 0000");
        h.put("buyer.email", "john@example.com");
        h.put("consignee.name", "Sample Trading LLC Warehouse");
        h.put("consignee.address", "200 Port Road, Houston, TX 77029, United States");
        h.put("consignee.country", "United States");
        h.put("consignee.taxId", "");
        h.put("consignee.contact", "Mike Lee");
        h.put("consignee.phone", "+1 713 111 1111");
        h.put("consignee.email", "");
        h.put("seller.name", "Sales Rep");
        h.put("seller.email", "sales@example.com");
        h.put("seller.phone", "+86 591 0000 0000");
        h.put("bank.name", "Sample Bank, Singapore Branch");
        h.put("bank.accountName", "YOUR COMPANY NAME");
        h.put("bank.accountNo", "0000000000");
        h.put("bank.swift", "SAMPSGSGXXX");
        h.put("bank.country", "Singapore");
        h.put("bank.address", "1 Bank Street, Singapore");
        h.put("bank.bankCode", "0000");
        h.put("bank.branchCode", "000");
        List<Map<String, Object>> items = new java.util.ArrayList<>();
        for (Map<String, Object> m : quotation().items()) {
            Map<String, Object> x = new LinkedHashMap<>(m);
            x.put("item.hsCode", "85371090");
            x.put("item.origin", "Germany");
            x.put("item.remark", "");
            items.add(x);
        }
        BigDecimal itemTotal = items.stream().map(i -> (BigDecimal) i.get("item.amount")).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal discount = itemTotal.multiply(new BigDecimal("0.05")).setScale(2, java.math.RoundingMode.HALF_UP).negate();
        List<Map<String, Object>> fees = List.of(piFee("Shipping Cost", "65"), piFee("Bank Charge", "0"), piFee("Discount", discount.toPlainString()));
        BigDecimal feeTotal = new BigDecimal("65");
        h.put("pi.itemTotal", itemTotal);
        h.put("pi.feeTotal", feeTotal);
        h.put("pi.discount", discount);
        h.put("pi.total", itemTotal.add(feeTotal).add(discount));
        return new RenderModel(h, items, fees);
    }

    private static Map<String, Object> piFee(String name, String amount) {
        Map<String, Object> m = fee(name, amount);
        m.put("fee.remark", "");
        return m;
    }

    private static Map<String, Object> item(int no, String model, String brand, String category, String condition,
                                            String lead, String leadEn, String warranty, int qty, String price) {
        BigDecimal unit = new BigDecimal(price);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("item.no", no);
        m.put("item.model", model);
        m.put("item.brand", brand);
        m.put("item.category", category);
        m.put("item.description", brand + " " + category);
        m.put("item.condition", condition);
        m.put("item.conditionEn", condition);
        m.put("item.leadTime", lead);
        m.put("item.leadTimeEn", leadEn);
        m.put("item.warranty", warranty);
        m.put("item.qty", qty);
        m.put("item.unitPrice", unit);
        m.put("item.unitPriceShort", unit.stripTrailingZeros().toPlainString());
        m.put("item.amount", unit.multiply(BigDecimal.valueOf(qty)));
        return m;
    }

    private static Map<String, Object> fee(String name, String amount) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("fee.name", name);
        m.put("fee.amount", new BigDecimal(amount));
        return m;
    }

    /** CI 示例：2 个型号 + 运费 */
    public static RenderModel ci() {
        Map<String, Object> h = parties();
        h.put("ci.no", "CI20261014001");
        h.put("ci.date", "2026-10-14");
        h.put("ci.currency", "USD");
        h.put("ci.currencySymbol", "$");
        h.put("ci.paymentRef", "TT No.000126150063 dated 10.10.2026");
        h.put("pi.no", "PI20261006001");
        h.put("so.no", "SO20261008001");
        h.put("shipment.no", "SH20261014001");
        h.put("shipment.waybill", "DHL 1234567890");
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(ciItem(1, "6ES7214-1AG40-0XB0", "PLC CPU 1214C", "8537.1090", 10, new BigDecimal("280.00")));
        items.add(ciItem(2, "E3Z-D61", "Photoelectric Sensor", "8536.5000", 5, new BigDecimal("21.23")));
        List<Map<String, Object>> fees = new ArrayList<>();
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("fee.name", "Shipping Cost");
        f.put("fee.amount", new BigDecimal("300.00"));
        fees.add(f);
        BigDecimal itemTotal = new BigDecimal("2906.15");
        h.put("ci.itemTotal", itemTotal);
        h.put("ci.feeTotal", new BigDecimal("300.00"));
        h.put("ci.total", itemTotal.add(new BigDecimal("300.00")));
        return new RenderModel(h, items, fees);
    }

    /** PL 示例：2 箱，第 1 箱 2 个型号 */
    public static RenderModel pl() {
        Map<String, Object> h = parties();
        h.put("pl.no", "PL20261014001");
        h.put("pl.date", "2026-10-14");
        h.put("pl.priceTerm", "DAP Dhaka");
        h.put("pl.totalBoxes", 2);
        h.put("pl.totalQty", 25);
        h.put("pl.totalNetWeight", new BigDecimal("34.32"));
        h.put("pl.totalGrossWeight", new BigDecimal("36.00"));
        h.put("pl.totalVolume", new BigDecimal("0.102"));
        h.put("ci.no", "CI20261014001");
        h.put("pi.no", "PI20261006001");
        h.put("so.no", "SO20261008001");
        h.put("shipment.no", "SH20261014001");
        h.put("shipment.waybill", "DHL 1234567890");
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(plItem(1, 1, "6ES7214-1AG40-0XB0", 10, new BigDecimal("28.52"), new BigDecimal("30.00"), "55x40x30", new BigDecimal("0.066")));
        items.add(plItem(2, 1, "6ES7215-1AG40-0XB0", 10, null, null, null, null));
        items.add(plItem(3, 2, "E3Z-D61", 5, new BigDecimal("5.80"), new BigDecimal("6.00"), "40x30x30", new BigDecimal("0.036")));
        return new RenderModel(h, items, List.of());
    }

    private static Map<String, Object> parties() {
        Map<String, Object> h = new LinkedHashMap<>();
        h.put("customer.name", "Sample Trading LLC");
        h.put("buyer.name", "Sample Trading LLC");
        h.put("buyer.address", "100 Main Street, Houston, TX 77002, United States");
        h.put("buyer.country", "United States");
        h.put("buyer.taxId", "US-12-3456789");
        h.put("buyer.contact", "John Smith");
        h.put("buyer.phone", "+1 713 000 0000");
        h.put("buyer.email", "john@example.com");
        h.put("consignee.name", "Sample Trading LLC Warehouse");
        h.put("consignee.address", "200 Port Road, Houston, TX 77029, United States");
        h.put("consignee.country", "United States");
        h.put("consignee.taxId", "");
        h.put("consignee.contact", "Mike Lee");
        h.put("consignee.phone", "+1 713 111 1111");
        h.put("consignee.email", "");
        h.put("seller.name", "Sales Rep");
        h.put("seller.email", "sales@example.com");
        h.put("seller.phone", "+86 591 0000 0000");
        return h;
    }

    private static Map<String, Object> ciItem(int no, String model, String desc, String hs, int qty, BigDecimal price) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("item.no", no);
        m.put("item.model", model);
        m.put("item.brand", "");
        m.put("item.description", desc);
        m.put("item.hsCode", hs);
        m.put("item.origin", "China");
        m.put("item.qty", qty);
        m.put("item.unitPrice", price);
        m.put("item.amount", price.multiply(BigDecimal.valueOf(qty)));
        return m;
    }

    private static Map<String, Object> plItem(int no, int box, String model, int qty, BigDecimal nw, BigDecimal gw, String dims, BigDecimal vol) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(RenderModel.BOX_GROUP, box);
        m.put("item.no", no);
        m.put("item.model", model);
        m.put("item.brand", "");
        m.put("item.description", "");
        m.put("item.qty", qty);
        m.put("box.no", gw == null ? "" : box);
        m.put("box.netWeight", nw == null ? "" : nw);
        m.put("box.grossWeight", gw == null ? "" : gw);
        m.put("box.dimensions", dims == null ? "" : dims);
        m.put("box.volume", vol == null ? "" : vol);
        return m;
    }
}
