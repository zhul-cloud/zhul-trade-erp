package com.zhul.erp.modules.tenant.service;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.modules.tenant.dto.PackageDeleteCheckVO;
import com.zhul.erp.modules.tenant.dto.SaveTenantPackageRequest;
import com.zhul.erp.modules.tenant.dto.TenantPackageQuery;
import com.zhul.erp.modules.tenant.dto.TenantPackageVO;

public interface TenantPackageService {

    PageResult<TenantPackageVO> page(TenantPackageQuery query);

    TenantPackageVO create(SaveTenantPackageRequest req);

    TenantPackageVO update(Integer id, SaveTenantPackageRequest req);

    /** 启用/禁用套餐 */
    void updateStatus(Integer id, Integer status);

    /** 删除前置校验：是否有租户绑定该套餐 */
    PackageDeleteCheckVO checkDeletable(Integer id);

    /** 软删除；已绑定租户的套餐禁止删除（由 checkDeletable 前置拦截，这里再兜底校验一次） */
    void delete(Integer id);
}
