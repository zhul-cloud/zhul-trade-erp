package com.zhul.erp.modules.sales.dto;

import lombok.Data;

import java.util.List;

/** 未认领到账与最近认领的到账 */
@Data
public class UnclaimedListVO {
    private List<ReceiptRowVO> unclaimed;
    private List<ReceiptRowVO> recentClaimed;
}
