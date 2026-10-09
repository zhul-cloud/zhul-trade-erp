package com.zhul.erp.modules.warehouse.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.masterdata.entity.SupplierDO;
import com.zhul.erp.modules.masterdata.repository.SupplierMapper;
import com.zhul.erp.modules.purchase.entity.PurchaseOrderDO;
import com.zhul.erp.modules.purchase.support.Counterparty;
import com.zhul.erp.modules.sales.support.PiStore;
import com.zhul.erp.modules.system.entity.SysConfigDO;
import com.zhul.erp.modules.system.repository.SysConfigMapper;
import com.zhul.erp.modules.system.service.LogService;
import com.zhul.erp.modules.warehouse.dto.ArrivalEstimateVO;
import com.zhul.erp.modules.warehouse.dto.SaveTransitTimeRequest;
import com.zhul.erp.modules.warehouse.dto.TransitTimeVO;
import com.zhul.erp.modules.warehouse.entity.TransitTimeDO;
import com.zhul.erp.modules.warehouse.repository.TransitTimeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 快递时效：货从供应商所在省份发到福州仓库的运输天数。
 * 估算顺序：快递公司 + 发货省份 → 快递公司默认 → 默认运输天数（系统参数 transit.default-days）。
 * 租户第一次用到时写入初始规则（之后删光也不再补）。
 */
@Service
@RequiredArgsConstructor
public class TransitTimeService {

    public static final String CONFIG_DEFAULT_DAYS = "transit.default-days";
    private static final int FALLBACK_DAYS = 3;
    private static final String DEST = "福州";
    private static final String[] SUFFIXES = {"维吾尔自治区", "壮族自治区", "回族自治区", "特别行政区", "自治区", "省", "市"};

    /** 初始规则：快递公司、发货省份（空为默认）、天数、备注 */
    private static final List<Object[]> SEED = List.of(
            new Object[] {"顺丰", "", 2, ""}, new Object[] {"京东", "", 2, ""}, new Object[] {"跨越", "", 2, ""},
            new Object[] {"中通", "", 3, ""}, new Object[] {"圆通", "", 3, ""}, new Object[] {"韵达", "", 3, ""},
            new Object[] {"申通", "", 3, ""}, new Object[] {"极兔", "", 3, ""}, new Object[] {"德邦", "", 3, ""},
            new Object[] {"EMS", "", 4, ""}, new Object[] {"供应商送货", "", 1, ""},
            new Object[] {"顺丰", "福建", 1, "省内"}, new Object[] {"京东", "福建", 1, "省内"}, new Object[] {"中通", "福建", 2, "省内"},
            new Object[] {"圆通", "福建", 2, "省内"}, new Object[] {"韵达", "福建", 2, "省内"}, new Object[] {"申通", "福建", 2, "省内"},
            new Object[] {"极兔", "福建", 2, "省内"},
            new Object[] {"顺丰", "新疆", 4, "偏远"}, new Object[] {"顺丰", "西藏", 4, "偏远"},
            new Object[] {"中通", "新疆", 6, "偏远"}, new Object[] {"中通", "西藏", 6, "偏远"},
            new Object[] {"圆通", "新疆", 6, "偏远"}, new Object[] {"圆通", "西藏", 6, "偏远"},
            new Object[] {"韵达", "新疆", 6, "偏远"}, new Object[] {"韵达", "西藏", 6, "偏远"},
            new Object[] {"申通", "新疆", 6, "偏远"}, new Object[] {"申通", "西藏", 6, "偏远"},
            new Object[] {"极兔", "新疆", 6, "偏远"}, new Object[] {"极兔", "西藏", 6, "偏远"},
            new Object[] {"EMS", "新疆", 7, "偏远"}, new Object[] {"EMS", "西藏", 7, "偏远"});

    private final TransitTimeMapper mapper;
    private final SysConfigMapper sysConfigMapper;
    private final SupplierMapper supplierMapper;
    private final LogService logService;

    // ---------------------------------------------------------------- 规则

    public List<TransitTimeVO> list(String carrier, String province) {
        ensureSeeded();
        LambdaQueryWrapper<TransitTimeDO> w = live();
        if (StringUtils.hasText(carrier)) {
            w.eq(TransitTimeDO::getCarrier, carrier.trim());
        }
        if (StringUtils.hasText(province)) {
            w.like(TransitTimeDO::getOriginProvince, province(province));
        }
        // 规则表有自然顺序（同一快递公司的默认在前），按快递公司、发货省份排，便于对照
        return mapper.selectList(w).stream()
                .sorted(Comparator.comparing(TransitTimeDO::getCarrier).thenComparing(TransitTimeDO::getOriginProvince))
                .map(TransitTimeService::toVo).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public TransitTimeVO create(SaveTransitTimeRequest req) {
        ensureSeeded();
        TransitTimeDO t = new TransitTimeDO();
        t.setTenantId(PiStore.tenantId());
        fill(t, req);
        requireUnique(t, null);
        mapper.insert(t);
        logService.recordOperateLog("快递时效", "新增快递时效", null, Map.of("rule", text(t)));
        return toVo(t);
    }

    @Transactional(rollbackFor = Exception.class)
    public TransitTimeVO update(Long id, SaveTransitTimeRequest req) {
        TransitTimeDO t = visible(id);
        String before = text(t);
        fill(t, req);
        requireUnique(t, id);
        mapper.updateById(t);
        logService.recordOperateLog("快递时效", "修改快递时效", Map.of("rule", before), Map.of("rule", text(t)));
        return toVo(t);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        TransitTimeDO t = visible(id);
        t.setDeletedAt(LocalDateTime.now());
        mapper.updateById(t);
        logService.recordOperateLog("快递时效", "删除快递时效", Map.of("rule", text(t)), null);
    }

    public int defaultDays() {
        int tenant = PiStore.tenantId();
        return sysConfigMapper.selectList(new LambdaQueryWrapper<SysConfigDO>()
                        .eq(SysConfigDO::getConfigKey, CONFIG_DEFAULT_DAYS)
                        .in(SysConfigDO::getTenantId, List.of(0, tenant))
                        .isNull(SysConfigDO::getDeletedAt))
                .stream().max(Comparator.comparing(SysConfigDO::getTenantId))
                .map(SysConfigDO::getConfigValue)
                .map(TransitTimeService::parseDays)
                .orElse(FALLBACK_DAYS);
    }

    /** 改默认运输天数：租户第一次改时生成租户自己的设置 */
    @Transactional(rollbackFor = Exception.class)
    public int setDefaultDays(int days) {
        if (days < 1 || days > 30) {
            throw new BizException("运输天数为 1 – 30 天");
        }
        int tenant = PiStore.tenantId();
        int before = defaultDays();
        SysConfigDO own = sysConfigMapper.selectOne(new LambdaQueryWrapper<SysConfigDO>()
                .eq(SysConfigDO::getConfigKey, CONFIG_DEFAULT_DAYS)
                .eq(SysConfigDO::getTenantId, tenant)
                .isNull(SysConfigDO::getDeletedAt)
                .last("LIMIT 1"));
        if (own != null) {
            own.setConfigValue(String.valueOf(days));
            sysConfigMapper.updateById(own);
        } else {
            SysConfigDO row = new SysConfigDO();
            row.setTenantId(tenant);
            row.setConfigKey(CONFIG_DEFAULT_DAYS);
            row.setConfigName("默认运输天数");
            row.setConfigValue(String.valueOf(days));
            row.setConfigType("NUMBER");
            row.setIsBuiltin(1);
            row.setIsEncrypted(0);
            row.setConfigGroup("purchase");
            row.setRemark("快递公司没有时效规则、或发货单没填快递公司时按这个天数估算预计到货");
            sysConfigMapper.insert(row);
        }
        logService.recordOperateLog("快递时效", "修改默认运输天数", Map.of("days", before), Map.of("days", days));
        return days;
    }

    // ---------------------------------------------------------------- 估算

    /** 采购单的发货省份：老供应商资料「地区」的省份，线上店铺与没填地区的为空 */
    public String originOf(PurchaseOrderDO po) {
        if (po == null || Counterparty.of(po).isShop() || po.getSupplierId() == null || po.getSupplierId() <= 0) {
            return "";
        }
        SupplierDO s = supplierMapper.selectById(po.getSupplierId());
        if (s == null || !StringUtils.hasText(s.getRegion())) {
            return "";
        }
        return province(s.getRegion().split("/")[0]);
    }

    public ArrivalEstimateVO estimate(PurchaseOrderDO po, String carrier, LocalDate shipDate) {
        ensureSeeded();
        String c = carrier == null ? "" : carrier.trim();
        String origin = originOf(po);
        List<TransitTimeDO> rules = c.isEmpty() ? List.of() : mapper.selectList(live().eq(TransitTimeDO::getCarrier, c));
        TransitTimeDO exact = origin.isEmpty() ? null
                : rules.stream().filter(r -> origin.equals(r.getOriginProvince())).findFirst().orElse(null);
        TransitTimeDO byCarrier = rules.stream().filter(r -> r.getOriginProvince().isEmpty()).findFirst().orElse(null);
        int days;
        String basis;
        if (exact != null) {
            days = exact.getDays();
            basis = c + " · " + origin + " → " + DEST + " " + days + " 天";
        } else if (byCarrier != null) {
            days = byCarrier.getDays();
            basis = c + "默认 " + days + " 天" + (origin.isEmpty() ? "（没有发货地）" : "（" + origin + "没有单独规则）");
        } else {
            days = defaultDays();
            basis = (c.isEmpty() ? "没填快递公司" : c + "没有时效规则") + "，按默认 " + days + " 天";
        }
        ArrivalEstimateVO vo = new ArrivalEstimateVO();
        vo.setDays(days);
        vo.setDate(shipDate == null ? null : shipDate.plusDays(days));
        vo.setBasis(basis);
        return vo;
    }

    /** 省份简称：去掉「省」「市」「自治区」等后缀，「广西壮族自治区」→「广西」 */
    public static String province(String raw) {
        String p = raw == null ? "" : raw.trim();
        for (String s : SUFFIXES) {
            if (p.length() > s.length() && p.endsWith(s)) {
                return p.substring(0, p.length() - s.length());
            }
        }
        return p;
    }

    // ---------------------------------------------------------------- 通用

    /** 租户还从没有过规则（含已删除）时写入初始规则 */
    private void ensureSeeded() {
        int tenant = PiStore.tenantId();
        if (mapper.selectCount(new LambdaQueryWrapper<TransitTimeDO>().eq(TransitTimeDO::getTenantId, tenant)) > 0) {
            return;
        }
        for (Object[] r : SEED) {
            TransitTimeDO t = new TransitTimeDO();
            t.setTenantId(tenant);
            t.setCarrier((String) r[0]);
            t.setOriginProvince((String) r[1]);
            t.setDays((Integer) r[2]);
            t.setRemark((String) r[3]);
            mapper.insert(t);
        }
    }

    private LambdaQueryWrapper<TransitTimeDO> live() {
        return new LambdaQueryWrapper<TransitTimeDO>()
                .eq(TransitTimeDO::getTenantId, PiStore.tenantId())
                .isNull(TransitTimeDO::getDeletedAt);
    }

    private static void fill(TransitTimeDO t, SaveTransitTimeRequest req) {
        t.setCarrier(req.getCarrier().trim());
        t.setOriginProvince(province(req.getOriginProvince()));
        t.setDays(req.getDays());
        t.setRemark(req.getRemark() == null ? "" : req.getRemark().trim());
    }

    private void requireUnique(TransitTimeDO t, Long selfId) {
        boolean dup = mapper.selectList(live().eq(TransitTimeDO::getCarrier, t.getCarrier())
                        .eq(TransitTimeDO::getOriginProvince, t.getOriginProvince()))
                .stream().anyMatch(x -> !Objects.equals(x.getId(), selfId));
        if (dup) {
            throw new BizException(t.getCarrier() + " · " + (t.getOriginProvince().isEmpty() ? "默认" : t.getOriginProvince()) + " 已有规则");
        }
    }

    private TransitTimeDO visible(Long id) {
        TransitTimeDO t = id == null ? null : mapper.selectById(id);
        if (t == null || t.getDeletedAt() != null || !Objects.equals(t.getTenantId(), PiStore.tenantId())) {
            throw new BizException("规则不存在");
        }
        return t;
    }

    private static String text(TransitTimeDO t) {
        return t.getCarrier() + " · " + (t.getOriginProvince().isEmpty() ? "默认" : t.getOriginProvince()) + " → " + t.getDays() + " 天";
    }

    private static int parseDays(String v) {
        try {
            int n = Integer.parseInt(v.trim());
            return n > 0 ? n : FALLBACK_DAYS;
        } catch (NumberFormatException e) {
            return FALLBACK_DAYS;
        }
    }

    private static TransitTimeVO toVo(TransitTimeDO t) {
        TransitTimeVO vo = new TransitTimeVO();
        vo.setId(t.getId());
        vo.setCarrier(t.getCarrier());
        vo.setOriginProvince(t.getOriginProvince());
        vo.setDays(t.getDays());
        vo.setRemark(t.getRemark());
        vo.setCreateTime(t.getCreateTime());
        vo.setCreateBy(t.getCreateBy());
        vo.setUpdateTime(t.getUpdateTime());
        vo.setUpdateBy(t.getUpdateBy());
        return vo;
    }
}
