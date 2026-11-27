package com.zhul.erp.modules.website.controller;

import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.listing.constants.ListingConstants;
import com.zhul.erp.modules.website.constants.WebsiteConstants;
import com.zhul.erp.modules.website.dto.PreviewVO;
import com.zhul.erp.modules.website.dto.ReleaseVO;
import com.zhul.erp.modules.website.entity.WebsiteReleaseDO;
import com.zhul.erp.modules.website.service.SiteDataService;
import com.zhul.erp.modules.website.service.SitePreviewService;
import com.zhul.erp.modules.website.service.SiteReleaseDispatcher;
import com.zhul.erp.modules.website.service.SiteReleaseService;
import com.zhul.erp.modules.website.service.SiteProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** 发布到独立站：预览，站点商品的手工售价、站点状态、改用 ERP 内容（在发品任务里操作） */
@Slf4j
@RestController
@RequestMapping("/api/v1/websites")
@RequiredArgsConstructor
public class WebsitePublishController {

    /** 发布单记录：发品任务或站点管理能看到的都可以看 */
    private static final String VIEW_RELEASE = "@perm.canAccessMenu('" + ListingConstants.MENU + "') or @perm.canAccessMenu('"
            + WebsiteConstants.MENU + "')";
    private static final String PUBLISH = "@perm.canAccessMenu('" + ListingConstants.MENU + "') and @perm.has('"
            + WebsiteConstants.PERM_PUBLISH + "')";

    private final SitePreviewService previews;
    private final SiteProductService siteProducts;
    private final SiteDataService siteData;
    private final SiteReleaseService releases;
    private final SiteReleaseDispatcher dispatcher;

    public record PreviewRequest(List<Long> taskIds) {
    }

    public record PriceRequest(BigDecimal price) {
    }

    public record GroupsRequest(List<String> groups) {
    }

    @PostMapping("/{siteId}/preview")
    @PreAuthorize(PUBLISH)
    public Result<List<PreviewVO>> preview(@PathVariable Long siteId, @RequestBody PreviewRequest req) {
        return Result.ok(previews.preview(siteId, req.taskIds()));
    }

    /** 发布：生成发布单并排队，站点空闲时立即开始 */
    @PostMapping("/{siteId}/releases")
    @PreAuthorize(PUBLISH)
    public Result<ReleaseVO> release(@PathVariable Long siteId, @RequestBody PreviewRequest req) {
        WebsiteReleaseDO r = releases.create(siteId, req.taskIds());
        kick(siteId);
        return Result.ok(releases.toVO(releases.get(r.getId()), false));
    }

    @GetMapping("/{siteId}/releases")
    @PreAuthorize(VIEW_RELEASE)
    public Result<List<ReleaseVO>> releases(@PathVariable Long siteId) {
        return Result.ok(releases.list(siteId).stream().map(r -> releases.toVO(r, false)).toList());
    }

    @GetMapping("/releases/{id}")
    @PreAuthorize(VIEW_RELEASE)
    public Result<ReleaseVO> releaseDetail(@PathVariable Long id) {
        return Result.ok(releases.toVO(releases.get(id), true));
    }

    /** 部署失败的发布单从部署一步重试 */
    @PostMapping("/releases/{id}/retry")
    @PreAuthorize(PUBLISH)
    public Result<ReleaseVO> retry(@PathVariable Long id) {
        WebsiteReleaseDO r = releases.retry(id);
        kick(r.getSiteId());
        return Result.ok(releases.toVO(releases.get(id), false));
    }

    /** 立即尝试派发；发布服务暂时不可用时由定时任务稍后再派，不影响发布单已生成 */
    private void kick(Long siteId) {
        try {
            dispatcher.dispatch(siteId);
        } catch (RuntimeException e) {
            log.warn("立即派发独立站发布单失败，等待定时任务，siteId={}：{}", siteId, e.getMessage());
        }
    }

    @PutMapping("/products/{id}/price")
    @PreAuthorize(PUBLISH)
    public Result<Void> price(@PathVariable Long id, @RequestBody PriceRequest req) {
        siteProducts.setManualPrice(id, req.price());
        return Result.ok();
    }

    @PutMapping("/products/{id}/site-status")
    @PreAuthorize(PUBLISH)
    public Result<Void> siteStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        siteProducts.setSiteStatus(id, body.get("status"));
        return Result.ok();
    }

    @PutMapping("/products/{id}/use-erp")
    @PreAuthorize(PUBLISH)
    public Result<Void> useErp(@PathVariable Long id, @RequestBody GroupsRequest req) {
        siteData.useErp(id, req.groups() == null ? List.of() : req.groups());
        return Result.ok();
    }
}
