package com.zhul.erp.modules.inquiry.sourcing.controller;

import com.zhul.erp.modules.inquiry.sourcing.service.SourcingExcelService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** 把询价包写回响应 */
final class ExcelResponses {

    private ExcelResponses() {
    }

    static void write(SourcingExcelService.Download d, HttpServletResponse response) throws IOException {
        response.setContentType(d.contentType());
        response.setContentLength(d.bytes().length);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''"
                + URLEncoder.encode(d.fileName(), StandardCharsets.UTF_8).replace("+", "%20"));
        response.getOutputStream().write(d.bytes());
    }
}
