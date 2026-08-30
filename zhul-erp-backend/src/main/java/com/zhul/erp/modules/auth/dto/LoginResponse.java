package com.zhul.erp.modules.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private String accessToken;
    private Long expiresIn;
    private String username;
    private String nickname;
    private String avatarUrl;
    private Boolean isAdmin;
}
