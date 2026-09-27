package com.zhul.erp.modules.inquiry.customerinquiry.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 客户询盘附件（图片/Excel）上传结果。 */
@Data
@AllArgsConstructor
public class AttachmentVO {
    /** 可通过浏览器直接访问的相对URL，如 /uploads/customer-inquiry/1/xxx.png */
    private String url;
    /** 原始文件名，仅用于前端展示 */
    private String filename;
}
