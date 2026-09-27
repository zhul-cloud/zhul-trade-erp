package com.zhul.erp.modules.product.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

@Mapper
public interface ProductCompletenessMapper {

    /**
     * 一次查询取回这批商品各子表模块的完成情况，避免逐个商品、逐个模块查询。
     * 每个模块对每个商品最多返回一行。基本信息不在子表里，由调用方按商品自身字段判断。
     */
    @Select("<script>"
            + "SELECT product_id, 'media' AS module_key FROM product_media WHERE deleted_at IS NULL AND "
            + CompletenessSql.MEDIA_DONE + " AND product_id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> GROUP BY product_id "
            + "UNION ALL SELECT product_id, 'specifications' FROM product_specification WHERE deleted_at IS NULL AND product_id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> GROUP BY product_id "
            + "UNION ALL SELECT product_id, 'logistics' FROM product_logistics WHERE deleted_at IS NULL AND "
            + CompletenessSql.LOGISTICS_FILLED + " AND product_id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> GROUP BY product_id "
            + "UNION ALL SELECT product_id, 'customs' FROM product_customs WHERE deleted_at IS NULL AND "
            + CompletenessSql.CUSTOMS_DONE + " AND product_id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> GROUP BY product_id "
            + "UNION ALL SELECT product_id, 'referencePrice' FROM product_reference_price WHERE deleted_at IS NULL AND product_id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> GROUP BY product_id "
            + "UNION ALL SELECT product_id, 'relationships' FROM product_relationship WHERE deleted_at IS NULL AND product_id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> GROUP BY product_id "
            + "UNION ALL SELECT product_id, 'documents' FROM product_document WHERE deleted_at IS NULL AND product_id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> GROUP BY product_id "
            + "UNION ALL SELECT product_id, 'applications' FROM product_application WHERE deleted_at IS NULL AND product_id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> GROUP BY product_id "
            + "UNION ALL SELECT product_id, 'faq' FROM product_faq WHERE deleted_at IS NULL AND "
            + CompletenessSql.FAQ_DONE + " AND product_id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> GROUP BY product_id"
            + "</script>")
    List<ModuleHit> findModuleHits(@Param("ids") Collection<Long> ids);
}
