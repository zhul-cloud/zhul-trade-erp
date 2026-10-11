package com.zhul.erp.modules.product.content.support;

import com.zhul.erp.modules.product.content.entity.ProductContentTaskDO;

import java.util.List;

/**
 * 营销平台（spec product/product-content）：编码、中文名与允许的语言。
 * 解析、写入、下载、阶段计算都以这里为准；前端 constants 里的同名常量手写一份，以这里为准。
 */
public enum MarketingPlatform {
    ALIBABA("alibaba", "阿里国际站", List.of("en")),
    MIC("mic", "中国制造网", List.of("en")),
    SITE("site", "独立站", List.of("en", "ru")),
    SOCIAL("social", "社媒", List.of("en"));

    private final String code;
    private final String label;
    private final List<String> langs;

    MarketingPlatform(String code, String label, List<String> langs) {
        this.code = code;
        this.label = label;
        this.langs = langs;
    }

    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    public List<String> langs() {
        return langs;
    }

    /** 编码对应的平台，无效时为空 */
    public static MarketingPlatform of(String code) {
        for (MarketingPlatform p : values()) {
            if (p.code.equals(code)) {
                return p;
            }
        }
        return null;
    }

    /** 本公司在该平台已写完：独立站要英文、俄文都写入，其他平台写入英文即可 */
    public boolean written(ProductContentTaskDO task) {
        if (task == null) {
            return false;
        }
        return switch (this) {
            case ALIBABA -> task.getAlibabaAt() != null;
            case MIC -> task.getMicAt() != null;
            case SITE -> task.getEnAt() != null && task.getRuAt() != null;
            case SOCIAL -> task.getSocialAt() != null;
        };
    }
}
