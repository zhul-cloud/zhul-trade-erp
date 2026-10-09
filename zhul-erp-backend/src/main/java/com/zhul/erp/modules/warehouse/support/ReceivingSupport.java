package com.zhul.erp.modules.warehouse.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.security.DataScope;
import com.zhul.erp.framework.security.DataScopeResolver;
import com.zhul.erp.modules.masterdata.entity.SupplierDO;
import com.zhul.erp.modules.masterdata.repository.SupplierMapper;
import com.zhul.erp.modules.purchase.entity.PurchaseOrderDO;
import com.zhul.erp.modules.purchase.entity.PurchaseOrderItemDO;
import com.zhul.erp.modules.purchase.entity.PurchaseRequirementDO;
import com.zhul.erp.modules.purchase.repository.PurchaseOrderItemMapper;
import com.zhul.erp.modules.purchase.repository.PurchaseOrderMapper;
import com.zhul.erp.modules.purchase.repository.PurchaseRequirementMapper;
import com.zhul.erp.modules.purchase.support.Counterparty;
import com.zhul.erp.modules.sales.support.PiStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 到货与入库共用的采购单读取：采购员侧按数据权限（负责人为采购单的采购员），仓库侧只看本租户。
 */
@Component
@RequiredArgsConstructor
public class ReceivingSupport {

    private static final int KEYWORD_LIMIT = 500;

    private final PurchaseOrderMapper orderMapper;
    private final PurchaseOrderItemMapper itemMapper;
    private final PurchaseRequirementMapper requirementMapper;
    private final SupplierMapper supplierMapper;
    private final DataScopeResolver dataScopeResolver;

    public DataScope scope() {
        return dataScopeResolver.current();
    }

    /** scoped 为 true 时按数据权限校验 */
    public PurchaseOrderDO po(Long poId, boolean scoped) {
        PurchaseOrderDO p = poId == null ? null : orderMapper.selectById(poId);
        if (p == null || p.getDeletedAt() != null || !Objects.equals(p.getTenantId(), PiStore.tenantId())
                || (scoped && !scope().canSee(p.getPurchaserId()))) {
            throw new BizException("采购单不存在");
        }
        return p;
    }

    /** 锁住采购单再读（同一采购单的发货、入库、差异处理串行） */
    public PurchaseOrderDO lockPo(Long poId, boolean scoped) {
        po(poId, scoped);
        orderMapper.lockById(poId);
        return po(poId, scoped);
    }

    public Map<Long, PurchaseOrderDO> pos(Collection<Long> ids) {
        return byId(ids, orderMapper::selectBatchIds, PurchaseOrderDO::getId);
    }

    public List<PurchaseOrderItemDO> poItems(Long poId) {
        return itemMapper.selectList(new LambdaQueryWrapper<PurchaseOrderItemDO>()
                .eq(PurchaseOrderItemDO::getPoId, poId)
                .isNull(PurchaseOrderItemDO::getDeletedAt)
                .orderByAsc(PurchaseOrderItemDO::getSortOrder, PurchaseOrderItemDO::getId));
    }

    public Map<Long, PurchaseOrderItemDO> poItemsById(Collection<Long> ids) {
        return byId(ids, itemMapper::selectBatchIds, PurchaseOrderItemDO::getId);
    }

    /** 需求 ID → 品类 */
    public Map<Long, String> categories(Collection<Long> requirementIds) {
        Map<Long, String> out = new HashMap<>();
        byId(requirementIds, requirementMapper::selectBatchIds, PurchaseRequirementDO::getId)
                .forEach((k, v) -> out.put(k, v.getCategory() == null ? "" : v.getCategory()));
        return out;
    }

    /** 采购单 ID → 采购对象（老供应商名称，或「平台 · 店铺名」） */
    public Map<Long, String> counterparties(Collection<PurchaseOrderDO> pos) {
        Map<Long, String> suppliers = new HashMap<>();
        List<Long> supplierIds = pos.stream().map(PurchaseOrderDO::getSupplierId).filter(x -> x != null && x > 0).distinct().toList();
        if (!supplierIds.isEmpty()) {
            supplierMapper.selectBatchIds(supplierIds).forEach(s -> suppliers.put(s.getId(), s.getName()));
        }
        Map<Long, String> out = new HashMap<>();
        for (PurchaseOrderDO p : pos) {
            Counterparty cp = Counterparty.of(p);
            out.put(p.getId(), cp.isShop() ? cp.shopTitle() : suppliers.get(p.getSupplierId()));
        }
        return out;
    }

    /** 采购单号、店铺名、供应商名、型号匹配关键字的采购单 */
    public Set<Long> poIdsByKeyword(String kw) {
        int tenant = PiStore.tenantId();
        Set<Long> ids = new LinkedHashSet<>();
        List<Long> supplierIds = supplierMapper.selectList(new LambdaQueryWrapper<SupplierDO>()
                        .select(SupplierDO::getId)
                        .eq(SupplierDO::getTenantId, tenant)
                        .and(x -> x.like(SupplierDO::getName, kw).or().like(SupplierDO::getShortName, kw))
                        .last("LIMIT " + KEYWORD_LIMIT))
                .stream().map(SupplierDO::getId).toList();
        orderMapper.selectList(new LambdaQueryWrapper<PurchaseOrderDO>()
                        .select(PurchaseOrderDO::getId)
                        .eq(PurchaseOrderDO::getTenantId, tenant)
                        .isNull(PurchaseOrderDO::getDeletedAt)
                        .and(x -> {
                            x.like(PurchaseOrderDO::getPoNo, kw).or().like(PurchaseOrderDO::getShopName, kw);
                            if (!supplierIds.isEmpty()) {
                                x.or().in(PurchaseOrderDO::getSupplierId, supplierIds);
                            }
                        })
                        .last("LIMIT " + KEYWORD_LIMIT))
                .forEach(p -> ids.add(p.getId()));
        itemMapper.selectList(new LambdaQueryWrapper<PurchaseOrderItemDO>()
                        .select(PurchaseOrderItemDO::getPoId)
                        .eq(PurchaseOrderItemDO::getTenantId, tenant)
                        .isNull(PurchaseOrderItemDO::getDeletedAt)
                        .like(PurchaseOrderItemDO::getModel, kw)
                        .last("LIMIT " + KEYWORD_LIMIT))
                .forEach(i -> ids.add(i.getPoId()));
        return ids;
    }

    /** 数据权限内的采购单子查询（只含数字 ID，可直接拼进 SQL）；全部权限返回 null */
    public String scopedPoSql() {
        DataScope s = scope();
        if (s.isAll()) {
            return null;
        }
        if (s.ownerIds().isEmpty()) {
            return "SELECT 0";
        }
        return "SELECT id FROM purchase_order WHERE purchaser_id IN ("
                + s.ownerIds().stream().map(String::valueOf).collect(Collectors.joining(",")) + ")";
    }

    public static <T> Map<Long, T> byId(Collection<Long> ids, Function<List<Long>, List<T>> loader, Function<T, Long> idOf) {
        List<Long> keys = ids == null ? List.of() : ids.stream().filter(Objects::nonNull).distinct().toList();
        Map<Long, T> out = new HashMap<>(keys.size() * 2 + 1);
        if (!keys.isEmpty()) {
            loader.apply(keys).forEach(x -> out.put(idOf.apply(x), x));
        }
        return out;
    }

    /** 素材键：品牌 + 型号，去首尾空格、小写 */
    public static String assetKey(String brand, String model) {
        return ((brand == null ? "" : brand.trim()) + " " + (model == null ? "" : model.trim())).toLowerCase(java.util.Locale.ROOT);
    }

    public static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    public static int nz(Integer v) {
        return v == null ? 0 : v;
    }
}
