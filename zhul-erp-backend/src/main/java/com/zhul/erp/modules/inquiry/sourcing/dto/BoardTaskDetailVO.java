package com.zhul.erp.modules.inquiry.sourcing.dto;

import lombok.Data;

import java.util.List;

/** 分配工作台的任务详情：采购负责人分配前看清型号与询盘概况，不跳转客户询盘、不含联系方式与原始内容 */
@Data
public class BoardTaskDetailVO {
    private BoardTaskVO task;
    private List<BoardTaskItemVO> items;
}
