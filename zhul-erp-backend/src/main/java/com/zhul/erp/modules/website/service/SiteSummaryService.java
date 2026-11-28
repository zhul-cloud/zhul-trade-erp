package com.zhul.erp.modules.website.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.modules.listing.constants.ListingConstants;
import com.zhul.erp.modules.website.dto.ReleaseVO;
import com.zhul.erp.modules.website.dto.SiteSummary;
import com.zhul.erp.modules.website.dto.SiteVO;
import com.zhul.erp.modules.website.entity.WebsiteReleaseDO;
import com.zhul.erp.modules.website.repository.WebsiteReleaseMapper;
import com.zhul.erp.modules.website.repository.WebsiteSiteMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 站点管理列表与详情的汇总（商品数、最近发布、未确认差异）；站点数量少，逐个站点查最近发布单与比对 */
@Service
@RequiredArgsConstructor
public class SiteSummaryService {

    private final WebsiteSiteService sites;
    private final WebsiteSiteMapper siteMapper;
    private final WebsiteReleaseMapper releaseMapper;
    private final SiteReleaseService releases;
    private final SiteImportService imports;

    public List<SiteVO> list() {
        List<SiteVO> out = sites.list();
        fill(out);
        return out;
    }

    public SiteVO detail(Long id) {
        SiteVO v = sites.detail(id);
        fill(List.of(v));
        return v;
    }

    private void fill(List<SiteVO> list) {
        Map<Long, int[]> tasks = new HashMap<>(16);
        for (Map<String, Object> r : siteMapper.taskCounts()) {
            int[] c = tasks.computeIfAbsent(((Number) r.get("sid")).longValue(), k -> new int[2]);
            int status = ((Number) r.get("st")).intValue();
            int cnt = ((Number) r.get("cnt")).intValue();
            if (status != ListingConstants.STATUS_CANCELLED) {
                c[0] += cnt;
            }
            if (status == ListingConstants.STATUS_PUBLISHED || status == ListingConstants.STATUS_LIVE_UNCONFIRMED) {
                c[1] += cnt;
            }
        }
        Map<Long, Integer> imported = new HashMap<>(16);
        for (Map<String, Object> r : siteMapper.importedCounts()) {
            imported.put(((Number) r.get("sid")).longValue(), ((Number) r.get("cnt")).intValue());
        }
        for (SiteVO v : list) {
            int[] c = tasks.getOrDefault(v.getId(), new int[2]);
            WebsiteReleaseDO last = releaseMapper.selectOne(new LambdaQueryWrapper<WebsiteReleaseDO>()
                    .eq(WebsiteReleaseDO::getSiteId, v.getId()).isNull(WebsiteReleaseDO::getDeletedAt)
                    .orderByDesc(WebsiteReleaseDO::getId).last("LIMIT 1"));
            ReleaseVO lastVO = last == null ? null : releases.toVO(last, false);
            v.setSummary(new SiteSummary(c[0], c[1], imported.getOrDefault(v.getId(), 0), lastVO,
                    imports.latestPending(v.getId())));
        }
    }
}
