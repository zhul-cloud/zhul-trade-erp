package com.zhul.erp.modules.masterdata.customerprofile.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhul.erp.modules.masterdata.customerprofile.entity.CountryProfileDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CountryProfileMapper extends BaseMapper<CountryProfileDO> {
}
