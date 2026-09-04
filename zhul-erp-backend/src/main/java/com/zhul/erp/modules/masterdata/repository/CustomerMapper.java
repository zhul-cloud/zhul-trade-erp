package com.zhul.erp.modules.masterdata.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhul.erp.modules.masterdata.entity.CustomerDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CustomerMapper extends BaseMapper<CustomerDO> {
}
