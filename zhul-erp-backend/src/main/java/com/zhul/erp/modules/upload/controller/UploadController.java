package com.zhul.erp.modules.upload.controller;

import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.upload.dto.CompleteUploadRequest;
import com.zhul.erp.modules.upload.dto.PartUrlVO;
import com.zhul.erp.modules.upload.dto.PrepareUploadRequest;
import com.zhul.erp.modules.upload.dto.PrepareUploadVO;
import com.zhul.erp.modules.upload.dto.UploadedFileVO;
import com.zhul.erp.modules.upload.service.DirectUploadService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
import java.util.List;
import java.util.Map;

/** 大文件分片直传（spec system/file-attachment）；各场景的权限由场景自己校验 */
@RestController
@RequestMapping("/api/v1/uploads")
@RequiredArgsConstructor
public class UploadController {

    private final DirectUploadService service;

    @PostMapping
    public Result<PrepareUploadVO> prepare(@Valid @RequestBody PrepareUploadRequest req) {
        return Result.ok(service.prepare(req));
    }

    /** 续签：领取从 from 开始的分片地址 */
    @GetMapping("/{id}/parts")
    public Result<List<PartUrlVO>> partUrls(@PathVariable String id, @RequestParam int from,
                                            @RequestParam(defaultValue = "8") int count) {
        return Result.ok(service.partUrls(id, from, count));
    }

    /** 本地存储时接收分片，请求体为分片原始字节 */
    @PutMapping("/{id}/parts/{n}")
    public Result<Map<String, String>> receivePart(@PathVariable String id, @PathVariable int n,
                                                   @RequestParam("t") String token, HttpServletRequest request) throws IOException {
        return Result.ok(Map.of("etag", service.receivePart(id, n, token, request.getInputStream())));
    }

    @PostMapping("/{id}/complete")
    public Result<UploadedFileVO> complete(@PathVariable String id, @Valid @RequestBody CompleteUploadRequest req) {
        return Result.ok(service.complete(id, req));
    }

    @DeleteMapping("/{id}")
    public Result<Void> abort(@PathVariable String id) {
        service.abort(id);
        return Result.ok();
    }
}
