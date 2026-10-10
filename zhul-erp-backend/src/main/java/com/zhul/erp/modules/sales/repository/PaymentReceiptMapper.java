package com.zhul.erp.modules.sales.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhul.erp.modules.sales.entity.PaymentReceiptDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PaymentReceiptMapper extends BaseMapper<PaymentReceiptDO> {
}
