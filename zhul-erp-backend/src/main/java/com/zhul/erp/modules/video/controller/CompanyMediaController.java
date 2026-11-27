package com.zhul.erp.modules.video.controller;

import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.media.service.MediaLibraryService;
import com.zhul.erp.modules.video.dto.CompanyMediaVO;
import com.zhul.erp.modules.video.service.CompanyMediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 商品档案「本公司素材」：需要素材管理菜单；预览链接用素材库的 /api/v1/media/urls。设为官方素材只有平台账号（服务里校验） */
@RestController
@RequestMapping("/api/v1/products/{productId}/company-media")
@RequiredArgsConstructor
public class CompanyMediaController {

    private static final String VIEW = "@perm.canAccessMenu('" + MediaLibraryService.MENU + "')";

    private final CompanyMediaService service;

    @GetMapping
    @PreAuthorize(VIEW)
    public Result<CompanyMediaVO> list(@PathVariable Long productId) {
        return Result.ok(service.list(productId));
    }

    @PostMapping("/{itemId}/official")
    @PreAuthorize(VIEW)
    public Result<Void> official(@PathVariable Long productId, @PathVariable Long itemId) {
        service.makeOfficial(productId, itemId);
        return Result.ok();
    }
}
