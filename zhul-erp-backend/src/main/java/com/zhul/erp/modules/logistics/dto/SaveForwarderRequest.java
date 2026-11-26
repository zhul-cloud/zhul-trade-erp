package com.zhul.erp.modules.logistics.dto;

import com.zhul.erp.modules.masterdata.dto.SupplierBankAccountRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 新增、修改货代（spec logistics/forwarder「货代管理」） */
@Data
public class SaveForwarderRequest {
    @NotBlank(message = "请填写货代名称")
    @Size(max = 100, message = "名称不能超过100个字符")
    private String name;
    @Size(max = 50, message = "简称不能超过50个字符")
    private String shortName;
    /** 归属客户：空为公司货代 */
    private Long customerId;
    /** 交接方式（1-送到货代、2-上门取件）；只有客户指定货代能选上门取件，空为送到货代 */
    @Min(value = 1, message = "交接方式不正确")
    @Max(value = 2, message = "交接方式不正确")
    private Integer handoverMode;
    /** 上门取件的承运商 */
    @Size(max = 32, message = "承运商不能超过32个字符")
    private String courierCarrier;
    /** 上门取件的客户快递账号（选填） */
    @Size(max = 32, message = "客户快递账号不能超过32个字符")
    private String courierAccount;
    @Size(max = 50, message = "联系人不能超过50个字符")
    private String contactName;
    @Size(max = 20, message = "联系电话不能超过20个字符")
    private String contactPhone;
    @Email(message = "请输入正确的邮箱格式")
    @Size(max = 100, message = "联系邮箱不能超过100个字符")
    private String contactEmail;
    @Size(max = 200, message = "地址不能超过200个字符")
    private String address;
    @Min(value = 1000, message = "体积系数为 1000 – 10000")
    @Max(value = 10000, message = "体积系数为 1000 – 10000")
    private Integer volumeDivisor;
    /** 收款账户；null 表示不修改，空列表表示清空 */
    @Valid
    @Size(max = 10, message = "每个货代最多 10 个收款账户")
    private List<SupplierBankAccountRequest> accounts;
    @Size(max = 500, message = "备注不能超过500个字符")
    private String remark;
    /** 0-停用、1-启用；新增时默认启用，修改时为空表示不变 */
    private Integer status;
}
