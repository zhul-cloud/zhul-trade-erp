package com.zhul.erp.modules.aitask.translation;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
import com.zhul.erp.modules.inquiry.item.entity.InquiryItemDO;
import com.zhul.erp.modules.inquiry.item.repository.InquiryItemMapper;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 生成英文描述（skill_id=translate-item-descriptions）：提交 AI 任务、查询进度；
 * 回调成功时把译文补到来源询盘型号的英文描述（只补空的）。报价行、PI 行由前端拿到结果后自己填，业务员保存后生效。
 * 发给编排服务的 key 为「询盘型号 ID#调用方 key」，回调时据此找到询盘型号，返回给前端时去掉前缀。
 */
@Component
@RequiredArgsConstructor
public class DescriptionTranslations implements AiTaskResultHandler {

    public static final String SKILL_ID = "translate-item-descriptions";
    private static final int MAX_TEXT = 300;

    private final AiTaskService aiTaskService;
    private final AiTaskMapper aiTaskMapper;
    private final InquiryItemMapper inquiryItemMapper;
    private final CurrentUserResolver currentUser;
    private final ObjectMapper objectMapper;

    public TranslationVO submit(TranslateItemsRequest req) {
        List<Map<String, String>> items = new ArrayList<>(req.getItems().size());
        for (TranslateItemsRequest.Item i : req.getItems()) {
            long inquiryItem = i.getInquiryItemId() == null ? 0 : i.getInquiryItemId();
            items.add(Map.of("key", inquiryItem + "#" + i.getKey(), "text", i.getText().trim()));
        }
        String input;
        try {
            input = objectMapper.writeValueAsString(Map.of("items", items));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("翻译请求序列化失败", e);
        }
        AiTaskVO task = aiTaskService.createAndSubmit(SKILL_ID, input, currentUser.resolve());
        TranslationVO vo = new TranslationVO();
        vo.setTaskId(task.getId());
        vo.setStatus(task.getStatus());
        return vo;
    }

    /** 只能查自己提交的翻译任务 */
    public TranslationVO status(Long taskId) {
        AiTaskDO task = taskId == null ? null : aiTaskMapper.selectById(taskId);
        if (task == null || task.getDeletedAt() != null || !SKILL_ID.equals(task.getSkillId())
                || !Objects.equals(task.getTenantId(), TenantContext.getTenantId())
                || !Objects.equals(task.getRequestedBy(), currentUser.resolve())) {
            throw new BizException("翻译任务不存在");
        }
        TranslationVO vo = new TranslationVO();
        vo.setTaskId(task.getId());
        vo.setStatus(task.getStatus());
        vo.setErrorMessage(task.getErrorMessage());
        if (task.getStatus() != null && task.getStatus() == AiTaskStatus.COMPLETED) {
            List<TranslationVO.Result> results = new ArrayList<>();
            parse(task.getOutput()).forEach((key, text) -> {
                TranslationVO.Result r = new TranslationVO.Result();
                r.setKey(key.substring(key.indexOf('#') + 1));
                r.setText(text);
                results.add(r);
            });
            vo.setResults(results);
        }
        return vo;
    }

    @Override
    public boolean supports(String skillId) {
        return SKILL_ID.equals(skillId);
    }

    /** 成功回调：补询盘型号的英文描述（只补空的，不覆盖人工填写的） */
    @Override
    public void handle(AiTaskDO task) {
        if (task.getStatus() == null || task.getStatus() != AiTaskStatus.COMPLETED) {
            return;
        }
        parse(task.getOutput()).forEach((key, text) -> {
            long inquiryItem = Long.parseLong(key.substring(0, key.indexOf('#')));
            if (inquiryItem > 0) {
                inquiryItemMapper.update(null, new LambdaUpdateWrapper<InquiryItemDO>()
                        .set(InquiryItemDO::getDescriptionEn, text)
                        .eq(InquiryItemDO::getId, inquiryItem)
                        .eq(InquiryItemDO::getTenantId, task.getTenantId())
                        .eq(InquiryItemDO::getDescriptionEn, ""));
            }
        });
    }

    /** 编排服务输出 {"items":[{"key","text"}]}；key 不合约定或译文为空的丢弃 */
    private Map<String, String> parse(String output) {
        Map<String, String> map = new LinkedHashMap<>();
        if (output == null || output.isBlank()) {
            return map;
        }
        try {
            for (JsonNode n : objectMapper.readTree(output).path("items")) {
                String key = n.path("key").asText("");
                String text = n.path("text").asText("").trim();
                int hash = key.indexOf('#');
                if (hash > 0 && key.substring(0, hash).chars().allMatch(Character::isDigit) && !text.isEmpty()) {
                    map.put(key, text.length() > MAX_TEXT ? text.substring(0, MAX_TEXT) : text);
                }
            }
        } catch (JsonProcessingException e) {
            throw new BizException("翻译结果格式不正确", e);
        }
        return map;
    }
}
