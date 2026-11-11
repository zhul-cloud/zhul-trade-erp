package com.zhul.erp.modules.system.doclog;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/** 单据添加附件（先上传，再挂到单据） */
@Data
public class DocAttachRequest {
    @NotEmpty(message = "请选择附件")
    private List<Long> ids;
}
