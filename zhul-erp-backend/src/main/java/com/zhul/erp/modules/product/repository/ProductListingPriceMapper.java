package com.zhul.erp.modules.product.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhul.erp.modules.product.entity.ProductListingPriceDO;
import org.apache.ibatis.annotations.Mapper;

/** 平台参考价 */
@Mapper
public interface ProductListingPriceMapper extends BaseMapper<ProductListingPriceDO> {
}
