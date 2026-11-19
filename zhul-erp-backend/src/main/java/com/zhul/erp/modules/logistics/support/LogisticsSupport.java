package com.zhul.erp.modules.logistics.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.security.DataScope;
import com.zhul.erp.framework.security.DataScopeResolver;
import com.zhul.erp.modules.inquiry.support.InquiryLookups;
import com.zhul.erp.modules.logistics.constants.LogisticsConstants;
import com.zhul.erp.modules.logistics.dto.ForwarderVO;
import com.zhul.erp.modules.masterdata.entity.CustomerDO;
import com.zhul.erp.modules.masterdata.entity.SupplierDO;
import com.zhul.erp.modules.masterdata.repository.SupplierMapper;
import com.zhul.erp.modules.sales.constants.SalesConstants;
import com.zhul.erp.modules.sales.entity.SalesOrderDO;
import com.zhul.erp.modules.sales.repository.SalesOrderMapper;
import com.zhul.erp.modules.sales.support.PiStore;
import com.zhul.erp.modules.system.migration.entity.MigrationIdMapDO;
import com.zhul.erp.modules.system.migration.repository.MigrationIdMapMapper;
import com.zhul.erp.modules.system.migration.service.MasterDataImporter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** 出库与出运共用：订单（按业务员数据权限）、货代（服务商）、客户名称 */
@Component
@RequiredArgsConstructor
public class LogisticsSupport {

    private final SalesOrderMapper soMapper;
    private final SupplierMapper supplierMapper;
    private final MigrationIdMapMapper idMapMapper;
    private final DataScopeResolver dataScopeResolver;
    private final InquiryLookups lookups;

    public DataScope scope() {
        return dataScopeResolver.current();
    }

    /** 订单；scoped 时按订单所属业务员校验数据权限 */
    public SalesOrderDO order(Long soId, boolean scoped) {
        SalesOrderDO o = soId == null ? null : soMapper.selectById(soId);
        if (o == null || o.getDeletedAt() != null || !Objects.equals(o.getTenantId(), PiStore.tenantId())
                || (scoped && !scope().canSee(o.getOwnerId()))) {
            throw new BizException("销售订单不存在");
        }
        return o;
    }

    public SalesOrderDO activeOrder(Long soId, boolean scoped) {
        SalesOrderDO o = order(soId, scoped);
        if (o.getStatus() != SalesConstants.SO_ACTIVE) {
            throw new BizException("订单已取消，不能发货");
        }
        if (SalesConstants.PROGRESS_COMPLETED.equals(o.getProgressCode())) {
            throw new BizException("订单已完成，不能发货");
        }
        return o;
    }

    public Map<Long, SalesOrderDO> orders(Collection<Long> ids) {
        Map<Long, SalesOrderDO> out = new HashMap<>();
        List<Long> keys = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (!keys.isEmpty()) {
            soMapper.selectBatchIds(keys).forEach(o -> out.put(o.getId(), o));
        }
        return out;
    }

    /** 历史迁移的「未登记货代」占位（只挂历史出运单），不出现在选择和货代管理里 */
    public List<Long> placeholderForwarders() {
        // migration_id_map 不经租户拦截器，手写租户条件
        return idMapMapper.selectList(new LambdaQueryWrapper<MigrationIdMapDO>()
                        .eq(MigrationIdMapDO::getTenantId, PiStore.tenantId())
                        .eq(MigrationIdMapDO::getSourceTable, "suppliers")
                        .eq(MigrationIdMapDO::getSourceCode, MasterDataImporter.FORWARDER_KEY)
                        .eq(MigrationIdMapDO::getTargetTable, "supplier"))
                .stream().map(MigrationIdMapDO::getTargetId).toList();
    }

    /**
     * 可选的货代（spec logistics/forwarder「选择货代的范围与顺序」）：启用的公司货代加这个客户的指定货代，
     * 客户指定的排在前面；不传客户时列出全部启用的货代。不含历史迁移的占位。
     */
    public List<ForwarderVO> forwarders(Long customerId) {
        List<Long> placeholders = placeholderForwarders();
        LambdaQueryWrapper<SupplierDO> w = new LambdaQueryWrapper<SupplierDO>()
                .eq(SupplierDO::getTenantId, PiStore.tenantId())
                .eq(SupplierDO::getSupplierType, LogisticsConstants.SUPPLIER_SERVICE)
                .eq(SupplierDO::getStatus, 1)
                .isNull(SupplierDO::getDeletedAt)
                .notIn(!placeholders.isEmpty(), SupplierDO::getId, placeholders)
                .orderByAsc(SupplierDO::getName);
        if (customerId != null) {
            w.and(x -> x.isNull(SupplierDO::getCustomerId).or().eq(SupplierDO::getCustomerId, customerId));
        }
        return supplierMapper.selectList(w).stream().map(s -> {
                    ForwarderVO vo = new ForwarderVO();
                    vo.setId(s.getId());
                    vo.setName(s.getName());
                    vo.setVolumeDivisor(s.getVolumeDivisor());
                    vo.setContactName(s.getContactName());
                    vo.setCustomerOwned(customerId != null && customerId.equals(s.getCustomerId()));
                    vo.setCustomerId(s.getCustomerId());
                    return vo;
                })
                .sorted(java.util.Comparator.comparing((ForwarderVO v) -> !v.getCustomerOwned()))
                .toList();
    }

    /** 货代须是本租户启用的货代（服务商） */
    public SupplierDO requireForwarder(Long id) {
        SupplierDO s = id == null ? null : supplierMapper.selectById(id);
        if (s == null || s.getDeletedAt() != null || !Objects.equals(s.getTenantId(), PiStore.tenantId())
                || !Objects.equals(s.getSupplierType(), LogisticsConstants.SUPPLIER_SERVICE)
                || !Objects.equals(s.getStatus(), 1)) {
            throw new BizException("请选择启用的货代");
        }
        return s;
    }

    /** 同上，且要么是公司货代，要么是这个客户的指定货代（其他客户的指定货代不能用） */
    public SupplierDO requireForwarder(Long id, Long customerId) {
        SupplierDO s = requireForwarder(id);
        if (s.getCustomerId() != null && !s.getCustomerId().equals(customerId)) {
            throw new BizException("「" + s.getName() + "」是其他客户指定的货代，不能用在这个客户的发货上");
        }
        return s;
    }

    /** 货代是否为客户指定货代（归属某个客户）；1-是、0-否 */
    public int customerForwarderFlag(Long forwarderId) {
        SupplierDO s = forwarderId == null ? null : supplierMapper.selectById(forwarderId);
        return s != null && s.getCustomerId() != null ? 1 : 0;
    }

    public BigDecimal divisorOf(Long forwarderId) {
        SupplierDO s = forwarderId == null ? null : supplierMapper.selectById(forwarderId);
        int d = s == null || s.getVolumeDivisor() == null || s.getVolumeDivisor() <= 0 ? 5000 : s.getVolumeDivisor();
        return BigDecimal.valueOf(d);
    }

    public Map<Long, String> supplierNames(Collection<Long> ids) {
        List<Long> keys = ids.stream().filter(x -> x != null && x > 0).distinct().toList();
        if (keys.isEmpty()) {
            return Map.of();
        }
        return supplierMapper.selectBatchIds(keys).stream().collect(Collectors.toMap(SupplierDO::getId, SupplierDO::getName));
    }

    public Map<Long, String> customerNames(Collection<Long> ids) {
        Map<Long, String> out = new HashMap<>();
        lookups.customers(ids.stream().filter(Objects::nonNull).distinct().toList())
                .forEach((k, v) -> out.put(k, InquiryLookups.customerName(v)));
        return out;
    }

    public Map<Long, CustomerDO> customers(Collection<Long> ids) {
        return lookups.customers(ids.stream().filter(Objects::nonNull).distinct().toList());
    }

    public CustomerDO customer(Long id) {
        return lookups.customers(List.of(id)).get(id);
    }

    public Map<Long, String> userNames(Collection<Long> ids) {
        return lookups.userNames(ids.stream().filter(Objects::nonNull).toList());
    }
}
