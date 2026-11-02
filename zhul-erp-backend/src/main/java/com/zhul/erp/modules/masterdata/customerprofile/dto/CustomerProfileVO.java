package com.zhul.erp.modules.masterdata.customerprofile.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 客户画像（五个模块）、系统推导、完整度、经营等级、字段来源与联系人 */
@Data
public class CustomerProfileVO {
    private Long customerId;
    private String name;
    private String country;
    private String city;
    private String timezone;
    private Integer customerRole;
    private Integer industry;
    private String website;
    private String linkedin;
    private String socialUrl;
    private Integer foundedYear;
    private Integer companySize;
    private Integer potentialRating;
    private String mainProducts;
    private String cnSuppliers;
    private BigDecimal estAnnualPurchase;
    private String estPurchaseCurrency;
    private Integer qualityReq;
    private Integer priceSensitivity;
    private Integer customsAbility;
    private Integer paymentMethod;
    private Integer depositRatio;
    private Integer paymentDays;
    private List<BrandRef> currentBrands = new ArrayList<>();
    private Integer designAbility;
    private List<Integer> protocols = new ArrayList<>();
    private List<Integer> voltages = new ArrayList<>();
    private List<Integer> certifications = new ArrayList<>();
    private Integer repairAbility;
    private Derived derived;
    private CountryProfileVO countryProfile;
    private Completeness completeness;
    private Grade grade;
    /** 字段键 → 来源；联系人字段键为 contact.{联系人ID}.{字段} */
    private Map<String, Source> sources;
    private List<ContactVO> contacts = new ArrayList<>();

    @Data
    public static class BrandRef {
        private Long brandId;
        @Size(max = 64, message = "品牌不能超过 64 字")
        private String name;
    }

    @Data
    public static class BrandCount {
        private String name;
        private Long count;
    }

    /** 系统推导，不能手工修改 */
    @Data
    public static class Derived {
        private Long inquiryCount;
        private LocalDate firstInquiryDate;
        private LocalDate lastInquiryDate;
        private Integer ordersInYear;
        private BigDecimal salesCnyInYear;
        private List<BrandCount> topBrands = new ArrayList<>();
    }

    @Data
    public static class Missing {
        private Integer module;
        private String moduleName;
        private List<String> keys = new ArrayList<>();
        private List<String> labels = new ArrayList<>();
    }

    @Data
    public static class Completeness {
        private Integer mask;
        private Integer done;
        private Boolean complete;
        private List<Missing> missing = new ArrayList<>();
    }

    @Data
    public static class Grade {
        /** 0-未成交、1-S、2-A、3-B、4-C */
        private Integer calc;
        private Integer finalGrade;
        private BigDecimal score;
        private Integer r;
        private Integer f;
        private Integer m;
        private Integer adjust;
        private String adjustReason;
        private String adjustByName;
        private LocalDateTime adjustAt;
        private LocalDateTime calcAt;
        private Integer followDays;
    }

    @Data
    public static class Source {
        /** 1-人工、2-背调报告、3-迁移、9-系统 */
        private Integer source;
        private Long reportId;
        private String operatorName;
        private LocalDateTime updateTime;
        private LocalDateTime askedAt;
        private String askedByName;
    }
}
