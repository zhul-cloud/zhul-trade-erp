package com.zhul.erp.modules.aitask.service;

import com.zhul.erp.framework.security.EffectivePermissionResolver;
import com.zhul.erp.modules.aitask.constants.AiConfigConstants;
import com.zhul.erp.modules.system.entity.ResourceDO;
import com.zhul.erp.modules.system.repository.ResourceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 能否使用平台订阅：平台租户始终可以；其他公司要求按钮资源「使用平台订阅」启用，且公司所属套餐包含它。
 * 按公司判断、不看员工角色，同一公司的员工发起任务时结果一致；每次查库，收回授权立即生效。
 */
@Component
@RequiredArgsConstructor
public class AiSubscriptionAccess {

    private final EffectivePermissionResolver permissionResolver;
    private final ResourceMapper resourceMapper;

    public boolean allowed(int tenantId) {
        if (tenantId == AiConfigConstants.PLATFORM_TENANT_ID) {
            return true;
        }
        ResourceDO resource = resourceMapper.selectById(AiConfigConstants.RESOURCE_USE_SUBSCRIPTION);
        if (resource == null || !Integer.valueOf(1).equals(resource.getStatus())
                || !Integer.valueOf(AiConfigConstants.RESOURCE_AI_SETTINGS).equals(resource.getPid())) {
            return false;
        }
        return permissionResolver.resolveTenantPackageResourceIds(tenantId)
                .contains(AiConfigConstants.RESOURCE_USE_SUBSCRIPTION);
    }
}
