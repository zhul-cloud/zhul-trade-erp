package com.zhul.erp.modules.system.notification.controller;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.system.notification.dto.NotificationCountVO;
import com.zhul.erp.modules.system.notification.dto.NotificationVO;
import com.zhul.erp.modules.system.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 站内通知：只看自己的，登录即可用，不需要菜单权限 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService service;

    @GetMapping
    public Result<PageResult<NotificationVO>> list(@RequestParam(defaultValue = "todo") String tab,
                                                   @RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(service.list(tab, page, pageSize));
    }

    @GetMapping("/count")
    public Result<NotificationCountVO> count() {
        return Result.ok(service.count());
    }

    @PostMapping("/{id}/read")
    public Result<Void> read(@PathVariable Long id) {
        service.read(id);
        return Result.ok();
    }

    @PostMapping("/read-all")
    public Result<Integer> readAll() {
        return Result.ok(service.readAll());
    }
}
