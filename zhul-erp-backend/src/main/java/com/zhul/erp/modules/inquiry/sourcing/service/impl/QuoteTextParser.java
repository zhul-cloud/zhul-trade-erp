package com.zhul.erp.modules.inquiry.sourcing.service.impl;

import com.zhul.erp.modules.inquiry.constants.InquiryConstants;
import com.zhul.erp.modules.inquiry.support.QuoteDictSnapshot;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 店家回复的报价文字 → 预览行。纯函数，不查库：型号只在给定的任务型号里匹配（忽略大小写与分隔符，取最长），
 * 去掉型号后取单价、含税、货况、货期；没有型号也没有价格的行作为整段通用说明，补到没写明的行上。金额两位小数 HALF_UP。
 */
public final class QuoteTextParser {

    public enum Status { MATCHED, UNMATCHED, NO_PRICE }

    public record Model(Long itemId, String model) {
    }

    public record Line(String raw, Long itemId, String model, BigDecimal unitPrice, boolean taxIncluded, Integer taxRate,
                       int condition, int leadTime, String note, Status status) {
    }

    public record Result(List<Line> lines, String common) {
    }

    private static final int NOTE_MAX = 300;
    private static final String SEPARATORS = "\\s\\-_/.·－—";
    private static final Pattern NOT_TAXED = Pattern.compile("未税|不含税|不开票|无票");
    private static final Pattern TAX_RATE = Pattern.compile("\\d{1,2}\\s*%");
    /** 「单价120」「价格:120」「¥120」：现价、单价这类优先于泛指的「价」（避免「原价 3500 现价 3140」取到原价） */
    private static final Pattern[] LABELED = {
        Pattern.compile("(?:现价|单价|报价|售价|含税价|未税价|成交价)\\s*[:：]?\\s*(\\d[\\d,]*(?:\\.\\d+)?)"),
        Pattern.compile("(?:价格|价|RMB|¥|￥)\\s*[:：]?\\s*(\\d[\\d,]*(?:\\.\\d+)?)")};
    private static final Pattern YUAN = Pattern.compile("(\\d[\\d,]*(?:\\.\\d+)?)\\s*(?:元|块)");
    /** 数量：「要 2 台」「数量 2」「x2」「2台」 */
    private static final Pattern QTY_BEFORE = Pattern.compile("(?:要|需要|数量|共|x|X|×|\\*)\\s*\\d+");
    private static final Pattern QTY_AFTER = Pattern.compile("\\d+\\s*(?:台|个|只|件|套|支|根|块|pcs|PCS|Pcs|pc|PC|片|盒)");
    /** 货期：「5周」「3-5天」「2个月」「8周以上」「现货」 */
    private static final Pattern LEAD = Pattern.compile(
            "(\\d+\\s*(?:[-~～至到]\\s*\\d+)?\\s*(?:个)?(?:工作日|天|日|周|星期|个月|月)(?:以上|左右)?)|现货|有货|有现货|库存");
    private static final Pattern NUMBER = Pattern.compile("\\d[\\d,]*(?:\\.\\d+)?");

    private QuoteTextParser() {
    }

    public static Result parse(String text, List<Model> models, QuoteDictSnapshot dicts) {
        List<Model> sorted = models.stream().filter(m -> !norm(m.model()).isEmpty())
                .sorted(Comparator.comparingInt((Model m) -> norm(m.model()).length()).reversed()).toList();
        List<Parsed> parsed = new ArrayList<>();
        Set<String> commons = new LinkedHashSet<>();
        for (String rawLine : (text == null ? "" : text).split("\\r?\\n")) {
            String raw = rawLine.trim();
            if (raw.isEmpty()) {
                continue;
            }
            Parsed p = parseLine(raw, sorted, dicts);
            if (p.model == null && p.price == null) {
                commons.add(raw);
            } else {
                parsed.add(p);
            }
        }
        String common = String.join(" ", commons);
        Parsed defaults = parseLine(common, List.of(), dicts);
        List<Line> lines = new ArrayList<>(parsed.size());
        for (Parsed p : parsed) {
            boolean taxed = p.taxKnown ? p.taxed : defaults.taxKnown && defaults.taxed;
            Integer rate = taxed ? (p.taxKnown ? p.rate : defaults.rate) : null;
            int condition = p.condition != 0 ? p.condition : defaults.condition != 0 ? defaults.condition : newCondition(dicts);
            int lead = p.lead != 0 ? p.lead : defaults.lead;
            String note = common.length() > NOTE_MAX ? common.substring(0, NOTE_MAX) : common;
            Status status = p.model == null ? Status.UNMATCHED : p.price == null ? Status.NO_PRICE : Status.MATCHED;
            lines.add(new Line(p.raw, p.model == null ? null : p.model.itemId(), p.model == null ? null : p.model.model(), p.price, taxed,
                    rate, condition, lead, note, status));
        }
        return new Result(lines, common);
    }

    private static final class Parsed {
        String raw;
        Model model;
        BigDecimal price;
        boolean taxKnown;
        boolean taxed;
        Integer rate;
        int condition;
        int lead;
    }

    private static Parsed parseLine(String raw, List<Model> models, QuoteDictSnapshot dicts) {
        Parsed p = new Parsed();
        p.raw = raw;
        String rest = raw;
        int[] span = findModel(raw, models);
        if (span != null) {
            p.model = models.get(span[2]);
            rest = raw.substring(0, span[0]) + " " + raw.substring(span[1]);
        }
        // 含税写法沿用询价包导入：「含税」「含税3%」「含3%税」；写了「未税 / 不含税」为未税
        if (NOT_TAXED.matcher(rest).find()) {
            p.taxKnown = true;
            rest = NOT_TAXED.matcher(rest).replaceAll(" ");
        } else {
            Matcher tm = SourcingExcelServiceImpl.TAXED.matcher(rest);
            if (tm.find()) {
                String r = tm.group(1) != null ? tm.group(1) : tm.group(2);
                p.taxKnown = true;
                p.taxed = true;
                p.rate = r == null ? InquiryConstants.DEFAULT_TAX_RATE : Integer.parseInt(r);
                rest = rest.substring(0, tm.start()) + " " + rest.substring(tm.end());
            }
        }
        p.condition = conditionOf(rest, dicts);
        Matcher lead = LEAD.matcher(rest);
        if (lead.find()) {
            Integer code = dicts.leadTimeOf(lead.group().replace("左右", ""));
            p.lead = code == null ? 0 : code;
        }
        p.price = price(rest);
        return p;
    }

    /** 在原文里找最长的任务型号，返回 {起点, 终点, 型号下标}；比较时忽略大小写与分隔符 */
    private static int[] findModel(String raw, List<Model> models) {
        StringBuilder norm = new StringBuilder();
        List<Integer> index = new ArrayList<>();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (String.valueOf(c).matches("[" + SEPARATORS + "]")) {
                continue;
            }
            norm.append(Character.toUpperCase(c));
            index.add(i);
        }
        String line = norm.toString();
        for (int k = 0; k < models.size(); k++) {
            String m = norm(models.get(k).model());
            int at = line.indexOf(m);
            if (at >= 0) {
                return new int[] {index.get(at), index.get(at + m.length() - 1) + 1, k};
            }
        }
        return null;
    }

    static String norm(String s) {
        return s == null ? "" : s.replaceAll("[" + SEPARATORS + "]", "").toUpperCase(Locale.ROOT);
    }

    /** 单价：优先取「单价 / ¥ / 元」等字样旁的数字；否则去掉数量、货期、税率后取第一个数字。0 视为没有 */
    private static BigDecimal price(String text) {
        String hit = null;
        for (Pattern p : LABELED) {
            Matcher m = p.matcher(text);
            if (m.find()) {
                hit = m.group(1);
                break;
            }
        }
        if (hit == null) {
            Matcher y = YUAN.matcher(text);
            hit = y.find() ? y.group(1) : null;
        }
        if (hit == null) {
            String s = QTY_BEFORE.matcher(text).replaceAll(" ");
            s = QTY_AFTER.matcher(s).replaceAll(" ");
            s = LEAD.matcher(s).replaceAll(" ");
            s = TAX_RATE.matcher(s).replaceAll(" ");
            Matcher n = NUMBER.matcher(s);
            hit = n.find() ? n.group() : null;
        }
        if (hit == null) {
            return null;
        }
        BigDecimal v = new BigDecimal(hit.replace(",", "")).setScale(2, RoundingMode.HALF_UP);
        return v.signum() > 0 ? v : null;
    }

    /** 货况关键词 → 字典码值（按字典名称找，停用的不用）；没写为 0 */
    private static int conditionOf(String text, QuoteDictSnapshot dicts) {
        String[][] words = {{"99新", "99新"}, {"翻新", "翻新"}, {"二手", "二手"}, {"拆机", "拆机"}, {"国产", "国产"}, {"替代", "国产"},
                {"全新", "全新"}, {"原装", "全新"}, {"正品", "全新"}};
        for (String[] w : words) {
            if (text.contains(w[0])) {
                Integer code = codeContaining(dicts.conditions(), w[1]);
                if (code != null) {
                    return code;
                }
            }
        }
        return 0;
    }

    private static int newCondition(QuoteDictSnapshot dicts) {
        return dicts.conditions().enabled().contains(InquiryConstants.CONDITION_NEW) ? InquiryConstants.CONDITION_NEW : 0;
    }

    private static Integer codeContaining(QuoteDictSnapshot.Dict dict, String word) {
        for (Map.Entry<Integer, String> e : dict.labels().entrySet()) {
            if (dict.enabled().contains(e.getKey()) && e.getValue().contains(word)) {
                return e.getKey();
            }
        }
        return null;
    }
}
