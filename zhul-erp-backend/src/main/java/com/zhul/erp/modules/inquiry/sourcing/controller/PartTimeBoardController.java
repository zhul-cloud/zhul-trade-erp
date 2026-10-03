package com.zhul.erp.modules.inquiry.sourcing.controller;

import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.inquiry.sourcing.dto.PartTimeBoardVO;
import com.zhul.erp.modules.inquiry.sourcing.service.MyTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 兼职工作台：只统计本人的询价 */
@RestController
@RequestMapping("/api/v1/inquiry/part-time-board")
@RequiredArgsConstructor
@PreAuthorize("@perm.canAccessMenu('/inquiry/part-time-board')")
public class PartTimeBoardController {

    private final MyTaskService myTaskService;

    @GetMapping
    public Result<PartTimeBoardVO> board() {
        return Result.ok(myTaskService.partTimeBoard());
    }
}
