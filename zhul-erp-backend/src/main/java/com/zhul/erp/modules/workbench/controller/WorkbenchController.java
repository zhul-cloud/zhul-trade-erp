package com.zhul.erp.modules.workbench.controller;

import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.workbench.dto.WorkbenchTemplateVO;
import com.zhul.erp.modules.workbench.dto.WorkbenchVO;
import com.zhul.erp.modules.workbench.service.WorkbenchService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 工作台：当前用户的看板与组件数据；模板列表给角色管理用 */
@RestController
@RequestMapping("/api/v1/workbench")
@RequiredArgsConstructor
public class WorkbenchController {

    private static final String VIEW = "@perm.canAccessMenu('/dashboard')";

    private final WorkbenchService service;

    @GetMapping
    @PreAuthorize(VIEW)
    public Result<WorkbenchVO> current() {
        return Result.ok(service.current());
    }

    @GetMapping("/widgets/{key}")
    @PreAuthorize(VIEW)
    public Result<Object> widget(@PathVariable String key) {
        return Result.ok(service.widget(key));
    }

    @GetMapping("/templates")
    @PreAuthorize("@perm.has('system:role:add') or @perm.has('system:role:edit')")
    public Result<List<WorkbenchTemplateVO>> templates() {
        return Result.ok(service.templates());
    }
}
