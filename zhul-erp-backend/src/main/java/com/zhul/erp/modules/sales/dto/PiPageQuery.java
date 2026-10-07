package com.zhul.erp.modules.sales.dto;

import lombok.Data;
import java.time.LocalDate;

/** PI 列表查询 */
@Data
public class PiPageQuery {
    /** PI 编号、客户、型号 */
    private String keyword;
    private Integer status;
    private Integer receiptStatus;
    private Long ownerId;
    private LocalDate createdFrom;
    private LocalDate createdTo;
    private Integer page = 1;
    private Integer pageSize = 20;
}
