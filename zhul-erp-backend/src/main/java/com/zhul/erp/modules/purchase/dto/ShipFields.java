package com.zhul.erp.modules.purchase.dto;

import java.time.LocalDate;

/** 采购单列表与详情共有的预计发货日期与发货进度字段（Lombok 生成实现） */
public interface ShipFields {
    void setExpectedShipDate(LocalDate v);

    void setShipProgress(String v);

    void setShipProgressName(String v);

    void setShippedQty(Integer v);

    void setTotalQty(Integer v);

    void setOverdueDays(Integer v);

    void setEarliestArrival(LocalDate v);
}
