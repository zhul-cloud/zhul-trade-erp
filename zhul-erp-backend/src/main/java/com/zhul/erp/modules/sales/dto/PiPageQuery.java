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
    /** 只看已过期未收款：已发送、未付款且过了有效期 */
    private Boolean expiredUnpaid;
    private Long ownerId;
    private LocalDate createdFrom;
    private LocalDate createdTo;
    private Integer page = 1;
    private Integer pageSize = 20;
}
