package com.zhul.erp.modules.supply.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.modules.product.support.BrandResolver;
import com.zhul.erp.modules.product.support.MpnNormalizer;
import com.zhul.erp.modules.sales.support.PiStore;
import com.zhul.erp.modules.supply.constants.SupplyConstants;
import com.zhul.erp.modules.supply.dto.SourceRow;
import com.zhul.erp.modules.supply.entity.ScDemandDO;
import com.zhul.erp.modules.supply.entity.ScDemandSignalDO;
import com.zhul.erp.modules.supply.repository.ScDemandSignalMapper;
import com.zhul.erp.modules.supply.repository.SupplySourceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 自动汇入需求信号（design 决策 2）：四种来源各一个读取器，按「来源类型 + 来源行」幂等写入。
 * 事务边界：每种来源一个事务，某种来源失败不影响已写入的其他来源，下次执行补上。调用方负责租户上下文。
 */
@Component
@RequiredArgsConstructor
public class SignalImporter {

    private final SupplySourceMapper sourceMapper;
    private final ScDemandSignalMapper signalMapper;
    private final DemandRegistry registry;
    private final BrandResolver brandResolver;
    private final SupplyLookups lookups;
    private final TransactionTemplate tx;

    /** 一次汇入的结果：新写入的信号数、因无货新产生信号的需求对象（用于提醒，值为无货说明） */
    public static class Result {
        public int created;
        public int updated;
        public final Map<Long, String> stockoutDemands = new HashMap<>();
    }

    /** 一次汇入共用的缓存 */
    private final class Ctx {
        final Map<String, Optional<Long>> brands = new HashMap<>();
        final Map<Long, Long> categoryParents = lookups.categoryParents();
        final Map<String, String> noStockReasons = lookups.dict("inquiry_no_stock_reason", true);
        Map<Long, Long> productCategories = Map.of();

        Long brand(Long id, String raw) {
            if (id != null && id > 0) {
                return id;
            }
            String key = BrandResolver.key(raw);
            if (key.isEmpty()) {
                return null;
            }
            return brands.computeIfAbsent(key, k -> Optional.ofNullable(brandResolver.resolve(raw))).orElse(null);
        }

        /** 二级品类：型号行关联的是细分品类直接用，否则看商品档案的品类，都不是细分品类时为 0 */
        Long category(Long categoryId, Long productId) {
            if (isLevel2(categoryId)) {
                return categoryId;
            }
            Long p = productId == null ? null : productCategories.get(productId);
            return isLevel2(p) ? p : 0L;
        }

        boolean isLevel2(Long id) {
            return id != null && categoryParents.get(id) != null;
        }
    }

    public Result importAll(LocalDateTime recent, LocalDateTime floor, java.util.Set<Integer> backfill) {
        Result r = new Result();
        Ctx ctx = new Ctx();
        run(SupplyConstants.SRC_INQUIRY, sourceMapper.inquiryRows(since(SupplyConstants.SRC_INQUIRY, recent, floor, backfill), floor), ctx, r);
        run(SupplyConstants.SRC_ORDER, sourceMapper.orderRows(since(SupplyConstants.SRC_ORDER, recent, floor, backfill), floor), ctx, r);
        run(SupplyConstants.SRC_STOCKOUT, sourceMapper.stockoutRows(since(SupplyConstants.SRC_STOCKOUT, recent, floor, backfill), floor), ctx, r);
        run(SupplyConstants.SRC_LOST, sourceMapper.lostRows(since(SupplyConstants.SRC_LOST, recent, floor, backfill), floor), ctx, r);
        return r;
    }

    private static LocalDateTime since(int type, LocalDateTime recent, LocalDateTime floor, java.util.Set<Integer> backfill) {
        return backfill.contains(type) ? floor : recent;
    }

    /** 单条来源行（询价联动实时写无货信号时用），调用方负责事务 */
    public ScDemandSignalDO importOne(int type, SourceRow row, Result r) {
        Ctx ctx = new Ctx();
        ctx.productCategories = lookups.productCategories(row.getProductId() == null ? List.of() : List.of(row.getProductId()));
        return upsert(type, row, ctx, r);
    }

    private void run(int type, List<SourceRow> rows, Ctx ctx, Result r) {
        if (rows.isEmpty()) {
            return;
        }
        ctx.productCategories = lookups.productCategories(rows.stream().map(SourceRow::getProductId).toList());
        tx.executeWithoutResult(s -> rows.forEach(row -> upsert(type, row, ctx, r)));
    }

    private ScDemandSignalDO upsert(int type, SourceRow row, Ctx ctx, Result r) {
        boolean cancelled = Boolean.TRUE.equals(row.getCancelled());
        String reasonText = reasonText(type, row, ctx);
        ScDemandSignalDO old = signalMapper.selectOne(new LambdaQueryWrapper<ScDemandSignalDO>()
                .eq(ScDemandSignalDO::getSourceType, type)
                .eq(ScDemandSignalDO::getSourceId, row.getSourceId())
                .last("LIMIT 1"));
        if (old != null) {
            int flag = cancelled ? 1 : 0;
            String code = text(row.getReasonCode(), 32);
            if (!Objects.equals(old.getSourceCancelled(), flag) || !Objects.equals(old.getReasonCode(), code)
                    || !Objects.equals(old.getReasonText(), reasonText)) {
                ScDemandSignalDO u = new ScDemandSignalDO();
                u.setId(old.getId());
                u.setSourceCancelled(flag);
                u.setReasonCode(code);
                u.setReasonText(reasonText);
                signalMapper.updateById(u);
                r.updated++;
            }
            return old;
        }
        if (cancelled) {
            return null;
        }
        ScDemandSignalDO s = new ScDemandSignalDO();
        s.setTenantId(PiStore.tenantId());
        s.setSourceType(type);
        s.setSourceId(row.getSourceId());
        s.setSourceDocId(row.getDocId() == null ? 0L : row.getDocId());
        s.setSourceDocNo(text(row.getDocNo(), 32));
        Long brandId = ctx.brand(row.getBrandId(), row.getBrandRaw());
        Long categoryId = ctx.category(row.getCategoryId(), row.getProductId());
        String modelKey = StringUtils.hasText(row.getModelKey()) ? row.getModelKey() : MpnNormalizer.normalize(row.getModelRaw());
        s.setBrandId(brandId == null ? 0L : brandId);
        s.setBrandRaw(text(row.getBrandRaw(), 64));
        s.setCategoryId(categoryId);
        s.setModelRaw(text(row.getModelRaw(), 128));
        s.setModelKey(text(modelKey, 128));
        s.setQuantity(row.getQuantity() == null ? 0 : row.getQuantity());
        s.setReasonCode(text(row.getReasonCode(), 32));
        s.setReasonText(reasonText);
        s.setOccurCount(1);
        s.setOccurredAt(row.getOccurredAt() == null ? LocalDateTime.now() : row.getOccurredAt());
        s.setSourceCancelled(0);
        s.setOperatorId(0L);
        long demandId = 0;
        if (brandId != null && StringUtils.hasText(modelKey)) {
            ScDemandDO d = registry.findOrCreate(brandId, categoryId, modelKey, row.getModelRaw());
            demandId = d.getId();
        }
        s.setDemandId(demandId);
        signalMapper.insert(s);
        r.created++;
        if (type == SupplyConstants.SRC_STOCKOUT && demandId > 0) {
            r.stockoutDemands.put(demandId, reasonText);
        }
        return s;
    }

    /** 无货：字典名称 + 说明；未成交：原因名称（去掉分类前缀） */
    private static String reasonText(int type, SourceRow row, Ctx ctx) {
        if (type == SupplyConstants.SRC_STOCKOUT) {
            String name = row.getReasonCode() == null ? null : ctx.noStockReasons.get(row.getReasonCode());
            String note = row.getReasonText() == null ? "" : row.getReasonText().trim();
            String t = name == null ? note : note.isEmpty() || note.equals(name) ? name : name + "：" + note;
            return text(t, 200);
        }
        String t = row.getReasonText() == null ? "" : row.getReasonText().replaceFirst("^【[^】]*】", "");
        return text(t, 200);
    }

    private static String text(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }
}
