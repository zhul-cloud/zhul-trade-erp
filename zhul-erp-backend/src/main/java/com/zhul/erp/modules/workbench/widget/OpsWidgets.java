package com.zhul.erp.modules.workbench.widget;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.listing.constants.ListingConstants;
import com.zhul.erp.modules.listing.dto.ListingTaskQuery;
import com.zhul.erp.modules.listing.entity.ListingTaskDO;
import com.zhul.erp.modules.listing.repository.ListingTaskMapper;
import com.zhul.erp.modules.listing.service.ListingTaskService;
import com.zhul.erp.modules.media.entity.MediaItemDO;
import com.zhul.erp.modules.media.repository.MediaItemMapper;
import com.zhul.erp.modules.media.support.MediaCategory;
import com.zhul.erp.modules.product.candidate.service.ProductCandidateService;
import com.zhul.erp.modules.product.constants.ProductConstants;
import com.zhul.erp.modules.product.content.constants.ContentConstants;
import com.zhul.erp.modules.product.content.dto.ContentTaskQuery;
import com.zhul.erp.modules.product.content.entity.ProductContentImportDO;
import com.zhul.erp.modules.product.content.entity.ProductFactTaskDO;
import com.zhul.erp.modules.product.content.repository.ProductContentImportMapper;
import com.zhul.erp.modules.product.content.repository.ProductFactTaskMapper;
import com.zhul.erp.modules.product.content.service.ContentTaskService;
import com.zhul.erp.modules.product.content.service.PlatformSettingService;
import com.zhul.erp.modules.warehouse.constants.WarehouseConstants;
import com.zhul.erp.modules.warehouse.entity.ShootTaskDO;
import com.zhul.erp.modules.warehouse.repository.ShootTaskMapper;
import com.zhul.erp.modules.workbench.dto.WidgetData;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 运营负责人看板（spec「运营负责人看板」）：内容流水线、平台发布进度、素材、本月产出。
 * 口径同资料任务、营销任务、发品任务、素材库与商品候选页（本公司）。
 */
@Configuration
public class OpsWidgets {

    static final String FACT_TASKS = "/product/fact-tasks";
    static final String MARKETING = "/operations/products";
    static final String LISTINGS = "/operations/listings";
    static final String MEDIA = "/operations/media";
    static final String CANDIDATES = "/product/candidates";

    @Bean
    public WorkbenchWidget contentPipelineWidget(ContentTaskService content, ListingTaskService listings,
                                                 ListingTaskMapper listingMapper) {
        return W.widget("content-pipeline", List.of(FACT_TASKS, MARKETING, LISTINGS), () -> {
            Map<String, Long> facts = content.counts(new ContentTaskQuery(), ContentTaskService.View.FACTS);
            Map<String, Long> marketing = content.counts(new ContentTaskQuery(), ContentTaskService.View.MARKETING);
            Map<String, Long> listed = listings.counts(new ListingTaskQuery());
            long needMedia = listingMapper.needMediaProducts(TenantContext.getTenantId());
            List<WidgetData.Step> steps = List.of(
                    step("资料待补", facts.get("1"), FACT_TASKS),
                    step("资料待核", facts.get("2"), FACT_TASKS),
                    step("营销待生成", marketing.get("3"), MARKETING),
                    step("发品待发布", listed.get("pending"), LISTINGS),
                    step("全部发完", listed.get("published"), LISTINGS));
            WidgetData.Block funnel = new WidgetData.Block();
            funnel.setType("funnel");
            funnel.setSteps(steps);
            return W.of(funnel, W.metrics(
                    W.link(W.tone(W.metric("其中素材待补", needMedia, "count", "待发布但没有发品素材的商品"),
                            needMedia > 0 ? "orange" : null), LISTINGS),
                    W.link(W.metric("内容有更新", listed.get("updated"), "count", "已发布后营销内容又导入过"), LISTINGS),
                    W.link(W.metric("待替换素材", listed.get("replace"), "count", "用参考素材发布、本型号已有素材"), LISTINGS)));
        });
    }

    @Bean
    public WorkbenchWidget platformProgressWidget(ListingTaskMapper listingMapper, PlatformSettingService platforms) {
        return W.widget("platform-progress", List.of(LISTINGS), () -> {
            Map<String, Map<Integer, Long>> counts = new HashMap<>();
            for (Map<String, Object> r : listingMapper.countByPlatformStatus(TenantContext.getTenantId())) {
                counts.computeIfAbsent(String.valueOf(r.get("platform")), k -> new HashMap<>())
                        .put(((Number) r.get("status")).intValue(), ((Number) r.get("cnt")).longValue());
            }
            WidgetData.Block b = new WidgetData.Block();
            b.setType("progress");
            b.setProgress(platforms.enabled().stream().map(p -> {
                Map<Integer, Long> c = counts.getOrDefault(p.code(), Map.of());
                WidgetData.Progress row = new WidgetData.Progress();
                row.setLabel(p.label());
                row.getParts().add(part("已发布", c.get(ListingConstants.STATUS_PUBLISHED), "green"));
                row.getParts().add(part("待发布", c.get(ListingConstants.STATUS_PENDING), "blue"));
                row.getParts().add(part("已取消", c.get(ListingConstants.STATUS_CANCELLED), "gray"));
                return row;
            }).toList());
            return W.of(b);
        });
    }

    @Bean
    public WorkbenchWidget mediaOverviewWidget(MediaItemMapper media, ShootTaskMapper shoots,
                                               ProductCandidateService candidates) {
        return W.widget("media-overview", List.of(MEDIA), () -> {
            int tenant = TenantContext.getTenantId();
            long noMarketing = media.countModels(tenant, null, false, null);
            long pendingShoots = shoots.selectCount(new LambdaQueryWrapper<ShootTaskDO>()
                    .eq(ShootTaskDO::getTenantId, tenant)
                    .eq(ShootTaskDO::getStatus, WarehouseConstants.SHOOT_PENDING)
                    .isNull(ShootTaskDO::getDeletedAt));
            long newMarketing = media.selectCount(new LambdaQueryWrapper<MediaItemDO>()
                    .eq(MediaItemDO::getTenantId, tenant)
                    .eq(MediaItemDO::getCategory, MediaCategory.MARKETING.code())
                    .ge(MediaItemDO::getCreateTime, W.monthStart().atStartOfDay())
                    .isNull(MediaItemDO::getDeletedAt));
            Long pendingCandidates = candidates.counts().get("pending");
            return W.of(W.metrics(
                    W.link(W.metric("缺营销实拍的型号", noMarketing, "count", "有素材但还没有营销实拍"), MEDIA),
                    W.link(W.metric("待拍摄任务", pendingShoots, "count", "到货后自动生成"), "/warehouse/shoots"),
                    W.metric("本月新增营销实拍", newMarketing, "count", null),
                    W.link(W.metric("待审核商品候选", pendingCandidates, "count", "等待建档"), CANDIDATES)));
        });
    }

    @Bean
    public WorkbenchWidget monthlyOutputWidget(ProductFactTaskMapper facts, ProductContentImportMapper imports,
                                               ListingTaskMapper listingMapper) {
        return W.widget("monthly-output", List.of(MARKETING, LISTINGS), () -> {
            LocalDateTime month = W.monthStart().atStartOfDay();
            long verified = facts.selectCount(new LambdaQueryWrapper<ProductFactTaskDO>()
                    .eq(ProductFactTaskDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                    .ge(ProductFactTaskDO::getVerifiedAt, month)
                    .isNull(ProductFactTaskDO::getDeletedAt));
            long written = imports.selectObjs(new QueryWrapper<ProductContentImportDO>()
                            .select("COUNT(DISTINCT product_id)")
                            .eq("tenant_id", TenantContext.getTenantId())
                            .eq("package_type", ContentConstants.TYPE_MARKETING)
                            .ge("create_time", month)
                            .isNull("deleted_at"))
                    .stream().findFirst().map(o -> ((Number) o).longValue()).orElse(0L);
            long published = listingMapper.selectCount(new LambdaQueryWrapper<ListingTaskDO>()
                    .eq(ListingTaskDO::getTenantId, TenantContext.getTenantId())
                    .ge(ListingTaskDO::getPublishedAt, month)
                    .isNull(ListingTaskDO::getDeletedAt));
            return W.of(W.metrics(
                    W.metric("资料核对", verified, "count", "个商品（所有公司共用）"),
                    W.metric("营销写入", written, "count", "个商品"),
                    W.metric("发品", published, "count", "个商品 × 平台")));
        });
    }

    private static WidgetData.Step step(String label, Long value, String link) {
        WidgetData.Step s = new WidgetData.Step();
        s.setLabel(label);
        s.setValue(BigDecimal.valueOf(value == null ? 0 : value));
        s.setLink(link);
        return s;
    }

    private static WidgetData.Part part(String label, Long value, String tone) {
        WidgetData.Part p = new WidgetData.Part();
        p.setLabel(label);
        p.setValue(BigDecimal.valueOf(value == null ? 0 : value));
        p.setTone(tone);
        return p;
    }
}
