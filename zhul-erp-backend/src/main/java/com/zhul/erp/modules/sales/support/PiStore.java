package com.zhul.erp.modules.sales.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.security.DataScopeResolver;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.sales.constants.SalesConstants;
import com.zhul.erp.modules.sales.entity.PiFeeDO;
import com.zhul.erp.modules.sales.entity.PiItemDO;
import com.zhul.erp.modules.sales.entity.PiVersionDO;
import com.zhul.erp.modules.sales.entity.ProformaInvoiceDO;
import com.zhul.erp.modules.sales.entity.SalesOrderDO;
import com.zhul.erp.modules.sales.repository.PiFeeMapper;
import com.zhul.erp.modules.sales.repository.PiItemMapper;
import com.zhul.erp.modules.sales.repository.PiVersionMapper;
import com.zhul.erp.modules.sales.repository.ProformaInvoiceMapper;
import com.zhul.erp.modules.sales.repository.SalesOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.List;

/** PI 读取：数据范围、行锁、版本与行 */
@Component
@RequiredArgsConstructor
public class PiStore {

    private final ProformaInvoiceMapper piMapper;
    private final PiVersionMapper versionMapper;
    private final PiItemMapper itemMapper;
    private final PiFeeMapper feeMapper;
    private final SalesOrderMapper orderMapper;
    private final DataScopeResolver dataScopeResolver;
    private final ObjectMapper objectMapper;

    /** 当前用户数据范围内的 PI（按创建人） */
    public ProformaInvoiceDO visible(Long id) {
        ProformaInvoiceDO pi = id == null ? null : piMapper.selectById(id);
        if (pi == null || pi.getDeletedAt() != null || !Objects.equals(pi.getTenantId(), tenantId())
                || !dataScopeResolver.current().canSee(pi.getOwnerId())) {
            throw new BizException("PI 不存在");
        }
        return pi;
    }

    /** 锁住 PI 行后再读 */
    public ProformaInvoiceDO lockVisible(Long id) {
        visible(id);
        piMapper.lockById(id);
        return visible(id);
    }

    public PiVersionDO version(Long piId, Integer versionNo) {
        if (versionNo == null) {
            return null;
        }
        return versionMapper.selectOne(new LambdaQueryWrapper<PiVersionDO>()
                .eq(PiVersionDO::getPiId, piId)
                .eq(PiVersionDO::getVersionNo, versionNo)
                .isNull(PiVersionDO::getDeletedAt));
    }

    /** 正在编辑的版本；没有时提示原因 */
    public PiVersionDO requireEditing(ProformaInvoiceDO pi) {
        if (pi.getStatus() == SalesConstants.PI_CONVERTED) {
            throw new BizException(convertedMessage(pi));
        }
        if (pi.getStatus() == SalesConstants.PI_VOID) {
            throw new BizException("PI 已作废，不能修改");
        }
        PiVersionDO v = version(pi.getId(), pi.getEditingVersionNo());
        if (v == null) {
            throw new BizException("PI 已发送，需要修改请先点「修改」生成新版本");
        }
        return v;
    }

    public String convertedMessage(ProformaInvoiceDO pi) {
        SalesOrderDO order = activeOrder(pi.getId());
        return "PI 已转成订单 " + (order == null ? "" : order.getSoNo()) + "，需要修改请先取消订单";
    }

    public SalesOrderDO activeOrder(Long piId) {
        return orderMapper.selectOne(new LambdaQueryWrapper<SalesOrderDO>()
                .eq(SalesOrderDO::getPiId, piId)
                .eq(SalesOrderDO::getStatus, SalesConstants.SO_ACTIVE)
                .isNull(SalesOrderDO::getDeletedAt)
                .last("LIMIT 1"));
    }

    public List<PiVersionDO> versions(Long piId) {
        return versionMapper.selectList(new LambdaQueryWrapper<PiVersionDO>()
                .eq(PiVersionDO::getPiId, piId)
                .isNull(PiVersionDO::getDeletedAt)
                .orderByAsc(PiVersionDO::getVersionNo));
    }

    public List<PiItemDO> items(Long versionId) {
        return itemMapper.selectList(new LambdaQueryWrapper<PiItemDO>()
                .eq(PiItemDO::getVersionId, versionId)
                .isNull(PiItemDO::getDeletedAt)
                .orderByAsc(PiItemDO::getLineNo)
                .orderByAsc(PiItemDO::getId));
    }

    public List<PiFeeDO> fees(Long versionId) {
        return feeMapper.selectList(new LambdaQueryWrapper<PiFeeDO>()
                .eq(PiFeeDO::getVersionId, versionId)
                .isNull(PiFeeDO::getDeletedAt)
                .orderByAsc(PiFeeDO::getSortOrder)
                .orderByAsc(PiFeeDO::getId));
    }

    public String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("PI 快照序列化失败", e);
        }
    }

    public <T> T fromJson(String json, Class<T> type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("PI 快照解析失败", e);
        }
    }

    public static int tenantId() {
        Integer t = TenantContext.getTenantId();
        return t == null ? 0 : t;
    }
}
