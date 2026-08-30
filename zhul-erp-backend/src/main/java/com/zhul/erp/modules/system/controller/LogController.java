package com.zhul.erp.modules.system.controller;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.system.dto.ForceLogoutRequest;
import com.zhul.erp.modules.system.dto.ForceLogoutResultVO;
import com.zhul.erp.modules.system.dto.LoginLogQuery;
import com.zhul.erp.modules.system.dto.LoginLogVO;
import com.zhul.erp.modules.system.dto.OperateLogDetailVO;
import com.zhul.erp.modules.system.dto.OperateLogQuery;
import com.zhul.erp.modules.system.dto.OperateLogVO;
import com.zhul.erp.modules.system.service.LogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/v1/system/logs")
@RequiredArgsConstructor
public class LogController {

    private final LogService logService;

    @GetMapping("/operate")
    public Result<PageResult<OperateLogVO>> listOperateLogs(OperateLogQuery query) {
        return Result.ok(logService.listOperateLogs(query));
    }

    @GetMapping("/operate/modules")
    public Result<List<String>> listOperateModules() {
        return Result.ok(logService.listOperateModules());
    }

    @GetMapping("/operate/{id}")
    public Result<OperateLogDetailVO> getOperateLogDetail(@PathVariable Long id) {
        return Result.ok(logService.getOperateLogDetail(id));
    }

    @GetMapping("/operate/export")
    public void exportOperateLogs(OperateLogQuery query, HttpServletResponse response) throws IOException {
        Workbook workbook = logService.exportOperateLogs(query);
        String filename = "操作日志_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".xlsx";
        writeWorkbook(workbook, filename, response);
    }

    @GetMapping("/login")
    public Result<PageResult<LoginLogVO>> listLoginLogs(LoginLogQuery query) {
        return Result.ok(logService.listLoginLogs(query));
    }

    @PreAuthorize("@perm.has('system:log:login:forceLogout')")
    @PostMapping("/login/force-logout")
    public Result<ForceLogoutResultVO> forceLogout(@Valid @RequestBody ForceLogoutRequest req,
                                                     HttpServletRequest request) {
        return Result.ok(logService.forceLogout(req.getTokenIds(), request));
    }

    @GetMapping("/login/export")
    public void exportLoginLogs(LoginLogQuery query, HttpServletResponse response) throws IOException {
        Workbook workbook = logService.exportLoginLogs(query);
        String filename = "登录日志_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".xlsx";
        writeWorkbook(workbook, filename, response);
    }

    private void writeWorkbook(Workbook workbook, String filename, HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename*=UTF-8''" + URLEncoder.encode(filename, StandardCharsets.UTF_8));
        workbook.write(response.getOutputStream());
        workbook.close();
    }
}
