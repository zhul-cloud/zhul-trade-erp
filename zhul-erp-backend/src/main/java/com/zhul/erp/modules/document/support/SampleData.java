package com.zhul.erp.modules.document.support;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
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
}
