package com.zhul.erp.modules.masterdata.dto;

import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 新增客户：管理页新增与询盘快速创建共用。快速创建只传名称和国家，其余取默认值；
 * 编码为空时系统生成，负责人为空时为当前用户。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class SaveCustomerRequest extends AbstractCustomerRequest {
    @Pattern(regexp = "^[A-Za-z0-9]{0,20}$", message = "客户编码只能包含字母和数字，最多20位")
    private String customerCode;
    /** 负责业务员 user_basic.id，为空时为当前用户 */
    private Long ownerId;
    /** 所属事业部：负责人在公共部门时必选，属于事业部时忽略（取负责人的事业部）；只在新增时使用 */
    private Integer businessUnitId;
}
