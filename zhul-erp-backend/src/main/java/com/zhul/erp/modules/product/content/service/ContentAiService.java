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
import com.zhul.erp.modules.product.content.dto.ContentBatchResultVO;
import com.zhul.erp.modules.product.content.dto.ContentConfirmVO;
import com.zhul.erp.modules.product.content.dto.ContentFileRequest;
import com.zhul.erp.modules.product.content.dto.ContentPreviewVO;
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
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
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
    private final TransactionTemplate tx;

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
        lockOpen();
        Set<Long> existing = productMapper.selectList(new LambdaQueryWrapper<ProductDO>()
                        .eq(ProductDO::getArchiveStatus, ProductConstants.ARCHIVE_DONE)
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
        lockOpen();
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
     * 锁住本公司未结束的排队、生成中记录（按 id 顺序）。发起、取消、AI 任务结束、定时补交都先拿这把锁再动单条记录，
     * 加锁顺序一致，并发的回调只会排队等待、不会互相死锁（audit BUS-087）。调用方须在事务内。
     */
    private List<ProductContentAiJobDO> lockOpen() {
        return jobMapper.selectList(new LambdaQueryWrapper<ProductContentAiJobDO>()
                .select(ProductContentAiJobDO::getId, ProductContentAiJobDO::getStatus, ProductContentAiJobDO::getProductId,
                        ProductContentAiJobDO::getRequestedBy, ProductContentAiJobDO::getAiTaskId)
                .in(ProductContentAiJobDO::getStatus, ContentConstants.AI_QUEUED, ContentConstants.AI_RUNNING)
                .isNull(ProductContentAiJobDO::getDeletedAt)
                .orderByAsc(ProductContentAiJobDO::getId)
                .last("FOR UPDATE"));
    }

    /**
     * 按并发上限提交本公司排队中的记录；调用方须在事务内（发起、取消、AI 任务结束的回调、定时补交都是）。
     * AI 暂不可用（连接方式都在暂停中或未配置）时不提交、继续排队，恢复后由定时补交带起：
     * 不能让 createAndSubmit 在当前事务里抛出，否则整个事务（含回调结果）只能回滚（audit BUS-087）。
     */
    void pump() {
        long running = 0;
        List<ProductContentAiJobDO> queued = new ArrayList<>();
        for (ProductContentAiJobDO j : lockOpen()) {
            if (j.getStatus() == ContentConstants.AI_QUEUED) {
                queued.add(j);
            } else if (!settleIfEnded(j)) {
                running++;
            }
        }
        if (queued.isEmpty() || running >= ContentConstants.AI_MAX_RUNNING) {
            return;
        }
        if (!aiConfigService.isConfiguredForCurrentTenant()) {
            log.info("AI 暂不可用，{} 条 AI 生成记录继续排队", queued.size());
            return;
        }
        for (ProductContentAiJobDO j : queued) {
            if (running >= ContentConstants.AI_MAX_RUNNING) {
                return;
            }
            if (submit(j)) {
                running++;
            }
        }
    }

    /**
     * 生成中的记录，AI 任务却已结束（结果没能保存，如回调事务失败）：按任务结果收尾，释放并发名额。
     * 正常情况下任务结束与记录收尾在同一事务，这里只兜底。
     */
    private boolean settleIfEnded(ProductContentAiJobDO j) {
        if (j.getAiTaskId() == null) {
            return false;
        }
        AiTaskVO t = aiTaskService.getById(j.getAiTaskId());
        if (!Objects.equals(t.getStatus(), AiTaskStatus.COMPLETED) && !Objects.equals(t.getStatus(), AiTaskStatus.FAILED)) {
            return false;
        }
        log.warn("AI 生成记录的任务已结束但结果未保存，按任务结果收尾，jobId={}, aiTaskId={}", j.getId(), t.getId());
        AiTaskDO task = new AiTaskDO();
        task.setId(t.getId());
        task.setStatus(t.getStatus());
        task.setOutput(t.getOutput());
        task.setErrorMessage(t.getErrorMessage());
        settle(j.getId(), task);
        return true;
    }

    /** 每分钟：有排队或生成中记录的公司补交一次（AI 恢复后继续排队的记录），并收尾结果没保存的生成中记录 */
    @Scheduled(fixedDelayString = "${zhul.product-content.ai-resume-interval-ms:60000}")
    public void resumeQueued() {
        List<Integer> tenants = TenantScope.platform(() -> jobMapper.selectList(new LambdaQueryWrapper<ProductContentAiJobDO>()
                        .select(ProductContentAiJobDO::getTenantId)
                        .in(ProductContentAiJobDO::getStatus, ContentConstants.AI_QUEUED, ContentConstants.AI_RUNNING)
                        .isNull(ProductContentAiJobDO::getDeletedAt)
                        .groupBy(ProductContentAiJobDO::getTenantId))
                .stream().map(ProductContentAiJobDO::getTenantId).toList());
        for (Integer tenantId : tenants) {
            try {
                TenantContext.runAs(tenantId, () -> tx.executeWithoutResult(s -> pump()));
            } catch (RuntimeException e) {
                // 一家公司补交出错不影响其他公司，下一轮再试
                log.warn("AI 生成排队补交失败，tenantId={}", tenantId, e);
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
        lockOpen();
        ProductContentAiJobDO j = jobMapper.selectOne(new LambdaQueryWrapper<ProductContentAiJobDO>()
                .eq(ProductContentAiJobDO::getAiTaskId, task.getId())
                .isNull(ProductContentAiJobDO::getDeletedAt)
                .last("LIMIT 1 FOR UPDATE"));
        if (j == null || j.getStatus() != ContentConstants.AI_RUNNING) {
            log.info("AI 生成记录已取消或已处理，忽略结果，aiTaskId={}", task.getId());
            pump();
            return;
        }
        settle(j.getId(), task);
        pump();
    }

    /** 按 AI 任务结果收尾一条生成中的记录：成功变为待确认，失败或结果无法识别记原因 */
    private void settle(Long jobId, AiTaskDO task) {
        if (task.getStatus() != null && task.getStatus() == AiTaskStatus.COMPLETED) {
            try {
                JsonNode out = objectMapper.readTree(task.getOutput() == null ? "{}" : task.getOutput());
                String facts = out.path("factsMarkdown").asText("");
                String marketing = out.path("marketingMarkdown").asText("");
                if (!StringUtils.hasText(facts) || !StringUtils.hasText(marketing)) {
                    throw new IllegalArgumentException("缺少资料包或营销包");
                }
                ProductContentAiJobDO u = new ProductContentAiJobDO();
                u.setId(jobId);
                u.setStatus(ContentConstants.AI_PENDING);
                u.setFactsMarkdown(facts);
                u.setMarketingMarkdown(marketing);
                u.setSelfScore(out.path("selfScore").canConvertToInt() ? out.path("selfScore").asInt() : null);
                u.setSummary(cut(out.path("summary").asText(""), SUMMARY_MAX));
                u.setCompletedAt(LocalDateTime.now());
                jobMapper.updateById(u);
            } catch (JsonProcessingException | IllegalArgumentException e) {
                log.warn("AI 生成结果无法识别，jobId={}, error={}", jobId, e.getMessage());
                end(jobId, ContentConstants.AI_FAILED, "AI 返回的结果无法识别，请重新生成");
            }
        } else {
            end(jobId, ContentConstants.AI_FAILED, StringUtils.hasText(task.getErrorMessage())
                    ? task.getErrorMessage() : "AI 处理失败");
        }
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
     * 确认写入：只用保存的原文（请求里的原文不采用），套用预览中的修改与跳过；按确认人的权限分别写资料包、营销包。
     * 资料写入后为资料待核，技术问答随资料包审核通过（openspec content-ai-batch-hs）。两份都写入后记录变为已导入。
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
        result.setApprovedFaqs(n(result.getApprovedFaqs()) + n(part.getApprovedFaqs()));
        if (part.getSucceeded() == 0) {
            release(j.getId(), facts);
        } else {
            logService.recordOperateLog(MENU, facts ? "确认写入资料包" : "确认写入营销包", null,
                    Map.of("jobId", j.getId(), "productId", j.getProductId()));
        }
    }

    /**
     * 批量确认的一条（openspec content-ai-batch-hs）：不逐章节修改，解析出问题的章节自动跳过，有文件级问题的文件不写；
     * 其余同逐个确认（按权限写、技术问答随资料包审核通过）。事务边界同 {@link #confirm}。
     */
    public ContentBatchResultVO.Item confirmAll(Long id) {
        ProductContentAiJobDO j = visible(id);
        ContentBatchResultVO.Item item = new ContentBatchResultVO.Item();
        item.setId(id);
        item.setProductId(j.getProductId());
        item.setLabel(label(j.getProductId()));
        if (j.getStatus() != ContentConstants.AI_PENDING) {
            item.setOutcome(ContentBatchResultVO.OUTCOME_FAILED);
            item.setMessage("不是待确认的结果");
            return item;
        }
        List<ContentFileRequest.ContentFile> files = new ArrayList<>(2);
        if (j.getFactsImportedAt() == null) {
            files.add(file(j.getFactsFileName(), j.getFactsMarkdown()));
        }
        if (j.getMarketingImportedAt() == null) {
            files.add(file(j.getMarketingFileName(), j.getMarketingMarkdown()));
        }
        ContentFileRequest pre = new ContentFileRequest();
        pre.setFiles(files);
        pre.setProductId(j.getProductId());
        List<String> skipped = new ArrayList<>();
        for (ContentPreviewVO p : importService.preview(pre, null)) {
            if (p.isConfirmable() || !p.getErrors().isEmpty()) {
                continue;
            }
            ContentFileRequest.ContentFile f = files.get(p.getFileIndex());
            List<String> skip = f.getSkip() == null ? new ArrayList<>() : new ArrayList<>(f.getSkip());
            for (ContentPreviewVO.BlockVO b : p.getBlocks()) {
                if (!b.isSkipped() && !b.getErrors().isEmpty()) {
                    skip.add(b.getSection());
                    skipped.add(b.getLabel() + "：" + b.getErrors().get(0));
                }
            }
            f.setSkip(skip);
        }
        ContentFileRequest req = new ContentFileRequest();
        req.setFiles(files);
        ContentConfirmVO r = confirm(id, req);
        Set<String> written = new LinkedHashSet<>();
        List<String> notWritten = new ArrayList<>();
        for (ContentConfirmVO.Item i : r.getItems()) {
            String part = ContentConstants.TYPE_FACTS.equals(i.getType()) ? "资料包" : "营销包";
            if (i.isSuccess()) {
                written.add(part);
            } else {
                notWritten.add(part + "：" + i.getMessage());
            }
        }
        List<String> lines = new ArrayList<>();
        if (!written.isEmpty()) {
            lines.add(String.join("、", written) + "已写入"
                    + (n(r.getApprovedFaqs()) > 0 ? "；技术问答 " + r.getApprovedFaqs() + " 条审核通过" : ""));
        }
        if (!skipped.isEmpty()) {
            lines.add("跳过「" + String.join("」「", skipped) + "」");
        }
        lines.addAll(notWritten);
        item.setOutcome(r.getSucceeded() == 0 ? ContentBatchResultVO.OUTCOME_FAILED
                : r.getFailed() > 0 || !skipped.isEmpty() ? ContentBatchResultVO.OUTCOME_PARTIAL
                : ContentBatchResultVO.OUTCOME_DONE);
        item.setMessage(lines.isEmpty() ? "没有可写入的内容" : String.join("；", lines));
        item.setApprovedFaqs(n(r.getApprovedFaqs()));
        return item;
    }

    private static ContentFileRequest.ContentFile file(String name, String markdown) {
        ContentFileRequest.ContentFile f = new ContentFileRequest.ContentFile();
        f.setFileName(name);
        f.setContent(markdown);
        return f;
    }

    private String label(Long productId) {
        ProductDO p = productMapper.selectById(productId);
        if (p == null) {
            return String.valueOf(productId);
        }
        return StringUtils.hasText(p.getMpnDisplay()) ? p.getMpnDisplay() : p.getMpnRaw();
    }

    private static int n(Integer v) {
        return v == null ? 0 : v;
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
