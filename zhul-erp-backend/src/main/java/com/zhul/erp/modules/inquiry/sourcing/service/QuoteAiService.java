package com.zhul.erp.modules.inquiry.sourcing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.aitask.constants.AiTaskStatus;
import com.zhul.erp.modules.aitask.dto.AiTaskVO;
import com.zhul.erp.modules.aitask.entity.AiTaskDO;
import com.zhul.erp.modules.aitask.exception.AiNotConfiguredException;
import com.zhul.erp.modules.aitask.exception.AiUnavailableException;
import com.zhul.erp.modules.aitask.repository.AiTaskMapper;
import com.zhul.erp.modules.aitask.service.AiConfigService;
import com.zhul.erp.modules.aitask.service.AiTaskService;
import com.zhul.erp.modules.inquiry.constants.InquiryConstants;
import com.zhul.erp.modules.inquiry.item.entity.InquiryItemDO;
import com.zhul.erp.modules.inquiry.item.repository.InquiryItemMapper;
import com.zhul.erp.modules.inquiry.sourcing.dto.MyTaskVO;
import com.zhul.erp.modules.inquiry.sourcing.dto.PasteAiVO;
import com.zhul.erp.modules.inquiry.sourcing.dto.PastePreviewVO;
import com.zhul.erp.modules.inquiry.sourcing.entity.SourcingTaskDO;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.inquiry.support.QuoteDictSnapshot;
import com.zhul.erp.modules.inquiry.support.QuoteDicts;
import com.zhul.erp.modules.quotation.support.QuotationLocks;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 粘贴报价的 AI 识别（change sourcing-ai-quote-fill）：发起识别写 ai_task 并异步提交，前端轮询结果；
 * 同一人同时只跑一个识别（发起新的会取消自己还在跑的）；超过 90 秒视为超时。
 * 结果不落回价：组装成「粘贴报价」的预览结构，采购确认后由前端填进草稿。原文只随任务提交给 AI，不另存。
 * 本类不声明事务：任务创建后立即提交给编排服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuoteAiService {

    /** 输入给 AI 的其他待处理任务上限，避免原文之外的上下文过长 */
    private static final int OTHER_TASKS_MAX = 30;
    private static final String ERROR_AI_NOT_CONFIGURED = "AI_NOT_CONFIGURED";
    private static final String REASON_TIMEOUT = "AI 识别超时";
    private static final String REASON_CANCELLED = "已取消";
    private static final String REASON_REPLACED = "已开始新的识别";
    private static final Set<String> KNOWN_REASONS = Set.of(REASON_TIMEOUT, REASON_CANCELLED, REASON_REPLACED);

    private final MyTaskService myTaskService;
    private final InquiryItemMapper itemMapper;
    private final QuotationLocks quotationLocks;
    private final QuoteDicts quoteDicts;
    private final AiTaskService aiTaskService;
    private final AiTaskMapper aiTaskMapper;
    private final AiConfigService aiConfigService;
    private final CurrentUserResolver currentUser;
    private final ObjectMapper objectMapper;

    /** 租户没有可用的 AI 时，弹窗只有「按规则识别」 */
    public boolean enabled() {
        return aiConfigService.isConfiguredForCurrentTenant();
    }

    public PasteAiVO start(Long taskId, String text) {
        SourcingTaskDO task = myTaskService.myTask(taskId);
        String raw = text.strip();
        if (raw.length() > InquiryConstants.QUOTE_AI_RAW_MAX) {
            throw new BizException("报价原文不能超过 20000 字，请分段粘贴");
        }
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("task", Map.of("code", nz(task.getTaskCode()), "brand", nz(task.getBrand()), "category", nz(task.getCategory())));
        input.put("items", items(List.of(taskId)).stream().map(i -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", i.getId());
            m.put("model", nz(i.getConfirmedModel()));
            m.put("originalModel", nz(i.getOriginalModel()));
            m.put("quantity", i.getQuantity());
            return m;
        }).toList());
        input.put("otherTasks", otherTasks(taskId));
        QuoteDictSnapshot dicts = quoteDicts.snapshot();
        input.put("conditions", dictList(dicts.conditions()));
        input.put("leadTimes", dictList(dicts.leadTimes()));
        input.put("taxRates", dictList(dicts.taxRates()));
        input.put("rawContent", raw);
        Long me = currentUser.resolve();
        // 同一人同时只跑一个识别：新的发起时，把自己还没结束的识别作废，迟到的回调会被忽略
        close(null, me, REASON_REPLACED);
        AiTaskVO ai;
        try {
            ai = aiTaskService.createAndSubmit(InquiryConstants.SKILL_QUOTE_PARSE, write(input), me);
        } catch (AiNotConfiguredException | AiUnavailableException e) {
            throw BizException.of(ERROR_AI_NOT_CONFIGURED, e.getMessage());
        }
        PasteAiVO vo = new PasteAiVO();
        vo.setAiTaskId(ai.getId());
        vo.setStatus(PasteAiVO.RUNNING);
        return vo;
    }

    public PasteAiVO result(Long taskId, Long aiTaskId) {
        myTaskService.myTask(taskId);
        AiTaskVO ai = ownTask(aiTaskId);
        PasteAiVO vo = new PasteAiVO();
        vo.setAiTaskId(ai.getId());
        if (AiTaskStatus.OPEN.contains(ai.getStatus())) {
            LocalDateTime deadline = ai.getCreateTime().plusSeconds(InquiryConstants.QUOTE_AI_TIMEOUT_SECONDS);
            if (LocalDateTime.now().isBefore(deadline) || !close(aiTaskId, null, REASON_TIMEOUT)) {
                vo.setStatus(PasteAiVO.RUNNING);
                return vo;
            }
            ai = aiTaskService.getById(aiTaskId);
        }
        if (Objects.equals(ai.getStatus(), AiTaskStatus.COMPLETED)) {
            vo.setStatus(PasteAiVO.DONE);
            vo.setPreview(preview(taskId, ai.getOutput()));
        } else {
            vo.setStatus(PasteAiVO.FAILED);
            vo.setErrorMessage(failReason(ai));
        }
        return vo;
    }

    /** 超时、取消照原因提示；其他失败（服务商报错、编排服务不可用等）对采购没有意义，只写日志 */
    private static String failReason(AiTaskVO ai) {
        String raw = ai.getErrorMessage() == null ? "" : ai.getErrorMessage();
        if (KNOWN_REASONS.contains(raw)) {
            return raw;
        }
        if (raw.contains("秒未完成")) {
            return REASON_TIMEOUT;
        }
        log.warn("粘贴报价 AI 识别失败，aiTaskId={}，原因：{}", ai.getId(), raw);
        return "AI 识别失败";
    }

    public void cancel(Long taskId, Long aiTaskId) {
        myTaskService.myTask(taskId);
        ownTask(aiTaskId);
        close(aiTaskId, null, REASON_CANCELLED);
    }

    /** AI 结果 → 预览：明细 id 只留当前任务的；其他任务的型号只留本人当前待处理的任务 */
    PastePreviewVO preview(Long taskId, String output) {
        JsonNode n = read(output);
        Map<Long, InquiryItemDO> items = items(List.of(taskId)).stream()
                .collect(Collectors.toMap(InquiryItemDO::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        Set<Long> locked = quotationLocks.lockedItemIds(items.keySet());
        List<PastePreviewVO.Row> rows = new ArrayList<>();
        for (JsonNode r : n.path("rows")) {
            InquiryItemDO item = items.get(r.path("itemId").asLong());
            if (item == null) {
                continue;
            }
            PastePreviewVO.Row row = new PastePreviewVO.Row();
            row.setRaw(r.path("raw").asText(""));
            row.setItemId(item.getId());
            row.setModel(item.getConfirmedModel());
            row.setUnitPrice(decimal(r, "unitPrice"));
            row.setUnitPriceExTax(decimal(r, "unitPriceExTax"));
            row.setTaxIncluded(r.path("taxIncluded").asBoolean(false));
            row.setTaxRate(r.hasNonNull("taxRate") ? r.path("taxRate").asInt() : null);
            row.setItemCondition(r.hasNonNull("itemCondition") ? r.path("itemCondition").asInt() : null);
            row.setLeadTime(r.hasNonNull("leadTime") ? r.path("leadTime").asInt() : null);
            row.setNote(r.path("note").asText(""));
            row.setStatus(row.getUnitPrice() == null ? "NO_PRICE" : "MATCHED");
            row.setLocked(locked.contains(item.getId()));
            row.setConfidence(InquiryConstants.CONFIDENCE_SURE.equals(r.path("confidence").asText())
                    ? InquiryConstants.CONFIDENCE_SURE : InquiryConstants.CONFIDENCE_CHECK);
            rows.add(row);
        }
        PastePreviewVO vo = new PastePreviewVO();
        vo.setRows(rows);
        vo.setCommon(n.path("common").asText(""));
        Map<Long, Set<String>> modelsByTask = new LinkedHashMap<>();
        for (JsonNode m : n.path("otherModels")) {
            modelsByTask.computeIfAbsent(m.path("taskId").asLong(), k -> new LinkedHashSet<>()).add(m.path("model").asText());
        }
        List<PastePreviewVO.OtherTask> others = new ArrayList<>();
        if (!modelsByTask.isEmpty()) {
            for (MyTaskVO t : myTaskService.myTasks(false)) {
                Set<String> models = modelsByTask.get(t.getId());
                if (models != null && !t.getId().equals(taskId)) {
                    PastePreviewVO.OtherTask o = new PastePreviewVO.OtherTask();
                    o.setTaskId(t.getId());
                    o.setTaskCode(t.getTaskCode());
                    o.setBrand(t.getBrand());
                    o.setCategory(t.getCategory());
                    o.setModelCount(models.size());
                    others.add(o);
                }
            }
        }
        vo.setOtherTasks(others);
        List<String> unknown = new ArrayList<>();
        n.path("unknownModels").forEach(m -> unknown.add(m.asText()));
        vo.setUnknownModels(unknown);
        return vo;
    }

    /** 本人其他待处理任务（不含当前任务）的型号清单 */
    private List<Map<String, Object>> otherTasks(Long taskId) {
        List<MyTaskVO> tasks = myTaskService.myTasks(false).stream()
                .filter(t -> !t.getId().equals(taskId)).limit(OTHER_TASKS_MAX).toList();
        if (tasks.isEmpty()) {
            return List.of();
        }
        Map<Long, List<String>> models = items(tasks.stream().map(MyTaskVO::getId).toList()).stream()
                .collect(Collectors.groupingBy(InquiryItemDO::getSourcingTaskId,
                        Collectors.mapping(i -> nz(i.getConfirmedModel()), Collectors.toList())));
        return tasks.stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("code", nz(t.getTaskCode()));
            m.put("brand", nz(t.getBrand()));
            m.put("category", nz(t.getCategory()));
            m.put("models", models.getOrDefault(t.getId(), List.of()));
            return m;
        }).toList();
    }

    private List<InquiryItemDO> items(List<Long> taskIds) {
        return itemMapper.selectList(new LambdaQueryWrapper<InquiryItemDO>()
                .select(InquiryItemDO::getId, InquiryItemDO::getSourcingTaskId, InquiryItemDO::getConfirmedModel,
                        InquiryItemDO::getOriginalModel, InquiryItemDO::getQuantity)
                .in(InquiryItemDO::getSourcingTaskId, taskIds)
                .isNull(InquiryItemDO::getDeletedAt)
                .orderByAsc(InquiryItemDO::getLineNo));
    }

    private static List<Map<String, Object>> dictList(QuoteDictSnapshot.Dict dict) {
        return dict.enabled().stream().map(code -> Map.<String, Object>of("code", code, "name", dict.labels().get(code))).toList();
    }

    /** 把还没结束的识别改为失败（超时、取消、被新识别取代）：按识别 id 或发起人；返回是否改到了 */
    private boolean close(Long aiTaskId, Long requestedBy, String reason) {
        LocalDateTime now = LocalDateTime.now();
        return aiTaskMapper.update(null, new LambdaUpdateWrapper<AiTaskDO>()
                .eq(AiTaskDO::getSkillId, InquiryConstants.SKILL_QUOTE_PARSE)
                .in(AiTaskDO::getStatus, AiTaskStatus.OPEN)
                .eq(aiTaskId != null, AiTaskDO::getId, aiTaskId)
                .eq(requestedBy != null, AiTaskDO::getRequestedBy, requestedBy)
                .set(AiTaskDO::getStatus, AiTaskStatus.FAILED)
                .set(AiTaskDO::getErrorMessage, reason)
                .set(AiTaskDO::getCompletedAt, now)
                .set(AiTaskDO::getUpdateTime, now)) > 0;
    }

    /** 只能查自己发起的识别 */
    private AiTaskVO ownTask(Long aiTaskId) {
        AiTaskVO ai;
        try {
            ai = aiTaskService.getById(aiTaskId);
        } catch (BizException e) {
            throw new BizException("识别任务不存在", e);
        }
        if (!InquiryConstants.SKILL_QUOTE_PARSE.equals(ai.getSkillId()) || !Objects.equals(ai.getRequestedBy(), currentUser.resolve())) {
            throw new BizException("识别任务不存在");
        }
        return ai;
    }

    private String write(Object input) {
        try {
            return objectMapper.writeValueAsString(input);
        } catch (JsonProcessingException e) {
            throw new BizException("AI 输入整理失败", e);
        }
    }

    private JsonNode read(String json) {
        try {
            return objectMapper.reader().with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readTree(json == null ? "{}" : json);
        } catch (JsonProcessingException e) {
            throw new BizException("AI 返回的结果无法读取，请重试", e);
        }
    }

    private static BigDecimal decimal(JsonNode r, String field) {
        return r.path(field).isNumber() ? r.path(field).decimalValue() : null;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
