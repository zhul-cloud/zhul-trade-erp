package com.zhul.erp.modules.masterdata.customerprofile.controller;

import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.masterdata.customerprofile.dto.CountryProfileRequest;
import com.zhul.erp.modules.masterdata.customerprofile.dto.CountryProfileVO;
import com.zhul.erp.modules.masterdata.customerprofile.service.CountryProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 国家资料（spec master-data/country-profile）：「业务设置 → 国家资料」菜单 */
@RestController
@RequestMapping("/api/v1/masterdata/country-profiles")
@RequiredArgsConstructor
@PreAuthorize("@perm.canAccessMenu('/system/country-profiles')")
public class CountryProfileController {

    private final CountryProfileService service;

    @GetMapping
    public Result<List<CountryProfileVO>> list(@RequestParam(required = false) String keyword,
                                               @RequestParam(required = false) String filter) {
        return Result.ok(service.list(keyword, filter));
    }

    @PutMapping
    public Result<CountryProfileVO> save(@Valid @RequestBody CountryProfileRequest req) {
        return Result.ok(service.save(req));
    }
}
