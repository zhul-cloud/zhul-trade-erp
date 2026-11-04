package com.zhul.erp.modules.crm.followup.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zhul.erp.modules.aitask.constants.AiTaskStatus;
import com.zhul.erp.modules.aitask.entity.AiTaskDO;
import com.zhul.erp.modules.aitask.repository.AiTaskMapper;
import com.zhul.erp.modules.aitask.service.AiTaskResultHandler;
import com.zhul.erp.modules.crm.followup.constants.FollowupConstants;
import com.zhul.erp.modules.crm.followup.dto.SopCardVO;
import com.zhul.erp.modules.masterdata.customerprofile.service.CustomerProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * skill_id=followup-extract / followup-reply 的结果处理器（design D11）。
 * 整理结果只写回 ai_task 的结果，不改业务数据：卡点只留 SOP 卡点码、档案建议只留可补字段、客户情况只留三个取值、下一步最多 2 条。
 * 英文回复由发起请求同步读取结果，这里不需要后续动作。
 * 回调没有租户上下文，由 {@code AiTaskServiceImpl.handleCallback} 按任务所属租户执行（卡点字典按租户读取）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FollowupExtractResultHandler implements AiTaskResultHandler {

    private static final Set<String> SITUATIONS = Set.of("feedback", "negotiating", "no_reply");

    private final FollowupService followupService;
    private final AiTaskMapper aiTaskMapper;
    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(String skillId) {
        return FollowupConstants.SKILL_EXTRACT.equals(skillId) || FollowupConstants.SKILL_REPLY.equals(skillId);
    }

    @Override
    public void handle(AiTaskDO task) {
        if (!FollowupConstants.SKILL_EXTRACT.equals(task.getSkillId())
                || task.getStatus() == null || task.getStatus() != AiTaskStatus.COMPLETED) {
            return;
        }
        String cleaned = clean(task.getOutput());
        AiTaskDO update = new AiTaskDO();
        update.setId(task.getId());
        update.setOutput(cleaned);
        aiTaskMapper.updateById(update);
        task.setOutput(cleaned);
    }

    String clean(String output) {
        JsonNode in;
        try {
            in = objectMapper.readTree(output == null ? "{}" : output);
        } catch (JsonProcessingException e) {
            log.warn("跟进整理结果不是 JSON，已清空：{}", e.getOriginalMessage());
            in = objectMapper.createObjectNode();
        }
        Set<String> blockers = followupService.blockerVos().stream().map(SopCardVO.Blocker::getCode).collect(Collectors.toSet());
        ObjectNode out = objectMapper.createObjectNode();
        String summary = in.path("summary").asText("");
        out.put("summary", summary.length() > 1000 ? summary.substring(0, 1000) : summary);
        ArrayNode situation = out.putArray("situation");
        boolean replied = false;
        for (JsonNode s : in.path("situation")) {
            replied |= "feedback".equals(s.asText()) || "negotiating".equals(s.asText());
        }
        for (JsonNode s : in.path("situation")) {
            String v = s.asText();
            if (SITUATIONS.contains(v) && !(replied && "no_reply".equals(v))) {
                situation.add(v);
            }
        }
        String blocker = in.path("blocker").asText("");
        out.put("blocker", blockers.contains(blocker) ? blocker : "");
        ArrayNode actions = out.putArray("nextActions");
        for (JsonNode a : in.path("nextActions")) {
            String text = a.path("text").asText("").trim();
            if (!text.isEmpty() && actions.size() < 2) {
                ObjectNode x = actions.addObject();
                x.put("text", text.length() > FollowupConstants.NEXT_ACTION_MAX ? text.substring(0, FollowupConstants.NEXT_ACTION_MAX) : text);
                x.put("days", Math.max(0, Math.min(a.path("days").asInt(0), 60)));
            }
        }
        ArrayNode suggestions = out.putArray("profileSuggestions");
        for (JsonNode s : in.path("profileSuggestions")) {
            String field = s.path("field").asText("");
            String value = s.path("value").asText("").trim();
            if (CustomerProfileService.FOLLOWUP_FIELDS.contains(field) && !value.isEmpty()) {
                ObjectNode x = suggestions.addObject();
                x.put("field", field);
                x.put("value", value);
                x.put("evidence", s.path("evidence").asText(""));
            }
        }
        out.put("truncated", in.path("truncated").asBoolean(false));
        return out.toString();
    }
}
