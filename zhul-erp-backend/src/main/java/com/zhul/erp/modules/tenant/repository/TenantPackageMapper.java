package com.zhul.erp.modules.tenant.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhul.erp.modules.tenant.entity.TenantPackageDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TenantPackageMapper extends BaseMapper<TenantPackageDO> {
}
