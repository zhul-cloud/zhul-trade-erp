package com.zhul.erp.modules.system.controller;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.system.dto.ConfigGroupCountVO;
import com.zhul.erp.modules.system.dto.ConfigVO;
import com.zhul.erp.modules.system.dto.SaveConfigRequest;
import com.zhul.erp.modules.system.dto.UpdateConfigValueRequest;
import com.zhul.erp.modules.system.dto.UploadImageVO;
import com.zhul.erp.modules.system.service.ConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/system/configs")
@RequiredArgsConstructor
public class ConfigController {

    private final ConfigService configService;

    @GetMapping
    public Result<PageResult<ConfigVO>> listAll(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String group) {
        return Result.ok(configService.listAll(page, pageSize, keyword, group));
    }

    @GetMapping("/group-counts")
    public Result<List<ConfigGroupCountVO>> groupCounts() {
        return Result.ok(configService.groupCounts());
    }

    @PreAuthorize("@perm.has('system:config:add')")
    @PostMapping
    public Result<Void> create(@Valid @RequestBody SaveConfigRequest req) {
        configService.create(req);
        return Result.ok();
    }

    @PreAuthorize("@perm.has('system:config:edit')")
    @PutMapping("/{id}")
    public Result<Void> updateValue(@PathVariable Integer id, @RequestBody UpdateConfigValueRequest req) {
        configService.updateValue(id, req.getValue());
        return Result.ok();
    }

    @PreAuthorize("@perm.has('system:config:delete')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Integer id) {
        configService.delete(id);
        return Result.ok();
    }

    @PreAuthorize("@perm.has('system:config:edit')")
    @PostMapping("/upload-image")
    public Result<UploadImageVO> uploadImage(@RequestPart("file") MultipartFile file) {
        return Result.ok(new UploadImageVO(configService.uploadImage(file)));
    }
}
