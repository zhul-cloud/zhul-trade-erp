package com.zhul.erp.modules.website.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhul.erp.modules.website.entity.WebsiteSiteDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface WebsiteSiteMapper extends BaseMapper<WebsiteSiteDO> {

    /** 锁住站点行：同一站点的发布单派发、发起在这把锁下串行（多实例也有效） */
    @Select("SELECT id FROM website_site WHERE id = #{id} AND tenant_id = #{tenantId} FOR UPDATE")
    Long lock(@Param("id") Long id, @Param("tenantId") Integer tenantId);
}
