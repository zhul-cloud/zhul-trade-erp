package com.zhul.erp.modules.sales.support;

import com.zhul.erp.modules.document.support.RenderModel;
import com.zhul.erp.modules.quotation.support.QuotationRenderModels;
import com.zhul.erp.modules.sales.constants.SalesConstants;
import com.zhul.erp.modules.sales.dto.BankSnapshotDTO;
import com.zhul.erp.modules.sales.dto.PartyDTO;
import com.zhul.erp.modules.sales.entity.PiFeeDO;
import com.zhul.erp.modules.sales.entity.PiItemDO;
import com.zhul.erp.modules.sales.entity.PiVersionDO;
import com.zhul.erp.modules.sales.entity.ProformaInvoiceDO;
import com.zhul.erp.modules.masterdata.entity.CustomerDO;
import com.zhul.erp.modules.system.entity.UserBasicDO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** PI 渲染数据：不含采购成本、毛利率、净利润；整单折扣作为一行负数费用「Discount」输出，导出单据不显示版本号 */
public final class PiRenderModels {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private PiRenderModels() {
    }

    public static RenderModel build(ProformaInvoiceDO pi, PiVersionDO v, List<PiItemDO> items, List<PiFeeDO> fees,
                                    PartyDTO buyer, PartyDTO consignee, BankSnapshotDTO bank, CustomerDO customer,
                                    UserBasicDO owner, QuotationRenderModels.Labels labels, LocalDate date) {
        Map<String, Object> h = new LinkedHashMap<>();
        h.put("pi.no", pi.getPiNo());
        h.put("pi.date", date == null ? "" : date.format(DATE));
        h.put("pi.currency", pi.getCurrencyCode());
        h.put("pi.currencySymbol", QuotationRenderModels.symbol(pi.getCurrencyCode()));
        h.put("pi.deliveryTime", nz(v.getDeliveryTime()));
        h.put("pi.paymentTerm", nz(v.getPaymentTerm()));
        h.put("pi.incoterm", join(v.getIncoterm(), v.getIncotermPlace()));
        h.put("pi.portOfShipment", nz(v.getPortOfShipment()));
        h.put("pi.remark", nz(v.getRemark()));
        h.put("pi.itemTotal", v.getItemAmount());
        h.put("pi.feeTotal", v.getFeeAmount());
        BigDecimal discount = v.getDiscountAmount() == null ? BigDecimal.ZERO : v.getDiscountAmount();
        h.put("pi.discount", discount.negate());
        h.put("pi.total", v.getTotalAmount());
        h.put("customer.name", customer == null ? "" : nz(customer.getName()));
        party(h, "buyer", buyer);
        party(h, "consignee", consignee);
        h.put("seller.name", owner == null ? "" : nz(owner.getName()));
        h.put("seller.email", owner == null ? "" : nz(owner.getEmail()));
        h.put("seller.phone", owner == null ? "" : nz(owner.getPhone()));
        h.put("bank.name", bank == null ? "" : nz(bank.getBankName()));
        h.put("bank.accountName", bank == null ? "" : nz(bank.getAccountName()));
        h.put("bank.accountNo", bank == null ? "" : nz(bank.getAccountNo()));
        h.put("bank.swift", bank == null ? "" : nz(bank.getSwiftCode()));
        h.put("bank.country", bank == null ? "" : nz(bank.getCountry()));
        h.put("bank.address", bank == null ? "" : nz(bank.getBankAddress()));
        h.put("bank.bankCode", bank == null ? "" : nz(bank.getBankCode()));
        h.put("bank.branchCode", bank == null ? "" : nz(bank.getBranchCode()));

        List<Map<String, Object>> rows = new ArrayList<>(items.size());
        int no = 1;
        for (PiItemDO i : items) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("item.no", no++);
            m.put("item.model", nz(i.getModel()));
            m.put("item.brand", nz(i.getBrand()));
            m.put("item.category", nz(i.getCategory()));
            m.put("item.description", nz(i.getDescription()));
            m.put("item.condition", labels.conditions().getOrDefault(i.getItemCondition(), ""));
            m.put("item.conditionEn", en(labels.conditionsEn(), labels.conditions(), i.getItemCondition()));
            m.put("item.leadTime", labels.leadTimes().getOrDefault(i.getLeadTime(), ""));
            m.put("item.leadTimeEn", en(labels.leadTimesEn(), labels.leadTimes(), i.getLeadTime()));
            m.put("item.warranty", nz(i.getWarranty()));
            m.put("item.qty", i.getQuantity());
            m.put("item.unitPrice", i.getUnitPrice());
            m.put("item.unitPriceShort", QuotationRenderModels.shortPrice(i.getUnitPrice()));
            m.put("item.amount", i.getAmount());
            m.put("item.hsCode", nz(i.getHsCode()));
            m.put("item.origin", nz(i.getOriginCountry()));
            m.put("item.remark", nz(i.getRemark()));
            rows.add(m);
        }
        List<Map<String, Object>> feeRows = new ArrayList<>(fees.size() + 1);
        for (PiFeeDO f : fees) {
            feeRows.add(fee(f.getFeeName(), f.getAmount(), f.getRemark()));
        }
        if (discount.signum() > 0) {
            String remark = v.getDiscountType() != null && v.getDiscountType() == SalesConstants.DISCOUNT_PERCENT
                    ? v.getDiscountValue().stripTrailingZeros().toPlainString() + "% off" : "";
            feeRows.add(fee(SalesConstants.DISCOUNT_NAME, discount.negate(), remark));
        }
        return new RenderModel(h, rows, feeRows);
    }

    private static void party(Map<String, Object> h, String prefix, PartyDTO p) {
        h.put(prefix + ".name", p == null ? "" : nz(p.getName()));
        h.put(prefix + ".address", p == null ? "" : address(p));
        h.put(prefix + ".country", p == null ? "" : nz(p.getCountry()));
        h.put(prefix + ".taxId", p == null ? "" : nz(p.getTaxId()));
        h.put(prefix + ".contact", p == null ? "" : nz(p.getContact()));
        h.put(prefix + ".phone", p == null ? "" : nz(p.getPhone()));
        h.put(prefix + ".email", p == null ? "" : nz(p.getEmail()));
    }

    /** 详细地址 + 城市 + 州 + 邮编 + 国家，地址里已经写了的不重复 */
    static String address(PartyDTO p) {
        StringBuilder sb = new StringBuilder(nz(p.getAddress()));
        for (String part : new String[] {p.getCity(), p.getState(), p.getPostcode(), p.getCountry()}) {
            if (part != null && !part.isBlank() && !sb.toString().toLowerCase().contains(part.trim().toLowerCase())) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(part.trim());
            }
        }
        return sb.toString();
    }

    private static Map<String, Object> fee(String name, BigDecimal amount, String remark) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("fee.name", nz(name));
        m.put("fee.amount", amount);
        m.put("fee.remark", nz(remark));
        return m;
    }

    private static String en(Map<Integer, String> en, Map<Integer, String> zh, Integer code) {
        String v = en.get(code);
        return v != null ? v : zh.getOrDefault(code, "");
    }

    private static String join(String a, String b) {
        String x = nz(a).trim();
        String y = nz(b).trim();
        return x.isEmpty() ? y : y.isEmpty() ? x : x + " " + y;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
