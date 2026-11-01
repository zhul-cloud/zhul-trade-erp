package com.zhul.erp.modules.sales.dto;

import lombok.Data;

import java.math.BigDecimal;

/** PI 与订单的余额面板（spec sales/proforma-invoice「PI 抵扣客户余额」、sales/payment-receipt） */
@Data
public class CreditPanelVO {
    /** 客户在本币种、本事业部的可用余额（已扣除别处的占用），以及本单当前占用的金额 */
    private BigDecimal available;
    private BigDecimal occupiedHere;
    /** 其他事业部同币种的可用余额（不能用于本单，提示用） */
    private BigDecimal otherUnits;
    /** 已到账中的余额抵扣 */
    private BigDecimal creditReceived;
    /** 代收：金额、客户承担的平台手续费、净额、是否已转入余额、转入的人民币价值 */
    private BigDecimal collection;
    private BigDecimal collectionFee;
    private BigDecimal collectionNet;
    private Boolean collectionTransferred;
    private BigDecimal collectionValue;
    /** 多收与待处理多收 */
    private BigDecimal overpaid;
    private BigDecimal overpaidPending;
}
