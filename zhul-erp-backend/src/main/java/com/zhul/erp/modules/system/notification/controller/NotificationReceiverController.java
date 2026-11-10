package com.zhul.erp.modules.system.notification.controller;

import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.system.notification.dto.NotificationReceiverVO;
import com.zhul.erp.modules.system.notification.dto.SaveReceiversRequest;
import com.zhul.erp.modules.system.notification.service.NotificationReceiverService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 业务设置 → 通知接收人 */
@RestController
@RequestMapping("/api/v1/system/notification-receivers")
@RequiredArgsConstructor
public class NotificationReceiverController {

    private static final String MENU = "@perm.canAccessMenu('/system/notification-receivers')";
    private static final String EDIT = MENU + " and @perm.has('system:notification-receiver:edit')";

    private final NotificationReceiverService service;

    @GetMapping
    @PreAuthorize(MENU)
    public Result<List<NotificationReceiverVO>> list() {
        return Result.ok(service.list());
    }

    /** 可选的接收人：本租户有效账号 */
    @GetMapping("/candidates")
    @PreAuthorize(MENU)
    public Result<List<NotificationReceiverVO.User>> candidates() {
        return Result.ok(service.candidates());
    }

    @PutMapping("/{post}")
    @PreAuthorize(EDIT)
    public Result<List<NotificationReceiverVO>> save(@PathVariable String post, @RequestBody SaveReceiversRequest req) {
        service.save(post, req.isPoBuyer(), req.getUserIds());
        return Result.ok(service.list());
    }
}
