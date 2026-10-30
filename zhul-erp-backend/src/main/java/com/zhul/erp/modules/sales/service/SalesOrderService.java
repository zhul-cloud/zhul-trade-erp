package com.zhul.erp.modules.sales.service;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.modules.sales.dto.ChainVO;
import com.zhul.erp.modules.sales.dto.SalesOrderListVO;
import com.zhul.erp.modules.sales.dto.SalesOrderPageQuery;
import com.zhul.erp.modules.sales.dto.SalesOrderVO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 销售订单（最小闭环）：由 PI 转成、不可修改只能取消；与报价单、询盘的成交联动在同一事务内 */
public interface SalesOrderService {

    SalesOrderVO convert(Long piId, com.zhul.erp.modules.sales.dto.ConvertOrderRequest req);

    SalesOrderVO create(com.zhul.erp.modules.sales.dto.CreateOrderRequest req);

    SalesOrderVO updateSalesDate(Long id, java.time.LocalDate salesDate);

    SalesOrderVO updateProgress(Long id, com.zhul.erp.modules.sales.dto.OrderItemsRequest req);

    SalesOrderVO updatePurchaser(Long id, com.zhul.erp.modules.sales.dto.OrderItemsRequest req);

    SalesOrderVO updateStockType(Long id, com.zhul.erp.modules.sales.dto.OrderItemsRequest req);

    /** 客户已收货：订单置为已完成 */
    SalesOrderVO complete(Long id);

    com.zhul.erp.modules.sales.dto.SalesOrderStatsVO stats();

    /** 新建销售订单「按 PI 创建」的候选 PI */
    List<com.zhul.erp.modules.sales.dto.OrderCandidatePiVO> candidatePis(String keyword);

    SalesOrderVO cancel(Long id, String reason);

    /** 补传附件（PI、合同、客户 PO 等） */
    SalesOrderVO addAttachments(Long id, java.util.List<Long> attachmentIds);

    /** 删除附件（软删除） */
    SalesOrderVO removeAttachment(Long id, Long attachmentId);

    SalesOrderVO updateRemark(Long id, String remark);

    SalesOrderVO detail(Long id);

    PageResult<SalesOrderListVO> page(SalesOrderPageQuery query);

    /** type：inquiry / quotation / pi / order */
    ChainVO chain(String type, Long id);

    /** 数据范围内有效订单在期间内的实收人民币（口径同订单列表顶部「本月实收」） */
    BigDecimal receivedNetCny(LocalDate from, LocalDate to);
}
