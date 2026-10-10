package com.zhul.erp.modules.inquiry.support;

import com.zhul.erp.modules.inquiry.constants.InquiryConstants;

import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 货况、货期、生命周期字典快照。码值 0 表示未填；labels 含已停用项（展示历史数据），enabled 只含启用项（新录入校验）。
 */
public record QuoteDictSnapshot(Dict conditions, Dict leadTimes, Dict lifecycles, Dict taxRates) {

    /** 「3周」「5-7天」「10个工作日」「8周以上」：取上限换算成天数，open 表示「以上」 */
    private static final Pattern SPAN = Pattern.compile("(\\d+)\\s*(?:[-~～至到]\\s*(\\d+))?\\s*(个)?(工作日|天|日|周|星期|个月|月)(以上)?");
    private static final Pattern IN_STOCK = Pattern.compile("现货|有货|库存");

    public record Dict(Map<Integer, String> labels, Set<Integer> enabled) {

        public String label(Integer code) {
            return code == null || code == 0 ? "" : labels.getOrDefault(code, "");
        }

        /** 0 / null 视为未填，允许；其余必须是启用的字典项 */
        public boolean acceptable(Integer code) {
            return code == null || code == 0 || enabled.contains(code);
        }

        Integer codeOfName(String name) {
            for (Integer code : enabled) {
                if (labels.get(code).equals(name)) {
                    return code;
                }
            }
            return null;
        }
    }

    /** Excel 货况文本 → 码值；空为 0（未填），认不出为 null */
    public Integer conditionOf(String raw) {
        String s = raw == null ? "" : raw.trim().replace("%", "");
        if (s.isEmpty()) {
            return 0;
        }
        Integer exact = conditions.codeOfName(s);
        if (exact != null) {
            return exact;
        }
        Integer guess = null;
        if (s.equals("全新") || s.equals("原装") || s.equals("全新正品")) {
            guess = InquiryConstants.CONDITION_NEW;
        } else if (s.startsWith("99")) {
            guess = 2;
        } else if (s.contains("仿") || s.contains("国产")) {
            guess = 6;
        }
        return guess != null && conditions.enabled().contains(guess) ? guess : null;
    }

    /** Excel 货期文本 → 码值：先按名称精确匹配，再按天数落到字典里的区间；空为 0，认不出为 null */
    public Integer leadTimeOf(String raw) {
        String s = raw == null ? "" : raw.trim().replace(" ", "");
        if (s.isEmpty()) {
            return 0;
        }
        Integer exact = leadTimes.codeOfName(s);
        if (exact != null) {
            return exact;
        }
        long[] wanted = span(s);
        if (wanted == null) {
            if (IN_STOCK.matcher(s).find()) {
                for (Integer code : leadTimes.enabled()) {
                    if (IN_STOCK.matcher(leadTimes.labels().get(code)).find()) {
                        return code;
                    }
                }
            }
            return null;
        }
        long days = wanted[1];
        for (Integer code : leadTimes.enabled()) {
            long[] range = span(leadTimes.labels().get(code));
            if (range != null && days >= range[0] && (range[2] == 1 || days <= range[1])) {
                return code;
            }
        }
        return null;
    }

    /** 返回 {下限天数, 上限天数, 是否「以上」}，认不出返回 null */
    private static long[] span(String text) {
        Matcher m = SPAN.matcher(text);
        if (!m.find()) {
            return null;
        }
        int unit = switch (m.group(4)) {
            case "周", "星期" -> 7;
            case "个月", "月" -> 30;
            default -> 1;
        };
        long low = Long.parseLong(m.group(1)) * unit;
        long high = m.group(2) == null ? low : Long.parseLong(m.group(2)) * unit;
        return new long[] {low, high, m.group(5) == null ? 0 : 1};
    }
}
