package com.zhul.erp.modules.aitask.dto;

/** 编排服务容器内两个官方 CLI 是否已登录（claude：Claude Code，openai：Codex），不含账号信息 */
public record AiLoginStatus(boolean claude, boolean openai) {

    public boolean loggedIn(String provider) {
        return "openai".equals(provider) ? openai : claude;
    }
}
