package com.zhul.erp.modules.inquiry.sourcing.dto;

import lombok.Data;

import java.util.List;

/** 分配工作台 */
@Data
public class BoardVO {
    private BoardStatsVO stats;
    private List<BoardTaskVO> tasks;
    private List<PurchaserVO> purchasers;
}
