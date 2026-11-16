package com.zhul.erp.modules.masterdata.dto;

import lombok.Data;

/** 公海客户列表的查询 */
@Data
public class CustomerPoolQuery {
    private Integer page = 1;
    private Integer pageSize = 10;
    /** 名称、编码、联系人 */
    private String keyword;
    private String country;
    private Long lastOwnerId;
}
