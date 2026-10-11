package com.zhul.erp.modules.product.content.repository;

import com.zhul.erp.modules.product.content.support.MarketingPlatform;
import lombok.Data;

import java.util.Set;

/** 本公司启用的营销平台，传给任务列表 SQL 拼接营销阶段的完成条件 */
@Data
public class PlatformFlags {
    private boolean alibaba;
    private boolean mic;
    private boolean site;
    private boolean social;

    public static PlatformFlags of(Set<MarketingPlatform> enabled) {
        PlatformFlags f = new PlatformFlags();
        f.setAlibaba(enabled.contains(MarketingPlatform.ALIBABA));
        f.setMic(enabled.contains(MarketingPlatform.MIC));
        f.setSite(enabled.contains(MarketingPlatform.SITE));
        f.setSocial(enabled.contains(MarketingPlatform.SOCIAL));
        return f;
    }
}
