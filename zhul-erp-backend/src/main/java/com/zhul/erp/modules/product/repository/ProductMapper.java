package com.zhul.erp.modules.product.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhul.erp.modules.product.entity.ProductDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

@Mapper
public interface ProductMapper extends BaseMapper<ProductDO> {

    /** 各品牌下未删除商品数，供品牌列表展示"使用数量" */
    @Select("<script>SELECT brand_id AS id, COUNT(*) AS cnt FROM product "
            + "WHERE tenant_id = 0 AND deleted_at IS NULL AND brand_id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> "
            + "GROUP BY brand_id</script>")
    List<IdCount> countByBrandIds(@Param("ids") Collection<Long> ids);

    @Select("<script>SELECT category_id AS id, COUNT(*) AS cnt FROM product "
            + "WHERE tenant_id = 0 AND deleted_at IS NULL AND category_id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> "
            + "GROUP BY category_id</script>")
    List<IdCount> countByCategoryIds(@Param("ids") Collection<Long> ids);

    @Select("<script>SELECT series_id AS id, COUNT(*) AS cnt FROM product "
            + "WHERE tenant_id = 0 AND deleted_at IS NULL AND series_id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> "
            + "GROUP BY series_id</script>")
    List<IdCount> countBySeriesIds(@Param("ids") Collection<Long> ids);
}
