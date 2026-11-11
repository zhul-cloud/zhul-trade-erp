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
     * 已被未删除的业务单据引用的客户 ID（删除限制用）：询盘、商机、报价、PI、销售订单、出库、出运、售后、
     * 客户余额与退款。已取消 / 作废但未删除的单据同样算引用（客户可改为禁用）。客户自己的联系人、单证、
     * 跟进、背调等附属资料不算。直接查各表，避免客户模块依赖业务模块。
     */
    @Select("<script>"
            + "SELECT customer_id FROM customer_inquiry WHERE deleted_at IS NULL AND customer_id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + " UNION SELECT customer_id FROM inquiry_order WHERE deleted_at IS NULL AND customer_id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + " UNION SELECT customer_id FROM opportunity WHERE deleted_at IS NULL AND customer_id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + " UNION SELECT customer_id FROM quotation WHERE deleted_at IS NULL AND customer_id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + " UNION SELECT customer_id FROM proforma_invoice WHERE deleted_at IS NULL AND customer_id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + " UNION SELECT customer_id FROM sales_order WHERE deleted_at IS NULL AND customer_id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + " UNION SELECT customer_id FROM outbound_order WHERE deleted_at IS NULL AND customer_id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + " UNION SELECT customer_id FROM logistics_shipment WHERE deleted_at IS NULL AND customer_id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + " UNION SELECT customer_id FROM after_sales WHERE deleted_at IS NULL AND customer_id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + " UNION SELECT customer_id FROM fin_customer_credit_entry WHERE deleted_at IS NULL AND customer_id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + " UNION SELECT customer_id FROM fin_customer_refund WHERE deleted_at IS NULL AND customer_id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + "</script>")
    List<Long> selectReferencedCustomerIds(@Param("ids") Collection<Long> ids);

    /** 行锁：同一客户的背调发起串行执行（同时只能一份背调中） */
    @Select("SELECT id FROM customer WHERE id = #{id} FOR UPDATE")
    Long lockById(@Param("id") Long id);
}
