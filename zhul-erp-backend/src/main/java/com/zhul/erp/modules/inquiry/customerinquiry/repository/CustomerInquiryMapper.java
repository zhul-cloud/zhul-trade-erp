package com.zhul.erp.modules.inquiry.customerinquiry.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.InquiryDownstreamVO;
import com.zhul.erp.modules.inquiry.customerinquiry.entity.CustomerInquiryDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CustomerInquiryMapper extends BaseMapper<CustomerInquiryDO> {

    /** 当日已有询盘编号里最大的3位流水号（不存在时返回 NULL），用于生成下一个编号。 */
    @Select("SELECT MAX(CAST(SUBSTRING(inquiry_code, -3) AS UNSIGNED)) FROM customer_inquiry " +
            "WHERE tenant_id = #{tenantId} AND inquiry_code LIKE CONCAT(#{prefix}, '%')")
    Integer selectMaxSequenceByPrefix(@Param("tenantId") int tenantId, @Param("prefix") String prefix);

    /** 锁住询盘单行：多人同时提交回价时串行重算进度（design.md 决策 12） */
    @Select("SELECT id FROM customer_inquiry WHERE id = #{id} FOR UPDATE")
    Long lockById(@Param("id") Long id);

    /** 引用询盘单、未取消或作废的下游单据：询价任务、报价单、PI、销售订单 */
    @Select("SELECT 'TASK' AS kind, t.id, t.task_code AS no, "
            + "ELT(t.status, '待分配', '询价中', '已回价', '已取消') AS status_name FROM sourcing_task t "
            + "WHERE t.tenant_id = #{tenantId} AND t.customer_inquiry_id = #{id} AND t.deleted_at IS NULL AND t.status <> 4 "
            + "UNION ALL SELECT 'QUOTATION', q.id, q.quotation_no, ELT(q.status, '草稿', '已发送', '已成交', '未成交', '已作废') "
            + "FROM quotation q WHERE q.tenant_id = #{tenantId} AND q.deleted_at IS NULL AND q.status <> 5 AND EXISTS ("
            + "SELECT 1 FROM quotation_item i WHERE i.quotation_id = q.id AND i.customer_inquiry_id = #{id}) "
            + "UNION ALL SELECT 'PI', p.id, p.pi_no, ELT(p.status, '草稿', '已发送', '已转订单', '已作废') "
            + "FROM proforma_invoice p WHERE p.tenant_id = #{tenantId} AND p.deleted_at IS NULL AND p.status <> 4 AND EXISTS ("
            + "SELECT 1 FROM proforma_invoice_item i WHERE i.pi_id = p.id AND i.customer_inquiry_id = #{id}) "
            + "UNION ALL SELECT 'ORDER', o.id, o.so_no, '有效' "
            + "FROM sales_order o WHERE o.tenant_id = #{tenantId} AND o.deleted_at IS NULL AND o.status <> 2 AND EXISTS ("
            + "SELECT 1 FROM sales_order_item i WHERE i.so_id = o.id AND i.customer_inquiry_id = #{id} AND i.deleted_at IS NULL)")
    List<InquiryDownstreamVO> selectDownstream(@Param("tenantId") int tenantId, @Param("id") Long id);
}
