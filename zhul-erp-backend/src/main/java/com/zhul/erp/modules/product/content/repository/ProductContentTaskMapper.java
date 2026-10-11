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
            + "AND t.deleted_at IS NULL LEFT JOIN product_fact_task f ON f.product_id = p.id AND f.tenant_id = 0 "
            + "AND f.deleted_at IS NULL WHERE p.tenant_id = 0 AND p.deleted_at IS NULL AND p.status = 1 ";

    /** 任务阶段（1-资料待补、2-资料待核、3-营销待生成、4-已完成），列表、筛选、页签计数共用 */
    String STAGE = "(CASE WHEN f.imported_at IS NULL AND f.verified_at IS NULL THEN 1 "
            + "WHEN f.verified_at IS NULL OR f.verified_at &lt; f.imported_at THEN 2 "
            + "WHEN t.en_at IS NULL OR t.ru_at IS NULL THEN 3 ELSE 4 END)";

    /** 更新时间取本公司任务与资料任务中较晚的一个，没有任务时取商品创建时间 */
    String UPDATE_TIME = "GREATEST(COALESCE(t.update_time, p.create_time), COALESCE(f.update_time, p.create_time))";

    String FILTER = "<if test='brandId != null'> AND p.brand_id = #{brandId}</if>"
            + "<if test='categoryId != null'> AND (p.category_id = #{categoryId} OR p.category_id IN (SELECT c.id FROM product_category c WHERE c.parent_id = #{categoryId} AND c.deleted_at IS NULL))</if>"
            + "<if test='keyword != null'> AND (p.product_name LIKE CONCAT('%', #{keyword}, '%') "
            + "OR p.mpn_raw LIKE CONCAT('%', #{keyword}, '%')"
            + "<if test='mpn != null'> OR p.mpn_normalized LIKE CONCAT('%', #{mpn}, '%')</if>)</if>";

    String STATUS = "<if test='status != null'> AND " + STAGE + " = #{status}</if>";

    @Select("<script>SELECT p.id AS product_id, p.brand_id, p.category_id, p.mpn_raw, p.mpn_display, p.product_name, "
            + "t.id AS task_id, " + STAGE + " AS status, t.en_at, t.en_by, t.ru_at, t.ru_by, t.downloaded_at, "
            + "f.imported_at AS fact_imported_at, f.imported_by AS fact_imported_by, f.verified_at AS fact_verified_at, "
            + "f.verified_by AS fact_verified_by, f.downloaded_at AS fact_downloaded_at, "
            + "COALESCE(t.create_time, p.create_time) AS create_time, COALESCE(t.create_by, p.create_by) AS create_by, "
            + UPDATE_TIME + " AS update_time, "
            + "CASE WHEN f.update_time IS NOT NULL AND (t.update_time IS NULL OR f.update_time &gt; t.update_time) "
            + "THEN f.update_by ELSE COALESCE(t.update_by, p.create_by) END AS update_by "
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

    /** 各阶段数量（页签用；同样受品牌、品类、关键词筛选） */
    @Select("<script>SELECT " + STAGE + " AS status, COUNT(*) AS cnt " + FROM + FILTER
            + " GROUP BY " + STAGE + "</script>")
    List<Map<String, Object>> countByStatus(@Param("tenantId") Integer tenantId, @Param("brandId") Long brandId,
                                            @Param("categoryId") Long categoryId, @Param("keyword") String keyword,
                                            @Param("mpn") String mpn);

    /** 写入前按唯一键锁任务行（不存在时由调用方插入，唯一键兜底并发） */
    @Select("SELECT id FROM product_content_task WHERE tenant_id = #{tenantId} AND product_id = #{productId} FOR UPDATE")
    Long lockByProduct(@Param("tenantId") Integer tenantId, @Param("productId") Long productId);
}
