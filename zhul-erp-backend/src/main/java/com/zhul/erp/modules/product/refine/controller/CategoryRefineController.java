package com.zhul.erp.modules.product.refine.controller;

import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.product.refine.dto.RefineDtos.ActionVO;
import com.zhul.erp.modules.product.refine.dto.RefineDtos.IdsRequest;
import com.zhul.erp.modules.product.refine.dto.RefineDtos.SubmitRequest;
import com.zhul.erp.modules.product.refine.dto.RefineDtos.TaskVO;
import com.zhul.erp.modules.product.refine.service.CategoryRefineService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 商品品类细分（spec product/category-refine）：批量 AI 细分品类与人工采纳 / 忽略，都要求「编辑商品」权限 */
@RestController
@RequestMapping("/api/v1/product/category-refine")
@RequiredArgsConstructor
public class CategoryRefineController {

    private final CategoryRefineService service;

    @PostMapping
    @PreAuthorize("@perm.has('product:product:edit')")
    public Result<TaskVO> submit(@RequestBody SubmitRequest req) {
        return Result.ok(service.submit(req));
    }

    @GetMapping("/{taskId}")
    @PreAuthorize("@perm.has('product:product:edit')")
    public Result<TaskVO> status(@PathVariable Long taskId) {
        return Result.ok(service.status(taskId));
    }

    @PostMapping("/accept")
    @PreAuthorize("@perm.has('product:product:edit')")
    public Result<ActionVO> accept(@RequestBody IdsRequest req) {
        return Result.ok(service.accept(req.getIds()));
    }

    @PostMapping("/ignore")
    @PreAuthorize("@perm.has('product:product:edit')")
    public Result<ActionVO> ignore(@RequestBody IdsRequest req) {
        return Result.ok(service.ignore(req.getIds()));
    }

    @PostMapping("/unignore")
    @PreAuthorize("@perm.has('product:product:edit')")
    public Result<ActionVO> unignore(@RequestBody IdsRequest req) {
        return Result.ok(service.unignore(req.getIds()));
    }
}
