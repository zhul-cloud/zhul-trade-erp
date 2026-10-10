package com.zhul.erp.modules.sales.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhul.erp.modules.sales.entity.ProformaInvoiceDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ProformaInvoiceMapper extends BaseMapper<ProformaInvoiceDO> {

    /** 锁住 PI 行：发送、改版、收款登记、转订单与取消订单串行执行 */
    @Select("SELECT id FROM proforma_invoice WHERE id = #{id} FOR UPDATE")
    Long lockById(@Param("id") Long id);
}
