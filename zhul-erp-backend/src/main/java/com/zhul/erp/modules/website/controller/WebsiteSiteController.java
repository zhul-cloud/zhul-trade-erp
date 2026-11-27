package com.zhul.erp.modules.website.controller;

import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.website.constants.WebsiteConstants;
import com.zhul.erp.modules.website.dto.CategoryMapRequest;
import com.zhul.erp.modules.website.dto.CategoryMapVO;
import com.zhul.erp.modules.website.dto.CheckItemVO;
import com.zhul.erp.modules.website.dto.SiteRequest;
import com.zhul.erp.modules.website.dto.SiteVO;
import com.zhul.erp.modules.website.service.WebsiteSiteService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 站点管理（spec「站点管理权限」）：查看需要「站点管理」菜单；新建、修改、停用、检测连接、品类对照需要「修改站点」。
 * 凭据只返回是否已配置与末四位。
 */
@RestController
@RequestMapping("/api/v1/websites")
@RequiredArgsConstructor
public class WebsiteSiteController {

    private static final String VIEW = "@perm.canAccessMenu('" + WebsiteConstants.MENU + "')";
    private static final String EDIT = VIEW + " and @perm.has('" + WebsiteConstants.PERM_EDIT + "')";

    private final WebsiteSiteService service;

    @GetMapping
    @PreAuthorize(VIEW)
    public Result<List<SiteVO>> list() {
        return Result.ok(service.list());
    }

    @GetMapping("/{id}")
    @PreAuthorize(VIEW)
    public Result<SiteVO> detail(@PathVariable Long id) {
        return Result.ok(service.detail(id));
    }

    @PostMapping
    @PreAuthorize(EDIT)
    public Result<SiteVO> create(@RequestBody SiteRequest req) {
        return Result.ok(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize(EDIT)
    public Result<SiteVO> update(@PathVariable Long id, @RequestBody SiteRequest req) {
        return Result.ok(service.update(id, req));
    }

    /** 启用或停用：{"status": 0|1} */
    @PutMapping("/{id}/status")
    @PreAuthorize(EDIT)
    public Result<SiteVO> status(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        return Result.ok(service.setStatus(id, body.get("status")));
    }

    @PostMapping("/{id}/check")
    @PreAuthorize(EDIT)
    public Result<List<CheckItemVO>> check(@PathVariable Long id) {
        return Result.ok(service.check(id));
    }

    @GetMapping("/{id}/category-map")
    @PreAuthorize(VIEW)
    public Result<List<CategoryMapVO>> categoryMap(@PathVariable Long id) {
        return Result.ok(service.categoryMap(id));
    }

    @PutMapping("/{id}/category-map")
    @PreAuthorize(EDIT)
    public Result<List<CategoryMapVO>> saveCategoryMap(@PathVariable Long id, @RequestBody CategoryMapRequest req) {
        return Result.ok(service.saveCategoryMap(id, req));
    }
}
