package com.zhul.erp.modules.quotation.service.impl;

import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.quotation.constants.QuotationConstants;
import com.zhul.erp.modules.quotation.dto.PricingStrategyVO;
import com.zhul.erp.modules.quotation.dto.SavePricingStrategyRequest;
import com.zhul.erp.modules.quotation.entity.PricingAmountTierDO;
import com.zhul.erp.modules.quotation.entity.PricingConditionMarginDO;
import com.zhul.erp.modules.quotation.repository.PricingAmountTierMapper;
import com.zhul.erp.modules.quotation.repository.PricingConditionMarginMapper;
import com.zhul.erp.modules.quotation.service.PricingStrategyService;
import com.zhul.erp.modules.quotation.support.PricingSettings;
import com.zhul.erp.modules.quotation.support.PricingStrategy;
import com.zhul.erp.modules.system.dto.DictItemVO;
import com.zhul.erp.modules.system.service.DictItemService;
import com.zhul.erp.modules.system.service.LogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PricingStrategyServiceImpl implements PricingStrategyService {

    private static final BigDecimal MAX_RATE = new BigDecimal("95");
    private static final BigDecimal MAX_COST = new BigDecimal("1000000");

    private final PricingSettings settings;
    private final PricingConditionMarginMapper marginMapper;
    private final PricingAmountTierMapper tierMapper;
    private final DictItemService dictItemService;
    private final LogService logService;

    @Override
    public PricingStrategyVO get() {
        PricingStrategy s = settings.load(tenantId());
        PricingStrategyVO vo = new PricingStrategyVO();
        List<PricingStrategyVO.ConditionMargin> conditions = new ArrayList<>();
        for (DictItemVO item : conditionItems()) {
            Integer code = parse(item.getItemValue());
            if (code == null) {
                continue;
            }
            PricingStrategy.Margin m = s.conditions().get(code);
            PricingStrategyVO.ConditionMargin c = new PricingStrategyVO.ConditionMargin();
            c.setItemCondition(code);
            c.setConditionName(item.getItemName());
            c.setConfigured(m != null);
            if (m != null) {
                c.setMarginRate(m.marginRate());
                c.setFloorRate(m.floorRate());
            }
            conditions.add(c);
        }
        vo.setConditions(conditions);
        vo.setTiers(s.tiers().stream().map(t -> {
            PricingStrategyVO.AmountTier a = new PricingStrategyVO.AmountTier();
            a.setMaxCost(t.maxCost());
            a.setMarginRate(t.marginRate());
            return a;
        }).toList());
        PricingStrategyVO.Hints h = new PricingStrategyVO.Hints();
        h.setDiscontinuedUrgent(s.hintDiscontinuedUrgent());
        h.setPremiumBrand(s.hintPremiumBrand());
        h.setReturningCustomer(s.hintReturningCustomer());
        h.setToConfirm(s.hintToConfirm());
        vo.setHints(h);
        vo.setPremiumBrands(s.premiumBrands());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PricingStrategyVO save(SavePricingStrategyRequest req) {
        int tenant = tenantId();
        validateConditions(req.getConditions());
        List<PricingStrategyVO.AmountTier> tiers = validateTiers(req.getTiers());
        List<String> brands = cleanBrands(req.getPremiumBrands());
        PricingStrategyVO before = get();

        saveConditions(tenant, req.getConditions());
        saveTiers(tenant, tiers);
        PricingStrategyVO.Hints h = req.getHints();
        settings.setConfig(tenant, QuotationConstants.CONFIG_HINT_DISCONTINUED_URGENT, String.valueOf(Boolean.TRUE.equals(h.getDiscontinuedUrgent())));
        settings.setConfig(tenant, QuotationConstants.CONFIG_HINT_PREMIUM_BRAND, String.valueOf(Boolean.TRUE.equals(h.getPremiumBrand())));
        settings.setConfig(tenant, QuotationConstants.CONFIG_HINT_RETURNING_CUSTOMER, String.valueOf(Boolean.TRUE.equals(h.getReturningCustomer())));
        settings.setConfig(tenant, QuotationConstants.CONFIG_HINT_TO_CONFIRM, String.valueOf(Boolean.TRUE.equals(h.getToConfirm())));
        settings.setConfig(tenant, QuotationConstants.CONFIG_PREMIUM_BRANDS, settings.toJson(brands));

        PricingStrategyVO after = get();
        logService.recordOperateLog("定价策略", "修改定价策略", before, after);
        return after;
    }

    private void validateConditions(List<PricingStrategyVO.ConditionMargin> list) {
        Set<Integer> known = new HashSet<>();
        Map<Integer, String> names = new HashMap<>();
        for (DictItemVO item : conditionItems()) {
            Integer code = parse(item.getItemValue());
            if (code != null) {
                known.add(code);
                names.put(code, item.getItemName());
            }
        }
        Set<Integer> seen = new HashSet<>();
        for (PricingStrategyVO.ConditionMargin c : list) {
            if (c.getItemCondition() == null || !known.contains(c.getItemCondition())) {
                throw new BizException("货况不存在：" + c.getItemCondition());
            }
            if (!seen.add(c.getItemCondition())) {
                throw new BizException("货况重复：" + names.get(c.getItemCondition()));
            }
            String name = names.get(c.getItemCondition());
            BigDecimal m = c.getMarginRate();
            BigDecimal f = c.getFloorRate();
            if (m == null) {
                if (f != null) {
                    throw new BizException(name + "：没有建议毛利率时不能设置红线");
                }
                continue;
            }
            requireRate(name + "建议毛利率", m);
            if (f == null) {
                throw new BizException(name + "：请填写红线");
            }
            requireRate(name + "红线", f);
            if (f.compareTo(m) > 0) {
                throw new BizException("红线不能高于建议毛利率");
            }
        }
    }

    private static void requireRate(String label, BigDecimal v) {
        if (v.signum() < 0 || v.compareTo(MAX_RATE) > 0) {
            throw new BizException(label + "需要在 0–95% 之间");
        }
        if (v.stripTrailingZeros().scale() > 2) {
            throw new BizException(label + "最多 2 位小数");
        }
    }

    private static List<PricingStrategyVO.AmountTier> validateTiers(List<PricingStrategyVO.AmountTier> tiers) {
        List<PricingStrategyVO.AmountTier> sorted = new ArrayList<>(tiers);
        for (PricingStrategyVO.AmountTier t : sorted) {
            if (t.getMaxCost() == null || t.getMaxCost().signum() <= 0 || t.getMaxCost().compareTo(MAX_COST) >= 0) {
                throw new BizException("低值耗材分层的金额上限需要大于 0");
            }
            if (t.getMarginRate() == null) {
                throw new BizException("请填写低值耗材分层的毛利率");
            }
            requireRate("低值耗材分层毛利率", t.getMarginRate());
        }
        sorted.sort((a, b) -> a.getMaxCost().compareTo(b.getMaxCost()));
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i).getMaxCost().compareTo(sorted.get(i - 1).getMaxCost()) == 0) {
                throw new BizException("低值耗材分层的金额上限不能重复");
            }
        }
        return sorted;
    }

    private static List<String> cleanBrands(List<String> brands) {
        Set<String> out = new LinkedHashSet<>();
        Set<String> lower = new HashSet<>();
        for (String b : brands) {
            String v = b == null ? "" : b.trim();
            if (v.isEmpty()) {
                continue;
            }
            if (v.length() > 64) {
                throw new BizException("品牌名称不能超过 64 个字符");
            }
            if (lower.add(v.toLowerCase())) {
                out.add(v);
            }
        }
        return new ArrayList<>(out);
    }

    /** 写租户自己的一套：第一次保存时整体复制，之后逐项更新；请求里没出现的货况保持原值 */
    private void saveConditions(int tenant, List<PricingStrategyVO.ConditionMargin> list) {
        Map<Integer, PricingConditionMarginDO> own = new HashMap<>();
        for (PricingConditionMarginDO r : settings.marginRows(tenant)) {
            own.put(r.getItemCondition(), r);
        }
        if (own.isEmpty() && tenant != 0) {
            for (PricingConditionMarginDO p : settings.marginRows(0)) {
                PricingConditionMarginDO copy = new PricingConditionMarginDO();
                copy.setTenantId(tenant);
                copy.setItemCondition(p.getItemCondition());
                copy.setMarginRate(p.getMarginRate());
                copy.setFloorRate(p.getFloorRate());
                copy.setStatus(p.getStatus());
                marginMapper.insert(copy);
                own.put(copy.getItemCondition(), copy);
            }
        }
        for (PricingStrategyVO.ConditionMargin c : list) {
            PricingConditionMarginDO row = own.get(c.getItemCondition());
            if (row == null) {
                row = new PricingConditionMarginDO();
                row.setTenantId(tenant);
                row.setItemCondition(c.getItemCondition());
                row.setStatus(1);
                row.setMarginRate(c.getMarginRate());
                row.setFloorRate(c.getFloorRate());
                marginMapper.insert(row);
            } else if (!same(row.getMarginRate(), c.getMarginRate()) || !same(row.getFloorRate(), c.getFloorRate())
                    || !Objects.equals(row.getStatus(), 1)) {
                row.setMarginRate(c.getMarginRate());
                row.setFloorRate(c.getFloorRate());
                row.setStatus(1);
                marginMapper.updateById(row);
            }
        }
    }

    /** 分层整体替换：原有的停用（不物理删除），再写入新的一套 */
    private void saveTiers(int tenant, List<PricingStrategyVO.AmountTier> tiers) {
        for (PricingAmountTierDO r : settings.tierRows(tenant)) {
            if (Objects.equals(r.getStatus(), 1)) {
                r.setStatus(0);
                tierMapper.updateById(r);
            }
        }
        int sort = 1;
        for (PricingStrategyVO.AmountTier t : tiers) {
            PricingAmountTierDO row = new PricingAmountTierDO();
            row.setTenantId(tenant);
            row.setMaxCost(t.getMaxCost());
            row.setMarginRate(t.getMarginRate());
            row.setSortOrder(sort++);
            row.setStatus(1);
            tierMapper.insert(row);
        }
    }

    private List<DictItemVO> conditionItems() {
        return dictItemService.listByDictType(QuotationConstants.DICT_CONDITION).stream()
                .filter(i -> Objects.equals(i.getStatus(), 1)).toList();
    }

    private static boolean same(BigDecimal a, BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }

    private static Integer parse(String v) {
        try {
            return v == null ? null : Integer.valueOf(v.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int tenantId() {
        Integer t = TenantContext.getTenantId();
        return t == null ? 0 : t;
    }
}
