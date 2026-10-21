package com.zhul.erp.modules.media.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhul.erp.modules.media.dto.MediaModelRow;
import com.zhul.erp.modules.media.entity.MediaItemDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MediaItemMapper extends BaseMapper<MediaItemDO> {

    String MODEL_FILTER = "FROM media_item WHERE tenant_id = #{tenantId} AND deleted_at IS NULL"
            + "<if test='keyword != null'> AND (brand LIKE #{keyword} OR model LIKE #{keyword})</if>"
            + " GROUP BY asset_key"
            + "<if test='hasMarketing != null or hasListing != null'> HAVING 1 = 1"
            + "<if test='hasMarketing == true'> AND SUM(category = 9) &gt; 0</if>"
            + "<if test='hasMarketing == false'> AND SUM(category = 9) = 0</if>"
            + "<if test='hasListing == true'> AND SUM(for_listing = 1) &gt; 0</if>"
            + "<if test='hasListing == false'> AND SUM(for_listing = 1) = 0</if>"
            + "</if>";

    /** 有素材的型号（按最近新增素材时间倒序）；keyword 已转成 LIKE 模式 */
    @Select("<script>SELECT asset_key AS assetKey, MAX(brand) AS brand, MAX(model) AS model, MAX(create_time) AS lastAddedAt, "
            + "SUM(category = 9) AS marketingCount, SUM(can_send = 1) AS sendableCount, SUM(for_listing = 1) AS listingCount "
            + MODEL_FILTER + " ORDER BY lastAddedAt DESC, assetKey LIMIT #{offset}, #{size}</script>")
    List<MediaModelRow> pageModels(@Param("tenantId") int tenantId, @Param("keyword") String keyword,
                                   @Param("hasMarketing") Boolean hasMarketing, @Param("hasListing") Boolean hasListing,
                                   @Param("offset") long offset, @Param("size") int size);

    @Select("<script>SELECT COUNT(*) FROM (SELECT asset_key " + MODEL_FILTER + ") t</script>")
    long countModels(@Param("tenantId") int tenantId, @Param("keyword") String keyword,
                     @Param("hasMarketing") Boolean hasMarketing, @Param("hasListing") Boolean hasListing);
}
