package com.zhul.erp.modules.masterdata.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/** 更新客户（管理页编辑）。没有编码、负责人字段：编码不可改，负责人只能通过转移修改 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class UpdateCustomerRequest extends AbstractCustomerRequest {

    @Override
    @NotNull(message = "请选择客户角色")
    public Integer getCustomerRole() {
        return super.getCustomerRole();
    }

    @Override
    @NotNull(message = "请选择状态")
    public Integer getStatus() {
        return super.getStatus();
    }
}
