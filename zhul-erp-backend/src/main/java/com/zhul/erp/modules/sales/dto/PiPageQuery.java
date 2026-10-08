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
    /** 排序字段：itemCount-型号数、totalQuantity-总数量、totalAmount-合计（按折合人民币比较）；不传按创建时间倒序 */
    private String sortField;
    /** ascend / descend */
    private String sortOrder;
    private Integer page = 1;
    private Integer pageSize = 20;
}
