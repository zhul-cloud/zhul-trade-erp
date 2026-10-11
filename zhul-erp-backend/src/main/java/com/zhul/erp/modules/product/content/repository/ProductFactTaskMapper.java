package com.zhul.erp.modules.product.content.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhul.erp.modules.product.content.entity.ProductFactTaskDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ProductFactTaskMapper extends BaseMapper<ProductFactTaskDO> {

    /** 导入、核对前按唯一键锁资料任务行（不存在时由调用方插入，唯一键兜底并发） */
    @Select("SELECT id FROM product_fact_task WHERE product_id = #{productId} FOR UPDATE")
    Long lockByProduct(@Param("productId") Long productId);
}
