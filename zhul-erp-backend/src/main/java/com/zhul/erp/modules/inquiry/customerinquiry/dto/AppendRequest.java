package com.zhul.erp.modules.inquiry.customerinquiry.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 追加型号：粘贴内容或附件，选 AI 解析或手动录入 */
@Data
public class AppendRequest {
    @Size(max = 20000, message = "追加内容不能超过 20000 字")
    private String rawContent;
    @Valid
    @Size(max = 1000, message = "附件太多，请分几次上传")
    private List<AttachmentRef> attachments;
    /** 铭牌图片（spec inquiry/inquiry-intake「铭牌图片」）：只收 JPG、PNG，采购能看到 */
    @Valid
    @Size(max = 20, message = "铭牌图片最多 20 张")
    private List<AttachmentRef> nameplates;
    /** true 为手动录入（不调用 AI，直接进确认页） */
    private Boolean manual;
}
