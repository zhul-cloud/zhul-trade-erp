package com.zhul.erp.modules.product.controller;

import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.product.dto.CountryVO;
import com.zhul.erp.modules.product.support.CountryCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 国家/地区清单：所有登录用户可读，清单是静态资源，不涉及写入 */
@RestController
@RequestMapping("/api/v1/product/countries")
@RequiredArgsConstructor
public class CountryController {

    private final CountryCatalog countryCatalog;

    @GetMapping
    public Result<List<CountryVO>> list() {
        return Result.ok(countryCatalog.all());
    }
}
