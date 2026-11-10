package com.zhul.erp.modules.crm.followup.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 按客户分组的跟进列表：每次 20 个客户，带客户总数与单据总数；PI 后另带按币种的未收合计 */
@Data
public class CustomerGroupListVO {
    private Long total;
    private Long docTotal;
    private List<CustomerGroupVO> records = new ArrayList<>();
    private List<FollowupListVO.CurrencyAmount> unpaid = new ArrayList<>();
}
