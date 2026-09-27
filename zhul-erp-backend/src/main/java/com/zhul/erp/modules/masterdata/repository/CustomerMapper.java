package com.zhul.erp.modules.masterdata.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhul.erp.modules.masterdata.entity.CustomerDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

@Mapper
public interface CustomerMapper extends BaseMapper<CustomerDO> {

    /**
     * 已被未删除的客户询盘或询盘单引用的客户 ID（删除限制用）。直接查询盘两张表，
     * 避免客户模块依赖询盘模块；报价、订单模块上线后在这里补充。
     */
    @Select("<script>"
            + "SELECT customer_id FROM customer_inquiry WHERE deleted_at IS NULL AND customer_id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + " UNION "
            + "SELECT customer_id FROM inquiry_order WHERE deleted_at IS NULL AND customer_id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + "</script>")
    List<Long> selectReferencedCustomerIds(@Param("ids") Collection<Long> ids);
}
