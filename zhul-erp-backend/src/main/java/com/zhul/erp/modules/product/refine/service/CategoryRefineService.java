package com.zhul.erp.modules.product.refine.service;

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
import com.zhul.erp.modules.aitask.support.AiTaskKeys;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.product.constants.ProductConstants;
import com.zhul.erp.modules.product.dto.ProductQuery;
import com.zhul.erp.modules.product.entity.ProductBrandDO;
import com.zhul.erp.modules.product.entity.ProductCategoryDO;
import com.zhul.erp.modules.product.entity.ProductDO;
import com.zhul.erp.modules.product.refine.dto.RefineDtos.ActionVO;
import com.zhul.erp.modules.product.refine.dto.RefineDtos.Skip;
import com.zhul.erp.modules.product.refine.dto.RefineDtos.SubmitRequest;
import com.zhul.erp.modules.product.refine.dto.RefineDtos.TaskVO;
import com.zhul.erp.modules.product.repository.ProductBrandMapper;
import com.zhul.erp.modules.product.repository.ProductCategoryMapper;
import com.zhul.erp.modules.product.repository.ProductMapper;
import com.zhul.erp.modules.product.service.ProductService;
import com.zhul.erp.modules.product.support.IndustryScope;
import com.zhul.erp.modules.product.support.ProductCategoryChangedEvent;
import com.zhul.erp.modules.product.support.ProductWriteGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 商品品类细分（spec product/category-refine，skill_id=product-category-refine）。
 * <ul>
 *   <li>发起：只把「品类未细分、待 AI 细分、当前账号能维护」的商品交给 AI，品类清单只给当前账号可见的启用细分品类；</li>
 *   <li>回调：只写建议，不改商品品类；品类仍是提交时的、且仍待 AI 细分的才写入，其余丢弃；</li>
 *   <li>采纳 / 忽略 / 撤销忽略：按条件更新，被别人改过或处理过的跳过并返回原因。</li>
 * </ul>
 * 发给编排服务的 key 为「p + 商品 ID#提交时的品类 ID」，带校验码（{@link AiTaskKeys}）。结果汇总写回 ai_task.output 的 summary，查询进度时直接返回。
 * 事务边界：采纳、忽略各一个事务；采纳后发布 {@link ProductCategoryChangedEvent}，供应链在事务提交后另起事务补齐需求型号品类。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CategoryRefineService implements AiTaskResultHandler {

    public static final String SKILL_ID = "product-category-refine";
    /** 一次发起、一次采纳的商品数上限 */
    public static final int MAX_BATCH = 200;
    private static final int MAX_TEXT = 200;
    static final String REASON_CHANGED = "品类已被修改";
    static final String REASON_NOT_OWNED = "该商品由其他公司维护，只能查看";
    static final String REASON_CATEGORY_OFF = "建议的品类已停用";
    static final String REASON_NOT_REFINE = "品类已细分";
    static final String REASON_MISSING = "商品不存在";

    private final ProductMapper productMapper;
    private final ProductCategoryMapper categoryMapper;
    private final ProductBrandMapper brandMapper;
    private final ProductService productService;
    private final ProductWriteGuard writeGuard;
    private final IndustryScope industryScope;
    private final AiTaskService aiTaskService;
    private final AiTaskMapper aiTaskMapper;
    private final CurrentUserResolver currentUser;
    private final ApplicationEventPublisher events;
    private final ObjectMapper objectMapper;

    // ---------- 发起与进度 ----------

    public TaskVO submit(SubmitRequest req) {
        Long user = currentUser.resolve();
        boolean running = aiTaskMapper.selectCount(new LambdaQueryWrapper<AiTaskDO>()
                .eq(AiTaskDO::getSkillId, SKILL_ID)
                .eq(AiTaskDO::getTenantId, TenantContext.getTenantId())
                .eq(AiTaskDO::getRequestedBy, user)
                .in(AiTaskDO::getStatus, AiTaskStatus.OPEN)
                .isNull(AiTaskDO::getDeletedAt)) > 0;
        if (running) {
            throw new BizException("上一批 AI 细分还在运行，跑完再发起");
        }
        List<Long> ids = targetIds(req);
        Map<Long, ProductCategoryDO> byId = categoryMapper.selectList(new LambdaQueryWrapper<ProductCategoryDO>()
                        .eq(ProductCategoryDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                        .isNull(ProductCategoryDO::getDeletedAt))
                .stream().collect(Collectors.toMap(ProductCategoryDO::getId, Function.identity()));
        List<ProductDO> rows = ids.isEmpty() ? List.of() : productMapper.selectBatchIds(ids);
        Map<Long, String> brands = brandNames(rows);
        List<Map<String, Object>> items = new ArrayList<>(rows.size());
        for (ProductDO p : rows) {
            if (!todo(p, byId) || !writeGuard.canWrite(p)) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", AiTaskKeys.sign("p" + p.getId() + "#" + p.getCategoryId()));
            item.put("brand", nz(brands.get(p.getBrandId())));
            item.put("model", nz(p.getMpnRaw()));
            item.put("name", cut(p.getProductName()));
            item.put("description", cut(join(p.getSpecSummary(), p.getShortDescription())));
            item.put("currentCategory", name(byId.get(p.getCategoryId())));
            items.add(item);
        }
        TaskVO vo = new TaskVO();
        vo.setTotal(items.size());
        vo.setSkipped(ids.size() - items.size());
        if (items.isEmpty()) {
            return vo;
        }
        Set<Long> visible = industryScope.visibleCategoryIds();
        List<Map<String, Object>> categories = byId.values().stream()
                .filter(c -> c.getParentId() != null && Objects.equals(c.getStatus(), ProductConstants.STATUS_ENABLED))
                .filter(c -> visible == null || visible.contains(c.getId()))
                .map(c -> Map.<String, Object>of("id", c.getId(), "name", name(byId.get(c.getParentId())) + " / " + name(c)))
                .toList();
        if (categories.isEmpty()) {
            throw new BizException("没有可选的细分品类，请先在「品类」中维护");
        }
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("items", items);
        input.put("categories", categories);
        AiTaskVO task = aiTaskService.createAndSubmit(SKILL_ID, write(input), user);
        vo.setTaskId(task.getId());
        vo.setStatus(task.getStatus());
        log.info("AI 细分品类已提交，taskId={}, total={}, skipped={}", task.getId(), vo.getTotal(), vo.getSkipped());
        return vo;
    }

    /** 勾选的商品（最多 200 个），或按列表筛选取「待 AI 细分」最近更新的前 200 个 */
    private List<Long> targetIds(SubmitRequest req) {
        List<Long> ids = req.getIds() == null ? List.of() : req.getIds().stream().filter(Objects::nonNull).distinct().toList();
        if (!ids.isEmpty()) {
            if (ids.size() > MAX_BATCH) {
                throw new BizException("一次最多处理 " + MAX_BATCH + " 个商品");
            }
            return ids;
        }
        ProductQuery q = new ProductQuery();
        if (req.getFilter() != null) {
            BeanUtils.copyProperties(req.getFilter(), q);
        }
        q.setMissing("refine");
        q.setRefineState("todo");
        q.setIncludeDeleted(false);
        return productService.idsMatching(q, MAX_BATCH);
    }

    /** 只能查自己提交的 */
    public TaskVO status(Long taskId) {
        AiTaskDO task = taskId == null ? null : aiTaskMapper.selectById(taskId);
        if (task == null || task.getDeletedAt() != null || !SKILL_ID.equals(task.getSkillId())
                || !Objects.equals(task.getTenantId(), TenantContext.getTenantId())
                || !Objects.equals(task.getRequestedBy(), currentUser.resolve())) {
            throw new BizException("AI 细分任务不存在");
        }
        TaskVO vo = new TaskVO();
        vo.setTaskId(task.getId());
        vo.setStatus(task.getStatus());
        vo.setErrorMessage(task.getErrorMessage());
        JsonNode summary = read(task.getOutput()).path("summary");
        if (!summary.isMissingNode()) {
            vo.setSuggested(summary.path("suggested").asInt());
            vo.setUnknown(summary.path("unknown").asInt());
            vo.setDiscarded(summary.path("discarded").asInt());
        }
        return vo;
    }

    // ---------- 回调 ----------

    @Override
    public boolean supports(String skillId) {
        return SKILL_ID.equals(skillId);
    }

    /** 由 AI 任务回调在任务所属租户下调用 */
    @Override
    public void handle(AiTaskDO task) {
        if (task.getStatus() == null || task.getStatus() != AiTaskStatus.COMPLETED) {
            return;
        }
        JsonNode output = read(task.getOutput());
        Set<Long> level2 = categoryMapper.selectList(new LambdaQueryWrapper<ProductCategoryDO>()
                        .select(ProductCategoryDO::getId)
                        .eq(ProductCategoryDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                        .eq(ProductCategoryDO::getStatus, ProductConstants.STATUS_ENABLED)
                        .isNotNull(ProductCategoryDO::getParentId)
                        .isNull(ProductCategoryDO::getDeletedAt))
                .stream().map(ProductCategoryDO::getId).collect(Collectors.toSet());
        LocalDateTime now = LocalDateTime.now();
        int suggested = 0;
        int unknown = 0;
        int discarded = 0;
        Set<Long> seen = new HashSet<>();
        for (JsonNode n : output.path("items")) {
            Key k = Key.parse(AiTaskKeys.verify(n.path("key").asText("")));
            if (k == null || !seen.add(k.id())) {
                continue;
            }
            long categoryId = n.path("categoryId").asLong(0);
            if (categoryId <= 0 || !level2.contains(categoryId)) {
                unknown++;
                continue;
            }
            // 商品库是全局表，不经租户拦截：只写本公司能维护的商品（audit AI-011）
            ProductDO target = productMapper.selectById(k.id());
            if (target == null || !writeGuard.canWrite(target)) {
                discarded++;
                continue;
            }
            int updated = productMapper.update(null, new LambdaUpdateWrapper<ProductDO>()
                    .eq(ProductDO::getId, k.id())
                    .eq(ProductDO::getCategoryId, k.before())
                    .eq(ProductDO::getAiCategoryState, ProductConstants.AI_CATEGORY_NONE)
                    .isNull(ProductDO::getDeletedAt)
                    .set(ProductDO::getAiCategoryId, categoryId)
                    .set(ProductDO::getAiCategoryState, ProductConstants.AI_CATEGORY_PENDING)
                    .set(ProductDO::getAiCategoryAt, now));
            if (updated > 0) {
                suggested++;
            } else {
                discarded++;
            }
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("suggested", suggested);
        summary.put("unknown", unknown);
        summary.put("discarded", discarded);
        Map<String, Object> merged = new LinkedHashMap<>();
        merged.put("items", output.path("items"));
        merged.put("summary", summary);
        AiTaskDO update = new AiTaskDO();
        update.setId(task.getId());
        update.setOutput(write(merged));
        aiTaskMapper.updateById(update);
        log.info("AI 细分品类回调完成，taskId={}, suggested={}, unknown={}, discarded={}", task.getId(), suggested, unknown, discarded);
    }

    /** 回调 key：p + 商品 ID#提交时的品类 ID；不合约定的返回 null */
    record Key(long id, long before) {
        static Key parse(String key) {
            if (key == null) {
                return null;
            }
            int hash = key.indexOf('#');
            if (key.length() < 4 || key.charAt(0) != 'p' || hash < 2 || hash == key.length() - 1) {
                return null;
            }
            String id = key.substring(1, hash);
            String before = key.substring(hash + 1);
            if (id.length() > 18 || before.length() > 18 || !id.chars().allMatch(Character::isDigit)
                    || !before.chars().allMatch(Character::isDigit)) {
                return null;
            }
            return new Key(Long.parseLong(id), Long.parseLong(before));
        }
    }

    // ---------- 采纳、忽略 ----------

    /** 采纳：商品品类改为建议的细分品类，清除建议；更新人、更新时间记为采纳的人和时间 */
    @Transactional(rollbackFor = Exception.class)
    public ActionVO accept(List<Long> ids) {
        List<ProductDO> rows = load(ids);
        Map<Long, ProductCategoryDO> categories = categoryMapper.selectBatchIds(rows.stream()
                        .map(ProductDO::getAiCategoryId).filter(id -> id != null && id > 0).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(ProductCategoryDO::getId, Function.identity()));
        ActionVO vo = new ActionVO();
        List<Long> accepted = new ArrayList<>(rows.size());
        for (ProductDO p : rows) {
            if (!writeGuard.canWrite(p)) {
                vo.getSkipped().add(Skip.of(p.getId(), p.getMpnRaw(), REASON_NOT_OWNED));
                continue;
            }
            if (!Objects.equals(p.getAiCategoryState(), ProductConstants.AI_CATEGORY_PENDING) || p.getAiCategoryId() == null
                    || p.getAiCategoryId() <= 0) {
                vo.getSkipped().add(Skip.of(p.getId(), p.getMpnRaw(), REASON_CHANGED));
                continue;
            }
            ProductCategoryDO target = categories.get(p.getAiCategoryId());
            if (target == null || target.getDeletedAt() != null || target.getParentId() == null
                    || !Objects.equals(target.getStatus(), ProductConstants.STATUS_ENABLED)) {
                clearSuggestion(p, ProductConstants.AI_CATEGORY_NONE, null);
                vo.getSkipped().add(Skip.of(p.getId(), p.getMpnRaw(), REASON_CATEGORY_OFF));
                continue;
            }
            // 实体里放品类，才能触发 update_time / update_by 的自动填充
            ProductDO change = new ProductDO();
            change.setCategoryId(target.getId());
            int updated = productMapper.update(change, new LambdaUpdateWrapper<ProductDO>()
                    .eq(ProductDO::getId, p.getId())
                    .eq(ProductDO::getCategoryId, p.getCategoryId())
                    .eq(ProductDO::getAiCategoryState, ProductConstants.AI_CATEGORY_PENDING)
                    .eq(ProductDO::getAiCategoryId, target.getId())
                    .isNull(ProductDO::getDeletedAt)
                    .set(ProductDO::getAiCategoryId, 0L)
                    .set(ProductDO::getAiCategoryState, ProductConstants.AI_CATEGORY_NONE)
                    .set(ProductDO::getAiCategoryAt, null));
            if (updated > 0) {
                accepted.add(p.getId());
            } else {
                vo.getSkipped().add(Skip.of(p.getId(), p.getMpnRaw(), REASON_CHANGED));
            }
        }
        vo.setDone(accepted.size());
        if (!accepted.isEmpty()) {
            events.publishEvent(new ProductCategoryChangedEvent(accepted));
            log.info("采纳 AI 细分品类，count={}", accepted.size());
        }
        return vo;
    }

    /** 忽略：清除建议，商品品类不变，进入「已忽略」，不再被 AI 细分选中 */
    @Transactional(rollbackFor = Exception.class)
    public ActionVO ignore(List<Long> ids) {
        Map<Long, ProductCategoryDO> byId = topCategories();
        ActionVO vo = new ActionVO();
        for (ProductDO p : load(ids)) {
            if (!writeGuard.canWrite(p)) {
                vo.getSkipped().add(Skip.of(p.getId(), p.getMpnRaw(), REASON_NOT_OWNED));
            } else if (!byId.containsKey(p.getCategoryId())) {
                vo.getSkipped().add(Skip.of(p.getId(), p.getMpnRaw(), REASON_NOT_REFINE));
            } else if (Objects.equals(p.getAiCategoryState(), ProductConstants.AI_CATEGORY_IGNORED)
                    || !clearSuggestion(p, ProductConstants.AI_CATEGORY_IGNORED, LocalDateTime.now())) {
                vo.getSkipped().add(Skip.of(p.getId(), p.getMpnRaw(), REASON_CHANGED));
            } else {
                vo.setDone(vo.getDone() + 1);
            }
        }
        return vo;
    }

    /** 撤销忽略：回到「待 AI 细分」 */
    @Transactional(rollbackFor = Exception.class)
    public ActionVO unignore(List<Long> ids) {
        ActionVO vo = new ActionVO();
        for (ProductDO p : load(ids)) {
            if (!writeGuard.canWrite(p)) {
                vo.getSkipped().add(Skip.of(p.getId(), p.getMpnRaw(), REASON_NOT_OWNED));
            } else if (!Objects.equals(p.getAiCategoryState(), ProductConstants.AI_CATEGORY_IGNORED)
                    || !clearSuggestion(p, ProductConstants.AI_CATEGORY_NONE, null)) {
                vo.getSkipped().add(Skip.of(p.getId(), p.getMpnRaw(), REASON_CHANGED));
            } else {
                vo.setDone(vo.getDone() + 1);
            }
        }
        return vo;
    }

    /** 按读到的状态条件更新：清除建议品类，状态改为 state；不改商品品类，也不改更新时间 */
    private boolean clearSuggestion(ProductDO p, int state, LocalDateTime at) {
        return productMapper.update(null, new LambdaUpdateWrapper<ProductDO>()
                .eq(ProductDO::getId, p.getId())
                .eq(ProductDO::getCategoryId, p.getCategoryId())
                .eq(ProductDO::getAiCategoryState, p.getAiCategoryState())
                .set(ProductDO::getAiCategoryId, 0L)
                .set(ProductDO::getAiCategoryState, state)
                .set(ProductDO::getAiCategoryAt, at)) > 0;
    }

    /** 未删除的商品，按传入顺序；不存在的忽略 */
    private List<ProductDO> load(List<Long> ids) {
        List<Long> list = ids == null ? List.of() : ids.stream().filter(Objects::nonNull).distinct().toList();
        if (list.isEmpty()) {
            throw new BizException("请选择商品");
        }
        if (list.size() > MAX_BATCH) {
            throw new BizException("一次最多处理 " + MAX_BATCH + " 个商品");
        }
        Map<Long, ProductDO> rows = productMapper.selectBatchIds(list).stream()
                .filter(p -> p.getDeletedAt() == null && Objects.equals(p.getTenantId(), ProductConstants.PLATFORM_TENANT_ID))
                .collect(Collectors.toMap(ProductDO::getId, Function.identity()));
        return list.stream().map(rows::get).filter(Objects::nonNull).toList();
    }

    // ---------- 工具 ----------

    /** 品类未细分且处于「待 AI 细分」 */
    private static boolean todo(ProductDO p, Map<Long, ProductCategoryDO> byId) {
        ProductCategoryDO c = byId.get(p.getCategoryId());
        return p.getDeletedAt() == null && Objects.equals(p.getTenantId(), ProductConstants.PLATFORM_TENANT_ID)
                && c != null && c.getParentId() == null
                && Objects.equals(p.getAiCategoryState(), ProductConstants.AI_CATEGORY_NONE);
    }

    private Map<Long, ProductCategoryDO> topCategories() {
        return categoryMapper.selectList(new LambdaQueryWrapper<ProductCategoryDO>()
                        .eq(ProductCategoryDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                        .isNull(ProductCategoryDO::getParentId))
                .stream().collect(Collectors.toMap(ProductCategoryDO::getId, Function.identity()));
    }

    private Map<Long, String> brandNames(List<ProductDO> rows) {
        Set<Long> ids = rows.stream().map(ProductDO::getBrandId).filter(Objects::nonNull).collect(Collectors.toSet());
        return ids.isEmpty() ? Map.of() : brandMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(ProductBrandDO::getId, ProductBrandDO::getBrandName));
    }

    private static String name(ProductCategoryDO c) {
        if (c == null) {
            return "";
        }
        return StringUtils.hasText(c.getCategoryNameZh()) ? c.getCategoryNameZh() : c.getCategoryName();
    }

    private static String join(String a, String b) {
        String x = nz(a);
        String y = nz(b);
        return x.isEmpty() ? y : y.isEmpty() ? x : x + "；" + y;
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
            throw new IllegalStateException("AI 细分请求序列化失败", e);
        }
    }

    private JsonNode read(String json) {
        if (json == null || json.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new BizException("AI 细分结果格式不正确", e);
        }
    }
}
