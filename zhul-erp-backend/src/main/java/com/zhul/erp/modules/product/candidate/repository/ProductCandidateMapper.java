package com.zhul.erp.modules.product.candidate.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhul.erp.modules.product.candidate.entity.ProductCandidateDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ProductCandidateMapper extends BaseMapper<ProductCandidateDO> {

    /** 审核前锁行，防止两人同时审核同一候选 */
    @Select("SELECT id FROM product_candidate WHERE id = #{id} FOR UPDATE")
    Long lockById(@Param("id") Long id);

    /** 进池时按唯一键锁行（不存在时不加锁，插入由唯一键兜底） */
    @Select("SELECT id FROM product_candidate WHERE brand_key = #{brandKey} AND mpn_normalized = #{mpn} FOR UPDATE")
    Long lockByKey(@Param("brandKey") String brandKey, @Param("mpn") String mpn);
}
