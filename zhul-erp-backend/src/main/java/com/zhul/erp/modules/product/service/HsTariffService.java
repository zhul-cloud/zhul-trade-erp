package com.zhul.erp.modules.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.aitask.constants.AiTaskStatus;
import com.zhul.erp.modules.aitask.dto.AiTaskVO;
import com.zhul.erp.modules.aitask.entity.AiTaskDO;
import com.zhul.erp.modules.aitask.repository.AiTaskMapper;
import com.zhul.erp.modules.aitask.service.AiTaskResultHandler;
import com.zhul.erp.modules.aitask.service.AiTaskService;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.product.constants.ProductConstants;
import com.zhul.erp.modules.product.constants.ProductErrorCodes;
import com.zhul.erp.modules.product.dto.SaveTariffRequest;
import com.zhul.erp.modules.product.dto.TariffAiVO;
import com.zhul.erp.modules.product.dto.TariffVO;
import com.zhul.erp.modules.product.entity.HsTariffRefDO;
import com.zhul.erp.modules.product.entity.ProductCategoryDO;
import com.zhul.erp.modules.product.repository.HsTariffRefMapper;
import com.zhul.erp.modules.product.repository.ProductCategoryMapper;
import com.zhul.erp.modules.product.support.PlatformScopeGuard;
import com.zhul.erp.modules.product.support.TextRules;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 目的国进口关税参考（openspec content-ai-batch-hs）：按海关编码前 6 位维护 9 个主要出口国的进口关税，
 * 所有公司只读，平台账号维护。参考信息，不代替报关行意见。
 * AI 补全只查未填的国家，结果作为建议返回给页面，由人核对后再保存（不自动写库）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HsTariffService implements AiTaskResultHandler {

    public static final String AI_SKILL = "hs-tariff-ref";

    /** 主要出口国（用户 2026-11-18 确认），按这个顺序显示 */
    public static final Map<String, String> COUNTRIES = countries();

    private static final Pattern HS6 = Pattern.compile("^\\d{6}$");
    private static final Pattern PERCENT = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*%");
    private static final int RATE_MAX = 32;
    private static final int NOTE_MAX = 200;
    private static final int SOURCE_MAX = 300;

    private final HsTariffRefMapper tariffMapper;
    private final ProductCategoryMapper categoryMapper;
    private final PlatformScopeGuard platformScopeGuard;
    private final AiTaskService aiTaskService;
    private final AiTaskMapper aiTaskMapper;
    private final CurrentUserResolver currentUser;
    private final ObjectMapper objectMapper;

    private static Map<String, String> countries() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("MX", "墨西哥");
        m.put("IN", "印度");
        m.put("RU", "俄罗斯");
        m.put("AE", "阿联酋");
        m.put("SA", "沙特");
        m.put("CL", "智利");
        m.put("ZA", "南非");
        m.put("ID", "印度尼西亚");
        m.put("IR", "伊朗");
        return m;
    }

    /** 列表上的概况：已填几国、未填的国家、税率范围 */
    public record Summary(int filled, int total, List<String> missing, String range) {
    }

    public TariffVO get(String hs6) {
        String key = hs6(hs6);
        TariffVO vo = new TariffVO();
        vo.setHs6(key);
        Map<String, HsTariffRefDO> rows = rows(List.of(key)).getOrDefault(key, Map.of());
        COUNTRIES.forEach((code, name) -> vo.getRates().add(toRate(code, name, rows.get(code))));
        categoryMapper.selectList(new LambdaQueryWrapper<ProductCategoryDO>()
                        .eq(ProductCategoryDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                        .likeRight(ProductCategoryDO::getHsCode, key)
                        .isNull(ProductCategoryDO::getDeletedAt)
                        .orderByAsc(ProductCategoryDO::getSortOrder))
                .forEach(c -> vo.getCategories().add(StringUtils.hasText(c.getCategoryNameZh())
                        ? c.getCategoryNameZh() : c.getCategoryName()));
        return vo;
    }

    /** 按国家保存：填了税率的核对日期记为今天，清空税率表示未填 */
    @Transactional(rollbackFor = Exception.class)
    public TariffVO save(String hs6, SaveTariffRequest req) {
        platformScopeGuard.requirePlatform();
        String key = hs6(hs6);
        Map<String, HsTariffRefDO> existing = rows(List.of(key)).getOrDefault(key, Map.of());
        LocalDate today = LocalDate.now();
        for (SaveTariffRequest.Rate r : req.getRates()) {
            String code = r.getCountryCode() == null ? "" : r.getCountryCode().trim().toUpperCase();
            if (!COUNTRIES.containsKey(code)) {
                throw new BizException("不支持的国家：" + r.getCountryCode());
            }
            String rate = TextRules.optional(r.getRate(), COUNTRIES.get(code) + "的进口关税", RATE_MAX);
            String note = TextRules.optional(r.getNote(), COUNTRIES.get(code) + "的备注", NOTE_MAX);
            String source = cut(r.getSource(), SOURCE_MAX);
            HsTariffRefDO row = existing.get(code);
            if (row == null) {
                if (rate.isEmpty() && note.isEmpty()) {
                    continue;
                }
                row = new HsTariffRefDO();
                row.setTenantId(ProductConstants.PLATFORM_TENANT_ID);
                row.setHs6(key);
                row.setCountryCode(code);
            }
            row.setRate(rate);
            row.setNote(note);
            row.setSource(source);
            row.setCheckedAt(rate.isEmpty() ? null : today);
            if (row.getId() == null) {
                tariffMapper.insert(row);
            } else {
                tariffMapper.updateById(row);
            }
        }
        return get(key);
    }

    /** 各 6 位编码的概况（列表用）；没有任何记录的编码也返回（全部未填） */
    public Map<String, Summary> summaries(Collection<String> hs6s) {
        List<String> keys = hs6s.stream().filter(k -> k != null && HS6.matcher(k).matches()).distinct().toList();
        Map<String, Map<String, HsTariffRefDO>> all = rows(keys);
        Map<String, Summary> out = new HashMap<>();
        for (String k : keys) {
            Map<String, HsTariffRefDO> rows = all.getOrDefault(k, Map.of());
            List<String> missing = new ArrayList<>();
            BigDecimal min = null;
            BigDecimal max = null;
            for (Map.Entry<String, String> c : COUNTRIES.entrySet()) {
                HsTariffRefDO r = rows.get(c.getKey());
                if (r == null || !StringUtils.hasText(r.getRate())) {
                    missing.add(c.getValue());
                    continue;
                }
                Matcher m = PERCENT.matcher(r.getRate());
                if (m.find()) {
                    BigDecimal v = new BigDecimal(m.group(1));
                    min = min == null || v.compareTo(min) < 0 ? v : min;
                    max = max == null || v.compareTo(max) > 0 ? v : max;
                }
            }
            String range = min == null ? "" : min.compareTo(max) == 0 ? pct(min) : pct(min) + "～" + pct(max);
            out.put(k, new Summary(COUNTRIES.size() - missing.size(), COUNTRIES.size(), missing, range));
        }
        return out;
    }

    // ---------------------------------------------------------------- AI 补全

    /** 让 AI 查未填国家的进口关税；返回 AI 任务，页面轮询结果 */
    public TariffAiVO submitAi(String hs6) {
        platformScopeGuard.requirePlatform();
        TariffVO current = get(hs6);
        List<Map<String, String>> countries = current.getRates().stream()
                .filter(r -> !StringUtils.hasText(r.getRate()))
                .map(r -> Map.of("code", r.getCountryCode(), "name", r.getCountryName()))
                .toList();
        if (countries.isEmpty()) {
            throw new BizException("9 个国家都已填写，不需要 AI 补全");
        }
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("hs6", current.getHs6());
        input.put("categories", current.getCategories());
        input.put("countries", countries);
        AiTaskVO task;
        try {
            task = aiTaskService.createAndSubmit(AI_SKILL, objectMapper.writeValueAsString(input), currentUser.resolve());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("AI 补全关税参考输入序列化失败", e);
        }
        TariffAiVO vo = new TariffAiVO();
        vo.setTaskId(task.getId());
        vo.setStatus(task.getStatus());
        return vo;
    }

    /** 只能查自己提交的；完成后返回建议（只含输入的国家） */
    public TariffAiVO aiStatus(Long taskId) {
        AiTaskDO task = taskId == null ? null : aiTaskMapper.selectById(taskId);
        if (task == null || task.getDeletedAt() != null || !AI_SKILL.equals(task.getSkillId())
                || !Objects.equals(task.getTenantId(), TenantContext.getTenantId())
                || !Objects.equals(task.getRequestedBy(), currentUser.resolve())) {
            throw BizException.of(ProductErrorCodes.AI_TASK_NOT_FOUND, "AI 补全任务不存在");
        }
        TariffAiVO vo = new TariffAiVO();
        vo.setTaskId(task.getId());
        vo.setStatus(task.getStatus());
        vo.setErrorMessage(task.getErrorMessage());
        if (task.getStatus() != null && task.getStatus() == AiTaskStatus.COMPLETED) {
            for (JsonNode n : read(task.getOutput()).path("rates")) {
                String code = n.path("country").asText("").trim().toUpperCase();
                if (!COUNTRIES.containsKey(code) || !StringUtils.hasText(n.path("rate").asText(""))) {
                    continue;
                }
                TariffVO.Rate r = new TariffVO.Rate();
                r.setCountryCode(code);
                r.setCountryName(COUNTRIES.get(code));
                r.setRate(cut(n.path("rate").asText(""), RATE_MAX));
                r.setNote(cut(n.path("note").asText(""), NOTE_MAX));
                r.setSource(cut(n.path("source").asText(""), SOURCE_MAX));
                vo.getRates().add(r);
            }
        }
        return vo;
    }

    @Override
    public boolean supports(String skillId) {
        return AI_SKILL.equals(skillId);
    }

    /** 结果由页面轮询时读取，人核对后保存，这里不做后续动作 */
    @Override
    public void handle(AiTaskDO task) {
        log.info("关税参考 AI 补全结束，taskId={}, status={}", task.getId(), task.getStatus());
    }

    // ---------------------------------------------------------------- 内部

    public static String hs6(String raw) {
        String digits = raw == null ? "" : raw.replaceAll("[.\\s]", "");
        if (digits.length() > 6) {
            digits = digits.substring(0, 6);
        }
        if (!HS6.matcher(digits).matches()) {
            throw BizException.of(ProductErrorCodes.HS_CODE_INVALID, "海关编码前 6 位应为数字");
        }
        return digits;
    }

    private Map<String, Map<String, HsTariffRefDO>> rows(Collection<String> keys) {
        if (keys.isEmpty()) {
            return Map.of();
        }
        Map<String, Map<String, HsTariffRefDO>> out = new HashMap<>();
        tariffMapper.selectList(new LambdaQueryWrapper<HsTariffRefDO>()
                        .eq(HsTariffRefDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                        .in(HsTariffRefDO::getHs6, keys))
                .forEach(r -> out.computeIfAbsent(r.getHs6(), k -> new HashMap<>()).put(r.getCountryCode(), r));
        return out;
    }

    private static TariffVO.Rate toRate(String code, String name, HsTariffRefDO row) {
        TariffVO.Rate r = new TariffVO.Rate();
        r.setCountryCode(code);
        r.setCountryName(name);
        r.setRate(row == null ? "" : row.getRate());
        r.setNote(row == null ? "" : row.getNote());
        r.setSource(row == null ? "" : row.getSource());
        r.setCheckedAt(row == null ? null : row.getCheckedAt());
        r.setUpdateBy(row == null ? null : row.getUpdateBy());
        return r;
    }

    private static String pct(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString() + "%";
    }

    private static String cut(String s, int max) {
        String t = s == null ? "" : s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    private JsonNode read(String json) {
        try {
            return objectMapper.readTree(json == null ? "{}" : json);
        } catch (JsonProcessingException e) {
            log.warn("关税参考 AI 结果无法识别：{}", e.getMessage());
            return objectMapper.createObjectNode();
        }
    }
}
