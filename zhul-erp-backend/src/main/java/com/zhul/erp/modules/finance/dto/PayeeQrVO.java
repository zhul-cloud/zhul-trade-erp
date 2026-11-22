package com.zhul.erp.modules.finance.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 登记付款时显示的对方收款码（供应商资料里上传的支付宝、微信收款码） */
@Data
public class PayeeQrVO {
    /** 2-支付宝、3-微信 */
    private Integer payChannel;
    private String label;
    /** 图片的临时访问链接 */
    private String url;
    private String createBy;
    private LocalDateTime createTime;
}
