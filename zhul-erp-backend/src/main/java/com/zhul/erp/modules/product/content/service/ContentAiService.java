package com.zhul.erp.modules.product.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.security.PermissionChecker;
import com.zhul.erp.framework.security.SecurityUtils;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.framework.tenant.TenantScope;
import com.zhul.erp.modules.aitask.constants.AiTaskStatus;
import com.zhul.erp.modules.aitask.dto.AiTaskVO;
import com.zhul.erp.modules.aitask.entity.AiTaskDO;
import com.zhul.erp.modules.aitask.service.AiConfigService;
import com.zhul.erp.modules.aitask.service.AiTaskService;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.product.constants.ProductConstants;
import com.zhul.erp.modules.product.content.constants.ContentConstants;
import com.zhul.erp.modules.product.content.dto.ContentAiResultVO;
import com.zhul.erp.modules.product.content.dto.ContentAiStartVO;
import com.zhul.erp.modules.product.content.dto.ContentConfirmVO;
import com.zhul.erp.modules.product.content.dto.ContentFileRequest;
import com.zhul.erp.modules.product.content.entity.ProductContentAiJobDO;
import com.zhul.erp.modules.product.content.repository.ProductContentAiJobMapper;
import com.zhul.erp.modules.product.entity.ProductDO;
import com.zhul.erp.modules.product.repository.ProductMapper;
import com.zhul.erp.modules.system.service.LogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 资料任务、营销任务的 AI 生成（spec product/product-content「AI 生成资料与营销内容」「AI 生成结果的确认与导入」）。
 * <p>一个商品一条记录：排队中 → 生成中 → 待确认 → 已导入；失败、取消、放弃为终态。
 * 同一公司同时生成中的不超过 {@link ContentConstants#AI_MAX_RUNNING} 个，其余排队，前一个结束后补上。
 * 生成结果只在人确认后写入，按确认人的权限分别写资料包、营销包，不自动审核。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentAiService {

    private static final String PERM_FACTS = "product:content:edit";
    private static final String PERM_MARKETING = "operations:marketing:edit";
    private static final String FACTS_MENU = "/product/fact-tasks";
    private static final String OPS_MENU = "/operations/products";
    private static final String MENU = "商品内容 AI 生成";
    private static final int SUMMARY_MAX = 1000;
    private static final int REASON_MAX = 500;

    private final ProductContentAiJobMapper jobMapper;
    private final ProductMapper productMapper;
    private final ContentPackageService packageService;
    private final ContentImportService importService;
    private final AiTaskService aiTaskService;
    private final AiConfigService aiConfigService;
    private final CurrentUserResolver currentUser;
    private final PermissionChecker perm;
    private final LogService logService;
    private final ObjectMapper objectMapper;

    // ---------------------------------------------------------------- 发起、取消

    /**
     * 发起（单个或批量）：已有排队中、生成中、待确认记录的商品跳过；AI 未配置或都不可用时提示、不建记录。
     * 事务边界：建记录与提交排队记录在同一事务，AI 任务在事务提交后才发给编排服务。
     */
    @Transactional(rollbackFor = Exception.class)
    public ContentAiStartVO start(List<Long> productIds) {
        requireEditor();
        Set<Long> ids = new LinkedHashSet<>(productIds);
        ids.remove(null);
        if (ids.isEmpty()) {
            throw new BizException("请选择商品");
        }
        if (ids.size() > ContentConstants.AI_MAX_BATCH) {
            throw new BizException("一次最多 AI 生成 " + ContentConstants.AI_MAX_BATCH + " 个商品");
        }
        // 没有可用的连接方式时直接提示（AiNotConfiguredException / AiUnavailableException），不建记录
        aiConfigService.resolveChainForCurrentTenant();
        Set<Long> existing = productMapper.selectList(new LambdaQueryWrapper<ProductDO>()
                        .select(ProductDO::getId)
                        .eq(ProductDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                        .in(ProductDO::getId, ids)
                        .isNull(ProductDO::getDeletedAt))
                .stream().map(ProductDO::getId).collect(Collectors.toSet());
        Set<Long> busy = jobMapper.selectList(new LambdaQueryWrapper<ProductContentAiJobDO>()
                        .select(ProductContentAiJobDO::getProductId)
                        .in(ProductContentAiJobDO::getProductId, ids)
                        .in(ProductContentAiJobDO::getStatus, ContentConstants.AI_OPEN)
                        .isNull(ProductContentAiJobDO::getDeletedAt))
                .stream().map(ProductContentAiJobDO::getProductId).collect(Collectors.toSet());
        ContentAiStartVO vo = new ContentAiStartVO();
        Long me = currentUser.resolve();
        for (Long id : ids) {
            String reason = !existing.contains(id) ? "商品不存在" : busy.contains(id) ? "这个商品正在生成或有待确认的结果" : null;
            if (reason != null) {
                ContentAiStartVO.Skipped s = new ContentAiStartVO.Skipped();
                s.setProductId(id);
                s.setReason(reason);
                vo.getSkipped().add(s);
                continue;
            }
            ProductContentAiJobDO j = new ProductContentAiJobDO();
            j.setTenantId(TenantContext.getTenantId());
            j.setProductId(id);
            j.setStatus(ContentConstants.AI_QUEUED);
            j.setSkillVersion(ContentConstants.AI_SKILL_VERSION);
            j.setRequestedBy(me);
            jobMapper.insert(j);
            vo.setCreated(vo.getCreated() + 1);
        }
        if (vo.getCreated() == 0 && ids.size() == 1) {
            throw new BizException(vo.getSkipped().get(0).getReason());
        }
        logService.recordOperateLog(MENU, "发起 AI 生成", null, Map.of("created", vo.getCreated(), "skipped", vo.getSkipped().size()));
        pump();
        return vo;
    }

    /** 取消排队中、生成中的记录；生成中的 AI 任务照常跑完，结果不再保存 */
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        requireEditor();
        ProductContentAiJobDO j = lock(id);
        if (j.getStatus() != ContentConstants.AI_QUEUED && j.getStatus() != ContentConstants.AI_RUNNING) {
            throw new BizException("只有排队中、生成中的可以取消");
        }
        end(j.getId(), ContentConstants.AI_CANCELLED, "已取消");
        pump();
    }

    /** 放弃待确认的结果：不写入，记录变为已取消，可以重新生成 */
    @Transactional(rollbackFor = Exception.class)
    public void abandon(Long id) {
        requireEditor();
        ProductContentAiJobDO j = lock(id);
        if (j.getStatus() != ContentConstants.AI_PENDING) {
            throw new BizException("只有待确认的结果可以放弃");
        }
        end(j.getId(), ContentConstants.AI_CANCELLED, "已放弃");
    }

    // ---------------------------------------------------------------- 排队与提交

    /**
     * 按并发上限提交本公司排队中的记录。锁住本公司未结束的排队、生成中记录后再数，避免同时发起时超过上限；
     * 调用方须在事务内（发起、取消、AI 任务结束的回调都是）。
     */
    void pump() {
        List<ProductContentAiJobDO> open = jobMapper.selectList(new LambdaQueryWrapper<ProductContentAiJobDO>()
                .select(ProductContentAiJobDO::getId, ProductContentAiJobDO::getStatus, ProductContentAiJobDO::getProductId,
                        ProductContentAiJobDO::getRequestedBy)
                .in(ProductContentAiJobDO::getStatus, ContentConstants.AI_QUEUED, ContentConstants.AI_RUNNING)
                .isNull(ProductContentAiJobDO::getDeletedAt)
                .orderByAsc(ProductContentAiJobDO::getId)
                .last("FOR UPDATE"));
        long running = open.stream().filter(j -> j.getStatus() == ContentConstants.AI_RUNNING).count();
        for (ProductContentAiJobDO j : open) {
            if (running >= ContentConstants.AI_MAX_RUNNING) {
                return;
            }
            if (j.getStatus() == ContentConstants.AI_QUEUED && submit(j)) {
                running++;
            }
        }
    }

    /** 生成这个商品的两份任务包交给 AI；不能提交（商品已删、AI 不可用）时记录直接失败 */
    private boolean submit(ProductContentAiJobDO j) {
        try {
            ContentPackageService.Packs packs = packageService.packsFor(j.getProductId());
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("factsFileName", packs.factsFileName());
            input.put("factsPack", packs.facts());
            input.put("marketingFileName", packs.marketingFileName());
            input.put("marketingPack", packs.marketing());
            AiTaskVO task = aiTaskService.createAndSubmit(ContentConstants.AI_SKILL, objectMapper.writeValueAsString(input),
                    j.getRequestedBy());
            ProductContentAiJobDO u = new ProductContentAiJobDO();
            u.setId(j.getId());
            u.setStatus(ContentConstants.AI_RUNNING);
            u.setAiTaskId(task.getId());
            u.setFactsFileName(packs.factsFileName());
            u.setMarketingFileName(packs.marketingFileName());
            u.setStartedAt(LocalDateTime.now());
            jobMapper.updateById(u);
            log.info("商品内容 AI 生成已提交，jobId={}, productId={}, aiTaskId={}", j.getId(), j.getProductId(), task.getId());
            return true;
        } catch (BizException e) {
            log.warn("商品内容 AI 生成提交失败，jobId={}, reason={}", j.getId(), e.getMessage());
            end(j.getId(), ContentConstants.AI_FAILED, e.getMessage());
            return false;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("AI 生成输入序列化失败", e);
        }
    }

    // ---------------------------------------------------------------- AI 任务结束

    /**
     * AI 任务结束（回调或超时；已在任务所属公司下）：成功变为待确认，失败记原因；已取消的不再保存结果。
     * 事务边界：保存结果与提交下一个排队记录在同一事务（回调事务）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void apply(AiTaskDO task) {
        ProductContentAiJobDO j = jobMapper.selectOne(new LambdaQueryWrapper<ProductContentAiJobDO>()
                .eq(ProductContentAiJobDO::getAiTaskId, task.getId())
                .isNull(ProductContentAiJobDO::getDeletedAt)
                .last("LIMIT 1 FOR UPDATE"));
        if (j == null || j.getStatus() != ContentConstants.AI_RUNNING) {
            log.info("AI 生成记录已取消或已处理，忽略结果，aiTaskId={}", task.getId());
            pump();
            return;
        }
        if (task.getStatus() != null && task.getStatus() == AiTaskStatus.COMPLETED) {
            try {
                JsonNode out = objectMapper.readTree(task.getOutput() == null ? "{}" : task.getOutput());
                String facts = out.path("factsMarkdown").asText("");
                String marketing = out.path("marketingMarkdown").asText("");
                if (!StringUtils.hasText(facts) || !StringUtils.hasText(marketing)) {
                    throw new IllegalArgumentException("缺少资料包或营销包");
                }
                ProductContentAiJobDO u = new ProductContentAiJobDO();
                u.setId(j.getId());
                u.setStatus(ContentConstants.AI_PENDING);
                u.setFactsMarkdown(facts);
                u.setMarketingMarkdown(marketing);
                u.setSelfScore(out.path("selfScore").canConvertToInt() ? out.path("selfScore").asInt() : null);
                u.setSummary(cut(out.path("summary").asText(""), SUMMARY_MAX));
                u.setCompletedAt(LocalDateTime.now());
                jobMapper.updateById(u);
            } catch (JsonProcessingException | IllegalArgumentException e) {
                log.warn("AI 生成结果无法识别，jobId={}, error={}", j.getId(), e.getMessage());
                end(j.getId(), ContentConstants.AI_FAILED, "AI 返回的结果无法识别，请重新生成");
            }
        } else {
            end(j.getId(), ContentConstants.AI_FAILED, StringUtils.hasText(task.getErrorMessage())
                    ? task.getErrorMessage() : "AI 处理失败");
        }
        pump();
    }

    // ---------------------------------------------------------------- 查看结果、确认写入

    public ContentAiResultVO result(Long id) {
        ProductContentAiJobDO j = visible(id);
        ContentAiResultVO vo = new ContentAiResultVO();
        vo.setId(j.getId());
        vo.setProductId(j.getProductId());
        vo.setStatus(j.getStatus());
        vo.setFactsFileName(j.getFactsFileName());
        vo.setMarketingFileName(j.getMarketingFileName());
        vo.setSummary(j.getSummary());
        vo.setSelfScore(j.getSelfScore());
        vo.setFactsImported(j.getFactsImportedAt() != null);
        vo.setMarketingImported(j.getMarketingImportedAt() != null);
        vo.setCanFacts(canFacts());
        vo.setCanMarketing(canMarketing());
        if (j.getStatus() == ContentConstants.AI_PENDING || j.getStatus() == ContentConstants.AI_IMPORTED) {
            vo.setFactsMarkdown(j.getFactsMarkdown());
            vo.setMarketingMarkdown(j.getMarketingMarkdown());
        }
        return vo;
    }

    /**
     * 确认写入：只用保存的原文（请求里的原文不采用），套用预览中的修改与跳过；按确认人的权限分别写资料包、营销包，
     * 不自动审核（资料写入后为资料待核，技术问答待审核）。两份都写入后记录变为已导入。
     * 事务边界：先用条件更新占住要写的那一份（防止重复写入），再按文件逐个写入（各自的事务，同手动上传），
     * 写入失败的那一份释放占用、可以重试。
     */
    public ContentConfirmVO confirm(Long id, ContentFileRequest req) {
        ProductContentAiJobDO j = visible(id);
        if (j.getStatus() != ContentConstants.AI_PENDING) {
            throw new BizException("只有待确认的结果可以写入");
        }
        Map<String, ContentFileRequest.ContentFile> byName = new LinkedHashMap<>();
        for (ContentFileRequest.ContentFile f : req.getFiles()) {
            if (!Objects.equals(f.getFileName(), j.getFactsFileName()) && !Objects.equals(f.getFileName(), j.getMarketingFileName())) {
                throw new BizException("文件不属于这次 AI 生成：" + f.getFileName());
            }
            byName.put(f.getFileName(), f);
        }
        ContentConfirmVO result = new ContentConfirmVO();
        writePart(j, byName.get(j.getFactsFileName()), j.getFactsFileName(), j.getFactsMarkdown(), true, result);
        writePart(j, byName.get(j.getMarketingFileName()), j.getMarketingFileName(), j.getMarketingMarkdown(), false, result);
        finishIfDone(j.getId());
        return result;
    }

    private void writePart(ProductContentAiJobDO j, ContentFileRequest.ContentFile edits, String fileName, String markdown,
                           boolean facts, ContentConfirmVO result) {
        if (edits == null) {
            return;
        }
        if (facts ? j.getFactsImportedAt() != null : j.getMarketingImportedAt() != null) {
            return;
        }
        if (!(facts ? canFacts() : canMarketing())) {
            ContentConfirmVO.Item item = new ContentConfirmVO.Item();
            item.setFileName(fileName);
            item.setType(facts ? ContentConstants.TYPE_FACTS : ContentConstants.TYPE_MARKETING);
            item.setProductId(j.getProductId());
            item.setMessage(facts ? "没有商品内容维护权限，资料包未写入" : "没有营销内容维护权限，营销包未写入");
            result.getItems().add(item);
            result.setFailed(result.getFailed() + 1);
            return;
        }
        if (!claim(j.getId(), facts, LocalDateTime.now())) {
            return;
        }
        ContentFileRequest one = new ContentFileRequest();
        ContentFileRequest.ContentFile file = new ContentFileRequest.ContentFile();
        file.setFileName(fileName);
        file.setContent(markdown);
        file.setSkip(edits.getSkip());
        file.setEdits(edits.getEdits());
        one.setFiles(List.of(file));
        one.setProductId(j.getProductId());
        ContentConfirmVO part = importService.confirm(one,
                facts ? ContentConstants.TYPE_FACTS : ContentConstants.TYPE_MARKETING, ContentConstants.SOURCE_AI);
        result.getItems().addAll(part.getItems());
        result.setSucceeded(result.getSucceeded() + part.getSucceeded());
        result.setFailed(result.getFailed() + part.getFailed());
        if (part.getSucceeded() == 0) {
            release(j.getId(), facts);
        } else {
            logService.recordOperateLog(MENU, facts ? "确认写入资料包" : "确认写入营销包", null,
                    Map.of("jobId", j.getId(), "productId", j.getProductId()));
        }
    }

    /** 占住一份：只有还没写入时才成功 */
    private boolean claim(Long id, boolean facts, LocalDateTime now) {
        LambdaUpdateWrapper<ProductContentAiJobDO> w = new LambdaUpdateWrapper<ProductContentAiJobDO>()
                .eq(ProductContentAiJobDO::getId, id)
                .eq(ProductContentAiJobDO::getStatus, ContentConstants.AI_PENDING)
                .set(ProductContentAiJobDO::getConfirmedBy, SecurityUtils.getCurrentUsername())
                .set(ProductContentAiJobDO::getConfirmedAt, now)
                .set(ProductContentAiJobDO::getUpdateTime, now);
        if (facts) {
            w.isNull(ProductContentAiJobDO::getFactsImportedAt).set(ProductContentAiJobDO::getFactsImportedAt, now);
        } else {
            w.isNull(ProductContentAiJobDO::getMarketingImportedAt).set(ProductContentAiJobDO::getMarketingImportedAt, now);
        }
        return jobMapper.update(null, w) > 0;
    }

    private void release(Long id, boolean facts) {
        LambdaUpdateWrapper<ProductContentAiJobDO> w = new LambdaUpdateWrapper<ProductContentAiJobDO>()
                .eq(ProductContentAiJobDO::getId, id)
                .set(ProductContentAiJobDO::getUpdateTime, LocalDateTime.now());
        w.set(facts ? ProductContentAiJobDO::getFactsImportedAt : ProductContentAiJobDO::getMarketingImportedAt, null);
        jobMapper.update(null, w);
    }

    private void finishIfDone(Long id) {
        LocalDateTime now = LocalDateTime.now();
        jobMapper.update(null, new LambdaUpdateWrapper<ProductContentAiJobDO>()
                .eq(ProductContentAiJobDO::getId, id)
                .eq(ProductContentAiJobDO::getStatus, ContentConstants.AI_PENDING)
                .isNotNull(ProductContentAiJobDO::getFactsImportedAt)
                .isNotNull(ProductContentAiJobDO::getMarketingImportedAt)
                .set(ProductContentAiJobDO::getStatus, ContentConstants.AI_IMPORTED)
                .set(ProductContentAiJobDO::getUpdateTime, now));
    }

    // ---------------------------------------------------------------- 待确认超期

    /** 每天：待确认超过 30 天的自动取消（定时任务没有登录上下文，按平台范围带条件更新各公司的记录） */
    @Scheduled(cron = "${zhul.product-content.ai-expire-cron:0 20 3 * * *}", zone = "Asia/Shanghai")
    public void expirePending() {
        LocalDateTime now = LocalDateTime.now();
        int n = TenantScope.platform(() -> jobMapper.update(null, new LambdaUpdateWrapper<ProductContentAiJobDO>()
                .eq(ProductContentAiJobDO::getStatus, ContentConstants.AI_PENDING)
                .lt(ProductContentAiJobDO::getCompletedAt, now.minusDays(ContentConstants.AI_PENDING_DAYS))
                .isNull(ProductContentAiJobDO::getDeletedAt)
                .set(ProductContentAiJobDO::getStatus, ContentConstants.AI_CANCELLED)
                .set(ProductContentAiJobDO::getFailReason, "待确认超过 " + ContentConstants.AI_PENDING_DAYS + " 天，已自动取消")
                .set(ProductContentAiJobDO::getUpdateTime, now)));
        if (n > 0) {
            log.info("待确认超期的 AI 生成结果已自动取消，count={}", n);
        }
    }

    // ---------------------------------------------------------------- 内部

    private void end(Long id, int status, String reason) {
        ProductContentAiJobDO u = new ProductContentAiJobDO();
        u.setId(id);
        u.setStatus(status);
        u.setFailReason(cut(reason, REASON_MAX));
        if (status == ContentConstants.AI_FAILED) {
            u.setCompletedAt(LocalDateTime.now());
        }
        jobMapper.updateById(u);
    }

    private ProductContentAiJobDO visible(Long id) {
        ProductContentAiJobDO j = id == null ? null : jobMapper.selectById(id);
        if (j == null || j.getDeletedAt() != null) {
            throw new BizException("AI 生成记录不存在");
        }
        return j;
    }

    private ProductContentAiJobDO lock(Long id) {
        ProductContentAiJobDO j = jobMapper.selectOne(new LambdaQueryWrapper<ProductContentAiJobDO>()
                .eq(ProductContentAiJobDO::getId, id)
                .isNull(ProductContentAiJobDO::getDeletedAt)
                .last("FOR UPDATE"));
        if (j == null) {
            throw new BizException("AI 生成记录不存在");
        }
        return j;
    }

    private boolean canFacts() {
        return perm.canAccessMenu(FACTS_MENU) && perm.has(PERM_FACTS);
    }

    private boolean canMarketing() {
        return perm.canAccessMenu(OPS_MENU) && perm.has(PERM_MARKETING);
    }

    /** 发起、取消、放弃要能写其中一份（生成会产生费用） */
    private void requireEditor() {
        if (!canFacts() && !canMarketing()) {
            throw new BizException("没有商品内容维护或营销内容维护权限");
        }
    }

    private static String cut(String s, int max) {
        return s == null ? "" : s.length() > max ? s.substring(0, max) : s;
    }
}
