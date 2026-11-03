package com.zhul.erp.modules.aitask.translation;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.modules.aitask.constants.AiTaskStatus;
import com.zhul.erp.modules.aitask.entity.AiTaskDO;
import com.zhul.erp.modules.aitask.service.AiConfigService;
import com.zhul.erp.modules.aitask.service.AiTaskResultHandler;
import com.zhul.erp.modules.aitask.service.AiTaskService;
import com.zhul.erp.modules.masterdata.customerprofile.entity.CustomerContactDO;
import com.zhul.erp.modules.masterdata.customerprofile.repository.CustomerContactMapper;
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
 * 发给编排服务的 key 为客户 ID；联系人中文名（spec master-data/customer-profile）的 key 为「contact:联系人 ID」，
 * 回填联系人中文名，主联系人同时回填客户的主联系人中文名，同样只写仍为空的。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContactNameTranslations implements AiTaskResultHandler {

    public static final String SKILL_ID = "translate-contact-names";
    private static final int MAX_NAME = 100;

    private final AiTaskService aiTaskService;
    private final AiConfigService aiConfigService;
    private final CustomerMapper customerMapper;
    private final CustomerContactMapper contactMapper;
    private final ObjectMapper objectMapper;

    /** 提交翻译；联系人名称为空或已含中文时不提交。提交在当前事务提交后异步发出，失败不影响调用方 */
    public void submit(Long customerId, String contactName, Long requestedBy) {
        if (customerId != null) {
            send(String.valueOf(customerId), contactName, requestedBy);
        }
    }

    /** 联系人保存后：中文名为空、英文名不为空且不含中文时提交翻译；租户未配置 AI 时跳过 */
    public void submitContact(Long contactId, String name, String nameCn, Long requestedBy) {
        if (contactId != null && (nameCn == null || nameCn.isBlank())) {
            send(CONTACT_KEY + contactId, name, requestedBy);
        }
    }

    private static final String CONTACT_KEY = "contact:";

    private void send(String key, String contactName, Long requestedBy) {
        String name = contactName == null ? "" : contactName.trim();
        if (name.isEmpty() || name.codePoints().anyMatch(c -> c >= 0x4E00 && c <= 0x9FFF)) {
            return;
        }
        // 保存商机时顺带触发：租户未配置 AI 时跳过，不影响商机保存（不能靠捕获异常，见 isConfiguredForCurrentTenant）
        if (!aiConfigService.isConfiguredForCurrentTenant()) {
            log.info("当前租户未配置 AI，跳过联系人名称翻译，key={}", key);
            return;
        }
        try {
            String input = objectMapper.writeValueAsString(
                    Map.of("items", List.of(Map.of("key", key, "text", name))));
            aiTaskService.createAndSubmit(SKILL_ID, input, requestedBy);
        } catch (JsonProcessingException e) {
            log.warn("联系人名称翻译请求序列化失败，key={}", key, e);
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
            if (text.isEmpty()) {
                continue;
            }
            String cut = text.length() > MAX_NAME ? text.substring(0, MAX_NAME) : text;
            if (key.startsWith(CONTACT_KEY) && isId(key.substring(CONTACT_KEY.length()))) {
                fillContact(Long.valueOf(key.substring(CONTACT_KEY.length())), task.getTenantId(), cut);
                continue;
            }
            if (!isId(key)) {
                continue;
            }
            customerMapper.update(null, new LambdaUpdateWrapper<CustomerDO>()
                    .set(CustomerDO::getNameCn, cut)
                    .set(CustomerDO::getUpdateTime, LocalDateTime.now())
                    .eq(CustomerDO::getId, Long.valueOf(key))
                    .eq(CustomerDO::getTenantId, task.getTenantId())
                    .eq(CustomerDO::getNameCn, ""));
        }
    }

    private static boolean isId(String s) {
        return !s.isEmpty() && s.chars().allMatch(Character::isDigit);
    }

    /** 回填联系人中文名（只写仍为空的）；是主联系人时同步客户的主联系人中文名（同样只写仍为空的） */
    private void fillContact(Long contactId, Integer tenantId, String text) {
        int n = contactMapper.update(null, new LambdaUpdateWrapper<CustomerContactDO>()
                .set(CustomerContactDO::getNameCn, text)
                .set(CustomerContactDO::getUpdateTime, LocalDateTime.now())
                .eq(CustomerContactDO::getId, contactId)
                .eq(CustomerContactDO::getTenantId, tenantId)
                .isNull(CustomerContactDO::getDeletedAt)
                .eq(CustomerContactDO::getNameCn, ""));
        if (n == 0) {
            return;
        }
        CustomerContactDO c = contactMapper.selectById(contactId);
        if (c != null && Integer.valueOf(1).equals(c.getIsPrimary())) {
            customerMapper.update(null, new LambdaUpdateWrapper<CustomerDO>()
                    .set(CustomerDO::getContactNameCn, text)
                    .eq(CustomerDO::getId, c.getCustomerId())
                    .eq(CustomerDO::getTenantId, tenantId)
                    .eq(CustomerDO::getContactNameCn, ""));
        }
    }
}
