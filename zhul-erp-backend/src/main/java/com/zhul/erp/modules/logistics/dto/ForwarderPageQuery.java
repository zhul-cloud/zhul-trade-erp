package com.zhul.erp.modules.logistics.dto;

import lombok.Data;

/** 货代管理列表的查询 */
@Data
public class ForwarderPageQuery {
    private Integer page = 1;
    private Integer pageSize = 20;
    /** 名称、简称、联系人、电话 */
    private String keyword;
    /** 归属：company-公司货代、customer-客户指定，空为全部 */
    private String owner;
    private Long customerId;
    /** 状态（0-停用、1-启用），空为全部 */
    private Integer status;
}
