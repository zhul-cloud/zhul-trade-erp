package com.zhul.erp.modules.sales.dto;

import com.zhul.erp.common.result.DocRefVO;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** PI 列表行 */
@Data
public class PiListVO {
    private Long id;
    private String piNo;
    private Long customerId;
    private String customerName;
    /** 客户主联系人姓名（客户档案中的主联系人，实时读取） */
    private String customerContact;
    /** 客户国家 */
    private String customerCountry;
    /** 1-新客户、2-老客户（按来源询盘单判断） */
    private Integer customerType;
    /** 总数量：全部型号数量之和 */
    private Integer totalQuantity;
    private Integer itemCount;
    private String currencyCode;
    private BigDecimal totalAmount;
    private Integer receiptStatus;
    private String receiptStatusName;
    private Integer status;
    private String statusName;
    private Integer currentVersionNo;
    private Boolean revising;
    private List<String> quotationNos;
    /** 来源单号：报价单（按 ID 排序） */
    private List<DocRefVO> quotations;
    private Long orderId;
    private String soNo;
    private Long ownerId;
    private String ownerName;
    private LocalDateTime createTime;
    private LocalDateTime sentAt;
    /** 有效期至（当前有效版本） */
    private java.time.LocalDate validUntil;
    /** 已发送、未付款且过了有效期 */
    private Boolean expired;
    /** 已过期天数，没过期时为空 */
    private Long expiredDays;
    /** 已关闭时的原因 */
    private String closeReasonName;
}
