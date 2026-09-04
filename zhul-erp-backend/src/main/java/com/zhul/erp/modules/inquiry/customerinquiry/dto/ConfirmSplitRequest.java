package com.zhul.erp.modules.inquiry.customerinquiry.dto;

import jakarta.validation.Valid;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ConfirmSplitRequest {
    @Valid
    private List<ConfirmSplitGroupRequest> groups = new ArrayList<>();
}
