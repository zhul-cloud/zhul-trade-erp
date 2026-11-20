package com.zhul.erp.modules.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.inventory.constants.InvConstants;
import com.zhul.erp.modules.inventory.entity.InvBatchDO;
import com.zhul.erp.modules.inventory.entity.InvWarehouseDO;
import com.zhul.erp.modules.inventory.repository.InvBatchMapper;
import com.zhul.erp.modules.inventory.repository.InvWarehouseMapper;
import com.zhul.erp.modules.sales.support.PiStore;
import com.zhul.erp.modules.system.entity.SysConfigDO;
import com.zhul.erp.modules.system.repository.SysConfigMapper;
import com.zhul.erp.modules.masterdata.entity.SupplierDO;
import com.zhul.erp.modules.masterdata.repository.SupplierMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 仓库、库存模式与批次（openspec inventory-foundation 决策 1）。
 * 自有仓每个租户一个，第一次用到时建（名称「公司仓库」）；货代仓每家货代一个，新建货代时或第一次用到时建，
 * 名称与状态显示时跟着货代走。仓库编码 WH + 4 位序号，租户内递增；建仓前锁住本租户的仓库行，防止并发重复建。
 */
@Service
@RequiredArgsConstructor
public class InventoryWarehouses {

    private final InvWarehouseMapper warehouseMapper;
    private final InvBatchMapper batchMapper;
    private final SysConfigMapper sysConfigMapper;
    private final SupplierMapper supplierMapper;

    /** 本租户的自有仓，没有就建 */
    public InvWarehouseDO own() {
        InvWarehouseDO w = findOwn();
        if (w != null) {
            return w;
        }
        lockTenantWarehouses();
        w = findOwn();
        return w != null ? w : create(InvConstants.WH_OWN, InvConstants.OWN_WAREHOUSE_NAME, null);
    }

    /** 某家货代的货代仓，没有就建 */
    public InvWarehouseDO forwarder(Long forwarderId) {
        if (forwarderId == null) {
            throw new BizException("没有指定货代");
        }
        InvWarehouseDO w = findForwarder(forwarderId);
        if (w != null) {
            return w;
        }
        SupplierDO s = supplierMapper.selectById(forwarderId);
        if (s == null || !Objects.equals(s.getTenantId(), PiStore.tenantId())) {
            throw new BizException("货代不存在");
        }
        lockTenantWarehouses();
        w = findForwarder(forwarderId);
        return w != null ? w : create(InvConstants.WH_FORWARDER, s.getName(), forwarderId);
    }

    public InvWarehouseDO require(Long id) {
        InvWarehouseDO w = id == null ? null : warehouseMapper.selectById(id);
        if (w == null || w.getDeletedAt() != null || !Objects.equals(w.getTenantId(), PiStore.tenantId())) {
            throw new BizException("仓库不存在");
        }
        return w;
    }

    public List<InvWarehouseDO> all() {
        return warehouseMapper.selectList(new LambdaQueryWrapper<InvWarehouseDO>()
                .eq(InvWarehouseDO::getTenantId, PiStore.tenantId())
                .isNull(InvWarehouseDO::getDeletedAt)
                .orderByAsc(InvWarehouseDO::getCode));
    }

    // ---------------------------------------------------------------- 库存模式

    /** 本租户的库存模式：没有设置按无货源 */
    public String mode() {
        List<SysConfigDO> rows = sysConfigMapper.selectList(new LambdaQueryWrapper<SysConfigDO>()
                .eq(SysConfigDO::getConfigKey, InvConstants.CONFIG_MODE)
                .in(SysConfigDO::getTenantId, List.of(0, PiStore.tenantId()))
                .isNull(SysConfigDO::getDeletedAt));
        return rows.stream().max(Comparator.comparing(SysConfigDO::getTenantId))
                .map(c -> InvConstants.MODE_STOCK.equals(c.getConfigValue()) ? InvConstants.MODE_STOCK : InvConstants.MODE_DROPSHIP)
                .orElse(InvConstants.MODE_DROPSHIP);
    }

    public boolean dropship() {
        return InvConstants.MODE_DROPSHIP.equals(mode());
    }

    /** 写本租户的配置值（没有就新增） */
    public void saveConfig(String key, String name, String value) {
        int tenantId = PiStore.tenantId();
        SysConfigDO c = sysConfigMapper.selectOne(new LambdaQueryWrapper<SysConfigDO>()
                .eq(SysConfigDO::getTenantId, tenantId)
                .eq(SysConfigDO::getConfigKey, key)
                .isNull(SysConfigDO::getDeletedAt)
                .last("LIMIT 1"));
        if (c == null) {
            c = new SysConfigDO();
            c.setTenantId(tenantId);
            c.setConfigKey(key);
            c.setConfigName(name);
            c.setConfigValue(value);
            c.setConfigType("STRING");
            c.setIsBuiltin(0);
            c.setIsEncrypted(0);
            c.setConfigGroup("库存");
            c.setRemark("");
            sysConfigMapper.insert(c);
        } else {
            c.setConfigValue(value);
            sysConfigMapper.updateById(c);
        }
    }

    public String config(String key) {
        SysConfigDO c = sysConfigMapper.selectOne(new LambdaQueryWrapper<SysConfigDO>()
                .eq(SysConfigDO::getTenantId, PiStore.tenantId())
                .eq(SysConfigDO::getConfigKey, key)
                .isNull(SysConfigDO::getDeletedAt)
                .last("LIMIT 1"));
        return c == null ? null : c.getConfigValue();
    }

    // ---------------------------------------------------------------- 批次

    /** 批次单价：原币、币种、汇率；cny 为空时 = 原币 × 汇率（HALF_UP 6 位），采购入库直接取采购订单行的人民币不含税单价 */
    public record Price(String currency, BigDecimal unitPrice, BigDecimal rate, BigDecimal cny) {
        public static final Price ZERO = new Price("CNY", BigDecimal.ZERO, BigDecimal.ONE, null);

        public Price(String currency, BigDecimal unitPrice, BigDecimal rate) {
            this(currency, unitPrice, rate, null);
        }

        public static Price cny(BigDecimal p) {
            return new Price("CNY", p == null ? BigDecimal.ZERO : p, BigDecimal.ONE, null);
        }

        BigDecimal cnyValue() {
            if (cny != null) {
                return cny.setScale(6, RoundingMode.HALF_UP);
            }
            BigDecimal r = rate == null ? BigDecimal.ONE : rate;
            return (unitPrice == null ? BigDecimal.ZERO : unitPrice).multiply(r).setScale(6, RoundingMode.HALF_UP);
        }
    }

    public InvBatchDO newBatch(String batchNo, Long productId, String grade, int sourceType, Long sourceId, Long sourceItemId,
                               Long poItemId, Long soItemId, LocalDate inDate, Price price) {
        InvBatchDO b = new InvBatchDO();
        b.setTenantId(PiStore.tenantId());
        b.setBatchNo(batchNo);
        b.setProductId(productId);
        b.setGrade(grade == null ? "" : grade);
        b.setSourceType(sourceType);
        b.setSourceId(sourceId);
        b.setSourceItemId(sourceItemId);
        b.setPoItemId(poItemId);
        b.setSoItemId(soItemId);
        b.setInDate(inDate == null ? LocalDate.now() : inDate);
        Price p = price == null ? Price.ZERO : price;
        b.setCurrencyCode(p.currency() == null ? "CNY" : p.currency());
        b.setUnitPrice((p.unitPrice() == null ? BigDecimal.ZERO : p.unitPrice()).setScale(6, RoundingMode.HALF_UP));
        b.setExchangeRate((p.rate() == null ? BigDecimal.ONE : p.rate()).setScale(6, RoundingMode.HALF_UP));
        b.setUnitPriceCny(p.cnyValue());
        batchMapper.insert(b);
        return b;
    }

    /** 更新批次的单价与入库日期（撤回退货入库后再次验收） */
    public void reprice(InvBatchDO b, LocalDate inDate, Price price) {
        b.setInDate(inDate == null ? LocalDate.now() : inDate);
        b.setCurrencyCode(price.currency() == null ? "CNY" : price.currency());
        b.setUnitPrice((price.unitPrice() == null ? BigDecimal.ZERO : price.unitPrice()).setScale(6, RoundingMode.HALF_UP));
        b.setExchangeRate((price.rate() == null ? BigDecimal.ONE : price.rate()).setScale(6, RoundingMode.HALF_UP));
        b.setUnitPriceCny(price.cnyValue());
        batchMapper.updateById(b);
    }

    public List<InvBatchDO> batches(List<Long> ids) {
        return ids.isEmpty() ? List.of() : batchMapper.selectBatchIds(ids);
    }

    public InvBatchDO batchOf(Long batchId) {
        return batchId == null ? null : batchMapper.selectById(batchId);
    }

    /** 某来源单据行已有的批次（采购入库单行、退货入库单行只建一个批次） */
    public InvBatchDO batchOf(int sourceType, Long sourceItemId) {
        return batchMapper.selectOne(new LambdaQueryWrapper<InvBatchDO>()
                .eq(InvBatchDO::getTenantId, PiStore.tenantId())
                .eq(InvBatchDO::getSourceType, sourceType)
                .eq(InvBatchDO::getSourceItemId, sourceItemId)
                .isNull(InvBatchDO::getDeletedAt)
                .last("LIMIT 1"));
    }

    // ---------------------------------------------------------------- 内部

    private InvWarehouseDO findOwn() {
        return warehouseMapper.selectOne(new LambdaQueryWrapper<InvWarehouseDO>()
                .eq(InvWarehouseDO::getTenantId, PiStore.tenantId())
                .eq(InvWarehouseDO::getType, InvConstants.WH_OWN)
                .isNull(InvWarehouseDO::getDeletedAt)
                .orderByAsc(InvWarehouseDO::getId)
                .last("LIMIT 1"));
    }

    private InvWarehouseDO findForwarder(Long forwarderId) {
        return warehouseMapper.selectOne(new LambdaQueryWrapper<InvWarehouseDO>()
                .eq(InvWarehouseDO::getTenantId, PiStore.tenantId())
                .eq(InvWarehouseDO::getType, InvConstants.WH_FORWARDER)
                .eq(InvWarehouseDO::getForwarderId, forwarderId)
                .isNull(InvWarehouseDO::getDeletedAt)
                .last("LIMIT 1"));
    }

    /** 建仓前锁住本租户已有的仓库行；还没有任何仓库时靠编码唯一键兜底 */
    private void lockTenantWarehouses() {
        warehouseMapper.selectList(new LambdaQueryWrapper<InvWarehouseDO>()
                .select(InvWarehouseDO::getId)
                .eq(InvWarehouseDO::getTenantId, PiStore.tenantId())
                .last("FOR UPDATE"));
    }

    private InvWarehouseDO create(int type, String name, Long forwarderId) {
        long count = warehouseMapper.selectCount(new LambdaQueryWrapper<InvWarehouseDO>()
                .eq(InvWarehouseDO::getTenantId, PiStore.tenantId()));
        InvWarehouseDO w = new InvWarehouseDO();
        w.setTenantId(PiStore.tenantId());
        w.setCode(String.format("WH%04d", count + 1));
        w.setName(name == null ? "" : name);
        w.setType(type);
        w.setForwarderId(forwarderId);
        w.setStatus(1);
        warehouseMapper.insert(w);
        return w;
    }
}
