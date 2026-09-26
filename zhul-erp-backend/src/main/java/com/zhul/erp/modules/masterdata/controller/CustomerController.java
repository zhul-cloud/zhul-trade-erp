package com.zhul.erp.modules.masterdata.controller;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.masterdata.dto.AssignableOwnersVO;
import com.zhul.erp.modules.masterdata.dto.CustomerBatchDeleteRequest;
import com.zhul.erp.modules.masterdata.dto.CustomerBatchDeleteResultVO;
import com.zhul.erp.modules.masterdata.dto.CustomerDetailVO;
import com.zhul.erp.modules.masterdata.dto.CustomerPageQuery;
import com.zhul.erp.modules.masterdata.dto.CustomerRefVO;
import com.zhul.erp.modules.masterdata.dto.CustomerTransferRequest;
import com.zhul.erp.modules.masterdata.dto.CustomerVO;
import com.zhul.erp.modules.masterdata.dto.SaveCustomerRequest;
import com.zhul.erp.modules.masterdata.dto.UpdateCustomerRequest;
import com.zhul.erp.modules.masterdata.service.CustomerService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 客户主数据（外贸档案）。除 {@code GET /{id}}（跨模块回显名称，只返回引用信息）外，
 * 读写接口都按当前用户的数据权限（负责业务员）过滤。新增接口不挂按钮权限：询盘录入的快速创建也用它。
 * 见 openspec/changes/enrich-customer-trade-profile/。
 */
@RestController
@RequestMapping("/api/v1/masterdata/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @PostMapping
    public Result<CustomerVO> create(@Valid @RequestBody SaveCustomerRequest req) {
        return Result.ok(customerService.create(req));
    }

    /** 客户选择器（询盘录入等），按数据权限过滤 */
    @GetMapping
    public Result<List<CustomerVO>> search(@RequestParam(required = false) String keyword) {
        return Result.ok(customerService.searchInScope(keyword));
    }

    @GetMapping("/page")
    public Result<PageResult<CustomerVO>> page(CustomerPageQuery query) {
        return Result.ok(customerService.page(query));
    }

    @GetMapping("/export")
    @PreAuthorize("@perm.has('partner:customer:export')")
    public void export(CustomerPageQuery query, HttpServletResponse response) throws IOException {
        String filename = "客户档案_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".xlsx";
        try (Workbook workbook = customerService.export(query)) {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename*=UTF-8''" + URLEncoder.encode(filename, StandardCharsets.UTF_8));
            workbook.write(response.getOutputStream());
        }
    }

    /** 当前用户可指定 / 转移给的负责人及其数据范围类型 */
    @GetMapping("/assignable-owners")
    public Result<AssignableOwnersVO> assignableOwners() {
        return Result.ok(customerService.assignableOwners());
    }

    @PostMapping("/transfer")
    @PreAuthorize("@perm.has('partner:customer:transfer')")
    public Result<Void> transfer(@Valid @RequestBody CustomerTransferRequest req) {
        customerService.transfer(req);
        return Result.ok();
    }

    @PostMapping("/batch-delete")
    @PreAuthorize("@perm.has('partner:customer:delete')")
    public Result<CustomerBatchDeleteResultVO> batchDelete(@Valid @RequestBody CustomerBatchDeleteRequest req) {
        return Result.ok(customerService.batchDelete(req.getIds()));
    }

    /** 引用信息（编码、名称、国家、状态），供询盘等模块回显，不按数据权限过滤 */
    @GetMapping("/{id}")
    public Result<CustomerRefVO> getRef(@PathVariable Long id) {
        return Result.ok(customerService.getRef(id));
    }

    @GetMapping("/{id}/detail")
    public Result<CustomerDetailVO> getDetail(@PathVariable Long id) {
        return Result.ok(customerService.getDetail(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@perm.has('partner:customer:edit')")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody UpdateCustomerRequest req) {
        customerService.update(id, req);
        return Result.ok();
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("@perm.has('partner:customer:status')")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        customerService.updateStatus(id, body.get("status"));
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.has('partner:customer:delete')")
    public Result<Void> delete(@PathVariable Long id) {
        customerService.delete(id);
        return Result.ok();
    }
}
