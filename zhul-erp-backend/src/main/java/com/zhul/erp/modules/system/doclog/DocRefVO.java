package com.zhul.erp.modules.system.doclog;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单据详情页「关联单据」的一行（change warehouse-logistics-docs）：
 * type 为单据类型代码（PO、SO、SD、GR、OB、SH、DIFF 到货差异、SHOOT 实拍任务、ST 运费账单），前端按类型拼详情地址与状态颜色。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocRefVO {
    private String type;
    private Long id;
    private String no;
    private Integer status;
    private String statusName;
}
