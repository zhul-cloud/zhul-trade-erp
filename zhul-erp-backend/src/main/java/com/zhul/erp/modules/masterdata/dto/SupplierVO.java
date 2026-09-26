package com.zhul.erp.modules.masterdata.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 供应商出参。bankAccount 一律是脱敏值，明文只在 {@link SupplierFormVO} 里返回 */
@Data
public class SupplierVO {
    private Long id;
    private String supplierCode;
    private String name;
    private String shortName;
    private Integer supplierType;
    private Integer industry;
    private String creditCode;
    private String legalRepresentative;
    /** 注册资本，单位万元人民币 */
    private BigDecimal registeredCapital;
    private LocalDate establishedDate;
    private String country;
    private String contactName;
    private String contactPhone;
    private String contactEmail;
    private String region;
    private String address;
    private String bankName;
    private String bankAccount;
    /** 主营产品：按品牌分组 */
    private List<SupplierProductScopeVO> productScopes;
    private String remark;
    private Integer status;
    private LocalDateTime createTime;
    private String createBy;
    private LocalDateTime updateTime;
    private String updateBy;
}
