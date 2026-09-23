package com.zhul.erp.modules.tenant.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/** 新增/编辑租户共用请求体；租户编码由后端生成，不在此处提交 */
@Data
public class SaveTenantRequest {
    @NotBlank(message = "租户名称不能为空")
    @Size(min = 2, max = 100, message = "租户名称长度需为 2~100 字符")
    private String name;

    @NotNull(message = "请选择套餐")
    private Integer packageId;

    @NotBlank(message = "联系人姓名不能为空")
    @Size(min = 2, max = 50, message = "联系人姓名长度需为 2~50 字符")
    private String contactName;

    @NotBlank(message = "联系人手机不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "请输入正确的手机号码")
    private String contactPhone;

    @NotBlank(message = "联系人邮箱不能为空")
    @Email(message = "请输入正确的邮箱地址")
    private String contactEmail;

    @NotNull(message = "请选择到期日期")
    private LocalDate expireDate;

    @Size(max = 500, message = "备注最多 500 字符")
    private String remark;
}
