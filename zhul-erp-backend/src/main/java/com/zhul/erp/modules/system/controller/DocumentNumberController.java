package com.zhul.erp.modules.system.controller;

import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.system.service.DocumentNumberService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 单据编号前缀：「业务设置 → 单据编号」页面查看，修改需要「编辑单据编号」 */
@RestController
@RequestMapping("/api/v1/system/document-numbering")
@RequiredArgsConstructor
public class DocumentNumberController {

    private final DocumentNumberService service;

    @GetMapping("/prefix")
    @PreAuthorize("@perm.canAccessMenu('/system/document-numbering')")
    public Result<Map<String, String>> prefix() {
        return Result.ok(Map.of("prefix", service.prefix()));
    }

    @PutMapping("/prefix")
    @PreAuthorize("@perm.has('system:document-numbering:edit')")
    public Result<Map<String, String>> savePrefix(@RequestBody Map<String, String> body) {
        return Result.ok(Map.of("prefix", service.savePrefix(body.get("prefix"))));
    }
}
