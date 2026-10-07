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

/** 单据编号前缀：登录用户可查看（导出与预览要用），修改需要「系统设置」编辑权限 */
@RestController
@RequestMapping("/api/v1/system/document-numbering")
@RequiredArgsConstructor
public class DocumentNumberController {

    private final DocumentNumberService service;

    @GetMapping("/prefix")
    public Result<Map<String, String>> prefix() {
        return Result.ok(Map.of("prefix", service.prefix()));
    }

    @PutMapping("/prefix")
    @PreAuthorize("@perm.has('system:config:edit')")
    public Result<Map<String, String>> savePrefix(@RequestBody Map<String, String> body) {
        return Result.ok(Map.of("prefix", service.savePrefix(body.get("prefix"))));
    }
}
