package com.zhul.erp.modules.product.candidate.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.product.candidate.constants.CandidateConstants;
import com.zhul.erp.modules.product.candidate.dto.CandidateAiFillRequest;
import com.zhul.erp.modules.product.candidate.dto.CandidateAiFillVO;
import com.zhul.erp.modules.product.candidate.entity.ProductCandidateDO;
import com.zhul.erp.modules.product.candidate.repository.ProductCandidateMapper;
import com.zhul.erp.modules.product.constants.ProductConstants;
import com.zhul.erp.modules.product.entity.ProductCategoryDO;
import com.zhul.erp.modules.product.repository.ProductCategoryMapper;
import com.zhul.erp.modules.product.support.BrandResolver;
import com.zhul.erp.modules.product.support.CategoryMatcher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 商品候选批量 AI 解析品类、品牌（spec product/product-candidate「批量 AI 解析品类与品牌」，skill_id=candidate-classify）。
 * 提交时筛出需要处理的候选交给 AI；回调时只改仍待审核、且品类（品牌）仍是提交时状态的候选，运行期间被人工改过的不覆盖。
 * 发给编排服务的 key 为「模式（c 品类 / b 品牌）+ 候选 ID#提交时的品类 ID（没有为 0）」，回调据此区分模式、判断品类有没有被改过。
 * 结果汇总写回 ai_task.output 的 summary，查询进度时直接返回。
 */
@Component
@RequiredArgsConstructor
public class CandidateAiFill implements AiTaskResultHandler {

    public static final String SKILL_ID = "candidate-classify";
    public static final String MODE_CATEGORY = "category";
    public static final String MODE_BRAND = "brand";
    private static final int MAX_TEXT = 200;
    private static final int MAX_BRAND = 64;

    private final ProductCandidateMapper candidateMapper;
    private final ProductCategoryMapper categoryMapper;
    private final ProductCandidateService candidateService;
    private final BrandResolver brandResolver;
    private final AiTaskService aiTaskService;
    private final AiTaskMapper aiTaskMapper;
    private final CurrentUserResolver currentUser;
    private final ObjectMapper objectMapper;

    public CandidateAiFillVO submit(CandidateAiFillRequest req) {
        Long user = currentUser.resolve();
        boolean running = aiTaskMapper.selectCount(new LambdaQueryWrapper<AiTaskDO>()
                .eq(AiTaskDO::getSkillId, SKILL_ID)
                .eq(AiTaskDO::getTenantId, TenantContext.getTenantId())
                .eq(AiTaskDO::getRequestedBy, user)
                .in(AiTaskDO::getStatus, AiTaskStatus.OPEN)
                .isNull(AiTaskDO::getDeletedAt)) > 0;
        if (running) {
            throw new BizException("上一次 AI 解析还没跑完，跑完再提交");
        }
        boolean brand = MODE_BRAND.equals(req.getMode());
        if (brand && brandResolver.resolve(ProductConstants.OTHERS_BRAND) == null) {
            throw new BizException("品牌库缺少 Others，请联系管理员");
        }
        List<ProductCategoryDO> categories = categories();
        Long other = CategoryMatcher.otherId(categories, CandidateConstants.CATEGORY_OTHER);
        Map<Long, ProductCategoryDO> byId = categories.stream().collect(Collectors.toMap(ProductCategoryDO::getId, Function.identity()));
        List<Long> ids = req.getIds().stream().filter(Objects::nonNull).distinct().toList();
        List<ProductCandidateDO> rows = ids.isEmpty() ? List.of() : candidateMapper.selectBatchIds(ids);
        List<Map<String, Object>> items = new ArrayList<>(rows.size());
        for (ProductCandidateDO c : rows) {
            if (c.getDeletedAt() != null || c.getStatus() != CandidateConstants.STATUS_PENDING || !candidateService.canSee(c.getId())) {
                continue;
            }
            boolean need = brand ? c.getBrandId() == null : needsCategory(c.getCategoryId(), other, byId);
            if (!need) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", (brand ? "b" : "c") + c.getId() + "#" + (c.getCategoryId() == null ? 0 : c.getCategoryId()));
            item.put("brand", nz(c.getBrandText()));
            item.put("model", nz(c.getMpnRaw()));
            item.put("name", cut(c.getProductName()));
            item.put("description", cut(c.getDescription()));
            item.put("categoryText", cut(c.getCategoryText()));
            items.add(item);
        }
        CandidateAiFillVO vo = new CandidateAiFillVO();
        vo.setMode(req.getMode());
        vo.setTotal(items.size());
        vo.setSkipped(ids.size() - items.size());
        if (items.isEmpty()) {
            return vo;
        }
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("mode", req.getMode());
        input.put("items", items);
        if (!brand) {
            input.put("categories", categories.stream().map(c -> Map.of("id", c.getId(), "name", path(c, byId))).toList());
        }
        AiTaskVO task = aiTaskService.createAndSubmit(SKILL_ID, write(input), user);
        vo.setTaskId(task.getId());
        vo.setStatus(task.getStatus());
        return vo;
    }

    /** 只能查自己提交的 */
    public CandidateAiFillVO status(Long taskId) {
        AiTaskDO task = taskId == null ? null : aiTaskMapper.selectById(taskId);
        if (task == null || task.getDeletedAt() != null || !SKILL_ID.equals(task.getSkillId())
                || !Objects.equals(task.getTenantId(), TenantContext.getTenantId())
                || !Objects.equals(task.getRequestedBy(), currentUser.resolve())) {
            throw new BizException("AI 解析任务不存在");
        }
        CandidateAiFillVO vo = new CandidateAiFillVO();
        vo.setTaskId(task.getId());
        vo.setStatus(task.getStatus());
        vo.setErrorMessage(task.getErrorMessage());
        JsonNode summary = read(task.getOutput()).path("summary");
        if (!summary.isMissingNode()) {
            vo.setMode(summary.path("mode").asText(null));
            vo.setFilled(summary.path("filled").asInt());
            vo.setMatched(summary.path("matched").asInt());
            vo.setUnmatched(summary.path("unmatched").asInt());
            vo.setUnchanged(summary.path("unchanged").asInt());
        }
        return vo;
    }

    @Override
    public boolean supports(String skillId) {
        return SKILL_ID.equals(skillId);
    }

    @Override
    public void handle(AiTaskDO task) {
        if (task.getStatus() == null || task.getStatus() != AiTaskStatus.COMPLETED) {
            return;
        }
        JsonNode output = read(task.getOutput());
        List<ProductCategoryDO> categories = categories();
        Map<Long, ProductCategoryDO> byId = categories.stream().collect(Collectors.toMap(ProductCategoryDO::getId, Function.identity()));
        boolean brandMode = false;
        int filled = 0;
        int matched = 0;
        int unmatched = 0;
        Map<Long, Boolean> seen = new HashMap<>();
        Long others = null;
        boolean othersLoaded = false;
        for (JsonNode n : output.path("items")) {
            Key k = Key.parse(n.path("key").asText(""));
            if (k == null || seen.put(k.id(), Boolean.TRUE) != null) {
                continue;
            }
            brandMode = k.brand();
            if (k.brand()) {
                if (!othersLoaded) {
                    others = brandResolver.resolve(ProductConstants.OTHERS_BRAND);
                    othersLoaded = true;
                }
                int r = writeBrand(k.id(), n.path("brand").asText("").trim(), others);
                matched += r == 1 ? 1 : 0;
                unmatched += r == 2 ? 1 : 0;
            } else {
                long categoryId = n.path("categoryId").asLong(0);
                filled += categoryId > 0 && writeCategory(k.id(), k.before(), categoryId, byId) ? 1 : 0;
            }
        }
        int unchanged = seen.size() - filled - matched - unmatched;
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("mode", brandMode ? MODE_BRAND : MODE_CATEGORY);
        summary.put("filled", filled);
        summary.put("matched", matched);
        summary.put("unmatched", unmatched);
        summary.put("unchanged", unchanged);
        Map<String, Object> merged = new LinkedHashMap<>();
        merged.put("items", output.path("items"));
        merged.put("summary", summary);
        AiTaskDO update = new AiTaskDO();
        update.setId(task.getId());
        update.setOutput(write(merged));
        aiTaskMapper.updateById(update);
    }

    /** 回调 key：模式（c / b）+ 候选 ID#提交时的品类 ID；不合约定的返回 null */
    record Key(boolean brand, long id, long before) {
        static Key parse(String key) {
            int hash = key.indexOf('#');
            if (key.length() < 4 || (key.charAt(0) != 'b' && key.charAt(0) != 'c') || hash < 2 || hash == key.length() - 1) {
                return null;
            }
            String id = key.substring(1, hash);
            String before = key.substring(hash + 1);
            if (!id.chars().allMatch(Character::isDigit) || !before.chars().allMatch(Character::isDigit)
                    || id.length() > 18 || before.length() > 18) {
                return null;
            }
            return new Key(key.charAt(0) == 'b', Long.parseLong(id), Long.parseLong(before));
        }
    }

    /** 品类必须存在且启用；只改仍待审核、品类仍是提交时那个的候选 */
    private boolean writeCategory(Long id, long before, long categoryId, Map<Long, ProductCategoryDO> byId) {
        if (!byId.containsKey(categoryId)) {
            return false;
        }
        LambdaUpdateWrapper<ProductCandidateDO> w = new LambdaUpdateWrapper<ProductCandidateDO>()
                .eq(ProductCandidateDO::getId, id)
                .eq(ProductCandidateDO::getStatus, CandidateConstants.STATUS_PENDING)
                .isNull(ProductCandidateDO::getDeletedAt)
                .set(ProductCandidateDO::getCategoryId, categoryId)
                .set(ProductCandidateDO::getAiCategory, 1)
                .set(ProductCandidateDO::getUpdateTime, LocalDateTime.now())
                .set(ProductCandidateDO::getUpdateBy, "AI");
        if (before == 0) {
            w.isNull(ProductCandidateDO::getCategoryId);
        } else {
            w.eq(ProductCandidateDO::getCategoryId, before);
        }
        return candidateMapper.update(null, w) > 0;
    }

    /**
     * 品牌按品牌库名称和别名匹配，不新建；AI 没给出品牌、匹配不上或给的就是 Others 时填 Others 并标「AI 未识别」，保留 AI 给出的名称。
     * Others 缺失（被删或改名）时匹配不上的只做标记、品牌仍待确认。
     * 返回 1-匹配到、2-未识别（记为 Others）、0-没写入（已审核、品牌已被人确认，或 Others 缺失且 AI 没给出品牌）。
     */
    private int writeBrand(Long id, String brand, Long others) {
        Long matched = StringUtils.hasText(brand) ? brandResolver.resolve(brand) : null;
        boolean recognized = matched != null && !matched.equals(others);
        Long brandId = recognized ? matched : others;
        if (brandId == null && !StringUtils.hasText(brand)) {
            return 0;
        }
        LambdaUpdateWrapper<ProductCandidateDO> w = new LambdaUpdateWrapper<ProductCandidateDO>()
                .eq(ProductCandidateDO::getId, id)
                .eq(ProductCandidateDO::getStatus, CandidateConstants.STATUS_PENDING)
                .isNull(ProductCandidateDO::getDeletedAt)
                .isNull(ProductCandidateDO::getBrandId)
                .set(ProductCandidateDO::getAiBrand, recognized ? 1 : 2)
                .set(ProductCandidateDO::getAiBrandName, brand.length() > MAX_BRAND ? brand.substring(0, MAX_BRAND) : brand)
                .set(ProductCandidateDO::getUpdateTime, LocalDateTime.now())
                .set(ProductCandidateDO::getUpdateBy, "AI");
        if (brandId != null) {
            w.set(ProductCandidateDO::getBrandId, brandId);
        }
        if (candidateMapper.update(null, w) == 0) {
            return 0;
        }
        return recognized ? 1 : 2;
    }

    /** 没选品类、只选了一级品类、或是兜底「其他」的需要解析；已选细分品类的跳过 */
    static boolean needsCategory(Long categoryId, Long other, Map<Long, ProductCategoryDO> byId) {
        if (categoryId == null || categoryId.equals(other)) {
            return true;
        }
        ProductCategoryDO c = byId.get(categoryId);
        return c == null || c.getParentId() == null || c.getParentId().equals(other);
    }

    private List<ProductCategoryDO> categories() {
        return categoryMapper.selectList(new LambdaQueryWrapper<ProductCategoryDO>()
                .eq(ProductCategoryDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .eq(ProductCategoryDO::getStatus, ProductConstants.STATUS_ENABLED)
                .isNull(ProductCategoryDO::getDeletedAt));
    }

    private static String path(ProductCategoryDO c, Map<Long, ProductCategoryDO> byId) {
        ProductCategoryDO parent = c.getParentId() == null ? null : byId.get(c.getParentId());
        return parent == null ? name(c) : name(parent) + " / " + name(c);
    }

    private static String name(ProductCategoryDO c) {
        return StringUtils.hasText(c.getCategoryNameZh()) ? c.getCategoryNameZh() : c.getCategoryName();
    }

    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }

    private static String cut(String s) {
        String t = nz(s);
        return t.length() > MAX_TEXT ? t.substring(0, MAX_TEXT) : t;
    }

    private String write(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("AI 解析请求序列化失败", e);
        }
    }

    private JsonNode read(String json) {
        if (json == null || json.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new BizException("AI 解析结果格式不正确", e);
        }
    }
}
