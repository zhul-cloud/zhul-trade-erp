package com.zhul.erp.modules.sales.dto;

import com.zhul.erp.modules.attachment.dto.AttachmentVO;
import com.zhul.erp.modules.system.doclog.DocLogVO;
import com.zhul.erp.modules.system.doclog.DocRefVO;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 收款单详情（change finance-cash-docs）：表头沿用收款单列表的行，另加凭证文件、补充附件、结汇分摊、关联单据与日志 */
@Data
public class ReceiptDocVO {
    private ReceiptRowVO receipt;
    /** 登记时上传的到账凭证与对应水单的文件（只读），index 用于取访问链接 */
    private List<DocFile> files;
    /** 补充上传的附件（归属 PAYMENT_RECEIPT） */
    private List<AttachmentVO> attachments;
    /** 外币收款单的结汇分摊（有效结汇单，按结汇先后） */
    private List<Settlement> settlements;
    /** 未结汇余额（原币，实收口径）；人民币为空 */
    private BigDecimal unsettledAmount;
    /** 未结汇部分按估算汇率折算的人民币 */
    private BigDecimal unsettledAmountCny;
    private List<DocRefVO> related;
    private List<DocLogVO> logs;
    /** 当前用户能否改备注、增删附件（「收款管理」菜单且有效） */
    private Boolean editable;

    @Data
    public static class DocFile {
        private Integer index;
        private String fileName;
        /** RECEIPT 到账凭证、SLIP 对应水单 */
        private String source;
    }

    @Data
    public static class Settlement {
        private Long settlementId;
        private String settleNo;
        private LocalDate settleDate;
        private BigDecimal amount;
        private BigDecimal exchangeRate;
        private BigDecimal amountCny;
    }
}
