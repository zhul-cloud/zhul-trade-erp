package com.zhul.erp.modules.product.candidate.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.inquiry.item.entity.InquiryItemDO;
import com.zhul.erp.modules.inquiry.item.repository.InquiryItemMapper;
import com.zhul.erp.modules.inquiry.support.PriceKeys;
import com.zhul.erp.modules.product.candidate.constants.CandidateConstants;
import com.zhul.erp.modules.product.candidate.dto.UnlinkedGroupVO;
import com.zhul.erp.modules.product.candidate.entity.ProductCandidateDO;
import com.zhul.erp.modules.product.candidate.repository.ProductCandidateMapper;
import com.zhul.erp.modules.product.candidate.support.ModelRules;
import com.zhul.erp.modules.product.constants.ProductConstants;
import com.zhul.erp.modules.product.dto.ProductOptionVO;
import com.zhul.erp.modules.product.entity.ProductDO;
import com.zhul.erp.modules.product.repository.ProductMapper;
import com.zhul.erp.modules.product.service.ProductLookupService;
import com.zhul.erp.modules.product.support.DocumentProductTables;
import com.zhul.erp.modules.product.support.MpnNormalizer;
import com.zhul.erp.modules.product.support.PlatformScopeGuard;
import com.zhul.erp.modules.system.service.LogService;
import com.zhul.erp.common.result.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 单据明细关联商品的历史回填与「待关联」清单（spec product/document-product-link「历史单据回填与待关联清单」）。
 * 各明细表按 {@link DocumentProductTables} 登记的清单处理；回填跨公司执行（调用方用 TenantScope.platform），
 * 清单与关联只处理当前公司的明细（平台账号看全部），SQL 显式带租户条件。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocProductLinkService {

    private static final int BATCH = 200;
    private static final String HISTORY_NOTE = "历史单据回填";

    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ProductArchiver archiver;
    private final ProductCandidateMapper candidateMapper;
    private final InquiryItemMapper itemMapper;
    private final ProductMapper productMapper;
    private final ProductLookupService lookup;
    private final PriceKeys priceKeys;
    private final PlatformScopeGuard platformScopeGuard;
    private final LogService logService;

    // ---------------------------------------------------------------- 历史回填

    /** 回填结果：各明细表补上的条数与仍为空的条数 */
    public record Report(Map<String, Integer> filled, Map<String, Integer> remaining) {
    }

    /**
     * 历史回填：①待审核候选补生成候选商品 ②候选中的询盘型号补关联 ③沿来源链补 ④按品牌与型号匹配，像型号又匹配不到的进池。
     * 只处理还为空的行，重复执行结果不变；每批一个事务，单批失败只记日志。须在 TenantScope.platform 内调用。
     */
    public Report backfill() {
        Map<String, Integer> filled = new LinkedHashMap<>();
        candidatesWithoutProduct();
        inquiryItemsWithoutProduct();
        for (DocumentProductTables.Entry e : DocumentProductTables.ENTRIES) {
            int n = 0;
            for (DocumentProductTables.Link l : e.chain()) {
                n += tx.execute(s -> jdbc.update("UPDATE `" + e.table() + "` c JOIN `" + l.parentTable() + "` p ON p.id = c." + l.column()
                        + " SET c.product_id = p.product_id WHERE c.product_id IS NULL AND p.product_id IS NOT NULL",
                        new MapSqlParameterSource()));
            }
            if (!e.table().equals("inquiry_item")) {
                n += matchRemaining(e);
            }
            if (n > 0) {
                filled.put(e.table(), n);
            }
        }
        Map<String, Integer> remaining = new LinkedHashMap<>();
        for (DocumentProductTables.Entry e : DocumentProductTables.ENTRIES) {
            Integer left = jdbc.queryForObject("SELECT COUNT(*) FROM `" + e.table() + "` WHERE product_id IS NULL AND deleted_at IS NULL",
                    new MapSqlParameterSource(), Integer.class);
            if (left != null && left > 0) {
                remaining.put(e.table(), left);
            }
        }
        if (!filled.isEmpty()) {
            log.info("单据明细关联商品回填完成，补上={}，仍待关联={}", filled, remaining);
        }
        return new Report(filled, remaining);
    }

    private void candidatesWithoutProduct() {
        long lastId = 0;
        while (true) {
            List<ProductCandidateDO> rows = candidateMapper.selectList(new LambdaQueryWrapper<ProductCandidateDO>()
                    .eq(ProductCandidateDO::getStatus, CandidateConstants.STATUS_PENDING)
                    .isNull(ProductCandidateDO::getProductId)
                    .isNull(ProductCandidateDO::getDeletedAt)
                    .gt(ProductCandidateDO::getId, lastId)
                    .orderByAsc(ProductCandidateDO::getId)
                    .last("LIMIT " + BATCH));
            if (rows.isEmpty()) {
                return;
            }
            lastId = rows.get(rows.size() - 1).getId();
            try {
                tx.executeWithoutResult(s -> rows.forEach(c -> {
                    candidateMapper.lockByKey(c.getBrandKey(), c.getMpnNormalized());
                    archiver.ensureProduct(c);
                }));
            } catch (RuntimeException ex) {
                log.warn("候选补生成商品失败，下次启动重试，lastId={}", lastId, ex);
            }
        }
    }

    private void inquiryItemsWithoutProduct() {
        long lastId = 0;
        while (true) {
            List<Long> ids = itemMapper.selectList(new LambdaQueryWrapper<InquiryItemDO>()
                            .select(InquiryItemDO::getId)
                            .eq(InquiryItemDO::getArchiveStatus, CandidateConstants.ARCHIVE_CANDIDATE)
                            .isNull(InquiryItemDO::getProductId)
                            .isNull(InquiryItemDO::getDeletedAt)
                            .gt(InquiryItemDO::getId, lastId)
                            .orderByAsc(InquiryItemDO::getId)
                            .last("LIMIT " + BATCH))
                    .stream().map(InquiryItemDO::getId).toList();
            if (ids.isEmpty()) {
                return;
            }
            lastId = ids.get(ids.size() - 1);
            try {
                tx.executeWithoutResult(s -> archiver.sync(ids));
            } catch (RuntimeException ex) {
                log.warn("询盘型号补关联候选商品失败，下次启动重试，lastId={}", lastId, ex);
            }
        }
    }

    /** 来源链补不到的行：按品牌与型号匹配已建档或候选商品，匹配不到且像型号的进池 */
    private int matchRemaining(DocumentProductTables.Entry e) {
        int n = 0;
        long lastId = 0;
        Map<String, PriceKeys.BrandRef> brands = new HashMap<>();
        while (true) {
            List<Row> rows = jdbc.query("SELECT id, tenant_id, brand, model FROM `" + e.table() + "` WHERE product_id IS NULL"
                            + " AND deleted_at IS NULL AND id > :last ORDER BY id LIMIT " + BATCH,
                    new MapSqlParameterSource("last", lastId),
                    (rs, i) -> new Row(rs.getLong("id"), rs.getInt("tenant_id"), rs.getString("brand"), rs.getString("model")));
            if (rows.isEmpty()) {
                return n;
            }
            lastId = rows.get(rows.size() - 1).id();
            try {
                Integer done = tx.execute(s -> {
                    int k = 0;
                    for (Row r : rows) {
                        Long pid = resolve(r.tenantId(), r.brand(), r.model(), brands);
                        if (pid != null) {
                            k += jdbc.update("UPDATE `" + e.table() + "` SET product_id = :pid WHERE id = :id AND product_id IS NULL",
                                    new MapSqlParameterSource().addValue("pid", pid).addValue("id", r.id()));
                        }
                    }
                    return k;
                });
                n += done == null ? 0 : done;
            } catch (RuntimeException ex) {
                log.warn("单据明细按型号匹配商品失败，下次启动重试，table={}, lastId={}", e.table(), lastId, ex);
            }
        }
    }

    private record Row(long id, int tenantId, String brand, String model) {
    }

    /** 品牌与型号 → 商品：已建档优先，其次候选；都没有且像型号的进池 */
    private Long resolve(int tenantId, String brandText, String model, Map<String, PriceKeys.BrandRef> brands) {
        String key = MpnNormalizer.normalize(model == null ? "" : model);
        if (key.isEmpty()) {
            return null;
        }
        String text = brandText == null ? "" : brandText.trim();
        PriceKeys.BrandRef ref = brands.computeIfAbsent(text.toLowerCase(Locale.ROOT), k -> priceKeys.brand(text));
        ProductDO hit = productMapper.selectOne(new LambdaQueryWrapper<ProductDO>()
                .select(ProductDO::getId, ProductDO::getMergedIntoId)
                .eq(ProductDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .eq(ref.brandId() != null, ProductDO::getBrandId, ref.brandId())
                .eq(ref.brandId() == null, ProductDO::getBrandKey, ref.brandKey())
                .eq(ProductDO::getMpnNormalized, key)
                .in(ProductDO::getArchiveStatus, ProductConstants.ARCHIVE_DONE, ProductConstants.ARCHIVE_CANDIDATE)
                .isNull(ProductDO::getDeletedAt)
                .orderByDesc(ProductDO::getArchiveStatus)
                .last("LIMIT 1"));
        if (hit != null) {
            return hit.getId();
        }
        if (text.isEmpty()) {
            // 单据上没写品牌：商品库里这个型号只有一个已建档商品时直接用它，有多个时不猜
            List<ProductDO> same = productMapper.selectList(new LambdaQueryWrapper<ProductDO>()
                    .select(ProductDO::getId)
                    .eq(ProductDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                    .eq(ProductDO::getMpnNormalized, key)
                    .eq(ProductDO::getArchiveStatus, ProductConstants.ARCHIVE_DONE)
                    .isNull(ProductDO::getDeletedAt)
                    .last("LIMIT 2"));
            if (same.size() == 1) {
                return same.get(0).getId();
            }
        }
        if (!ModelRules.looksLikeModel(model)) {
            return null;
        }
        return archiver.historySource(tenantId, ref.brandId(), ref.brandKey(), text, model.trim(), HISTORY_NOTE);
    }

    // ---------------------------------------------------------------- 待关联清单

    /** 当前公司（平台账号为全部）还没关联商品的单据明细，按「品牌 + 归一化型号」归并，最近出现的在前 */
    public PageResult<UnlinkedGroupVO> unlinked(String keyword, String table, int page, int pageSize) {
        Map<String, UnlinkedGroupVO> groups = new LinkedHashMap<>();
        String kw = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        String kwKey = MpnNormalizer.normalize(kw);
        for (DocumentProductTables.Entry e : DocumentProductTables.ENTRIES) {
            if (e.table().equals("inquiry_item") || StringUtils.hasText(table) && !e.table().equals(table)) {
                continue;
            }
            for (Line l : lines(e)) {
                String key = groupKey(l.brand(), l.model());
                if (key == null) {
                    continue;
                }
                if (!kw.isEmpty() && !(MpnNormalizer.normalize(nz(l.model())).contains(kwKey.isEmpty() ? kw : kwKey)
                        || nz(l.brand()).toLowerCase(Locale.ROOT).contains(kw))) {
                    continue;
                }
                UnlinkedGroupVO g = groups.computeIfAbsent(key, k -> {
                    UnlinkedGroupVO v = new UnlinkedGroupVO();
                    v.setKey(k);
                    v.setBrand(nz(l.brand()).trim());
                    v.setModel(nz(l.model()).trim());
                    v.setDocs(new LinkedHashMap<>());
                    return v;
                });
                g.getDocs().merge(e.label(), 1, Integer::sum);
                g.setLineCount(g.getLineCount() + 1);
                if (g.getLastSeenAt() == null || l.time() != null && l.time().isAfter(g.getLastSeenAt())) {
                    g.setLastSeenAt(l.time());
                }
            }
        }
        List<UnlinkedGroupVO> all = new ArrayList<>(groups.values());
        all.sort(Comparator.comparing(UnlinkedGroupVO::getLastSeenAt, Comparator.nullsLast(Comparator.reverseOrder())));
        int size = pageSize <= 0 ? 20 : Math.min(pageSize, 100);
        int from = Math.max(0, (Math.max(page, 1) - 1) * size);
        List<UnlinkedGroupVO> slice = from >= all.size() ? List.of() : all.subList(from, Math.min(all.size(), from + size));
        suggest(slice);
        return PageResult.of((long) all.size(), slice);
    }

    /** 为一组明细选择商品（已建档或候选），批量关联；返回关联的条数 */
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public int link(String key, Long productId) {
        ProductOptionVO p = productId == null ? null : lookup.selectable(productId);
        if (p == null) {
            throw new BizException("请选择商品");
        }
        int n = apply(key, productId);
        logService.recordOperateLog(CandidateConstants.MENU, "待关联单据关联商品", null,
                Map.of("key", key, "productId", productId, "lines", n));
        return n;
    }

    /** 填写真实型号：按候选池规则进池（或匹配已有商品）后关联这组明细；返回关联的条数 */
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public int fillModel(String key, String brand, String model) {
        if (!StringUtils.hasText(model)) {
            throw new BizException("请填写真实型号");
        }
        if (!ModelRules.looksLikeModel(model.trim())) {
            throw new BizException(model.trim() + " 不像型号，请填写真实型号");
        }
        Integer tenant = TenantContext.getTenantId();
        Long pid = resolve(tenant == null ? 0 : tenant, brand, model.trim(), new HashMap<>());
        if (pid == null) {
            throw new BizException("这个型号的候选已被驳回，请换一个型号");
        }
        int n = apply(key, pid);
        logService.recordOperateLog(CandidateConstants.MENU, "待关联单据填写真实型号", null,
                Map.of("key", key, "brand", nz(brand), "model", model.trim(), "productId", pid, "lines", n));
        return n;
    }

    private int apply(String key, Long productId) {
        int n = 0;
        for (DocumentProductTables.Entry e : DocumentProductTables.ENTRIES) {
            if (e.table().equals("inquiry_item")) {
                continue;
            }
            List<Long> ids = lines(e).stream().filter(l -> key.equals(groupKey(l.brand(), l.model()))).map(Line::id).toList();
            if (ids.isEmpty()) {
                continue;
            }
            n += jdbc.update("UPDATE `" + e.table() + "` SET product_id = :pid WHERE id IN (:ids) AND product_id IS NULL",
                    new MapSqlParameterSource().addValue("pid", productId).addValue("ids", ids));
        }
        if (n == 0) {
            throw new BizException("这组单据已关联，请刷新");
        }
        return n;
    }

    private record Line(long id, String brand, String model, LocalDateTime time) {
    }

    /** 某表当前公司还没关联商品的明细（询盘型号不在这里，由询盘里「回填真实型号」处理） */
    private List<Line> lines(DocumentProductTables.Entry e) {
        boolean all = platformScopeGuard.isPlatform();
        Integer tenant = TenantContext.getTenantId();
        String sql = "SELECT id, brand, model, create_time FROM `" + e.table() + "` WHERE product_id IS NULL AND deleted_at IS NULL"
                + (all ? "" : " AND tenant_id = :tenant");
        return jdbc.query(sql, new MapSqlParameterSource("tenant", tenant == null ? -1 : tenant),
                (rs, i) -> new Line(rs.getLong("id"), rs.getString("brand"), rs.getString("model"),
                        rs.getTimestamp("create_time") == null ? null : rs.getTimestamp("create_time").toLocalDateTime()));
    }

    /** 「可能是」：同归一化型号的已建档或候选商品（当前公司可选的） */
    private void suggest(List<UnlinkedGroupVO> groups) {
        for (UnlinkedGroupVO g : groups) {
            String key = MpnNormalizer.normalize(g.getModel());
            if (key.isEmpty()) {
                continue;
            }
            List<ProductOptionVO> hits = lookup.search(g.getModel(), 5, null).stream()
                    .filter(o -> o.getMpnDisplay() != null && MpnNormalizer.normalize(o.getMpnDisplay()).equals(key)).toList();
            if (hits.isEmpty()) {
                hits = lookup.search(g.getModel(), 1, null);
            }
            if (!hits.isEmpty()) {
                g.setSuggestion(hits.get(0));
            }
        }
    }

    /** 归并键：品牌原文（小写去空格）| 归一化型号；型号归一化后为空的不列入 */
    static String groupKey(String brand, String model) {
        String key = MpnNormalizer.normalize(model == null ? "" : model);
        if (key.isEmpty()) {
            return null;
        }
        return nz(brand).trim().toLowerCase(Locale.ROOT) + "|" + key;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
