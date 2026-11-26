package com.zhul.erp.modules.product.candidate.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.security.SecurityUtils;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.aitask.constants.AiTaskStatus;
import com.zhul.erp.modules.aitask.dto.AiTaskVO;
import com.zhul.erp.modules.aitask.entity.AiTaskDO;
import com.zhul.erp.modules.aitask.repository.AiTaskMapper;
import com.zhul.erp.modules.aitask.service.AiTaskResultHandler;
import com.zhul.erp.modules.aitask.service.AiTaskService;
import com.zhul.erp.modules.aitask.support.AiTaskKeys;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.product.candidate.constants.CandidateConstants;
import com.zhul.erp.modules.product.candidate.dto.UnlinkedAdoptVO;
import com.zhul.erp.modules.product.candidate.dto.UnlinkedAiVO;
import com.zhul.erp.modules.product.candidate.dto.UnlinkedGroupVO;
import com.zhul.erp.modules.product.candidate.entity.DocUnlinkedMarkDO;
import com.zhul.erp.modules.product.candidate.repository.DocUnlinkedMarkMapper;
import com.zhul.erp.modules.product.constants.ProductErrorCodes;
import com.zhul.erp.modules.system.service.LogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 待关联清单的「标为不是商品」、批量 AI 识别型号与批量采纳（openspec unlinked-docs-enhance）。
 * 标记与 AI 建议按「当前公司 + 归并键」记在 doc_unlinked_mark，单据明细本身不改。
 * AI 只给建议：识别结果写进标记表，关联商品必须由人「采纳」（走「填写真实型号」）。
 */
@Service
@RequiredArgsConstructor
public class UnlinkedMarkService implements AiTaskResultHandler {

    public static final String SKILL_ID = "unlinked-model-parse";
    static final int KIND_MODEL = 1;
    static final int KIND_FEE = 2;
    static final int KIND_UNKNOWN = 3;
    private static final int MAX_BRAND = 64;
    private static final int MAX_MODEL = 128;

    private final DocProductLinkService linkService;
    private final DocUnlinkedMarkMapper markMapper;
    private final AiTaskService aiTaskService;
    private final AiTaskMapper aiTaskMapper;
    private final CurrentUserResolver currentUser;
    private final ObjectMapper objectMapper;
    private final LogService logService;

    // ---------------------------------------------------------------- 标为不是商品

    /** 标为不是商品：只标清单里还有的组；返回标记的组数 */
    @Transactional(rollbackFor = Exception.class)
    public int ignore(List<String> keys) {
        Map<String, UnlinkedGroupVO> groups = linkService.groupsOf(distinct(keys));
        if (groups.isEmpty()) {
            throw new BizException("这些型号已关联或不在待关联清单里，请刷新");
        }
        String operator = operator();
        LocalDateTime now = LocalDateTime.now();
        for (String key : groups.keySet()) {
            DocUnlinkedMarkDO m = markOf(key);
            m.setIgnored(1);
            m.setIgnoredBy(operator);
            m.setIgnoredAt(now);
            save(m);
        }
        logService.recordOperateLog(CandidateConstants.MENU, "待关联单据标为不是商品", null,
                Map.of("models", groups.values().stream().map(UnlinkedGroupVO::getModel).toList()));
        return groups.size();
    }

    /** 恢复：去掉「不是商品」标记，回到待关联清单；返回恢复的组数 */
    @Transactional(rollbackFor = Exception.class)
    public int restore(List<String> keys) {
        int n = 0;
        for (DocUnlinkedMarkDO m : markMapper.selectList(new LambdaQueryWrapper<DocUnlinkedMarkDO>()
                .in(DocUnlinkedMarkDO::getGroupKey, distinct(keys))
                .eq(DocUnlinkedMarkDO::getIgnored, 1)
                .isNull(DocUnlinkedMarkDO::getDeletedAt))) {
            m.setIgnored(0);
            m.setIgnoredBy("");
            m.setIgnoredAt(null);
            markMapper.updateById(m);
            n++;
        }
        if (n > 0) {
            logService.recordOperateLog(CandidateConstants.MENU, "待关联单据恢复", null, Map.of("keys", keys, "count", n));
        }
        return n;
    }

    // ---------------------------------------------------------------- AI 识别型号

    public UnlinkedAiVO submit(List<String> keys) {
        Long user = currentUser.resolve();
        boolean running = aiTaskMapper.selectCount(new LambdaQueryWrapper<AiTaskDO>()
                .eq(AiTaskDO::getSkillId, SKILL_ID)
                .eq(AiTaskDO::getTenantId, TenantContext.getTenantId())
                .eq(AiTaskDO::getRequestedBy, user)
                .in(AiTaskDO::getStatus, AiTaskStatus.OPEN)
                .isNull(AiTaskDO::getDeletedAt)) > 0;
        if (running) {
            throw new BizException("上一次 AI 识别还没跑完，跑完再提交");
        }
        List<String> wanted = distinct(keys);
        Map<String, UnlinkedGroupVO> groups = linkService.groupsOf(wanted);
        List<Map<String, Object>> items = new ArrayList<>(groups.size());
        groups.forEach((key, g) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", AiTaskKeys.sign(key));
            item.put("brand", g.getBrand());
            item.put("model", g.getModel());
            item.put("docs", new ArrayList<>(g.getDocs().keySet()));
            items.add(item);
        });
        UnlinkedAiVO vo = new UnlinkedAiVO();
        vo.setTotal(items.size());
        vo.setSkipped(wanted.size() - items.size());
        if (items.isEmpty()) {
            return vo;
        }
        AiTaskVO task = aiTaskService.createAndSubmit(SKILL_ID, write(Map.of("items", items)), user);
        vo.setTaskId(task.getId());
        vo.setStatus(task.getStatus());
        return vo;
    }

    /** 只能查自己提交的 */
    public UnlinkedAiVO status(Long taskId) {
        AiTaskDO task = taskId == null ? null : aiTaskMapper.selectById(taskId);
        if (task == null || task.getDeletedAt() != null || !SKILL_ID.equals(task.getSkillId())
                || !Objects.equals(task.getTenantId(), TenantContext.getTenantId())
                || !Objects.equals(task.getRequestedBy(), currentUser.resolve())) {
            throw BizException.of(ProductErrorCodes.AI_TASK_NOT_FOUND, "AI 识别任务不存在");
        }
        UnlinkedAiVO vo = new UnlinkedAiVO();
        vo.setTaskId(task.getId());
        vo.setStatus(task.getStatus());
        vo.setErrorMessage(task.getErrorMessage());
        JsonNode summary = read(task.getOutput()).path("summary");
        if (!summary.isMissingNode()) {
            vo.setModels(summary.path("models").asInt());
            vo.setFees(summary.path("fees").asInt());
            vo.setUnknown(summary.path("unknown").asInt());
        }
        return vo;
    }

    @Override
    public boolean supports(String skillId) {
        return SKILL_ID.equals(skillId);
    }

    /** 回调：按归并键写 AI 建议（覆盖上一次的建议，不动「不是商品」标记）；回调没有登录上下文，按任务所属公司写 */
    @Override
    public void handle(AiTaskDO task) {
        if (task.getStatus() == null || task.getStatus() != AiTaskStatus.COMPLETED) {
            return;
        }
        TenantContext.runAs(task.getTenantId(), () -> writeSuggestions(task));
    }

    private void writeSuggestions(AiTaskDO task) {
        JsonNode output = read(task.getOutput());
        int[] counts = new int[4];
        Map<String, Boolean> seen = new HashMap<>();
        LocalDateTime now = LocalDateTime.now();
        for (JsonNode n : output.path("items")) {
            String key = AiTaskKeys.verify(n.path("key").asText(""));
            if (key == null || seen.put(key, Boolean.TRUE) != null) {
                continue;
            }
            String model = cut(n.path("model").asText("").trim(), MAX_MODEL);
            String kindText = n.path("kind").asText("");
            int kind = "model".equals(kindText) && !model.isEmpty() ? KIND_MODEL : "fee".equals(kindText) ? KIND_FEE : KIND_UNKNOWN;
            DocUnlinkedMarkDO m = markOf(key);
            m.setAiKind(kind);
            m.setAiBrand(kind == KIND_MODEL ? cut(n.path("brand").asText("").trim(), MAX_BRAND) : "");
            m.setAiModel(kind == KIND_MODEL ? model : "");
            m.setAiTaskId(task.getId());
            m.setAiAt(now);
            save(m);
            counts[kind]++;
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("models", counts[KIND_MODEL]);
        summary.put("fees", counts[KIND_FEE]);
        summary.put("unknown", counts[KIND_UNKNOWN]);
        Map<String, Object> merged = new LinkedHashMap<>();
        merged.put("items", output.path("items"));
        merged.put("summary", summary);
        AiTaskDO update = new AiTaskDO();
        update.setId(task.getId());
        update.setOutput(write(merged));
        aiTaskMapper.updateById(update);
    }

    // ---------------------------------------------------------------- 批量采纳

    /**
     * 批量采纳：逐组按 AI 建议的品牌与型号「填写真实型号」关联。每组各自一个事务（经代理调用），
     * 一组失败（型号不合规、候选已驳回、已被别人关联）只记下原因，不影响其他组。
     */
    public UnlinkedAdoptVO adopt(List<String> keys) {
        UnlinkedAdoptVO vo = new UnlinkedAdoptVO();
        Map<String, DocUnlinkedMarkDO> marks = linkService.marks(distinct(keys));
        for (String key : distinct(keys)) {
            DocUnlinkedMarkDO m = marks.get(key);
            if (m == null || !Objects.equals(m.getAiKind(), KIND_MODEL) || !StringUtils.hasText(m.getAiModel())) {
                vo.getFailures().add(failure(key, null, "没有 AI 建议的型号"));
                continue;
            }
            try {
                vo.setLines(vo.getLines() + linkService.fillModel(key, m.getAiBrand(), m.getAiModel()));
                vo.setAdopted(vo.getAdopted() + 1);
            } catch (BizException e) {
                vo.getFailures().add(failure(key, m.getAiModel(), e.getMessage()));
            }
        }
        return vo;
    }

    private static UnlinkedAdoptVO.Failure failure(String key, String model, String message) {
        UnlinkedAdoptVO.Failure f = new UnlinkedAdoptVO.Failure();
        f.setKey(key);
        f.setModel(model);
        f.setMessage(message);
        return f;
    }

    // ---------------------------------------------------------------- 工具

    /** 当前公司这个归并键的标记，没有时新建一条（未保存） */
    private DocUnlinkedMarkDO markOf(String key) {
        DocUnlinkedMarkDO m = markMapper.selectOne(new LambdaQueryWrapper<DocUnlinkedMarkDO>()
                .eq(DocUnlinkedMarkDO::getGroupKey, key)
                .isNull(DocUnlinkedMarkDO::getDeletedAt));
        if (m != null) {
            return m;
        }
        m = new DocUnlinkedMarkDO();
        m.setTenantId(TenantContext.getTenantId());
        m.setGroupKey(key);
        m.setIgnored(0);
        m.setIgnoredBy("");
        m.setAiKind(0);
        m.setAiBrand("");
        m.setAiModel("");
        return m;
    }

    private void save(DocUnlinkedMarkDO m) {
        if (m.getId() == null) {
            markMapper.insert(m);
        } else {
            markMapper.updateById(m);
        }
    }

    private static List<String> distinct(List<String> keys) {
        return keys == null ? List.of() : keys.stream().filter(StringUtils::hasText).distinct().toList();
    }

    private static String operator() {
        String name = SecurityUtils.getCurrentUsername();
        return name == null ? "" : cut(name, 32);
    }

    private static String cut(String s, int max) {
        return s == null ? "" : s.length() > max ? s.substring(0, max) : s;
    }

    private String write(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("序列化 AI 输入失败", e);
        }
    }

    private JsonNode read(String json) {
        try {
            return objectMapper.readTree(json == null || json.isBlank() ? "{}" : json);
        } catch (JsonProcessingException e) {
            return objectMapper.createObjectNode();
        }
    }
}
