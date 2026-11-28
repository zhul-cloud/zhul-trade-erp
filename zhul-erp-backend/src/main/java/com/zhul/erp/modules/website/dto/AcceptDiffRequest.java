package com.zhul.erp.modules.website.dto;

import java.util.List;
import java.util.Map;

/** 确认接受比对差异：items 为空表示全部，每项 {model, field} */
public record AcceptDiffRequest(List<Map<String, String>> items, String reason) {
}
