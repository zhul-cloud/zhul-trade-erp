package com.zhul.erp.modules.masterdata.customerprofile.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 合并前预览：各类单据改挂的数量、搬过去的联系人、补上的字段；blocked 不为空时不能合并 */
@Data
public class MergePreviewVO {
    private Map<String, Long> documents;
    private List<String> contacts = new ArrayList<>();
    private List<String> filledFields = new ArrayList<>();
    private String blocked;
}
