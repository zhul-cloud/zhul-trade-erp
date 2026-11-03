package com.zhul.erp.modules.masterdata.customerbackground.support;

import com.zhul.erp.modules.masterdata.customerbackground.dto.BackgroundReportDetailVO;
import com.zhul.erp.modules.masterdata.customerbackground.dto.BackgroundSuggestion;
import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.web.util.HtmlUtils;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 背调报告导出的 HTML（design 决策 8）：标题、客户、编号、时间、核心结论色块、正文、字段采纳表。
 * 正文是 AI 或上传的文字，Markdown 里的原始 HTML 一律转义，链接只保留安全协议。
 */
public final class BackgroundReportHtml {

    private static final List<Extension> EXTENSIONS = List.of(TablesExtension.create());
    private static final Parser PARSER = Parser.builder().extensions(EXTENSIONS).build();
    private static final HtmlRenderer RENDERER = HtmlRenderer.builder().extensions(EXTENSIONS)
            .escapeHtml(true).sanitizeUrls(true).build();
    private static final String TABLE = "<table width=\"100%\" border=\"1\" bordercolor=\"#d1d5db\" cellpadding=\"5\" cellspacing=\"0\">";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** 跟进优先级：名称与色块颜色 */
    private static final Map<Integer, String[]> PRIORITY = Map.of(
            1, new String[] {"立即重点跟进", "#15803d", "#f0fdf4"},
            2, new String[] {"正常跟进", "#1d4ed8", "#eff6ff"},
            3, new String[] {"低优先级", "#b45309", "#fffbeb"},
            4, new String[] {"不建议跟进", "#b91c1c", "#fef2f2"});
    private static final String[] NO_PRIORITY = {"未出结论", "#4b5563", "#f9fafb"};

    /** LibreOffice 的 HTML 导入只认部分 CSS：表格边框、底色、宽度用 HTML 属性写，字体与段距用 CSS */
    private static final String CSS = """
            body,p,td,th,li,h1,h2,h3,h4{font-family:"Noto Sans CJK SC","PingFang SC","Microsoft YaHei",sans-serif;color:#1f2937}
            body,p,li{font-size:10.5pt}
            p,li{margin-top:0;margin-bottom:4pt}
            td,th{font-size:9.5pt;margin:0}
            h1{font-size:18pt;margin:0 0 4pt 0}
            h2{font-size:13pt;margin:14pt 0 6pt 0}
            h3{font-size:11.5pt;margin:10pt 0 4pt 0}
            .meta{color:#6b7280;font-size:9pt}
            .k{color:#6b7280}
            """;

    private BackgroundReportHtml() {
    }

    public static String markdownToHtml(String markdown) {
        // Markdown 表格同样补上边框与宽度属性
        return RENDERER.render(PARSER.parse(markdown == null ? "" : markdown)).replace("<table>", TABLE);
    }

    public static String render(String customerName, BackgroundReportDetailVO r) {
        String[] p = PRIORITY.getOrDefault(r.getPriority(), NO_PRIORITY);
        StringBuilder h = new StringBuilder(8192);
        h.append("<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>").append(e(r.getReportNo())).append("</title><style>")
                .append(CSS).append("</style></head><body>");
        h.append("<h1>客户背调报告</h1>");
        h.append("<div class=\"meta\">客户：").append(e(customerName))
                .append("　｜　编号：").append(e(r.getReportNo()))
                .append("　｜　来源：").append(r.getSource() != null && r.getSource() == 2 ? "上传" : "AI 背调")
                .append("　｜　时间：").append(r.getCreateTime() == null ? "" : r.getCreateTime().format(TIME))
                .append("　｜　发起人：").append(e(r.getRequestedByName())).append("</div>");

        // 核心结论色块：一个带底色的表格，第一行是优先级与评分
        h.append("<table width=\"100%\" border=\"1\" bordercolor=\"").append(p[1]).append("\" cellpadding=\"6\" cellspacing=\"0\" bgcolor=\"")
                .append(p[2]).append("\"><tr><td colspan=\"2\"><font color=\"").append(p[1]).append("\" size=\"4\"><b>").append(p[0]);
        if (r.getScore() != null && r.getScore() > 0) {
            h.append("　综合评分 ").append(r.getScore()).append(" / 5");
        }
        h.append("</b></font></td></tr>");
        row(h, "主要矛盾", r.getMainConflict());
        row(h, "第一步行动", r.getFirstAction());
        row(h, "客户身份", r.getIdentityNote());
        row(h, "产品货源", r.getSupplyNote());
        row(h, "关键发现", r.getKeyFinding());
        row(h, "最大障碍", r.getObstacle());
        h.append("</table>");

        h.append("<h2>报告正文</h2>").append(markdownToHtml(r.getReportMarkdown()));

        List<BackgroundSuggestion> list = r.getSuggestions() == null ? List.of() : r.getSuggestions();
        h.append("<h2>字段采纳情况</h2>");
        if (list.isEmpty()) {
            h.append("<p>没有字段建议。</p>");
        } else {
            h.append(TABLE).append("<tr bgcolor=\"#f3f4f6\"><th width=\"18%\">字段</th><th width=\"22%\">建议值</th><th>依据</th>"
                    + "<th width=\"20%\">状态</th></tr>");
            for (BackgroundSuggestion s : list) {
                String field = s.getLabel() == null ? s.getField() : s.getLabel();
                if (s.getContact() != null && s.getContact().getName() != null && !s.getContact().getName().isBlank()) {
                    field += "（" + s.getContact().getName() + "）";
                }
                h.append("<tr valign=\"top\"><td>").append(e(field)).append("</td><td>").append(e(s.getValue()))
                        .append("</td><td>").append(e(s.getEvidence())).append("</td><td>").append(e(state(s))).append("</td></tr>");
            }
            h.append("</table>");
        }
        h.append("</body></html>");
        return h.toString();
    }

    private static String state(BackgroundSuggestion s) {
        if (BackgroundSuggestion.ADOPTED.equals(s.getState())) {
            return "已采纳" + (s.getOperatorName() == null ? "" : " · " + s.getOperatorName());
        }
        if (BackgroundSuggestion.IGNORED.equals(s.getState())) {
            return "已忽略";
        }
        return s.getUnavailableReason() != null ? s.getUnavailableReason() : "待处理";
    }

    private static void row(StringBuilder h, String k, String v) {
        h.append("<tr valign=\"top\"><td width=\"16%\" class=\"k\">").append(k).append("</td><td>").append(e(v)).append("</td></tr>");
    }

    private static String e(String s) {
        return s == null ? "" : HtmlUtils.htmlEscape(s, "UTF-8");
    }
}
