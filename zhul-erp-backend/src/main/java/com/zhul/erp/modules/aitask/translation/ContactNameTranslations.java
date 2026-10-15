package com.zhul.erp.modules.aitask.translation;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.modules.aitask.constants.AiTaskStatus;
import com.zhul.erp.modules.aitask.entity.AiTaskDO;
import com.zhul.erp.modules.aitask.service.AiTaskResultHandler;
import com.zhul.erp.modules.aitask.service.AiTaskService;
import com.zhul.erp.modules.masterdata.entity.CustomerDO;
import com.zhul.erp.modules.masterdata.repository.CustomerMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 联系人名称译中文（skill_id=translate-contact-names，spec crm/opportunity「登记新商机」）：
 * 登记商机新建客户后提交，回调成功时把译名写入客户中文名称，只写仍为空的，不覆盖手动填写。
 * 发给编排服务的 key 为客户 ID。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContactNameTranslations implements AiTaskResultHandler {

    public static final String SKILL_ID = "translate-contact-names";
    private static final int MAX_NAME = 100;

    private final AiTaskService aiTaskService;
    private final CustomerMapper customerMapper;
    private final ObjectMapper objectMapper;

    /** 提交翻译；联系人名称为空或已含中文时不提交。提交在当前事务提交后异步发出，失败不影响调用方 */
    public void submit(Long customerId, String contactName, Long requestedBy) {
        String name = contactName == null ? "" : contactName.trim();
        if (customerId == null || name.isEmpty() || name.codePoints().anyMatch(c -> c >= 0x4E00 && c <= 0x9FFF)) {
            return;
        }
        try {
            String input = objectMapper.writeValueAsString(
                    Map.of("items", List.of(Map.of("key", String.valueOf(customerId), "text", name))));
            aiTaskService.createAndSubmit(SKILL_ID, input, requestedBy);
        } catch (JsonProcessingException e) {
            log.warn("联系人名称翻译请求序列化失败，customerId={}", customerId, e);
        }
    }

    @Override
    public boolean supports(String skillId) {
        return SKILL_ID.equals(skillId);
    }

    @Override
    public void handle(AiTaskDO task) {
        if (task.getStatus() == null || task.getStatus() != AiTaskStatus.COMPLETED
                || task.getOutput() == null || task.getOutput().isBlank()) {
            return;
        }
        JsonNode items;
        try {
            items = objectMapper.readTree(task.getOutput()).path("items");
        } catch (JsonProcessingException e) {
            log.warn("联系人名称翻译结果格式不正确，taskId={}", task.getId(), e);
            return;
        }
        for (JsonNode n : items) {
            String key = n.path("key").asText("");
            String text = n.path("text").asText("").trim();
            if (key.isEmpty() || !key.chars().allMatch(Character::isDigit) || text.isEmpty()) {
                continue;
            }
            customerMapper.update(null, new LambdaUpdateWrapper<CustomerDO>()
                    .set(CustomerDO::getNameCn, text.length() > MAX_NAME ? text.substring(0, MAX_NAME) : text)
                    .set(CustomerDO::getUpdateTime, LocalDateTime.now())
                    .eq(CustomerDO::getId, Long.valueOf(key))
                    .eq(CustomerDO::getTenantId, task.getTenantId())
                    .eq(CustomerDO::getNameCn, ""));
        }
    }
}
