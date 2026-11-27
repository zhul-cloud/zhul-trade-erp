package com.zhul.erp.modules.website.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhul.erp.modules.website.entity.WebsiteProductDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.Map;

@Mapper
public interface WebsiteProductMapper extends BaseMapper<WebsiteProductDO> {

    /**
     * 本公司近期这个商品有货的最低询价回价（已提交、未删除、不含税人民币单价大于 0）；
     * 回价经询价型号 inquiry_item.product_id 关联商品。同价取最近的一条。返回 quote_id、unit_price_cny、quoted_at，没有时为 null。两张表都由租户拦截器限定本公司。
     */
    @Select("SELECT q.id AS quote_id, q.unit_price_cny, q.quoted_at "
            + "FROM sourcing_quote q JOIN inquiry_item i ON i.id = q.inquiry_item_id "
            + "WHERE i.product_id = #{productId} AND q.status = 2 AND q.no_stock = 0 AND q.unit_price_cny > 0 "
            + "AND q.deleted_at IS NULL AND q.quoted_at >= #{since} "
            + "ORDER BY q.unit_price_cny ASC, q.quoted_at DESC LIMIT 1")
    Map<String, Object> lowestQuote(@Param("productId") Long productId, @Param("since") LocalDateTime since);
}
