package com.zhul.erp.modules.system.migration.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.product.candidate.service.ProductArchiver;
import com.zhul.erp.modules.system.migration.dto.MigrationBatchVO;
import com.zhul.erp.modules.system.migration.dto.MigrationBundleVO;
import com.zhul.erp.modules.system.migration.entity.MigrationBatchDO;
import com.zhul.erp.modules.system.migration.entity.MigrationIdMapDO;
import com.zhul.erp.modules.system.migration.repository.MigrationBatchMapper;
import com.zhul.erp.modules.system.migration.repository.MigrationIdMapMapper;
import com.zhul.erp.modules.system.service.DocumentNumberService;
import com.zhul.erp.modules.tenant.entity.TenantDO;
import com.zhul.erp.modules.tenant.repository.TenantMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 飞书历史数据迁移（spec system/history-migration）：平台账号对指定租户执行，读取离线清洗产出的清洗结果目录。
 * 正式执行按 主数据 → 询盘与询价 → 订单与下游 → 财务 → 候选商品与素材 分阶段，每阶段一个事务，失败的阶段回滚并停止后续阶段；
 * 干跑把全部阶段放在一个事务里执行后回滚，不推进编号序列、不上传文件。
 * 执行与回滚都在后台线程里运行（正式执行约半小时，超过反向代理的超时），提交后立即返回；同一时间只运行一个任务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HistoryMigrationService {

    private static final int PLATFORM = 0;
    private static final DateTimeFormatter BATCH_NO = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final List<String> BUNDLE_FILES = List.of("users", "suppliers", "customers", "opportunities", "inquiries",
            "sourcing", "orders", "purchases", "shipments", "finance", "media", "reports");
    /** 回滚时软删除的目标表（白名单）；user_basic 新建时即为停用，不删 */
    private static final Set<String> SOFT_DELETE_TABLES = Set.of("supplier", "customer", "opportunity", "customer_inquiry",
            "inquiry_item", "sourcing_task", "sourcing_quote", "sales_order", "sales_order_item", "purchase_order",
            "purchase_order_item", "supplier_shipment", "purchase_receipt", "purchase_receipt_item", "logistics_shipment", "payment_receipt", "fin_fx_settlement",
            "sales_order_cost_snapshot", "fin_customer_refund", "fin_expense", "fin_other_income", "biz_attachment",
            "customer_inquiry_attachment");

    private final MigrationBatchMapper batchMapper;
    private final MigrationIdMapMapper idMapMapper;
    private final TenantMapper tenantMapper;
    private final DocumentNumberService numbers;
    private final MasterDataImporter masterData;
    private final InquiryImporter inquiries;
    private final OrderImporter orders;
    private final FinanceImporter finance;
    private final ArchiveImporter archive;
    private final ProductArchiver archiver;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbc;
    private final PlatformTransactionManager transactionManager;
    private final MigrationBundleStore bundles;
    private final MigrationVerifier verifier;

    /** 当前在跑的批次（执行或回滚）；没有为 null。是全局任务状态，不是请求状态 */
    private final AtomicReference<Long> active = new AtomicReference<>();
    private final ThreadPoolExecutor pool = new ThreadPoolExecutor(1, 1, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>(1),
            r -> {
                Thread t = new Thread(r, "history-migration-pool");
                t.setDaemon(true);
                return t;
            });

    private void requirePlatform() {
        Integer caller = TenantContext.getTenantId();
        if (caller == null || caller != PLATFORM) {
            throw new BizException("仅平台账号可以执行历史数据迁移");
        }
    }

    private TenantDO requireTenant(Integer tenantId) {
        TenantDO tenant = tenantId == null || tenantId == PLATFORM ? null : tenantMapper.selectById(tenantId);
        if (tenant == null) {
            throw new BizException("租户不存在");
        }
        return tenant;
    }

    /**
     * 提交迁移，立即返回运行中的批次；进度与结果用 {@link #get} 查看。
     *
     * @param bundleId      上传的迁移包（与 bundleDir 二选一）
     * @param bundleDir     清洗结果目录（服务器本地路径，本机演练用）
     * @param attachmentDir 附件缓存目录；为空时只归档带下载链接的图片
     */
    public MigrationBatchVO run(Integer tenantId, String bundleId, String bundleDir, String attachmentDir, boolean dryRun) {
        requirePlatform();
        requireTenant(tenantId);
        String dir = bundleId == null || bundleId.isBlank() ? bundleDir : bundles.dir(bundleId).toString();
        Map<String, JsonNode> bundle = loadBundle(dir);
        MigrationBatchDO batch = new MigrationBatchDO();
        batch.setTenantId(tenantId);
        batch.setBatchNo("HM" + LocalDateTime.now().format(BATCH_NO) + (dryRun ? "D" : ""));
        batch.setBundleHash(bundle.containsKey("manifest") ? bundle.get("manifest").path("version").asText("") : "");
        batch.setRunMode(dryRun ? 1 : 2);
        batch.setStatus(1);
        batch.setStartedAt(LocalDateTime.now());
        String attachments = attachmentDir == null || attachmentDir.isBlank() ? dir : attachmentDir;
        submit(() -> batchMapper.insert(batch), batch::getId, () -> execute(batch, bundle, attachments, dryRun));
        return toVo(batch);
    }

    /** 占用任务槽后在后台运行；已有任务在跑时拒绝。prepare 在占槽后、提交前执行（如写入批次） */
    private void submit(Runnable prepare, java.util.function.Supplier<Long> batchId, Runnable job) {
        if (!active.compareAndSet(null, -1L)) {
            throw new BizException("已有迁移任务在运行，请等它结束后再操作");
        }
        try {
            prepare.run();
            active.set(batchId.get());
            SecurityContext caller = SecurityContextHolder.getContext();
            pool.execute(() -> {
                SecurityContextHolder.setContext(caller);
                try {
                    job.run();
                } catch (RuntimeException e) {
                    log.error("历史数据迁移后台任务出错", e);
                } finally {
                    SecurityContextHolder.clearContext();
                    active.set(null);
                }
            });
        } catch (RuntimeException e) {
            active.set(null);
            throw e;
        }
    }

    private void execute(MigrationBatchDO batch, Map<String, JsonNode> bundle, String attachmentDir, boolean dryRun) {
        int tenantId = batch.getTenantId();
        MigrationRun r = new MigrationRun(tenantId, batch.getId(), dryRun, bundle, attachmentDir, idMapMapper, numbers);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        String error = null;
        TenantContext.setTenantId(tenantId);
        try {
            if (dryRun) {
                tx.executeWithoutResult(status -> {
                    stages(r);
                    status.setRollbackOnly();
                });
            } else {
                Integer bu = masterData.businessUnit();
                tx.executeWithoutResult(s -> masterData.run(r));
                progress(batch, r);
                tx.executeWithoutResult(s -> inquiries.run(r, bu));
                progress(batch, r);
                tx.executeWithoutResult(s -> orders.run(r, bu));
                progress(batch, r);
                tx.executeWithoutResult(s -> finance.run(r, bu));
                progress(batch, r);
                archive.run(r, r.inquiryItemIds, () -> progress(batch, r));
            }
        } catch (RuntimeException e) {
            log.error("历史数据迁移失败，tenantId={}，batchId={}", tenantId, batch.getId(), e);
            error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        } finally {
            TenantContext.setTenantId(PLATFORM);
        }
        batch.setStatus(error == null ? 2 : 3);
        batch.setErrorText(error == null ? "" : error.length() > 1000 ? error.substring(0, 1000) : error);
        batch.setStageStats(statsJson(r));
        if (error == null && !dryRun && bundle.containsKey("reports")) {
            batch.setVerifyResult(verifier.verify(tenantId, bundle));
        }
        batch.setFinishedAt(LocalDateTime.now());
        batchMapper.updateById(batch);
        log.info("历史数据迁移结束，tenantId={}，batchNo={}，dryRun={}，status={}", tenantId, batch.getBatchNo(), dryRun, batch.getStatus());
    }

    /** 正式执行每提交一个阶段写一次进度；候选商品与素材阶段每处理 100 张图片再写一次 */
    private void progress(MigrationBatchDO batch, MigrationRun r) {
        MigrationBatchDO p = new MigrationBatchDO();
        p.setId(batch.getId());
        p.setStageStats(statsJson(r));
        batchMapper.updateById(p);
    }

    /** 后端重启时还停在「运行中」的批次已经中断：标为失败，已提交的阶段可以按批次回滚 */
    @EventListener(ApplicationReadyEvent.class)
    public void markInterrupted() {
        MigrationBatchDO p = new MigrationBatchDO();
        p.setStatus(3);
        p.setErrorText("后端重启，执行中断；已提交的阶段保留，可以按批次回滚后重新执行");
        p.setFinishedAt(LocalDateTime.now());
        int n = batchMapper.update(p, new LambdaQueryWrapper<MigrationBatchDO>().eq(MigrationBatchDO::getStatus, 1));
        if (n > 0) {
            log.warn("历史数据迁移：{} 个批次因后端重启中断，已标为失败", n);
        }
    }

    private void stages(MigrationRun r) {
        Integer bu = masterData.businessUnit();
        masterData.run(r);
        inquiries.run(r, bu);
        orders.run(r, bu);
        finance.run(r, bu);
        archive.run(r, r.inquiryItemIds, () -> {
        });
    }

    private Map<String, JsonNode> loadBundle(String dir) {
        if (dir == null || dir.isBlank()) {
            throw new BizException("请选择迁移包");
        }
        Map<String, JsonNode> out = new HashMap<>();
        try {
            for (String name : Stream.concat(BUNDLE_FILES.stream(), Stream.of("manifest")).toList()) {
                Path p = Path.of(dir, name + ".json");
                if (Files.isRegularFile(p)) {
                    out.put(name, objectMapper.readTree(p.toFile()));
                }
            }
        } catch (IOException e) {
            throw new BizException("读取清洗结果失败：" + e.getMessage(), e);
        }
        if (!out.containsKey("customers")) {
            throw new BizException("目录里没有清洗结果（customers.json）：" + dir);
        }
        return out;
    }

    private String statsJson(MigrationRun r) {
        Map<String, Map<String, Map<String, Integer>>> out = new LinkedHashMap<>();
        r.stats.forEach((stage, kinds) -> {
            Map<String, Map<String, Integer>> m = new LinkedHashMap<>();
            kinds.forEach((kind, c) -> m.put(kind, Map.of("新建", c[0], "跳过", c[1], "失败", c[2])));
            out.put(stage, m);
        });
        try {
            return objectMapper.writeValueAsString(out);
        } catch (IOException e) {
            return "{}";
        }
    }

    /** 按批次回滚（后台运行）：该批次写入的数据软删除，候选来源按型号行重算，素材移出素材库 */
    public MigrationBatchVO rollback(Long batchId) {
        requirePlatform();
        MigrationBatchDO batch = batchId == null ? null : batchMapper.selectById(batchId);
        if (batch == null || batch.getRunMode() != 2) {
            throw new BizException("迁移批次不存在或不是正式执行的批次");
        }
        if (batch.getStatus() == 4) {
            throw new BizException("该批次已回滚");
        }
        if (batch.getStatus() == 1) {
            throw new BizException("该批次还在执行，等它结束后再回滚");
        }
        submit(() -> { }, batch::getId, () -> doRollback(batch));
        return toVo(batch);
    }

    private void doRollback(MigrationBatchDO batch) {
        Long batchId = batch.getId();
        List<MigrationIdMapDO> maps = idMapMapper.selectList(new LambdaQueryWrapper<MigrationIdMapDO>()
                .eq(MigrationIdMapDO::getBatchId, batchId)
                .eq(MigrationIdMapDO::getStatus, 1));
        Map<String, List<Long>> byTable = maps.stream().collect(Collectors.groupingBy(MigrationIdMapDO::getTargetTable,
                Collectors.mapping(MigrationIdMapDO::getTargetId, Collectors.toList())));
        TenantContext.setTenantId(batch.getTenantId());
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(s -> {
                LocalDateTime now = LocalDateTime.now();
                for (Map.Entry<String, List<Long>> e : byTable.entrySet()) {
                    if (!SOFT_DELETE_TABLES.contains(e.getKey())) {
                        continue;
                    }
                    for (List<Long> chunk : chunks(e.getValue())) {
                        String in = chunk.stream().map(x -> "?").collect(Collectors.joining(","));
                        jdbc.update("UPDATE `" + e.getKey() + "` SET deleted_at = ? WHERE tenant_id = ? AND id IN (" + in + ")",
                                Stream.concat(Stream.of(now, batch.getTenantId()), chunk.stream()).toArray());
                        if (e.getKey().equals("biz_attachment") || e.getKey().equals("customer_inquiry_attachment")) {
                            jdbc.update("UPDATE media_item SET deleted_at = ? WHERE tenant_id = ? AND source_table = ? AND source_id IN (" + in + ")",
                                    Stream.concat(Stream.of(now, batch.getTenantId(), e.getKey()), chunk.stream()).toArray());
                        }
                    }
                }
                // 候选来源：软删除这些型号行的来源，再重算受影响的候选（没有来源的待审核候选会被软删除）
                List<Long> items = byTable.getOrDefault("inquiry_item", List.of());
                Set<Long> candidates = new java.util.HashSet<>();
                for (List<Long> chunk : chunks(items)) {
                    String in = chunk.stream().map(x -> "?").collect(Collectors.joining(","));
                    candidates.addAll(jdbc.queryForList("SELECT DISTINCT candidate_id FROM product_candidate_source WHERE deleted_at IS NULL AND inquiry_item_id IN (" + in + ")",
                            Long.class, chunk.toArray()));
                    jdbc.update("UPDATE product_candidate_source SET deleted_at = ? WHERE deleted_at IS NULL AND inquiry_item_id IN (" + in + ")",
                            Stream.concat(Stream.of(now), chunk.stream()).toArray());
                }
                candidates.forEach(archiver::refresh);
                idMapMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<MigrationIdMapDO>()
                        .set(MigrationIdMapDO::getStatus, 2)
                        .eq(MigrationIdMapDO::getBatchId, batchId));
            });
        } finally {
            TenantContext.setTenantId(PLATFORM);
        }
        batch.setStatus(4);
        batchMapper.updateById(batch);
        log.info("历史数据迁移批次已回滚，batchNo={}，记录数={}", batch.getBatchNo(), maps.size());
    }

    private static List<List<Long>> chunks(List<Long> ids) {
        List<List<Long>> out = new ArrayList<>();
        for (int i = 0; i < ids.size(); i += 500) {
            out.add(ids.subList(i, Math.min(ids.size(), i + 500)));
        }
        return out;
    }

    public MigrationBundleVO upload(org.springframework.web.multipart.MultipartFile file) {
        requirePlatform();
        return bundles.save(file);
    }

    /** 单独核对一次（不保存）：本机 verify.js 用来输出差异表 */
    public JsonNode verify(Integer tenantId, String bundleId, String bundleDir) {
        requirePlatform();
        requireTenant(tenantId);
        Map<String, JsonNode> bundle = loadBundle(bundleId == null || bundleId.isBlank() ? bundleDir : bundles.dir(bundleId).toString());
        if (!bundle.containsKey("reports")) {
            throw new BizException("迁移包里没有旧报表数字（reports.json），请用 pack.js 重新打包");
        }
        try {
            return objectMapper.readTree(verifier.verify(tenantId, bundle));
        } catch (IOException e) {
            throw new BizException("核对结果解析失败", e);
        }
    }

    public MigrationBatchVO get(Long batchId) {
        requirePlatform();
        MigrationBatchDO b = batchId == null ? null : batchMapper.selectById(batchId);
        if (b == null) {
            throw new BizException("迁移批次不存在");
        }
        return toVo(b);
    }

    public List<MigrationBatchVO> list(Integer tenantId) {
        requirePlatform();
        return batchMapper.selectList(new LambdaQueryWrapper<MigrationBatchDO>()
                        .eq(tenantId != null, MigrationBatchDO::getTenantId, tenantId)
                        .orderByDesc(MigrationBatchDO::getId))
                .stream().map(b -> {
                    MigrationBatchVO vo = toVo(b);
                    // 列表只带核对汇总，明细在单个批次里看
                    if (vo.getVerifyResult() != null && vo.getVerifyResult().isObject()) {
                        ((com.fasterxml.jackson.databind.node.ObjectNode) vo.getVerifyResult()).remove("rows");
                    }
                    return vo;
                }).toList();
    }

    private MigrationBatchVO toVo(MigrationBatchDO b) {
        MigrationBatchVO vo = new MigrationBatchVO();
        vo.setId(b.getId());
        vo.setTenantId(b.getTenantId());
        vo.setBatchNo(b.getBatchNo());
        vo.setBundleHash(b.getBundleHash());
        vo.setRunMode(b.getRunMode());
        vo.setStatus(b.getStatus());
        vo.setErrorText(b.getErrorText());
        vo.setStartedAt(b.getStartedAt());
        vo.setFinishedAt(b.getFinishedAt());
        vo.setRollingBack(b.getStatus() != null && b.getStatus() != 1 && b.getId() != null && b.getId().equals(active.get()));
        try {
            vo.setStageStats(b.getStageStats() == null ? null : objectMapper.readTree(b.getStageStats()));
            vo.setVerifyResult(b.getVerifyResult() == null ? null : objectMapper.readTree(b.getVerifyResult()));
        } catch (IOException e) {
            vo.setStageStats(null);
        }
        return vo;
    }
}
