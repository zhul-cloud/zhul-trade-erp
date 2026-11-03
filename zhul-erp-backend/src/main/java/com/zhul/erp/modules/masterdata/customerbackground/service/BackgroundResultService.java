package com.zhul.erp.modules.masterdata.customerbackground.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.modules.aitask.constants.AiTaskStatus;
import com.zhul.erp.modules.aitask.entity.AiTaskDO;
import com.zhul.erp.modules.masterdata.customerbackground.dto.BackgroundSuggestion;
import com.zhul.erp.modules.masterdata.customerbackground.entity.CustomerBackgroundReportDO;
import com.zhul.erp.modules.masterdata.customerbackground.repository.CustomerBackgroundReportMapper;
import com.zhul.erp.modules.masterdata.customerbackground.support.BackgroundFields;
import com.zhul.erp.modules.masterdata.customerprofile.support.ProfileField;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.zhul.erp.modules.masterdata.customerbackground.constants.BackgroundConstants.SOURCE_AI;
import static com.zhul.erp.modules.masterdata.customerbackground.constants.BackgroundConstants.STATUS_DONE;
import static com.zhul.erp.modules.masterdata.customerbackground.constants.BackgroundConstants.STATUS_FAILED;
import static com.zhul.erp.modules.masterdata.customerbackground.constants.BackgroundConstants.STATUS_RUNNING;

/**
 * AI 任务结束后保存背调结果（spec crm/customer-background「背调报告」）：核心结论、正文（AI 背调）、字段建议、用量快照。
 * 回调线程里没有租户上下文，按 ai_task_id 找报告。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BackgroundResultService {

    private static final int TEXT_MAX = 500;
    private static final int VALUE_MAX = 1000;

    private final CustomerBackgroundReportMapper reportMapper;
    private final ObjectMapper objectMapper;

    @Transactional(rollbackFor = Exception.class)
    public void apply(AiTaskDO task) {
        CustomerBackgroundReportDO r = reportMapper.selectOne(new LambdaQueryWrapper<CustomerBackgroundReportDO>()
                .eq(CustomerBackgroundReportDO::getAiTaskId, task.getId())
                .last("LIMIT 1"));
        if (r == null || r.getDeletedAt() != null || r.getStatus() != STATUS_RUNNING) {
            log.info("背调报告已删除或已处理，忽略 AI 结果，aiTaskId={}", task.getId());
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        CustomerBackgroundReportDO u = new CustomerBackgroundReportDO();
        u.setId(r.getId());
        u.setModel(task.getModel() == null ? "" : task.getModel());
        u.setInputTokens(task.getInputTokens());
        u.setOutputTokens(task.getOutputTokens());
        u.setWebSearches(task.getWebSearches());
        u.setCompletedAt(now);
        u.setDurationSeconds((int) Math.max(0, Duration.between(r.getCreateTime(), now).getSeconds()));
        if (task.getStatus() != null && task.getStatus() == AiTaskStatus.COMPLETED) {
            try {
                fill(u, r, objectMapper.readTree(task.getOutput() == null ? "{}" : task.getOutput()));
                u.setStatus(STATUS_DONE);
            } catch (JsonProcessingException | IllegalArgumentException e) {
                log.warn("背调结果无法识别，reportNo={}, error={}", r.getReportNo(), e.getMessage());
                u.setStatus(STATUS_FAILED);
                u.setFailReason("AI 返回的结果无法识别，请重新发起");
            }
        } else {
            u.setStatus(STATUS_FAILED);
            u.setFailReason(cut(task.getErrorMessage() == null || task.getErrorMessage().isBlank()
                    ? "AI 处理失败" : task.getErrorMessage(), TEXT_MAX));
        }
        reportMapper.updateById(u);
        log.info("背调结束，reportNo={}, status={}", r.getReportNo(), u.getStatus());
    }

    private void fill(CustomerBackgroundReportDO u, CustomerBackgroundReportDO r, JsonNode out) throws JsonProcessingException {
        JsonNode c = out.path("conclusion");
        if (!c.isObject()) {
            throw new IllegalArgumentException("没有核心结论");
        }
        int priority = c.path("priority").asInt(0);
        int score = c.path("score").asInt(0);
        u.setPriority(priority >= 1 && priority <= 4 ? priority : 0);
        u.setScore(score >= 1 && score <= 5 ? score : 0);
        u.setMainConflict(cut(c.path("mainConflict").asText(""), TEXT_MAX));
        u.setFirstAction(cut(c.path("firstAction").asText(""), TEXT_MAX));
        u.setIdentityNote(cut(c.path("identity").asText(""), TEXT_MAX));
        u.setSupplyNote(cut(c.path("supply").asText(""), TEXT_MAX));
        u.setKeyFinding(cut(c.path("keyFinding").asText(""), TEXT_MAX));
        u.setObstacle(cut(c.path("obstacle").asText(""), TEXT_MAX));
        // 上传的报告正文是原文，上传时已保存；AI 背调用 AI 写的正文
        if (r.getSource() == SOURCE_AI) {
            u.setReportMarkdown(out.path("reportMarkdown").asText(""));
        }
        u.setSuggestions(objectMapper.writeValueAsString(suggestions(out.path("suggestions"), u.getScore())));
    }

    /** 只保留允许的字段，同一字段（联系人字段按联系人）只留第一条；综合评分同时作为潜力评级的建议 */
    private List<BackgroundSuggestion> suggestions(JsonNode arr, int score) {
        List<BackgroundSuggestion> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonNode s : arr.isArray() ? arr : objectMapper.createArrayNode()) {
            String field = s.path("field").asText("");
            String value = s.path("value").asText("").trim();
            if (!BackgroundFields.allowed(field) || value.isEmpty()) {
                continue;
            }
            BackgroundSuggestion x = new BackgroundSuggestion();
            x.setField(field);
            x.setModule(ProfileField.of(field).map(ProfileField::module).orElse(BackgroundFields.isContact(field) ? 4 : 1));
            x.setValue(cut(value, VALUE_MAX));
            x.setEvidence(cut(s.path("evidence").asText(""), TEXT_MAX));
            x.setSourceUrl(cut(s.path("sourceUrl").asText(""), TEXT_MAX));
            JsonNode ct = s.path("contact");
            if (BackgroundFields.isContact(field)) {
                BackgroundSuggestion.Contact contact = new BackgroundSuggestion.Contact();
                contact.setName(cut(ct.path("name").asText("").trim(), 100));
                contact.setEmail(cut(ct.path("email").asText("").trim(), 200));
                contact.setTitle(cut(ct.path("title").asText("").trim(), 200));
                x.setContact(contact);
            }
            String dedup = field + "|" + (x.getContact() == null ? "" : x.getContact().getEmail().toLowerCase() + "|"
                    + x.getContact().getName().toLowerCase());
            if (!seen.add(dedup)) {
                continue;
            }
            x.setState(BackgroundSuggestion.PENDING);
            out.add(x);
        }
        if (score > 0) {
            BackgroundSuggestion p = new BackgroundSuggestion();
            p.setModule(ProfileField.POTENTIAL.module());
            p.setField(ProfileField.POTENTIAL.key());
            p.setValue(String.valueOf(score));
            p.setEvidence("背调综合评分 " + score + " 分");
            p.setSourceUrl("");
            p.setState(BackgroundSuggestion.PENDING);
            out.add(p);
        }
        return out;
    }

    private static String cut(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() > max ? s.substring(0, max) : s;
    }
}
