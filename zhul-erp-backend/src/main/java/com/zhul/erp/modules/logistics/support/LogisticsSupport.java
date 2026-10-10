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

    /** 启用的服务商（货代、快递公司） */
    public List<ForwarderVO> forwarders() {
        return supplierMapper.selectList(new LambdaQueryWrapper<SupplierDO>()
                        .eq(SupplierDO::getTenantId, PiStore.tenantId())
                        .eq(SupplierDO::getSupplierType, LogisticsConstants.SUPPLIER_SERVICE)
                        .eq(SupplierDO::getStatus, 1)
                        .orderByAsc(SupplierDO::getName))
                .stream().map(s -> {
                    ForwarderVO vo = new ForwarderVO();
                    vo.setId(s.getId());
                    vo.setName(s.getName());
                    vo.setVolumeDivisor(s.getVolumeDivisor());
                    return vo;
                }).toList();
    }

    /** 货代须是本租户启用的服务商 */
    public SupplierDO requireForwarder(Long id) {
        SupplierDO s = id == null ? null : supplierMapper.selectById(id);
        if (s == null || !Objects.equals(s.getTenantId(), PiStore.tenantId()) || !Objects.equals(s.getSupplierType(), LogisticsConstants.SUPPLIER_SERVICE)
                || !Objects.equals(s.getStatus(), 1)) {
            throw new BizException("请选择启用的货代（供应商类型为「服务商」）");
        }
        return s;
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

    public CustomerDO customer(Long id) {
        return lookups.customers(List.of(id)).get(id);
    }

    public Map<Long, String> userNames(Collection<Long> ids) {
        return lookups.userNames(ids.stream().filter(Objects::nonNull).toList());
    }
}
