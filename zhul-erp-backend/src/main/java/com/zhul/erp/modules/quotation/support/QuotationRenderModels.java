package com.zhul.erp.modules.quotation.support;

import java.util.Locale;
import com.zhul.erp.modules.quotation.constants.QuotationConstants;
import com.zhul.erp.modules.document.support.RenderModel;
import com.zhul.erp.modules.masterdata.entity.CustomerDO;
import com.zhul.erp.modules.quotation.entity.QuotationDO;
import com.zhul.erp.modules.quotation.entity.QuotationFeeDO;
import com.zhul.erp.modules.quotation.entity.QuotationItemDO;
import com.zhul.erp.modules.system.entity.UserBasicDO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 报价单 → 模版渲染数据。只放对客户可见的字段：不含采购成本价、毛利率、净利润、采购渠道与店铺。
 */
public final class QuotationRenderModels {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final Map<String, String> SYMBOLS = Map.of("USD", "$", "EUR", "€", "GBP", "£", "JPY", "¥", "RUB", "₽", "CNY", "¥");

    private QuotationRenderModels() {
    }

    /** 字典码值 → 名称（中文 / 英文），英文缺失时回退中文 */
    public record Labels(Map<Integer, String> conditions, Map<Integer, String> conditionsEn,
                         Map<Integer, String> leadTimes, Map<Integer, String> leadTimesEn) {
    }

    public static RenderModel build(QuotationDO q, List<QuotationItemDO> items, List<QuotationFeeDO> fees, CustomerDO customer,
                                    UserBasicDO owner, Labels labels, LocalDate quoteDate) {
        Map<String, Object> h = new LinkedHashMap<>();
        h.put("quotation.no", q.getQuotationNo());
        h.put("quotation.date", quoteDate == null ? "" : quoteDate.format(DATE));
        h.put("quotation.validUntil", q.getValidUntil() == null ? "" : q.getValidUntil().format(DATE));
        h.put("quotation.currency", q.getCurrencyCode());
        h.put("quotation.currencySymbol", symbol(q.getCurrencyCode()));
        h.put("quotation.incoterm", join(q.getIncoterm(), q.getIncotermPlace()));
        h.put("quotation.remark", nz(q.getRemark()));
        h.put("quotation.itemTotal", q.getItemAmount());
        h.put("quotation.feeTotal", q.getFeeAmount());
        h.put("quotation.total", q.getTotalAmount());
        h.put("customer.name", customer == null ? "" : nz(customer.getName()));
        h.put("customer.contact", customer == null ? "" : nz(customer.getContactName()));
        h.put("customer.country", customer == null ? "" : nz(customer.getCountry()));
        h.put("customer.address", customer == null ? "" : nz(customer.getAddress()));
        h.put("customer.email", customer == null ? "" : nz(customer.getContactEmail()));
        h.put("customer.phone", customer == null ? "" : nz(customer.getContactPhone()));
        h.put("seller.name", sellerName(owner));
        h.put("seller.email", owner == null ? "" : nz(owner.getEmail()));
        h.put("seller.phone", owner == null ? "" : nz(owner.getPhone()));
        List<Map<String, Object>> rows = new ArrayList<>(items.size());
        int no = 1;
        for (QuotationItemDO i : items) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("item.no", no++);
            m.put("item.model", nz(i.getModel()));
            m.put("item.brand", nz(i.getBrand()));
            m.put("item.category", nz(i.getCategory()));
            m.put("item.description", customerDescription(i.getDescriptionEn(), i.getDescription()));
            m.put("item.condition", labels.conditions().getOrDefault(i.getItemCondition(), ""));
            m.put("item.conditionEn", en(labels.conditionsEn(), labels.conditions(), i.getItemCondition()));
            m.put("item.leadTime", labels.leadTimes().getOrDefault(i.getLeadTime(), ""));
            m.put("item.leadTimeEn", en(labels.leadTimesEn(), labels.leadTimes(), i.getLeadTime()));
            m.put("item.warranty", nz(i.getWarranty()));
            m.put("item.qty", i.getQuantity());
            m.put("item.unitPrice", i.getUnitPrice());
            m.put("item.unitPriceShort", shortPrice(i.getUnitPrice()));
            m.put("item.amount", i.getAmount());
            if (isNoStockLine(i)) {
                // 无货行：单价位置写无货原因、小计留空；停产有替代型号时描述后追加替代型号
                NoStockText t = noStockText(i);
                m.put(RenderModel.NO_STOCK, true);
                m.put(RenderModel.NO_STOCK_TEXT, t.chat());
                m.put("item.unitPrice", t.label());
                m.put("item.unitPriceShort", t.label());
                m.put("item.amount", null);
                if (!t.replacement().isEmpty()) {
                    String desc = customerDescription(i.getDescriptionEn(), i.getDescription()).trim();
                    m.put("item.description", (desc.isEmpty() ? "" : desc + " · ") + "Discontinued, replacement: " + t.replacement());
                }
            } else if (!nz(i.getReplacementModel()).trim().isEmpty()) {
                // 有价格的停产替代行：描述后追加，文字报价行尾追加（spec quotation/quotation「停产替代型号提示」）
                String replacement = i.getReplacementModel().trim();
                String desc = customerDescription(i.getDescriptionEn(), i.getDescription()).trim();
                m.put("item.description", (desc.isEmpty() ? "" : desc + " · ") + "Discontinued, replaced by " + replacement);
                m.put(RenderModel.REPLACEMENT_NOTE, "discontinued, replaced by " + replacement);
            }
            rows.add(m);
        }
        List<Map<String, Object>> feeRows = new ArrayList<>(fees.size());
        for (QuotationFeeDO f : fees) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("fee.name", nz(f.getFeeName()));
            m.put("fee.amount", f.getAmount());
            feeRows.add(m);
        }
        return new RenderModel(h, rows, feeRows);
    }

    private static final String NO_STOCK_LABEL = "No stock";
    private static final String DISCONTINUED_LABEL = "Discontinued";

    /** 无货行给客户看的文字：label 写在导出的单价位置，chat 用于文字报价（小写），replacement 非空时导出描述后追加 */
    record NoStockText(String label, String chat, String replacement) {
    }

    /**
     * 按无货原因输出（spec quotation/quotation「文字报价」「正式报价单导出」）：
     * 暂时缺货 No stock；已停产 Discontinued；停产有替代型号 No stock + 替代型号；其他为业务员填写的说明。
     * 原因未设置（0）的按是否有替代型号判断，与上线前一致；「停产有替代型号」没填替代型号的按暂时缺货输出。
     */
    static NoStockText noStockText(QuotationItemDO i) {
        String replacement = nz(i.getReplacementModel()).trim();
        int reason = i.getNoStockReason() == null ? QuotationConstants.NO_STOCK_NONE : i.getNoStockReason();
        if (reason == QuotationConstants.NO_STOCK_NONE) {
            reason = replacement.isEmpty() ? QuotationConstants.NO_STOCK_OUT : QuotationConstants.NO_STOCK_REPLACEMENT;
        }
        String note = nz(i.getNoStockNote()).trim();
        if (reason == QuotationConstants.NO_STOCK_OTHER && !note.isEmpty()) {
            return new NoStockText(note, note.toLowerCase(Locale.ROOT), "");
        }
        if (reason == QuotationConstants.NO_STOCK_DISCONTINUED) {
            return new NoStockText(DISCONTINUED_LABEL, "discontinued", "");
        }
        if (reason == QuotationConstants.NO_STOCK_REPLACEMENT && !replacement.isEmpty()) {
            return new NoStockText(NO_STOCK_LABEL, "no stock, discontinued, replacement: " + replacement, replacement);
        }
        return new NoStockText(NO_STOCK_LABEL, "no stock", "");
    }

    /** 无货行：询价结果无货且没有填售价（填了售价就按正常报价） */
    public static boolean isNoStockLine(QuotationItemDO i) {
        return Objects.equals(i.getNoStock(), 1) && (i.getUnitPrice() == null || i.getUnitPrice().signum() == 0);
    }

    /** 发给客户的描述：英文描述，没有时退回中文描述 */
    public static String customerDescription(String en, String zh) {
        return en != null && !en.isBlank() ? en.trim() : nz(zh);
    }

    /** 发给客户的单据上的业务员：用昵称（一般是英文名），没填昵称才用姓名 */
    public static String sellerName(UserBasicDO owner) {
        if (owner == null) {
            return "";
        }
        String nick = owner.getNickname();
        return nick != null && !nick.isBlank() ? nick.trim() : nz(owner.getName());
    }

    public static String symbol(String currency) {
        return SYMBOLS.getOrDefault(currency, currency == null ? "" : currency + " ");
    }

    /** 463.20 → 463.2，400.00 → 400 */
    public static String shortPrice(BigDecimal price) {
        if (price == null) {
            return "";
        }
        return price.signum() == 0 ? "0" : price.stripTrailingZeros().toPlainString();
    }

    private static String en(Map<Integer, String> en, Map<Integer, String> zh, Integer code) {
        String v = en.get(code);
        return v != null ? v : zh.getOrDefault(code, "");
    }

    /** 贸易术语 + 地点，如 DAP Guatemala */
    public static String incoterm(String incoterm, String place) {
        return join(incoterm, place);
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
