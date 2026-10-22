package com.zhul.erp.modules.system.controller;

import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.system.dto.BusinessUnitVO;
import com.zhul.erp.modules.system.service.BusinessUnitService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 事业部：设置、最低分摊比例与当前用户的统计可见范围（spec system/business-unit） */
@RestController
@RequestMapping("/api/v1/system/business-units")
@RequiredArgsConstructor
public class BusinessUnitController {

    private final BusinessUnitService service;

    @GetMapping
    public Result<BusinessUnitVO> current() {
        return Result.ok(service.view());
    }

    @PreAuthorize("@perm.has('system:dept:edit')")
    @PutMapping("/min-share")
    public Result<Void> setMinShare(@RequestBody Map<String, Integer> body) {
        Integer percent = body.get("percent");
        service.setMinSharePercent(percent == null ? -1 : percent);
        return Result.ok();
    }
}
