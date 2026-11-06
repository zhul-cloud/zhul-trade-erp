package com.zhul.erp.modules.masterdata.customerprofile.controller;

import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.masterdata.customerprofile.dto.DupGroupVO;
import com.zhul.erp.modules.masterdata.customerprofile.dto.MergePreviewVO;
import com.zhul.erp.modules.masterdata.customerprofile.dto.MergeRequest;
import com.zhul.erp.modules.masterdata.customerprofile.service.CustomerMergeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 疑似重复客户与合并（spec master-data/customer-merge）：查看随客户管理菜单，处理需要「合并客户」 */
@RestController
@RequestMapping("/api/v1/masterdata/customers")
@RequiredArgsConstructor
public class CustomerMergeController {

    private static final String MERGE = "@perm.has('partner:customer:merge')";

    private final CustomerMergeService service;

    @GetMapping("/duplicates")
    @PreAuthorize("@perm.canAccessMenu('/customer/list')")
    public Result<List<DupGroupVO>> duplicates() {
        return Result.ok(service.groups());
    }

    @PostMapping("/duplicates/dismiss")
    @PreAuthorize(MERGE)
    public Result<Void> dismiss(@RequestBody Map<String, String> body) {
        service.dismiss(body.get("groupKey"));
        return Result.ok();
    }

    /** 手动合并选客户：第一个成员是当前客户 */
    @GetMapping("/merge-candidates")
    @PreAuthorize(MERGE)
    public Result<DupGroupVO> candidates(@RequestParam Long targetId, @RequestParam(required = false) String keyword) {
        return Result.ok(service.candidates(targetId, keyword));
    }

    @GetMapping("/merge-preview")
    @PreAuthorize(MERGE)
    public Result<MergePreviewVO> preview(@RequestParam Long targetId, @RequestParam Long sourceId) {
        return Result.ok(service.preview(targetId, sourceId));
    }

    @PostMapping("/merge")
    @PreAuthorize(MERGE)
    public Result<Void> merge(@Valid @RequestBody MergeRequest req) {
        service.merge(req.getTargetId(), req.getSourceId());
        return Result.ok();
    }
}
