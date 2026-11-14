package com.zhul.erp.modules.inquiry.sourcing.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zhul.erp.modules.aitask.constants.AiTaskStatus;
import com.zhul.erp.modules.aitask.entity.AiTaskDO;
import com.zhul.erp.modules.aitask.repository.AiTaskMapper;
import com.zhul.erp.modules.aitask.service.AiTaskResultHandler;
import com.zhul.erp.modules.inquiry.constants.InquiryConstants;
import com.zhul.erp.modules.inquiry.support.QuoteDictSnapshot;
import com.zhul.erp.modules.inquiry.support.QuoteDicts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.Set;

/**
 * skill_id=sourcing-quote-parse 的结果处理器（change sourcing-ai-quote-fill，design D1）。
 * 按租户字典校验：单价不是非负数、货况货期税率不是启用的字典值的置空并标「请核对」；含税价换算不含税价
 * （含税价 ÷ (1 + 税率%)，两位小数 HALF_UP）。明细 id 是否属于任务由读取结果时按任务校验（任务输入不落库）。
 * 只写回 ai_task 的结果，不改回价；回调由 {@code AiTaskServiceImpl.handleCallback} 按任务所属租户执行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuoteParseResultHandler implements AiTaskResultHandler {

    private static final int ROWS_MAX = 200;
    private static final int TEXT_MAX = 200;
    private static final int UNKNOWN_MAX = 50;

    private final QuoteDicts quoteDicts;
    private final AiTaskMapper aiTaskMapper;
    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(String skillId) {
        return InquiryConstants.SKILL_QUOTE_PARSE.equals(skillId);
    }

    @Override
    public void handle(AiTaskDO task) {
        if (task.getStatus() == null || task.getStatus() != AiTaskStatus.COMPLETED) {
            return;
        }
        String cleaned = clean(task.getOutput(), quoteDicts.snapshot());
        AiTaskDO update = new AiTaskDO();
        update.setId(task.getId());
        update.setOutput(cleaned);
        aiTaskMapper.updateById(update);
        task.setOutput(cleaned);
    }

    String clean(String output, QuoteDictSnapshot dicts) {
        JsonNode in;
        try {
            // 金额按 BigDecimal 读，不经 double，避免舍入前就丢精度
            in = objectMapper.reader().with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readTree(output == null ? "{}" : output);
        } catch (JsonProcessingException e) {
            log.warn("报价识别结果不是 JSON，已清空：{}", e.getOriginalMessage());
            in = objectMapper.createObjectNode();
        }
        ObjectNode out = objectMapper.createObjectNode();
        ArrayNode rows = out.putArray("rows");
        Set<Long> seen = new HashSet<>(16);
        for (JsonNode r : in.path("rows")) {
            if (rows.size() >= ROWS_MAX) {
                break;
            }
            if (!r.path("itemId").canConvertToLong() || !seen.add(r.path("itemId").asLong())) {
                continue;
            }
            rows.add(cleanRow(r, dicts));
        }
        ArrayNode others = out.putArray("otherModels");
        for (JsonNode m : in.path("otherModels")) {
            String model = cut(m.path("model").asText("").trim(), 100);
            if (m.path("taskId").canConvertToLong() && !model.isEmpty()) {
                others.addObject().put("taskId", m.path("taskId").asLong()).put("model", model);
            }
        }
        ArrayNode unknown = out.putArray("unknownModels");
        Set<String> names = new HashSet<>(16);
        for (JsonNode m : in.path("unknownModels")) {
            String model = cut(m.asText("").trim(), 100);
            if (!model.isEmpty() && unknown.size() < UNKNOWN_MAX && names.add(model)) {
                unknown.add(model);
            }
        }
        out.put("common", cut(in.path("common").asText("").trim(), 500));
        return out.toString();
    }

    private ObjectNode cleanRow(JsonNode r, QuoteDictSnapshot dicts) {
        boolean check = !InquiryConstants.CONFIDENCE_SURE.equalsIgnoreCase(r.path("confidence").asText(""));
        ObjectNode row = objectMapper.createObjectNode();
        row.put("itemId", r.path("itemId").asLong());
        row.put("raw", cut(r.path("raw").asText("").trim(), TEXT_MAX));
        row.put("note", cut(r.path("note").asText("").trim(), TEXT_MAX));
        BigDecimal price = r.path("unitPrice").isNumber() ? r.path("unitPrice").decimalValue() : null;
        if (price != null && price.signum() < 0) {
            price = null;
            check = true;
        } else if (price == null && !r.path("unitPrice").isMissingNode() && !r.path("unitPrice").isNull()) {
            check = true;
        }
        boolean taxed = r.path("taxIncluded").asBoolean(false);
        Integer rate = null;
        if (taxed) {
            rate = intOf(r.path("taxRate"));
            if (rate == null || !dicts.taxRates().enabled().contains(rate)) {
                rate = null;
                check = true;
            }
        }
        row.put("taxIncluded", taxed);
        if (rate != null) {
            row.put("taxRate", rate);
        }
        if (price != null) {
            price = price.setScale(2, RoundingMode.HALF_UP);
            row.put("unitPrice", price);
            row.put("unitPriceExTax", taxed
                    ? MyTaskServiceImpl.excludeTax(price, rate == null ? InquiryConstants.DEFAULT_TAX_RATE : rate) : price);
        }
        check |= putDict(row, "itemCondition", r.path("itemCondition"), dicts.conditions());
        check |= putDict(row, "leadTime", r.path("leadTime"), dicts.leadTimes());
        row.put("confidence", check ? InquiryConstants.CONFIDENCE_CHECK : InquiryConstants.CONFIDENCE_SURE);
        return row;
    }

    /** 启用的字典值才保留；给了但不合法时返回 true（需要核对） */
    private static boolean putDict(ObjectNode row, String field, JsonNode v, QuoteDictSnapshot.Dict dict) {
        Integer code = intOf(v);
        if (code != null && code != 0 && dict.enabled().contains(code)) {
            row.put(field, code);
            return false;
        }
        return code != null && code != 0 || code == null && !v.isMissingNode() && !v.isNull();
    }

    private static Integer intOf(JsonNode v) {
        return v.isIntegralNumber() && v.canConvertToInt() ? v.asInt() : null;
    }

    private static String cut(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }
}
