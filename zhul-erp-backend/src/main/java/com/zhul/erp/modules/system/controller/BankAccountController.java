package com.zhul.erp.modules.system.controller;

import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.system.dto.BankAccountVO;
import com.zhul.erp.modules.system.dto.SaveBankAccountRequest;
import com.zhul.erp.modules.system.service.BankAccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 收款账户：列表需要「收款账户」菜单（账号脱敏）；开 PI 用的启用账户选项要求「PI」菜单；维护需要「管理收款账户」 */
@RestController
@RequestMapping("/api/v1/system/bank-accounts")
@RequiredArgsConstructor
public class BankAccountController {

    private final BankAccountService service;

    @GetMapping
    @PreAuthorize("@perm.canAccessMenu('/system/bank-account')")
    public Result<List<BankAccountVO>> list() {
        return Result.ok(service.list());
    }

    @GetMapping("/options")
    @PreAuthorize("@perm.canAccessMenu('/sales/pi') or @perm.canAccessMenu('/system/bank-account')")
    public Result<List<BankAccountVO>> options() {
        return Result.ok(service.enabledOptions());
    }

    @GetMapping("/{id}")
    @PreAuthorize("@perm.has('system:bank-account:edit')")
    public Result<BankAccountVO> get(@PathVariable Integer id) {
        return Result.ok(service.get(id));
    }

    @PostMapping
    @PreAuthorize("@perm.has('system:bank-account:edit')")
    public Result<BankAccountVO> create(@Valid @RequestBody SaveBankAccountRequest req) {
        return Result.ok(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@perm.has('system:bank-account:edit')")
    public Result<BankAccountVO> update(@PathVariable Integer id, @Valid @RequestBody SaveBankAccountRequest req) {
        return Result.ok(service.update(id, req));
    }

    @PutMapping("/{id}/default")
    @PreAuthorize("@perm.has('system:bank-account:edit')")
    public Result<Void> setDefault(@PathVariable Integer id) {
        service.setDefault(id);
        return Result.ok();
    }

    @PutMapping("/{id}/enabled")
    @PreAuthorize("@perm.has('system:bank-account:edit')")
    public Result<Void> setEnabled(@PathVariable Integer id, @RequestParam boolean enabled) {
        service.setEnabled(id, enabled);
        return Result.ok();
    }
}
