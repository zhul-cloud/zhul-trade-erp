package com.zhul.erp.modules.tenant.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

/**
 * 续期请求：mode=DURATION 时按 months 自然月续期（从当前到期时间起算）；
 * mode=DATE 时直接指定新的到期日期。两种模式互斥，具体校验在 Service 里做
 * （取决于 mode，另一个字段是否必填不一样，用 Bean Validation 表达不直观）。
 */
@Data
public class RenewTenantRequest {
    @NotBlank(message = "请选择续期方式")
    private String mode;
    /** mode=DURATION 时必填：1/3/6/12 个月 */
    private Integer months;
    /** mode=DATE 时必填：新的到期日期，不可早于当前到期时间 */
    private LocalDate expireDate;
}
