package com.zhul.erp.modules.system.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class ForceLogoutRequest {
    @NotEmpty(message = "请选择要强制下线的用户")
    private List<String> tokenIds;
}
