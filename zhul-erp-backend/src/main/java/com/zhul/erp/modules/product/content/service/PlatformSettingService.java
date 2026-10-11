package com.zhul.erp.modules.product.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.product.constants.ProductErrorCodes;
import com.zhul.erp.modules.product.content.constants.ContentConstants;
import com.zhul.erp.modules.product.content.dto.MarketingPlatformVO;
import com.zhul.erp.modules.product.content.support.MarketingPlatform;
import com.zhul.erp.modules.system.entity.SysConfigDO;
import com.zhul.erp.modules.system.repository.SysConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 营销平台设置（spec product/product-content「营销平台设置」）：公司设置优先，其次平台模板（tenant 0）；
 * 平台账号的设置就是模板。配置为空或全部无效时按四个都启用，避免配置被改坏后任务全部算作完成。
 */
@Service
@RequiredArgsConstructor
public class PlatformSettingService {

    private final SysConfigMapper sysConfigMapper;

    /** 本公司启用的平台，按平台固定顺序 */
    public Set<MarketingPlatform> enabled() {
        return enabled(TenantContext.getTenantId());
    }

    public Set<MarketingPlatform> enabled(int tenantId) {
        String value = sysConfigMapper.selectList(new LambdaQueryWrapper<SysConfigDO>()
                        .eq(SysConfigDO::getConfigKey, ContentConstants.CONFIG_PLATFORMS)
                        .in(SysConfigDO::getTenantId, List.of(0, tenantId))
                        .isNull(SysConfigDO::getDeletedAt))
                .stream()
                .max(Comparator.comparing(SysConfigDO::getTenantId))
                .map(SysConfigDO::getConfigValue)
                .orElse(null);
        Set<MarketingPlatform> result = EnumSet.noneOf(MarketingPlatform.class);
        if (value != null) {
            for (String code : value.split(",")) {
                MarketingPlatform p = MarketingPlatform.of(code.trim());
                if (p != null) {
                    result.add(p);
                }
            }
        }
        return result.isEmpty() ? EnumSet.allOf(MarketingPlatform.class) : result;
    }

    /** 平台没启用时报错（下载、确认写入、标记不发布前调用） */
    public MarketingPlatform requireEnabled(String code) {
        MarketingPlatform p = MarketingPlatform.of(code);
        if (p == null) {
            throw BizException.of(ProductErrorCodes.PARAM_INVALID, "平台须为 alibaba、mic、site 或 social");
        }
        if (!enabled().contains(p)) {
            throw BizException.of(ProductErrorCodes.PLATFORM_NOT_ENABLED, "本公司没有启用" + p.label());
        }
        return p;
    }

    public List<MarketingPlatformVO> list() {
        Set<MarketingPlatform> enabled = enabled();
        List<MarketingPlatformVO> result = new ArrayList<>(MarketingPlatform.values().length);
        for (MarketingPlatform p : MarketingPlatform.values()) {
            MarketingPlatformVO vo = new MarketingPlatformVO();
            vo.setCode(p.code());
            vo.setLabel(p.label());
            vo.setLangs(p.langs());
            vo.setEnabled(enabled.contains(p));
            result.add(vo);
        }
        return result;
    }

    /** 保存本公司启用的平台：公司第一次保存时生成自己的设置，之后改自己的 */
    public List<MarketingPlatformVO> save(List<String> codes) {
        Set<MarketingPlatform> selected = EnumSet.noneOf(MarketingPlatform.class);
        for (String code : codes == null ? List.<String>of() : codes) {
            MarketingPlatform p = MarketingPlatform.of(code);
            if (p == null) {
                throw BizException.of(ProductErrorCodes.PARAM_INVALID, "平台「" + code + "」无效");
            }
            selected.add(p);
        }
        if (selected.isEmpty()) {
            throw BizException.of(ProductErrorCodes.PARAM_INVALID, "至少启用一个平台");
        }
        String value = String.join(",", selected.stream().map(MarketingPlatform::code).toList());
        int tenantId = TenantContext.getTenantId();
        SysConfigDO own = sysConfigMapper.selectOne(new LambdaQueryWrapper<SysConfigDO>()
                .eq(SysConfigDO::getConfigKey, ContentConstants.CONFIG_PLATFORMS)
                .eq(SysConfigDO::getTenantId, tenantId)
                .isNull(SysConfigDO::getDeletedAt)
                .last("LIMIT 1"));
        if (own != null) {
            own.setConfigValue(value);
            sysConfigMapper.updateById(own);
        } else {
            SysConfigDO row = new SysConfigDO();
            row.setTenantId(tenantId);
            row.setConfigKey(ContentConstants.CONFIG_PLATFORMS);
            row.setConfigName("营销平台");
            row.setConfigValue(value);
            row.setConfigType("STRING");
            row.setIsBuiltin(1);
            row.setIsEncrypted(0);
            row.setConfigGroup("product");
            row.setRemark("本公司做哪些平台的营销内容（alibaba-阿里国际站、mic-中国制造网、site-独立站、social-社媒，逗号分隔）");
            sysConfigMapper.insert(row);
        }
        return list();
    }
}
