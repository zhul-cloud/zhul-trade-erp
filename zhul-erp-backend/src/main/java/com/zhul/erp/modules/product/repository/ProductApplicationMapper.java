package com.zhul.erp.modules.product.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhul.erp.modules.product.entity.ProductApplicationDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProductApplicationMapper extends BaseMapper<ProductApplicationDO> {
}
