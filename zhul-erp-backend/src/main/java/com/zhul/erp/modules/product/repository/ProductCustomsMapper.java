package com.zhul.erp.modules.product.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhul.erp.modules.product.entity.ProductCustomsDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProductCustomsMapper extends BaseMapper<ProductCustomsDO> {
}
