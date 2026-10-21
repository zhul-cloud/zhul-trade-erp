package com.zhul.erp.modules.listing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 标记已发布或修改发布记录 */
@Data
public class PublishRequest {
    @NotBlank(message = "请填写平台链接")
    @Size(max = 500, message = "平台链接最长 500 个字符")
    private String listingUrl;
    @Size(max = 64, message = "平台商品编号最长 64 个字符")
    private String platformItemId;
    @Size(max = 300, message = "备注最长 300 个字符")
    private String note;
}
