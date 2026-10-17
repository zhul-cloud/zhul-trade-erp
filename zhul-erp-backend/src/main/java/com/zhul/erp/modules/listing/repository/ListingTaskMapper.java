package com.zhul.erp.modules.listing.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhul.erp.modules.listing.entity.ListingTaskDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Mapper
public interface ListingTaskMapper extends BaseMapper<ListingTaskDO> {

    /** 这个平台当前营销内容的写入时间（独立站取英文、俄文较晚者） */
    String CONTENT_AT = "(CASE l.platform WHEN 'alibaba' THEN t.alibaba_at WHEN 'mic' THEN t.mic_at "
            + "WHEN 'social' THEN t.social_at ELSE GREATEST(COALESCE(t.en_at, t.ru_at), COALESCE(t.ru_at, t.en_at)) END)";

    /** 本型号有用于发品的素材 */
    String HAS_OWN = "EXISTS (SELECT 1 FROM media_item m WHERE m.tenant_id = l.tenant_id AND m.asset_key = l.asset_key "
            + "AND m.for_listing = 1 AND m.kind IN (1, 2) AND m.deleted_at IS NULL)";

    String NEED_MEDIA = "(l.status = 1 AND l.ref_model = '' AND NOT " + HAS_OWN + ")";
    String UPDATED = "(l.status = 2 AND l.content_at IS NOT NULL AND " + CONTENT_AT + " &gt; l.content_at)";
    String NEED_REPLACE = "(l.status = 2 AND l.ref_model != '' AND " + HAS_OWN + ")";

    String FROM = "FROM listing_task l LEFT JOIN product_content_task t ON t.tenant_id = l.tenant_id "
            + "AND t.product_id = l.product_id AND t.deleted_at IS NULL "
            + "WHERE l.tenant_id = #{tenantId} AND l.deleted_at IS NULL ";

    String FILTER = "<if test='platform != null'> AND l.platform = #{platform}</if>"
            + "<if test='brandId != null or categoryId != null or keyword != null'> AND l.product_id IN (SELECT p.id FROM product p "
            + "WHERE p.tenant_id = 0 AND p.deleted_at IS NULL"
            + "<if test='brandId != null'> AND p.brand_id = #{brandId}</if>"
            + "<if test='categoryId != null'> AND (p.category_id = #{categoryId} OR p.category_id IN (SELECT c.id FROM product_category c "
            + "WHERE c.parent_id = #{categoryId} AND c.deleted_at IS NULL))</if>"
            + "<if test='keyword != null'> AND (p.product_name LIKE CONCAT('%', #{keyword}, '%') OR p.mpn_raw LIKE CONCAT('%', #{keyword}, '%')"
            + "<if test='mpn != null'> OR p.mpn_normalized LIKE CONCAT('%', #{mpn}, '%')</if>)</if>)</if>";

    String TAB = "<choose>"
            + "<when test=\"tab == 'pending'\"> AND l.status = 1</when>"
            + "<when test=\"tab == 'updated'\"> AND " + UPDATED + "</when>"
            + "<when test=\"tab == 'replace'\"> AND " + NEED_REPLACE + "</when>"
            + "<when test=\"tab == 'published'\"> AND l.status = 2</when>"
            + "<when test=\"tab == 'cancelled'\"> AND l.status = 3</when>"
            + "</choose>";

    @Select("<script>SELECT l.id, l.tenant_id, l.product_id, l.platform, l.asset_key, l.status, l.cancel_reason, l.ref_brand, "
            + "l.ref_model, l.ref_by, l.ref_time, l.listing_url, l.platform_item_id, l.note, l.published_by, l.published_at, "
            + "l.content_at, l.synced_by, l.synced_at, l.replaced_by, l.replaced_at, l.create_time, l.create_by, "
            + "l.update_time, l.update_by, " + NEED_MEDIA + " AS need_media, " + UPDATED + " AS content_updated, "
            + NEED_REPLACE + " AS need_replace "
            + FROM + FILTER + TAB + " ORDER BY l.update_time DESC, l.id DESC LIMIT #{offset}, #{size}</script>")
    List<ListingTaskRow> page(@Param("tenantId") Integer tenantId, @Param("tab") String tab, @Param("platform") String platform,
                              @Param("brandId") Long brandId, @Param("categoryId") Long categoryId,
                              @Param("keyword") String keyword, @Param("mpn") String mpn,
                              @Param("offset") long offset, @Param("size") long size);

    @Select("<script>SELECT COUNT(*) " + FROM + FILTER + TAB + "</script>")
    long count(@Param("tenantId") Integer tenantId, @Param("tab") String tab, @Param("platform") String platform,
               @Param("brandId") Long brandId, @Param("categoryId") Long categoryId,
               @Param("keyword") String keyword, @Param("mpn") String mpn);

    /** 各页签数量：pending、updated、replace、published、cancelled、all */
    @Select("<script>SELECT COALESCE(SUM(l.status = 1), 0) AS pending, COALESCE(SUM(" + UPDATED + "), 0) AS updated, "
            + "COALESCE(SUM(" + NEED_REPLACE + "), 0) AS `replace`, COALESCE(SUM(l.status = 2), 0) AS published, "
            + "COALESCE(SUM(l.status = 3), 0) AS cancelled, COUNT(*) AS `all` " + FROM + FILTER + "</script>")
    Map<String, Object> counts(@Param("tenantId") Integer tenantId, @Param("platform") String platform,
                               @Param("brandId") Long brandId, @Param("categoryId") Long categoryId,
                               @Param("keyword") String keyword, @Param("mpn") String mpn);

    /** 一个任务连同提醒（详情、操作前校验用） */
    @Select("<script>SELECT l.id, " + NEED_MEDIA + " AS need_media, " + UPDATED + " AS content_updated, "
            + NEED_REPLACE + " AS need_replace, " + HAS_OWN + " AS has_own, " + CONTENT_AT + " AS current_content_at "
            + "FROM listing_task l LEFT JOIN product_content_task t ON t.tenant_id = l.tenant_id "
            + "AND t.product_id = l.product_id AND t.deleted_at IS NULL WHERE l.id = #{id}</script>")
    Map<String, Object> flags(@Param("id") Long id);

    @Select("SELECT id FROM listing_task WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted_at IS NULL FOR UPDATE")
    Long lock(@Param("id") Long id, @Param("tenantId") Integer tenantId);

    /** 有该商品营销任务的公司（资料核对后对这些公司重算） */
    @Select("<script>SELECT DISTINCT tenant_id FROM product_content_task WHERE deleted_at IS NULL AND product_id IN "
            + "<foreach collection='productIds' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    List<Integer> tenantsOf(@Param("productIds") Collection<Long> productIds);

    /** 有营销写入的公司（启动补齐用，显式跨公司） */
    @Select("SELECT DISTINCT tenant_id FROM product_content_task WHERE deleted_at IS NULL "
            + "AND (alibaba_at IS NOT NULL OR mic_at IS NOT NULL OR social_at IS NOT NULL OR en_at IS NOT NULL OR ru_at IS NOT NULL)")
    List<Integer> tenantsWithContent();

    /** 这家公司有营销写入的商品（平台设置修改后重算） */
    @Select("SELECT product_id FROM product_content_task WHERE tenant_id = #{tenantId} AND deleted_at IS NULL "
            + "AND (alibaba_at IS NOT NULL OR mic_at IS NOT NULL OR social_at IS NOT NULL OR en_at IS NOT NULL OR ru_at IS NOT NULL) "
            + "ORDER BY product_id")
    List<Long> writtenProducts(@Param("tenantId") Integer tenantId);
}
