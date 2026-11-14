package com.zhul.erp.modules.supply.support;

import com.zhul.erp.modules.product.support.BrandResolver;
import com.zhul.erp.modules.product.support.MpnNormalizer;
import com.zhul.erp.modules.supply.constants.SupplyConstants;
import com.zhul.erp.modules.supply.entity.ScDemandDO;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 价格证据与主营供应商（design 决策 6、8）：证据不复制，每次从询价记录与采购订单读。
 * 证据要求型号一致，品牌能识别时还须一致；识别不到品牌的记录只按型号算。
 */
@Component
@RequiredArgsConstructor
public class EvidenceIndex {

    private final SupplySourceMapper sourceMapper;
    private final BrandResolver brandResolver;

    /** 一条证据 */
    public record Evidence(int type, long id, LocalDate date, Long supplierId) {
    }

    /** 一个租户的快照 */
    public final class Snapshot {
        /** 型号 → 证据列表 */
        private final Map<String, List<Raw>> byModel = new HashMap<>();
        /** 品牌 → [品类（null 为全部品类）, 供应商] */
        private final Map<Long, List<long[]>> scopes = new HashMap<>();
        private final Map<String, String> sourcing = new HashMap<>();
        private final Map<String, Optional<Long>> brands = new HashMap<>();

        private record Raw(int type, long id, String brand, Long supplierId, LocalDate date) {
        }

        /** 该需求对象最近一条证据（可限定供应商） */
        public Evidence latest(ScDemandDO d, Long supplierId) {
            if (!StringUtils.hasText(d.getModelKey())) {
                return null;
            }
            Raw best = null;
            for (Raw r : byModel.getOrDefault(d.getModelKey(), List.of())) {
                if (supplierId != null && !supplierId.equals(r.supplierId())) {
                    continue;
                }
                Long b = brand(r.brand());
                if (b != null && !b.equals(d.getBrandId())) {
                    continue;
                }
                if (best == null || r.date().isAfter(best.date()) || r.date().equals(best.date()) && r.id() > best.id()) {
                    best = r;
                }
            }
            return best == null ? null : new Evidence(best.type(), best.id(), best.date(), best.supplierId());
        }

        /** 主营该品牌（及品类）的供应商数 */
        public int leadSuppliers(ScDemandDO d) {
            Set<Long> out = new HashSet<>();
            for (long[] s : scopes.getOrDefault(d.getBrandId(), List.of())) {
                long cat = s[0];
                if (cat == 0 || d.getCategoryId() == null || d.getCategoryId() == 0 || cat == d.getCategoryId()) {
                    out.add(s[1]);
                }
            }
            return out.size();
        }

        /** 型号在询价中时返回询价任务编号 */
        public String sourcingTask(String modelKey) {
            return modelKey == null ? null : sourcing.get(modelKey);
        }

        private Long brand(String raw) {
            String key = BrandResolver.key(raw);
            if (key.isEmpty()) {
                return null;
            }
            return brands.computeIfAbsent(key, k -> Optional.ofNullable(brandResolver.resolve(raw))).orElse(null);
        }
    }

    /** 读取这些需求对象需要的证据；调用方负责租户上下文 */
    public Snapshot load(Collection<ScDemandDO> demands) {
        Snapshot s = new Snapshot();
        List<String> keys = demands.stream().map(ScDemandDO::getModelKey).filter(StringUtils::hasText).distinct().toList();
        Set<String> wanted = new HashSet<>(keys);
        for (int from = 0; from < keys.size(); from += 500) {
            for (Map<String, Object> m : sourceMapper.quoteEvidence(keys.subList(from, Math.min(keys.size(), from + 500)))) {
                add(s, SupplyConstants.EVIDENCE_QUOTE, m, (String) m.get("modelKey"));
            }
        }
        if (!wanted.isEmpty()) {
            for (Map<String, Object> m : sourceMapper.poEvidence()) {
                String key = MpnNormalizer.normalize((String) m.get("model"));
                if (wanted.contains(key)) {
                    add(s, SupplyConstants.EVIDENCE_PO, m, key);
                }
            }
        }
        for (Map<String, Object> m : sourceMapper.supplierScopes()) {
            Number cat = (Number) m.get("categoryId");
            s.scopes.computeIfAbsent(((Number) m.get("brandId")).longValue(), k -> new ArrayList<>())
                    .add(new long[]{cat == null ? 0 : cat.longValue(), ((Number) m.get("supplierId")).longValue()});
        }
        for (Map<String, Object> m : sourceMapper.sourcingModels()) {
            s.sourcing.putIfAbsent((String) m.get("modelKey"), (String) m.get("taskCode"));
        }
        return s;
    }

    private static void add(Snapshot s, int type, Map<String, Object> m, String key) {
        Object d = m.get("evidenceDate");
        LocalDate date = d instanceof Date sd ? sd.toLocalDate() : d instanceof LocalDate ld ? ld : null;
        if (date == null) {
            return;
        }
        Number sup = (Number) m.get("supplierId");
        s.byModel.computeIfAbsent(key, k -> new ArrayList<>()).add(new Snapshot.Raw(type, ((Number) m.get("id")).longValue(),
                (String) m.get("brand"), sup == null || sup.longValue() == 0 ? null : sup.longValue(), date));
    }
}
