package com.zhul.erp.modules.website.controller;

import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.listing.constants.ListingConstants;
import com.zhul.erp.modules.website.constants.WebsiteConstants;
import com.zhul.erp.modules.website.dto.PreviewVO;
import com.zhul.erp.modules.website.service.SiteDataService;
import com.zhul.erp.modules.website.service.SitePreviewService;
import com.zhul.erp.modules.website.service.SiteProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RestController
@RequestMapping("/api/v1/websites")
@RequiredArgsConstructor
public class WebsitePublishController {

    private static final String PUBLISH = "@perm.canAccessMenu('" + ListingConstants.MENU + "') and @perm.has('"
            + WebsiteConstants.PERM_PUBLISH + "')";

    private final SitePreviewService previews;
    private final SiteProductService siteProducts;
    private final SiteDataService siteData;

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
