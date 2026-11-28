package com.zhul.erp.modules.website.dto;

/**
 * 站点管理列表与详情上的汇总：
 * scopeCount 范围内商品数（发品任务未取消）、publishedCount 已发布或已上线待确认的商品数、importedCount 导入的站点商品数、
 * lastRelease 最近一张发布单（没有为 null）、pendingDiffs 最近一次比对未确认的差异条数（最近的比对未完成或没有比对为 null）。
 */
public record SiteSummary(int scopeCount, int publishedCount, int importedCount, ReleaseVO lastRelease, Integer pendingDiffs) {
}
