package com.zhul.erp.modules.website.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhul.erp.modules.website.entity.WebsiteSiteDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface WebsiteSiteMapper extends BaseMapper<WebsiteSiteDO> {

    /** 锁住站点行：同一站点的发布单派发、发起在这把锁下串行（多实例也有效） */
    @Select("SELECT id FROM website_site WHERE id = #{id} AND tenant_id = #{tenantId} FOR UPDATE")
    Long lock(@Param("id") Long id, @Param("tenantId") Integer tenantId);

    /** 本公司各站点的发品任务数，按状态分组（站点管理列表的商品数） */
    @Select("SELECT site_id AS sid, status AS st, COUNT(*) AS cnt FROM listing_task "
            + "WHERE deleted_at IS NULL AND site_id > 0 GROUP BY site_id, status")
    List<Map<String, Object>> taskCounts();

    /** 本公司各站点导入的站点商品数 */
    @Select("SELECT site_id AS sid, COUNT(*) AS cnt FROM website_product "
            + "WHERE deleted_at IS NULL AND imported_at IS NOT NULL GROUP BY site_id")
    List<Map<String, Object>> importedCounts();

    /** 站点范围内（发品任务未取消）的商品数，按商品品类分组 */
    @Select("SELECT p.category_id AS cid, COUNT(*) AS cnt FROM listing_task l JOIN product p ON p.id = l.product_id "
            + "WHERE l.site_id = #{siteId} AND l.deleted_at IS NULL AND l.status <> 3 AND p.category_id IS NOT NULL "
            + "GROUP BY p.category_id")
    List<Map<String, Object>> categoryCounts(@Param("siteId") Long siteId);
}
