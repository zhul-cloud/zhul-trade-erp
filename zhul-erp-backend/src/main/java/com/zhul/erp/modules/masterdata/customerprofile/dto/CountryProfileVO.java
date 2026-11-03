package com.zhul.erp.modules.masterdata.customerprofile.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 国家资料；id 为空表示这个国家还没维护（未维护国家列表） */
@Data
public class CountryProfileVO {
    private Integer id;
    private String country;
    /** 本公司在这个国家的客户数 */
    private Long customerCount;
    private List<Integer> voltages;
    private String plug;
    private String holidays;
    /** 0-未填、1-无、2-一般、3-严格 */
    private Integer fxControl;
    private String fxNote;
    private String tariffNote;
    private List<Integer> certifications;
    private LocalDateTime createTime;
    private String createBy;
    private LocalDateTime updateTime;
    private String updateBy;
}
