package com.zhul.erp.modules.masterdata.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhul.erp.modules.masterdata.dto.PendingBrandVO;
import com.zhul.erp.modules.masterdata.entity.SupplierProductScopeDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface SupplierProductScopeMapper extends BaseMapper<SupplierProductScopeDO> {

    /**
     * 待确认品牌汇总。有意跨租户读取：只供平台账号确认品牌用，只返回名称与计数，不返回租户和供应商。
     * 已删除供应商的行不计入。
     */
    @Select("SELECT pending_key AS pendingKey, MIN(pending_brand_name) AS name, "
            + "COUNT(DISTINCT tenant_id, supplier_id) AS supplierCount, MIN(create_time) AS firstSeen "
            + "FROM supplier_product_scope WHERE brand_id IS NULL AND pending_key <> '' AND deleted_at IS NULL"
            + " AND EXISTS (SELECT 1 FROM supplier s WHERE s.id = supplier_product_scope.supplier_id AND s.deleted_at IS NULL) "
            + "GROUP BY pending_key ORDER BY supplierCount DESC, firstSeen")
    List<PendingBrandVO> selectPendingBrands();

    /** 使用某待确认品牌名的供应商（跨租户，确认品牌时批量关联用） */
    @Select("SELECT DISTINCT supplier_id FROM supplier_product_scope "
            + "WHERE pending_key = #{pendingKey} AND brand_id IS NULL AND deleted_at IS NULL")
    List<Long> selectSupplierIdsByPendingKey(@Param("pendingKey") String pendingKey);

    /** 把某待确认品牌名的所有行关联到正式品牌（跨租户） */
    @Update("UPDATE supplier_product_scope SET brand_id = #{brandId}, pending_brand_name = '', pending_key = '', "
            + "update_time = NOW(), update_by = #{operator} "
            + "WHERE pending_key = #{pendingKey} AND brand_id IS NULL AND deleted_at IS NULL")
    int linkPendingToBrand(@Param("pendingKey") String pendingKey, @Param("brandId") Long brandId,
                           @Param("operator") String operator);
}
