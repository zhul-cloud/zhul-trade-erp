package com.zhul.erp.modules.masterdata.customerprofile.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 修改客户画像（按模块编辑或补全向导）：fields 列出这次要改的字段键（见 ProfileField），只改这些字段，
 * 没列出的字段即使带了值也不改。清空字段：列出字段键、值传空。
 */
@Data
public class ProfileUpdateRequest {
    @NotEmpty(message = "没有要保存的字段")
    private List<String> fields;
    private Integer customerRole;
    private Integer industry;
    @Size(max = 200, message = "官网不能超过 200 字")
    private String website;
    @Size(max = 300, message = "LinkedIn 不能超过 300 字")
    private String linkedin;
    @Size(max = 300, message = "其他社媒不能超过 300 字")
    private String socialUrl;
    @Min(value = 1800, message = "成立年份不正确")
    private Integer foundedYear;
    private Integer companySize;
    @Min(value = 0, message = "潜力评级为 1–5 星")
    @Max(value = 5, message = "潜力评级为 1–5 星")
    private Integer potentialRating;
    @Size(max = 300, message = "主营产品不能超过 300 字")
    private String mainProducts;
    @Size(max = 300, message = "现有中国供应商不能超过 300 字")
    private String cnSuppliers;
    @DecimalMin(value = "0", message = "年度采购量估算不能为负")
    private BigDecimal estAnnualPurchase;
    private Integer qualityReq;
    private Integer priceSensitivity;
    private Integer customsAbility;
    private Integer paymentMethod;
    @Min(value = 0, message = "定金比例为 0–100")
    @Max(value = 100, message = "定金比例为 0–100")
    private Integer depositRatio;
    @Min(value = 0, message = "账期为 0–365 天")
    @Max(value = 365, message = "账期为 0–365 天")
    private Integer paymentDays;
    @Valid
    @Size(max = 20, message = "现有使用品牌最多 20 个")
    private List<CustomerProfileVO.BrandRef> currentBrands;
    private Integer designAbility;
    private List<Integer> protocols;
    private List<Integer> voltages;
    private List<Integer> certifications;
    private Integer repairAbility;
    @Size(max = 50, message = "城市不能超过 50 字")
    private String city;
    @Size(max = 64, message = "时区不正确")
    private String timezone;
}
