package com.zhul.erp.modules.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class VerifyResetCodeResponse {
    private String verifyToken;
}
