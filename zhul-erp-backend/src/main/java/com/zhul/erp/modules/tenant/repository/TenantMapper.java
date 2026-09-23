package com.zhul.erp.modules.tenant.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhul.erp.modules.tenant.entity.TenantDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

@Mapper
public interface TenantMapper extends BaseMapper<TenantDO> {

    @Select("<script>SELECT package_id AS id, COUNT(*) AS cnt FROM tenant WHERE package_id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> "
            + "GROUP BY package_id</script>")
    List<PackageTenantCount> countByPackageIds(@Param("ids") Collection<Integer> ids);
}
