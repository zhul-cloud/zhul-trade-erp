package com.zhul.erp.modules.system.doclog;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** 单据备注（详情页随时可改，已作废只读） */
@Data
public class DocRemarkRequest {
    @Size(max = 500, message = "备注最多 500 字")
    private String remark;
}
