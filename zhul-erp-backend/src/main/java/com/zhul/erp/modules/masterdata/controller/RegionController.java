package com.zhul.erp.modules.masterdata.controller;

import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.masterdata.dto.RegionVO;
import com.zhul.erp.modules.masterdata.support.RegionCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 省市区行政区划：所有登录用户可读，数据是静态资源，不涉及写入 */
@RestController
@RequestMapping("/api/v1/masterdata/regions")
@RequiredArgsConstructor
public class RegionController {

    private final RegionCatalog regionCatalog;

    @GetMapping
    public Result<List<RegionVO>> list() {
        return Result.ok(regionCatalog.all());
    }
}
