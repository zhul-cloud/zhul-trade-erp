package com.zhul.erp.modules.product.merge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.product.constants.ProductConstants;
import com.zhul.erp.modules.product.entity.ProductDO;
import com.zhul.erp.modules.product.merge.entity.ProductMergeDO;
import com.zhul.erp.modules.product.merge.repository.ProductMergeMapper;
import com.zhul.erp.modules.product.repository.ProductMapper;
import com.zhul.erp.modules.product.support.DocumentProductTables;
import com.zhul.erp.modules.product.support.ProductOrderCodes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 商品合并与单据引用统计（spec product/product「商品合并」、product/product-candidate「候选审核」）。
 * 单据明细表按 {@link DocumentProductTables} 登记的清单处理。商品是平台共享数据，合并要把各家公司单据上的引用一起改掉，
 * 所以这里的 SQL 有意不带租户条件（JdbcTemplate 不经租户拦截器），只按 product_id 定位。
 * 须在调用方事务内执行。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductMergeService {

    private final NamedParameterJdbcTemplate jdbc;
    private final ProductMapper productMapper;
    private final ProductMergeMapper mergeMapper;
    private final ProductOrderCodes orderCodes;
    private final CurrentUserResolver currentUser;
    private final ObjectMapper objectMapper;

    /** 一个商品被各类单据明细引用的条数（按登记顺序，只列大于 0 的），键为单据类型名称 */
    public Map<String, Long> references(Long productId) {
        return references(List.of(productId)).getOrDefault(productId, Map.of());
    }

    /** 批量统计：商品 ID → (单据类型名称 → 条数) */
    public Map<Long, Map<String, Long>> references(Collection<Long> productIds) {
        List<Long> ids = productIds.stream().filter(Objects::nonNull).distinct().toList();
        Map<Long, Map<String, Long>> out = new HashMap<>();
        if (ids.isEmpty()) {
            return out;
        }
        for (DocumentProductTables.Entry e : DocumentProductTables.ENTRIES) {
            jdbc.query("SELECT product_id, COUNT(*) AS n FROM `" + e.table() + "` WHERE product_id IN (:ids) AND deleted_at IS NULL"
                            + " GROUP BY product_id", new MapSqlParameterSource("ids", ids),
                    rs -> {
                        out.computeIfAbsent(rs.getLong("product_id"), k -> new LinkedHashMap<>())
                                .put(e.label(), rs.getLong("n"));
                    });
        }
        return out;
    }

    /** 是否已被销售订单及之后的单据引用（这时候选不能驳回） */
    public boolean usedByOrders(Long productId) {
        for (DocumentProductTables.Entry e : DocumentProductTables.ENTRIES) {
            if (!e.afterOrder()) {
                continue;
            }
            Long n = jdbc.queryForObject("SELECT COUNT(*) FROM `" + e.table() + "` WHERE product_id = :id AND deleted_at IS NULL",
                    new MapSqlParameterSource("id", productId), Long.class);
            if (n != null && n > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * 把 fromId 并入 toId：各明细表改指向主商品，原商品标已并入（品牌键改为 ~ID 让出唯一键），订货号补到主商品，
     * 写合并记录。主商品必须已建档，原商品必须是候选；合并不能撤销。返回各单据类型改指向的条数。
     */
    public Map<String, Long> merge(Long fromId, Long toId, String reason) {
        if (fromId == null || toId == null || fromId.equals(toId)) {
            throw new BizException("请选择要并入的商品");
        }
        // 固定按 ID 顺序加锁，避免两个合并互相等待
        ProductDO first = lock(Math.min(fromId, toId));
        ProductDO second = lock(Math.max(fromId, toId));
        ProductDO from = first.getId().equals(fromId) ? first : second;
        ProductDO to = first.getId().equals(toId) ? first : second;
        if (to.getDeletedAt() != null || !Objects.equals(to.getArchiveStatus(), ProductConstants.ARCHIVE_DONE)) {
            throw new BizException("只能并入已建档的商品");
        }
        if (!Objects.equals(from.getArchiveStatus(), ProductConstants.ARCHIVE_CANDIDATE)) {
            throw new BizException("只有候选商品可以并入其他商品");
        }
        Map<String, Long> affected = new LinkedHashMap<>();
        Map<String, Integer> byTable = new LinkedHashMap<>();
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("from", fromId).addValue("to", toId);
        for (DocumentProductTables.Entry e : DocumentProductTables.ENTRIES) {
            int n = jdbc.update("UPDATE `" + e.table() + "` SET product_id = :to WHERE product_id = :from", p);
            if (n > 0) {
                affected.merge(e.label(), (long) n, Long::sum);
                byTable.put(e.table(), n);
            }
        }
        orderCodes.addAll(to.getId(), to.getBrandId(), orderCodes.codesOf(fromId));
        productMapper.update(null, new LambdaUpdateWrapper<ProductDO>()
                .eq(ProductDO::getId, fromId)
                .set(ProductDO::getArchiveStatus, ProductConstants.ARCHIVE_MERGED)
                .set(ProductDO::getMergedIntoId, toId)
                .set(ProductDO::getBrandKey, "~" + fromId)
                .set(ProductDO::getUpdateTime, LocalDateTime.now()));
        ProductMergeDO m = new ProductMergeDO();
        m.setTenantId(ProductConstants.PLATFORM_TENANT_ID);
        m.setFromProductId(fromId);
        m.setToProductId(toId);
        m.setFromLabel(cut(label(from), 200));
        m.setAffected(json(byTable));
        m.setReason(cut(reason == null ? "" : reason.trim(), 200));
        Long operator = currentUser.resolve();
        m.setOperatorId(operator == null ? "sys" : String.valueOf(operator));
        Integer tenant = TenantContext.getTenantId();
        m.setOperatorTenantId(tenant == null ? 0 : tenant);
        mergeMapper.insert(m);
        log.info("商品合并，fromId={}, toId={}, affected={}", fromId, toId, byTable);
        return affected;
    }

    /** 已并入的商品换成主商品，其余原样返回 */
    public Map<Long, Long> effective(Collection<Long> productIds) {
        List<Long> ids = productIds.stream().filter(Objects::nonNull).distinct().toList();
        Map<Long, Long> out = new HashMap<>();
        if (ids.isEmpty()) {
            return out;
        }
        productMapper.selectList(new LambdaQueryWrapper<ProductDO>()
                        .select(ProductDO::getId, ProductDO::getMergedIntoId)
                        .in(ProductDO::getId, ids))
                .forEach(x -> out.put(x.getId(), x.getMergedIntoId() != null ? x.getMergedIntoId() : x.getId()));
        return out;
    }

    private ProductDO lock(Long id) {
        ProductDO p = productMapper.selectOne(new LambdaQueryWrapper<ProductDO>().eq(ProductDO::getId, id).last("FOR UPDATE"));
        if (p == null) {
            throw new BizException("商品不存在");
        }
        return p;
    }

    private String json(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("合并记录序列化失败", e);
        }
    }

    private static String label(ProductDO p) {
        return (p.getBrandKey() == null ? "" : p.getBrandKey() + " / ") + p.getMpnRaw();
    }

    private static String cut(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }
}
