package com.zhul.erp.modules.website.constants;

import java.util.List;

/** 独立站发布常量（openspec website-publish） */
public final class WebsiteConstants {

    private WebsiteConstants() {
    }

    public static final String MENU = "/operations/websites";
    /** 修改站点：新建、修改、停用、检测连接、品类对照、导入与比对 */
    public static final String PERM_EDIT = "operations:website:edit";
    /** 发布到站点：在发品任务里发起发布 */
    public static final String PERM_PUBLISH = "operations:website:publish";

    /** 发布方式：1-自动发布、2-只复制 */
    public static final int MODE_AUTO = 1;
    public static final int MODE_COPY = 2;

    /** 接管状态：1-接管中、2-以 ERP 为准 */
    public static final int TAKEOVER_PENDING = 1;
    public static final int TAKEOVER_DONE = 2;

    public static final int STATUS_DISABLED = 0;
    public static final int STATUS_ENABLED = 1;

    public static final String SECRET_REPO_TOKEN = "repo_token";
    public static final String SECRET_DEPLOY_KEY = "deploy_key";
    public static final String SECRET_INDEXNOW_KEY = "indexnow_key";
    public static final List<String> SECRET_TYPES = List.of(SECRET_REPO_TOKEN, SECRET_DEPLOY_KEY, SECRET_INDEXNOW_KEY);

    /** 站点可选的语言与默认地址前缀 */
    public static final List<String> LANGUAGES = List.of("en", "ru");

    public static final int MAX_NAME = 50;
    public static final int MAX_URL = 200;
    public static final int MAX_PURGE_NOTE = 500;
    public static final int MAX_SECRET = 16 * 1024;
    public static final String CURRENCY = "USD";
}
