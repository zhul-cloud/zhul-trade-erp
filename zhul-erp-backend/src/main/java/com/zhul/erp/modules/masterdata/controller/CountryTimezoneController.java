package com.zhul.erp.modules.masterdata.controller;

import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.masterdata.support.CountryTimezoneCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 国家默认时区：所有登录用户可读，数据是静态资源 */
@RestController
@RequestMapping("/api/v1/masterdata/country-timezones")
@RequiredArgsConstructor
public class CountryTimezoneController {

    private final CountryTimezoneCatalog catalog;

    @GetMapping
    public Result<Map<String, List<String>>> list() {
        return Result.ok(catalog.all());
    }
}
