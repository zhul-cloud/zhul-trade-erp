package com.zhul.erp.modules.document.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文字报价模版：${…} 替换 + {{#items}} … {{/items}}、{{#fees}} … {{/fees}} 块逐行重复。
 * 不引入模板引擎依赖。
 */
public final class TextTemplateEngine {

    private static final Pattern BLOCK = Pattern.compile("\\{\\{#(items|fees)}}(.*?)\\{\\{/\\1}}", Pattern.DOTALL);
    private static final Pattern ANY_TAG = Pattern.compile("\\{\\{[#/]?([A-Za-z]*)}}");

    private TextTemplateEngine() {
    }

    public static List<TemplateProblem> validate(String template) {
        List<TemplateProblem> problems = new ArrayList<>();
        if (template == null || template.isBlank()) {
            problems.add(new TemplateProblem("", "模版内容不能为空"));
            return problems;
        }
        String outside = BLOCK.matcher(template).replaceAll(r -> " ".repeat(r.group().length()));
        Matcher tags = ANY_TAG.matcher(outside);
        while (tags.find()) {
            problems.add(new TemplateProblem(lineOf(template, tags.start()), "块标记 " + tags.group() + " 没有成对出现"));
        }
        Matcher blocks = BLOCK.matcher(template);
        while (blocks.find()) {
            String kind = blocks.group(1);
            Matcher t = Placeholders.TOKEN.matcher(blocks.group(2));
            while (t.find()) {
                String name = t.group(1);
                int at = blocks.start(2) + t.start();
                if (!Placeholders.known(name)) {
                    problems.add(new TemplateProblem(lineOf(template, at), "不认识的占位符 ${" + name + "}"));
                } else if ("fees".equals(kind) && name.startsWith(Placeholders.ITEM_PREFIX)) {
                    problems.add(new TemplateProblem(lineOf(template, at), "${" + name + "} 需要放在 {{#items}} 和 {{/items}} 之间"));
                } else if ("items".equals(kind) && name.startsWith(Placeholders.FEE_PREFIX)) {
                    problems.add(new TemplateProblem(lineOf(template, at), "${" + name + "} 需要放在 {{#fees}} 和 {{/fees}} 之间"));
                }
            }
        }
        Matcher t = Placeholders.TOKEN.matcher(outside);
        while (t.find()) {
            String name = t.group(1);
            if (!Placeholders.known(name)) {
                problems.add(new TemplateProblem(lineOf(template, t.start()), "不认识的占位符 ${" + name + "}"));
            } else if (name.startsWith(Placeholders.ITEM_PREFIX)) {
                problems.add(new TemplateProblem(lineOf(template, t.start()), "${" + name + "} 需要放在 {{#items}} 和 {{/items}} 之间"));
            } else if (name.startsWith(Placeholders.FEE_PREFIX)) {
                problems.add(new TemplateProblem(lineOf(template, t.start()), "${" + name + "} 需要放在 {{#fees}} 和 {{/fees}} 之间"));
            }
        }
        return problems;
    }

    public static String render(String template, RenderModel model) {
        StringBuilder out = new StringBuilder();
        Matcher m = BLOCK.matcher(template);
        int last = 0;
        while (m.find()) {
            out.append(fill(template.substring(last, m.start()), model.header()));
            String body = m.group(2);
            if (body.startsWith("\n")) {
                body = body.substring(1);
            }
            List<Map<String, Object>> rows = "items".equals(m.group(1)) ? model.items() : model.fees();
            for (Map<String, Object> row : rows) {
                // 无货行不套模版的行格式（避免「$」后面跟空单价），固定输出一行说明
                String line = Boolean.TRUE.equals(row.get(RenderModel.NO_STOCK))
                        ? RenderModel.NO_STOCK_LINE + (body.endsWith("\n") ? "\n" : "") : body;
                out.append(fill(fill(line, row), model.header()));
            }
            last = m.end();
            if (last < template.length() && template.charAt(last) == '\n' && out.length() > 0 && out.charAt(out.length() - 1) == '\n') {
                last++;
            }
        }
        out.append(fill(template.substring(last), model.header()));
        return out.toString().strip();
    }

    static String fill(String text, Map<String, Object> values) {
        Matcher m = Placeholders.TOKEN.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String name = m.group(1);
            String replacement = values.containsKey(name) ? RenderModel.text(values.get(name)) : m.group();
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String lineOf(String text, int index) {
        int line = 1;
        for (int i = 0; i < index && i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return "第 " + line + " 行";
    }
}
