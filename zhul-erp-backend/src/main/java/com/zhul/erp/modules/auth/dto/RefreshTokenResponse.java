package com.zhul.erp.modules.auth.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RefreshTokenResponse {
    private String accessToken;
    private Long expiresIn;
}
