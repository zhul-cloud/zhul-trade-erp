package com.zhul.erp.modules.masterdata.customerbackground.controller;

import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.masterdata.customerbackground.dto.CompletionQueueVO;
import com.zhul.erp.modules.masterdata.customerbackground.dto.CompletionWizardVO;
import com.zhul.erp.modules.masterdata.customerbackground.service.ProfileCompletionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 档案补全页签（spec crm/customer-background「档案补全」）：待补全的客户与四步向导，按数据权限。
 * 保存走画像与联系人接口，「已经问了」走 /profile/asked，背调与采纳走背调报告接口。
 */
@RestController
@RequestMapping("/api/v1/masterdata")
@RequiredArgsConstructor
@PreAuthorize("@perm.canAccessMenu('/customer/list')")
public class ProfileCompletionController {

    private final ProfileCompletionService completionService;

    @GetMapping("/customer-completion/queue")
    public Result<CompletionQueueVO> queue(@RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(completionService.queue(page, pageSize));
    }

    @GetMapping("/customers/{id}/completion")
    public Result<CompletionWizardVO> wizard(@PathVariable Long id) {
        return Result.ok(completionService.wizard(id));
    }
}
