package com.zhul.erp.modules.inquiry.sourcing.controller;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.PriceHistoryGroupVO;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.PriceHistoryQuery;
import com.zhul.erp.modules.inquiry.sourcing.service.PriceHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 历史询价：业务员、采购、采购负责人可查，兼职采购不可见（没有该菜单） */
@RestController
@RequestMapping("/api/v1/inquiry/price-history")
@RequiredArgsConstructor
@PreAuthorize("@perm.canAccessMenu('/inquiry/price-history')")
public class PriceHistoryController {

    private final PriceHistoryService priceHistoryService;

    @PostMapping("/page")
    public Result<PageResult<PriceHistoryGroupVO>> page(@RequestBody PriceHistoryQuery query) {
        return Result.ok(priceHistoryService.query(query));
    }
}
