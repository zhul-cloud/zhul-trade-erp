package com.zhul.erp.modules.video.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhul.erp.modules.video.entity.VideoTaskDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface VideoTaskMapper extends BaseMapper<VideoTaskDO> {

    /** 品牌、品类（含细分品类）、关键词（型号、名称）筛选 */
    String FILTER = "<if test='brandId != null or categoryId != null or keyword != null'> AND v.product_id IN (SELECT p.id FROM product p "
            + "WHERE p.tenant_id = 0 AND p.deleted_at IS NULL"
            + "<if test='brandId != null'> AND p.brand_id = #{brandId}</if>"
            + "<if test='categoryId != null'> AND (p.category_id = #{categoryId} OR p.category_id IN (SELECT c.id FROM product_category c "
            + "WHERE c.parent_id = #{categoryId} AND c.deleted_at IS NULL))</if>"
            + "<if test='keyword != null'> AND (p.product_name LIKE CONCAT('%', #{keyword}, '%') OR p.mpn_raw LIKE CONCAT('%', #{keyword}, '%')"
            + "<if test='mpn != null'> OR p.mpn_normalized LIKE CONCAT('%', #{mpn}, '%')</if>)</if>)</if>";

    String WHERE = "FROM product_video_task v WHERE v.tenant_id = #{tenantId} AND v.deleted_at IS NULL "
            + "<if test='statuses != null'> AND v.status IN <foreach collection='statuses' item='s' open='(' separator=',' close=')'>#{s}</foreach></if>"
            + FILTER;

    @Select("<script>SELECT v.id " + WHERE + " ORDER BY v.update_time DESC, v.id DESC LIMIT #{offset}, #{size}</script>")
    List<Long> pageIds(@Param("tenantId") Integer tenantId, @Param("statuses") List<Integer> statuses,
                       @Param("brandId") Long brandId, @Param("categoryId") Long categoryId,
                       @Param("keyword") String keyword, @Param("mpn") String mpn,
                       @Param("offset") long offset, @Param("size") long size);

    @Select("<script>SELECT COUNT(*) " + WHERE + "</script>")
    long count(@Param("tenantId") Integer tenantId, @Param("statuses") List<Integer> statuses,
               @Param("brandId") Long brandId, @Param("categoryId") Long categoryId,
               @Param("keyword") String keyword, @Param("mpn") String mpn);

    /** 各状态的任务数（按同样的筛选条件） */
    @Select("<script>SELECT v.status AS status, COUNT(*) AS cnt FROM product_video_task v WHERE v.tenant_id = #{tenantId} "
            + "AND v.deleted_at IS NULL " + FILTER + " GROUP BY v.status</script>")
    List<Map<String, Object>> statusCounts(@Param("tenantId") Integer tenantId, @Param("brandId") Long brandId,
                                           @Param("categoryId") Long categoryId, @Param("keyword") String keyword,
                                           @Param("mpn") String mpn);

    /**
     * 认领一个排队中的任务开始生成：只有它仍在排队、且全服务器没有生成中的任务时才成功（同一时间只生成一个）。
     * 跨租户的队列，调用方在 TenantScope.platform 内执行。
     */
    @Update("UPDATE product_video_task SET status = 3, started_at = #{now}, finished_at = NULL, fail_reason = '', "
            + "current_version = current_version + 1, update_time = #{now}, update_by = 'sys' "
            + "WHERE id = #{id} AND status = 2 AND deleted_at IS NULL "
            + "AND NOT EXISTS (SELECT 1 FROM (SELECT id FROM product_video_task WHERE status = 3 AND deleted_at IS NULL) g)")
    int claim(@Param("id") Long id, @Param("now") LocalDateTime now);
}
