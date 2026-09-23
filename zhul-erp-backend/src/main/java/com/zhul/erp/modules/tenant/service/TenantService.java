package com.zhul.erp.modules.tenant.service;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.modules.tenant.dto.PackageOptionVO;
import com.zhul.erp.modules.tenant.dto.RenewTenantRequest;
import com.zhul.erp.modules.tenant.dto.ResetPasswordResultVO;
import com.zhul.erp.modules.tenant.dto.SaveTenantRequest;
import com.zhul.erp.modules.tenant.dto.TenantCreateResultVO;
import com.zhul.erp.modules.tenant.dto.TenantQuery;
import com.zhul.erp.modules.tenant.dto.TenantVO;

import java.util.List;

public interface TenantService {

    PageResult<TenantVO> page(TenantQuery query);

    /** 创建租户 + 同步创建管理员账号；返回结果里带明文临时密码，仅这一次可见 */
    TenantCreateResultVO create(SaveTenantRequest req);

    TenantVO update(Integer id, SaveTenantRequest req);

    /** 启用/禁用租户；级联同步该租户下所有账号的登录状态 */
    void updateStatus(Integer id, Integer status);

    TenantVO renew(Integer id, RenewTenantRequest req);

    /** 重置该租户管理员账号密码；返回结果里带明文临时密码，仅这一次可见 */
    ResetPasswordResultVO resetPassword(Integer id);

    /** 新增/编辑租户时选择套餐用，只返回启用中的套餐 */
    List<PackageOptionVO> packageOptions();
}
