package com.zhul.erp.modules.product.content.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhul.erp.modules.product.content.entity.ProductContentTaskDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface ProductContentTaskMapper extends BaseMapper<ProductContentTaskDO> {

    String FROM = "FROM product p LEFT JOIN product_content_task t ON t.product_id = p.id AND t.tenant_id = #{tenantId} "
            + "AND t.deleted_at IS NULL WHERE p.tenant_id = 0 AND p.deleted_at IS NULL AND p.status = 1 ";

    String FILTER = "<if test='brandId != null'> AND p.brand_id = #{brandId}</if>"
            + "<if test='categoryId != null'> AND p.category_id = #{categoryId}</if>"
            + "<if test='keyword != null'> AND (p.product_name LIKE CONCAT('%', #{keyword}, '%') "
            + "OR p.mpn_raw LIKE CONCAT('%', #{keyword}, '%')"
            + "<if test='mpn != null'> OR p.mpn_normalized LIKE CONCAT('%', #{mpn}, '%')</if>)</if>";

    String STATUS = "<if test='status != null'> AND COALESCE(t.status, 1) = #{status}</if>";

    @Select("<script>SELECT p.id AS product_id, p.brand_id, p.category_id, p.mpn_raw, p.mpn_display, p.product_name, "
            + "t.id AS task_id, COALESCE(t.status, 1) AS status, t.zh_at, t.zh_by, t.en_at, t.en_by, t.ru_at, t.ru_by, "
            + "t.downloaded_at, COALESCE(t.create_time, p.create_time) AS create_time, "
            + "COALESCE(t.create_by, p.create_by) AS create_by, COALESCE(t.update_time, p.create_time) AS update_time, "
            + "COALESCE(t.update_by, p.create_by) AS update_by "
            + FROM + FILTER + STATUS
            + " ORDER BY update_time DESC, p.id DESC LIMIT #{offset}, #{size}</script>")
    List<ContentTaskRow> page(@Param("tenantId") Integer tenantId, @Param("status") Integer status,
                              @Param("brandId") Long brandId, @Param("categoryId") Long categoryId,
                              @Param("keyword") String keyword, @Param("mpn") String mpn,
                              @Param("offset") long offset, @Param("size") long size);

    @Select("<script>SELECT COUNT(*) " + FROM + FILTER + STATUS + "</script>")
    long count(@Param("tenantId") Integer tenantId, @Param("status") Integer status,
               @Param("brandId") Long brandId, @Param("categoryId") Long categoryId,
               @Param("keyword") String keyword, @Param("mpn") String mpn);

    /** 各状态数量（页签用；同样受品牌、品类、关键词筛选） */
    @Select("<script>SELECT COALESCE(t.status, 1) AS status, COUNT(*) AS cnt " + FROM + FILTER
            + " GROUP BY COALESCE(t.status, 1)</script>")
    List<Map<String, Object>> countByStatus(@Param("tenantId") Integer tenantId, @Param("brandId") Long brandId,
                                            @Param("categoryId") Long categoryId, @Param("keyword") String keyword,
                                            @Param("mpn") String mpn);

    /** 写入前按唯一键锁任务行（不存在时由调用方插入，唯一键兜底并发） */
    @Select("SELECT id FROM product_content_task WHERE tenant_id = #{tenantId} AND product_id = #{productId} FOR UPDATE")
    Long lockByProduct(@Param("tenantId") Integer tenantId, @Param("productId") Long productId);
}
