package com.zhul.erp.modules.supply.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.modules.product.support.BrandResolver;
import com.zhul.erp.modules.product.support.MpnNormalizer;
import com.zhul.erp.modules.supply.constants.SupplyConstants;
import com.zhul.erp.modules.supply.entity.ScDemandDO;
import com.zhul.erp.modules.supply.entity.ScExpansionScopeDO;
import com.zhul.erp.modules.supply.repository.ScDemandMapper;
import com.zhul.erp.modules.supply.repository.ScExpansionScopeMapper;
import com.zhul.erp.modules.supply.repository.SupplySourceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 供应商开发的数据底座（spec supply-chain/supplier-development；design 决策 2、3）：
 * 按「品牌 × 二级品类」汇总近 90 天的合格供应商、线索供应商与需求热度。每次请求实时算，不存快照。
 * <p>
 * 证据归到哪个范围：型号已有需求对象的取需求对象的品牌与品类；否则取商品主数据的品牌与品类（不是二级品类的算未分类）；
 * 都取不到品类的归到「品牌 · 未分类」（品类 0），取不到品牌的丢弃。只算启用且未删除的供应商。调用方负责租户上下文。
 */
@Component
@RequiredArgsConstructor
public class DevPool {

    private final SupplySourceMapper sourceMapper;
    private final ScDemandMapper demandMapper;
    private final ScExpansionScopeMapper expansionMapper;
    private final BrandResolver brandResolver;
    private final SupplyLookups lookups;

    /** 一条合格依据 */
    public record Evidence(int type, long id, LocalDate date) {
    }

    /** 一个品牌 × 二级品类 */
    public static final class Range {
        private final long brandId;
        private final long categoryId;
        /** 需求对象推出的热度，0 为没有需求对象 */
        private int autoHeat;
        private ScExpansionScopeDO expansion;
        private final Map<Long, Evidence> qualified = new LinkedHashMap<>();
        private final Set<Long> leads = new LinkedHashSet<>();
        /** 范围内的需求对象（不含暂不覆盖的） */
        private final List<Long> demandIds = new ArrayList<>();

        public Range(long brandId, long categoryId) {
            this.brandId = brandId;
            this.categoryId = categoryId;
        }

        public long brandId() {
            return brandId;
        }

        public long categoryId() {
            return categoryId;
        }

        public String key() {
            return key(brandId, categoryId);
        }

        public static String key(long brandId, long categoryId) {
            return brandId + ":" + categoryId;
        }

        public int autoHeat() {
            return autoHeat;
        }

        public ScExpansionScopeDO expansion() {
            return expansion;
        }

        /** 取手动热度与自动热度中较高的一档 */
        public int heat() {
            int manual = expansion == null || expansion.getHeat() == null ? 0 : expansion.getHeat();
            return Math.max(Math.max(autoHeat, manual), SupplyConstants.HEAT_LOW);
        }

        public int required() {
            return SupplyConstants.requiredSuppliers(heat());
        }

        public Map<Long, Evidence> qualified() {
            return qualified;
        }

        public Set<Long> leads() {
            return leads;
        }

        public List<Long> demandIds() {
            return demandIds;
        }

        public int gap() {
            return Math.max(0, required() - qualified.size());
        }

        /** 来源：demand / expansion / both */
        public String source() {
            if (autoHeat > 0 && expansion != null) {
                return "both";
            }
            return expansion != null ? "expansion" : "demand";
        }

        void heatFrom(int priority) {
            autoHeat = Math.max(autoHeat, priority);
        }

        void setExpansion(ScExpansionScopeDO e) {
            expansion = e;
        }
    }

    /** 一个租户的快照 */
    public static final class Snapshot {
        /** 清单里的范围：有需求对象或在主动拓展里 */
        private final Map<String, Range> listed = new LinkedHashMap<>();
        /** 全部范围的合格供应商（含不在清单里的，供候选自动合格用） */
        private final Map<String, Map<Long, Evidence>> qualified = new HashMap<>();

        public Collection<Range> listed() {
            return listed.values();
        }

        public Range listed(long brandId, long categoryId) {
            return listed.get(Range.key(brandId, categoryId));
        }

        /** 范围内的合格供应商（不论是否在清单里） */
        public Map<Long, Evidence> qualified(long brandId, long categoryId) {
            return qualified.getOrDefault(Range.key(brandId, categoryId), Map.of());
        }
    }

    public Snapshot load(LocalDate today) {
        Snapshot s = new Snapshot();
        List<ScDemandDO> demands = demandMapper.selectList(new LambdaQueryWrapper<ScDemandDO>()
                .select(ScDemandDO::getId, ScDemandDO::getBrandId, ScDemandDO::getCategoryId, ScDemandDO::getModelKey,
                        ScDemandDO::getPriority, ScDemandDO::getStatus)
                .isNull(ScDemandDO::getDeletedAt));
        Map<String, List<long[]>> demandScopes = new HashMap<>();
        for (ScDemandDO d : demands) {
            long b = d.getBrandId() == null ? 0 : d.getBrandId();
            long c = d.getCategoryId() == null ? 0 : d.getCategoryId();
            if (StringUtils.hasText(d.getModelKey()) && b > 0) {
                demandScopes.computeIfAbsent(d.getModelKey(), k -> new ArrayList<>()).add(new long[]{b, c});
            }
            if (b > 0 && !Integer.valueOf(SupplyConstants.DEMAND_PAUSED).equals(d.getStatus())) {
                Range r = s.listed.computeIfAbsent(Range.key(b, c), k -> new Range(b, c));
                r.heatFrom(d.getPriority() == null ? SupplyConstants.HEAT_LOW : d.getPriority());
                r.demandIds.add(d.getId());
            }
        }
        for (ScExpansionScopeDO e : expansionMapper.selectList(new LambdaQueryWrapper<ScExpansionScopeDO>()
                .isNull(ScExpansionScopeDO::getDeletedAt))) {
            s.listed.computeIfAbsent(Range.key(e.getBrandId(), e.getCategoryId()), k -> new Range(e.getBrandId(), e.getCategoryId()))
                    .setExpansion(e);
        }
        Set<Long> active = new HashSet<>(sourceMapper.activeSupplierIds());
        LocalDate since = today.minusDays(SupplyConstants.DEV_WINDOW_DAYS - 1L);
        List<Raw> raws = new ArrayList<>();
        for (Map<String, Object> m : sourceMapper.devQuotes(since.atStartOfDay())) {
            raws.add(raw(SupplyConstants.EVIDENCE_QUOTE, m, (String) m.get("modelKey")));
        }
        for (Map<String, Object> m : sourceMapper.devPos(since)) {
            raws.add(raw(SupplyConstants.EVIDENCE_PO, m, MpnNormalizer.normalize((String) m.get("model"))));
        }
        Map<String, List<long[]>> productScopes = productScopes(raws.stream().map(Raw::modelKey)
                .filter(k -> StringUtils.hasText(k) && !demandScopes.containsKey(k)).distinct().toList());
        Map<Long, Long> parents = lookups.categoryParents();
        Set<Long> level2 = new HashSet<>();
        parents.forEach((id, parent) -> {
            if (parent != null) {
                level2.add(id);
            }
        });
        Map<String, Optional<Long>> brandCache = new HashMap<>();
        for (Raw r : raws) {
            if (r.date() == null || r.supplierId() == null || !active.contains(r.supplierId())) {
                continue;
            }
            Long brand = brandCache.computeIfAbsent(BrandResolver.key(r.brand()),
                    k -> k.isEmpty() ? Optional.empty() : Optional.ofNullable(brandResolver.resolve(r.brand()))).orElse(null);
            long[] at = attribute(r.modelKey(), brand, demandScopes, productScopes, level2);
            if (at == null) {
                continue;
            }
            s.qualified.computeIfAbsent(Range.key(at[0], at[1]), k -> new HashMap<>())
                    .merge(r.supplierId(), new Evidence(r.type(), r.id(), r.date()), DevPool::later);
        }
        List<Map<String, Object>> scopes = sourceMapper.supplierScopes();
        for (Range r : s.listed.values()) {
            Map<Long, Evidence> q = s.qualified(r.brandId(), r.categoryId());
            q.entrySet().stream().sorted(Map.Entry.<Long, Evidence>comparingByValue(
                    (a, b) -> b.date().compareTo(a.date()))).forEach(e -> r.qualified.put(e.getKey(), e.getValue()));
            for (Map<String, Object> m : scopes) {
                long sup = ((Number) m.get("supplierId")).longValue();
                Number cat = (Number) m.get("categoryId");
                long c = cat == null ? 0 : cat.longValue();
                if (((Number) m.get("brandId")).longValue() == r.brandId() && scopeCovers(c, r.categoryId(), parents)
                        && active.contains(sup) && !r.qualified.containsKey(sup)) {
                    r.leads.add(sup);
                }
            }
        }
        return s;
    }

    /**
     * 证据归属的范围 [品牌, 品类]：需求对象优先，其次商品主数据（非二级品类算 0），都没有品类时品牌 · 未分类；取不到品牌返回 null。
     * 型号对应多个品牌时，用证据上识别到的品牌挑；识别不到品牌时取第一个。
     */
    static long[] attribute(String modelKey, Long brandId, Map<String, List<long[]>> demandScopes,
                            Map<String, List<long[]>> productScopes, Set<Long> level2) {
        if (StringUtils.hasText(modelKey)) {
            long[] d = pick(demandScopes.get(modelKey), brandId);
            if (d != null) {
                return d;
            }
            long[] p = pick(productScopes.get(modelKey), brandId);
            if (p != null) {
                return new long[]{p[0], level2.contains(p[1]) ? p[1] : 0};
            }
        }
        return brandId == null ? null : new long[]{brandId, 0};
    }

    private static long[] pick(List<long[]> list, Long brandId) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        if (brandId == null) {
            return list.get(0);
        }
        return list.stream().filter(x -> x[0] == brandId).findFirst().orElse(null);
    }

    private static Evidence later(Evidence a, Evidence b) {
        if (b.date().isAfter(a.date()) || b.date().isEqual(a.date()) && b.id() > a.id()) {
            return b;
        }
        return a;
    }

    private Map<String, List<long[]>> productScopes(List<String> keys) {
        Map<String, List<long[]>> out = new HashMap<>();
        for (List<String> part : DemandAssembler.chunks(keys)) {
            for (Map<String, Object> m : sourceMapper.productScopes(part)) {
                Number b = (Number) m.get("brandId");
                Number c = (Number) m.get("categoryId");
                if (b != null && b.longValue() > 0) {
                    out.computeIfAbsent((String) m.get("modelKey"), k -> new ArrayList<>())
                            .add(new long[]{b.longValue(), c == null ? 0 : c.longValue()});
                }
            }
        }
        return out;
    }

    /** 主营产品的品类（0 为该品牌全部品类，也可能登记的是一级品类）是否覆盖范围的品类；范围是未分类时品牌对上就算 */
    static boolean scopeCovers(long scopeCategory, long rangeCategory, Map<Long, Long> parents) {
        return scopeCategory == 0 || rangeCategory == 0 || scopeCategory == rangeCategory
                || Long.valueOf(scopeCategory).equals(parents.get(rangeCategory));
    }

    private record Raw(int type, long id, String modelKey, String brand, Long supplierId, LocalDate date) {
    }

    private static Raw raw(int type, Map<String, Object> m, String modelKey) {
        Object d = m.get("evidenceDate");
        LocalDate date = d instanceof Date sd ? sd.toLocalDate() : d instanceof LocalDate ld ? ld : null;
        Number sup = (Number) m.get("supplierId");
        return new Raw(type, ((Number) m.get("id")).longValue(), modelKey, (String) m.get("brand"),
                sup == null || sup.longValue() == 0 ? null : sup.longValue(), date);
    }
}
