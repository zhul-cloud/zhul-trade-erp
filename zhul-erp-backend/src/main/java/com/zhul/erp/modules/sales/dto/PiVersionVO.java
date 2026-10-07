package com.zhul.erp.modules.sales.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** PI 某个版本的完整内容 */
@Data
public class PiVersionVO {
    private Integer versionNo;
    /** 版本状态（1-编辑中、2-已发送、3-已放弃） */
    private Integer status;
    private PartyDTO buyer;
    private PartyDTO consignee;
    private String deliveryTime;
    private String paymentTerm;
    private String incoterm;
    private String incotermPlace;
    private String portOfShipment;
    private String remark;
    private BankSnapshotDTO bankAccount;
    private Integer discountType;
    private BigDecimal discountValue;
    private BigDecimal discountAmount;
    private BigDecimal itemAmount;
    private BigDecimal feeAmount;
    private BigDecimal totalAmount;
    private BigDecimal totalAmountCny;
    private BigDecimal netProfit;
    private BigDecimal netProfitCny;
    private BigDecimal marginRate;
    private LocalDateTime sentAt;
    private List<PiItemVO> items;
    private List<PiFeeVO> fees;
}
