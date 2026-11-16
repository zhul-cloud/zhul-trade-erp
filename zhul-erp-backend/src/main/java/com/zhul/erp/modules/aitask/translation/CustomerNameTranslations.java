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
import com.zhul.erp.modules.aitask.service.AiConfigService;
import com.zhul.erp.modules.aitask.service.AiTaskResultHandler;
import com.zhul.erp.modules.aitask.service.AiTaskService;
import com.zhul.erp.modules.aitask.support.AiTaskKeys;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.masterdata.customerprofile.entity.CustomerFieldSourceDO;
import com.zhul.erp.modules.masterdata.customerprofile.support.FieldSources;
import com.zhul.erp.modules.masterdata.entity.CustomerDO;
import com.zhul.erp.modules.masterdata.repository.CustomerMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 客户英文名称翻译中文（skill_id=translate-company-names，spec master-data/customer「客户英文名称自动翻译中文」）。
 * <ul>
 *   <li>客户保存后：英文名称新填或有变化、中文名称为空或来源是「AI 翻译」时，后台提交翻译；回调时再检查一次再写入，来源记「AI 翻译」。</li>
 *   <li>编辑页「AI 翻译」按钮：提交只返回译名的任务，前端轮询结果填进输入框，不写库。</li>
 * </ul>
 * 发给编排服务的 key 为「customer:客户ID:英文名称摘要」，回调时英文名称已经又改过的丢弃；按钮的 key 为「preview」。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerNameTranslations implements AiTaskResultHandler {

    public static final String SKILL_ID = "translate-company-names";
    private static final int MAX_NAME = 100;
    private static final String CUSTOMER_KEY = "customer:";
    private static final String PREVIEW_KEY = "preview";

    private final AiTaskService aiTaskService;
    private final AiConfigService aiConfigService;
    private final AiTaskMapper aiTaskMapper;
    private final CustomerMapper customerMapper;
    private final FieldSources fieldSources;
    private final CurrentUserResolver currentUser;
    private final ObjectMapper objectMapper;

    /**
     * 客户保存后调用（在保存事务内，任务在事务提交后才发出）。
     *
     * @param nameBefore 修改前的英文名称，新建时为空
     * @param justTranslated 这次保存的中文名称就是「AI 翻译」按钮的译名，不用再翻译
     */
    public void onSaved(CustomerDO c, String nameBefore, boolean justTranslated, Long requestedBy) {
        String name = text(c.getName());
        if (justTranslated || name.isEmpty() || sameName(name, nameBefore)) {
            return;
        }
        if (!text(c.getNameCn()).isEmpty() && !fromAi(c.getId())) {
            return;
        }
        // 保存时顺带触发：租户未配置 AI 时跳过，不影响客户保存
        if (!aiConfigService.isConfiguredForCurrentTenant()) {
            log.info("当前租户未配置 AI，跳过客户名称翻译，customerId={}", c.getId());
            return;
        }
        aiTaskService.createAndSubmit(SKILL_ID, input(customerKey(c.getId(), name), name), requestedBy);
    }

    /** 「AI 翻译」按钮：提交翻译，返回任务编号；租户未配置 AI 时提示 */
    public TranslationVO preview(String name) {
        AiTaskVO task = aiTaskService.createAndSubmit(SKILL_ID, input(PREVIEW_KEY, text(name)), currentUser.resolve());
        TranslationVO vo = new TranslationVO();
        vo.setTaskId(task.getId());
        vo.setStatus(task.getStatus());
        return vo;
    }

    /** 查询按钮翻译的结果：只能查自己提交的 */
    public TranslationVO status(Long taskId) {
        AiTaskDO task = taskId == null ? null : aiTaskMapper.selectById(taskId);
        if (task == null || task.getDeletedAt() != null || !SKILL_ID.equals(task.getSkillId())
                || !Objects.equals(task.getTenantId(), TenantContext.getTenantId())
                || !Objects.equals(task.getRequestedBy(), requester())) {
            throw new BizException("翻译任务不存在");
        }
        TranslationVO vo = new TranslationVO();
        vo.setTaskId(task.getId());
        vo.setStatus(task.getStatus());
        vo.setErrorMessage(task.getErrorMessage());
        if (task.getStatus() != null && task.getStatus() == AiTaskStatus.COMPLETED) {
            String text = parse(task.getOutput()).get(PREVIEW_KEY);
            if (text != null) {
                TranslationVO.Result r = new TranslationVO.Result();
                r.setKey(PREVIEW_KEY);
                r.setText(text);
                vo.setResults(List.of(r));
            } else {
                vo.setResults(List.of());
            }
        }
        return vo;
    }

    /** 提交人：按用户名找不到用户档案时任务里存的是 0，查询时按同样的规则比较（audit AI-014） */
    private Long requester() {
        Long me = currentUser.resolve();
        return me == null ? 0L : me;
    }

    @Override
    public boolean supports(String skillId) {
        return SKILL_ID.equals(skillId);
    }

    /** 回调：英文名称没再改过、中文名称仍为空或来源仍是「AI 翻译」时写入，来源记「AI 翻译」 */
    @Override
    public void handle(AiTaskDO task) {
        if (task.getStatus() == null || task.getStatus() != AiTaskStatus.COMPLETED) {
            return;
        }
        parse(task.getOutput()).forEach((key, text) -> {
            if (!key.startsWith(CUSTOMER_KEY)) {
                return;
            }
            String[] parts = key.substring(CUSTOMER_KEY.length()).split(":", 2);
            if (parts.length != 2 || parts[0].isEmpty() || !parts[0].chars().allMatch(Character::isDigit)) {
                return;
            }
            fill(Long.valueOf(parts[0]), parts[1], task.getTenantId(), text, task.getRequestedBy());
        });
    }

    private void fill(Long customerId, String nameDigest, Integer tenantId, String text, Long operatorId) {
        CustomerDO c = customerMapper.selectById(customerId);
        if (c == null || c.getDeletedAt() != null || !Objects.equals(c.getTenantId(), tenantId)
                || !digest(text(c.getName())).equals(nameDigest)) {
            return;
        }
        String current = text(c.getNameCn());
        if (!current.isEmpty() && !fromAi(customerId)) {
            return;
        }
        int n = customerMapper.update(null, new LambdaUpdateWrapper<CustomerDO>()
                .set(CustomerDO::getNameCn, text)
                .set(CustomerDO::getUpdateTime, LocalDateTime.now())
                .eq(CustomerDO::getId, customerId)
                .eq(CustomerDO::getTenantId, tenantId)
                .eq(CustomerDO::getNameCn, c.getNameCn()));
        if (n > 0) {
            fieldSources.record(tenantId, customerId, List.of(FieldSources.NAME_CN), FieldSources.AI_TRANSLATE, 0L,
                    operatorId);
        }
    }

    private boolean fromAi(Long customerId) {
        CustomerFieldSourceDO s = fieldSources.get(customerId, FieldSources.NAME_CN);
        return s != null && Objects.equals(s.getSource(), FieldSources.AI_TRANSLATE);
    }

    private String input(String key, String name) {
        try {
            return objectMapper.writeValueAsString(
                    Map.of("items", List.of(Map.of("key", AiTaskKeys.sign(key), "text", name))));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("客户名称翻译请求序列化失败", e);
        }
    }

    /** 编排服务输出 {"items":[{"key","text"}]}；key 校验码不对或译文为空的丢弃 */
    private Map<String, String> parse(String output) {
        Map<String, String> map = new LinkedHashMap<>();
        if (output == null || output.isBlank()) {
            return map;
        }
        try {
            for (JsonNode n : objectMapper.readTree(output).path("items")) {
                String key = AiTaskKeys.verify(n.path("key").asText(""));
                String text = n.path("text").asText("").trim();
                if (key != null && !text.isEmpty()) {
                    map.put(key, text.length() > MAX_NAME ? text.substring(0, MAX_NAME) : text);
                }
            }
        } catch (JsonProcessingException e) {
            log.warn("客户名称翻译结果格式不正确", e);
        }
        return map;
    }

    /** 去掉首尾空格、忽略大小写比较 */
    static boolean sameName(String a, String b) {
        return b != null && text(a).equalsIgnoreCase(text(b));
    }

    /** 客户翻译任务的 key（不含校验码）：「customer:客户ID:英文名称摘要」 */
    public static String customerKey(Long customerId, String name) {
        return CUSTOMER_KEY + customerId + ":" + digest(name);
    }

    /** 英文名称摘要：回调时据此判断名称是否又改过 */
    static String digest(String name) {
        return Integer.toHexString(text(name).toLowerCase(Locale.ROOT).hashCode());
    }

    private static String text(String s) {
        return s == null ? "" : s.trim();
    }
}
