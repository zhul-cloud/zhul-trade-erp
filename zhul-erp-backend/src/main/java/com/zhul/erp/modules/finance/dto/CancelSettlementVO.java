package com.zhul.erp.modules.finance.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 采购订单取消预览：一笔实付及选「会退款」后的处理方式（openspec po-cancel-refund） */
@Data
public class CancelSettlementVO {
    /** 作废这笔垫付 */
    public static final int VOID = 1;
    /** 以垫付人名义登记已收到退款 */
    public static final int RECEIVED = 2;
    /** 应付单标记待退款，财务到账后登记 */
    public static final int REFUND_EXPECTED = 3;

    private Long paymentId;
    private String paymentNo;
    /** 1-公司对公、2-员工垫付、3-私对私 */
    private Integer method;
    private String advanceUser;
    /** 0-不适用、1-待报销、2-已进报销单 */
    private Integer reimburseStatus;
    private String currencyCode;
    /** 这笔付款核销到本应付单的金额（原币） */
    private BigDecimal amount;
    private BigDecimal amountCny;
    /** 选「会退款」后的处理：1-作废、2-登记已收到退款、3-标记待退款 */
    private Integer action;
}
