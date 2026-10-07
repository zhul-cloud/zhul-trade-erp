package com.zhul.erp.modules.sales.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** PI 详情：表头、正在查看的版本内容、版本列表、收款与订单 */
@Data
public class PiVO {
    private Long id;
    private String piNo;
    private Long customerId;
    private String customerName;
    /** 客户国家：贸易术语 DAP 的默认地点 */
    private String customerCountry;
    private Long ownerId;
    private String ownerName;
    private String currencyCode;
    private BigDecimal exchangeRate;
    private LocalDateTime rateTime;
    private Integer status;
    private String statusName;
    private Integer receiptStatus;
    private String receiptStatusName;
    private BigDecimal receivedAmount;
    private BigDecimal feeDiffAmount;
    /** 当前有效版本合计 − 已到账 − 手续费差额 */
    private BigDecimal remainingAmount;
    private Integer currentVersionNo;
    private Integer editingVersionNo;
    /** 正在查看的是编辑中的版本 */
    private Boolean editable;
    /** 有效期至（当前有效版本）与是否已过期（已发送、未付款且过了有效期） */
    private java.time.LocalDate validUntil;
    private Boolean expired;
    private Long expiredDays;
    /** 已关闭时的原因、说明、关闭人与时间 */
    private String closeReason;
    private String closeReasonName;
    private String closeNote;
    private LocalDateTime closedAt;
    private String closedByName;
    /** 关闭 PI 时给业务员的提示（只在关闭接口的返回里有） */
    private List<String> notices;
    /** 正在查看的版本内容 */
    private PiVersionVO version;
    private List<VersionBrief> versions;
    private List<SendLog> sendLogs;
    private List<ReceiptVO> receipts;
    /** 有效销售订单 */
    private OrderBrief order;
    /** 来源报价单 */
    private List<QuotationBrief> quotations;
    private Boolean hasBankAccount;
    private LocalDateTime createTime;

    @Data
    public static class VersionBrief {
        private Integer versionNo;
        private Integer status;
        private BigDecimal totalAmount;
        private LocalDateTime sentAt;
        private LocalDateTime createTime;
    }

    @Data
    public static class SendLog {
        private Integer versionNo;
        private Integer channel;
        private String channelName;
        private String sentByName;
        private LocalDateTime sentAt;
    }

    @Data
    public static class OrderBrief {
        private Long id;
        private String soNo;
        private Integer status;
        private LocalDateTime createTime;
    }

    @Data
    public static class QuotationBrief {
        private Long id;
        private String quotationNo;
        private Integer status;
    }
}
