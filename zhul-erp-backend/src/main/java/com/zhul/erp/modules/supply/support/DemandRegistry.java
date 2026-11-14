package com.zhul.erp.modules.supply.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.modules.sales.support.PiStore;
import com.zhul.erp.modules.supply.constants.SupplyConstants;
import com.zhul.erp.modules.supply.entity.ScDemandDO;
import com.zhul.erp.modules.supply.repository.ScDemandMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 需求对象的键（design 决策 3）：有型号为「品牌 + 归一化型号」，没有型号为「品牌 + 二级品类」。
 * 合并掉的需求对象保留原键并指向保留方，之后同键的信号自动归到保留方。
 */
@Component
@RequiredArgsConstructor
public class DemandRegistry {

    private final ScDemandMapper demandMapper;

    public static String scopeKey(String modelKey, Long categoryId) {
        return StringUtils.hasText(modelKey) ? "m:" + modelKey : "c:" + (categoryId == null ? 0 : categoryId);
    }

    /** 按键找需求对象，合并掉的跟到保留方；没有返回 null */
    public ScDemandDO find(Long brandId, String scopeKey) {
        ScDemandDO d = demandMapper.selectOne(new LambdaQueryWrapper<ScDemandDO>()
                .eq(ScDemandDO::getBrandId, brandId)
                .eq(ScDemandDO::getScopeKey, scopeKey)
                .last("LIMIT 1"));
        for (int i = 0; d != null && d.getDeletedAt() != null && i < 10; i++) {
            if (d.getMergedIntoId() == null || d.getMergedIntoId() == 0) {
                return null;
            }
            d = demandMapper.selectById(d.getMergedIntoId());
        }
        return d != null && d.getDeletedAt() == null ? d : null;
    }

    /** 找到或新建；新建的为观察中、低优先级，类别与显示型号取首次出现的 */
    public ScDemandDO findOrCreate(Long brandId, Long categoryId, String modelKey, String model) {
        String key = scopeKey(modelKey, categoryId);
        ScDemandDO d = find(brandId, key);
        if (d != null) {
            if ((d.getCategoryId() == null || d.getCategoryId() == 0) && categoryId != null && categoryId > 0) {
                ScDemandDO u = new ScDemandDO();
                u.setId(d.getId());
                u.setCategoryId(categoryId);
                demandMapper.updateById(u);
                d.setCategoryId(categoryId);
            }
            return d;
        }
        d = new ScDemandDO();
        d.setTenantId(PiStore.tenantId());
        d.setBrandId(brandId);
        d.setCategoryId(categoryId == null ? 0L : categoryId);
        d.setModelKey(StringUtils.hasText(modelKey) ? modelKey : "");
        d.setModel(model == null ? "" : model.length() > 128 ? model.substring(0, 128) : model.trim());
        d.setScopeKey(key);
        d.setStatus(SupplyConstants.DEMAND_WATCHING);
        d.setDemandLevel(SupplyConstants.LEVEL_OBSERVE);
        d.setSuggestedPriority(SupplyConstants.PRIORITY_LOW);
        d.setPriority(SupplyConstants.PRIORITY_LOW);
        demandMapper.insert(d);
        return d;
    }
}
