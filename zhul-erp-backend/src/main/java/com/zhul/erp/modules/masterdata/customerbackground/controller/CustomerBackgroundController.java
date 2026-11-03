package com.zhul.erp.modules.masterdata.customerbackground.controller;

import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.masterdata.customerbackground.dto.BackgroundReportVO;
import com.zhul.erp.modules.masterdata.customerbackground.dto.BackgroundResearchRequest;
import com.zhul.erp.modules.masterdata.customerbackground.service.CustomerBackgroundService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 客户背调（spec crm/customer-background）。按数据权限只能操作自己范围内的客户；发起与上传需要「客户背调」 */
@RestController
@RequestMapping("/api/v1/masterdata/customers/{id}/background-reports")
@RequiredArgsConstructor
@PreAuthorize("@perm.canAccessMenu('/customer/list')")
public class CustomerBackgroundController {

    private static final String BACKGROUND = "@perm.has('partner:customer:background')";

    private final CustomerBackgroundService backgroundService;

    /** 发起 AI 背调；24 小时内背调过的返回 BACKGROUND_RECENT，确认后带 confirmed=true 再发 */
    @PostMapping("/research")
    @PreAuthorize(BACKGROUND)
    public Result<BackgroundReportVO> research(@PathVariable Long id, @RequestBody(required = false) BackgroundResearchRequest req) {
        return Result.ok(backgroundService.research(id, req != null && Boolean.TRUE.equals(req.getConfirmed())));
    }
}
