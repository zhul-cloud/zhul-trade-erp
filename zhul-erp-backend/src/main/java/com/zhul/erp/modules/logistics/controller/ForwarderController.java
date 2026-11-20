package com.zhul.erp.modules.logistics.controller;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.logistics.dto.ForwarderCountsVO;
import com.zhul.erp.modules.logistics.dto.ForwarderPageQuery;
import com.zhul.erp.modules.logistics.dto.ForwarderRowVO;
import com.zhul.erp.modules.logistics.dto.SaveForwarderRequest;
import com.zhul.erp.modules.logistics.service.ForwarderService;
import com.zhul.erp.modules.masterdata.dto.SupplierFormVO;
import com.zhul.erp.modules.masterdata.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 货代管理（spec logistics/forwarder）。发货通知用的下拉仍是 {@code GET /api/v1/logistics/forwarders}（OutboundController）。
 * 客户详情的「客户指定货代」按客户的数据范围与客户编辑权限放行，不要求货代管理菜单。
 */
@RestController
@RequestMapping("/api/v1/logistics/forwarders")
@RequiredArgsConstructor
public class ForwarderController {

    private static final String MENU = "@perm.canAccessMenu('/logistics/forwarders')";

    private final ForwarderService service;
    private final CustomerService customerService;

    @GetMapping("/page")
    @PreAuthorize(MENU)
    public Result<PageResult<ForwarderRowVO>> page(ForwarderPageQuery query) {
        return Result.ok(service.page(query));
    }

    @GetMapping("/counts")
    @PreAuthorize(MENU)
    public Result<ForwarderCountsVO> counts() {
        return Result.ok(service.counts());
    }

    /** 编辑取数：收款账号为明文 */
    @GetMapping("/{id}/form")
    @PreAuthorize("@perm.has('logistics:forwarder:edit')")
    public Result<SupplierFormVO> form(@PathVariable Long id) {
        return Result.ok(service.form(id));
    }

    @PostMapping
    @PreAuthorize("@perm.has('logistics:forwarder:add')")
    public Result<Long> create(@Valid @RequestBody SaveForwarderRequest req) {
        return Result.ok(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@perm.has('logistics:forwarder:edit')")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody SaveForwarderRequest req) {
        service.update(id, req);
        return Result.ok();
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("@perm.has('logistics:forwarder:status')")
    /** 返回停用时的提示（货代仓还有货），没有提示为 null */
    public Result<String> updateStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        return Result.ok(service.updateStatus(id, body.get("status")));
    }

    /** 客户详情「客户指定货代」：客户在自己的数据范围内即可查看 */
    @GetMapping("/by-customer/{customerId}")
    @PreAuthorize("@perm.canAccessMenu('/customer/list')")
    public Result<List<ForwarderRowVO>> ofCustomer(@PathVariable Long customerId) {
        customerService.requireInScope(customerId);
        return Result.ok(service.ofCustomer(customerId));
    }

    /** 在客户详情里新增客户指定货代：归属客户固定为这个客户，要求客户编辑权限 */
    @PostMapping("/by-customer/{customerId}")
    @PreAuthorize("@perm.has('partner:customer:edit')")
    public Result<Long> createForCustomer(@PathVariable Long customerId, @Valid @RequestBody SaveForwarderRequest req) {
        customerService.requireInScope(customerId);
        req.setCustomerId(customerId);
        return Result.ok(service.create(req));
    }
}
