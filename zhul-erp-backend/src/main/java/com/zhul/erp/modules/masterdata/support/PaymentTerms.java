package com.zhul.erp.modules.masterdata.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.masterdata.dto.PaymentTermDTO;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 付款条件：一期或多期，每期比例 + 到期时点（下单后、发货前、入库后 N 天），合计 100%。
 * 以 JSON 数组存在 supplier.payment_terms、purchase_order.payment_terms，空串为未设置。
 */
public final class PaymentTerms {

    public static final int ON_ORDER = 1;
    public static final int BEFORE_SHIPMENT = 2;
    public static final int AFTER_RECEIPT = 3;
    public static final Map<Integer, String> TRIGGER_NAMES = Map.of(ON_ORDER, "下单后", BEFORE_SHIPMENT, "发货前", AFTER_RECEIPT, "入库后");

    private static final int MAX_TERMS = 6;
    private static final int MAX_DAYS = 365;
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PaymentTerms() {
    }

    /** 校验并规整（比例两位小数、非「入库后」的天数清零）；null 或空列表返回空列表 */
    public static List<PaymentTermDTO> normalize(List<PaymentTermDTO> terms) {
        if (terms == null || terms.isEmpty()) {
            return List.of();
        }
        if (terms.size() > MAX_TERMS) {
            throw new BizException("付款条件最多 " + MAX_TERMS + " 期");
        }
        List<PaymentTermDTO> out = new ArrayList<>(terms.size());
        BigDecimal sum = BigDecimal.ZERO;
        for (PaymentTermDTO t : terms) {
            if (t == null || t.getPercent() == null || t.getPercent().signum() <= 0) {
                throw new BizException("每期比例须大于 0");
            }
            if (t.getTrigger() == null || !TRIGGER_NAMES.containsKey(t.getTrigger())) {
                throw new BizException("请选择付款时点");
            }
            int days = t.getTrigger() == AFTER_RECEIPT && t.getDays() != null ? t.getDays() : 0;
            if (days < 0 || days > MAX_DAYS) {
                throw new BizException("入库后天数须在 0 到 " + MAX_DAYS + " 之间");
            }
            PaymentTermDTO n = new PaymentTermDTO();
            n.setPercent(t.getPercent().setScale(2, RoundingMode.HALF_UP));
            n.setTrigger(t.getTrigger());
            n.setDays(days);
            sum = sum.add(n.getPercent());
            out.add(n);
        }
        if (sum.compareTo(HUNDRED) != 0) {
            throw new BizException("各期比例合计须为 100%");
        }
        return out;
    }

    public static String toJson(List<PaymentTermDTO> terms) {
        if (terms == null || terms.isEmpty()) {
            return "";
        }
        try {
            return MAPPER.writeValueAsString(terms);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("付款条件序列化失败", e);
        }
    }

    public static List<PaymentTermDTO> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<PaymentTermDTO>>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("付款条件解析失败", e);
        }
    }

    /** 显示文字：全额预付、货到付款、入库后 30 天、30% 下单后 · 70% 入库后 */
    public static String text(List<PaymentTermDTO> terms) {
        if (terms == null || terms.isEmpty()) {
            return "";
        }
        if (terms.size() == 1) {
            PaymentTermDTO t = terms.get(0);
            if (t.getTrigger() == ON_ORDER) {
                return "全额预付";
            }
            if (t.getTrigger() == AFTER_RECEIPT) {
                return t.getDays() == null || t.getDays() == 0 ? "货到付款" : "入库后 " + t.getDays() + " 天";
            }
            return "发货前付全款";
        }
        List<String> parts = new ArrayList<>(terms.size());
        for (PaymentTermDTO t : terms) {
            String when = TRIGGER_NAMES.get(t.getTrigger());
            if (t.getTrigger() == AFTER_RECEIPT && t.getDays() != null && t.getDays() > 0) {
                when = when + " " + t.getDays() + " 天";
            }
            parts.add(t.getPercent().stripTrailingZeros().toPlainString() + "% " + when);
        }
        return String.join(" · ", parts);
    }
}
