package com.zhul.erp.modules.masterdata.controller;

import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.masterdata.dto.CustomerCreateResultVO;
import com.zhul.erp.modules.masterdata.dto.CustomerVO;
import com.zhul.erp.modules.masterdata.dto.SaveCustomerRequest;
import com.zhul.erp.modules.masterdata.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 客户主数据最小可用管理：创建（含内联快速创建场景复用同一接口）、
 * 关键词搜索（供询盘等业务模块的客户选择器使用）、软删除。
 * 独立的列表/详情管理页本轮不做（见 openspec/changes/add-inquiry-management/proposal.md）。
 */
@RestController
@RequestMapping("/api/v1/masterdata/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @PostMapping
    public Result<CustomerCreateResultVO> create(@Valid @RequestBody SaveCustomerRequest req) {
        return Result.ok(customerService.create(req));
    }

    @GetMapping
    public Result<List<CustomerVO>> search(@RequestParam(required = false) String keyword) {
        return Result.ok(customerService.search(keyword));
    }

    @GetMapping("/{id}")
    public Result<CustomerVO> getById(@PathVariable Long id) {
        return Result.ok(customerService.getById(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        customerService.delete(id);
        return Result.ok();
    }
}
