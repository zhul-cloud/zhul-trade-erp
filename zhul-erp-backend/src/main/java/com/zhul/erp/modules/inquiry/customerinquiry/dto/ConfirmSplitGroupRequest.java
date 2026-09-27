package com.zhul.erp.modules.inquiry.customerinquiry.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 确认拆单时提交的一个分组。groupIndex 对应 ai_task.output.groups 的下标，
 * 品牌/品类/询价话术等不可编辑字段由服务端从原始 AI 输出取值，不信任前端回传；
 * 分组内 items 为空时该分组在确认时被跳过，不生成询盘单。
 */
@Data
public class ConfirmSplitGroupRequest {
    @NotNull(message = "分组序号不能为空")
    private Integer groupIndex;
    @Valid
    private List<ConfirmSplitItemRequest> items = new ArrayList<>();
}
