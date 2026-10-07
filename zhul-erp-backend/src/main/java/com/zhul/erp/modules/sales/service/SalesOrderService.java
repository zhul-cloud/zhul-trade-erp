package com.zhul.erp.modules.sales.service;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.modules.sales.dto.ChainVO;
import com.zhul.erp.modules.sales.dto.SalesOrderListVO;
import com.zhul.erp.modules.sales.dto.SalesOrderPageQuery;
import com.zhul.erp.modules.sales.dto.SalesOrderVO;

/** 销售订单（最小闭环）：由 PI 转成、不可修改只能取消；与报价单、询盘的成交联动在同一事务内 */
public interface SalesOrderService {

    SalesOrderVO convert(Long piId);

    SalesOrderVO cancel(Long id, String reason);

    SalesOrderVO detail(Long id);

    PageResult<SalesOrderListVO> page(SalesOrderPageQuery query);

    /** type：inquiry / quotation / pi / order */
    ChainVO chain(String type, Long id);
}
