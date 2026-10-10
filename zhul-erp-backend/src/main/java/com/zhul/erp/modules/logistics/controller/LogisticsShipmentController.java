package com.zhul.erp.modules.logistics.controller;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.document.dto.TemplateFile;
import com.zhul.erp.modules.logistics.dto.ConfirmDirectRequest;
import com.zhul.erp.modules.logistics.dto.GenerateDocsRequest;
import com.zhul.erp.modules.logistics.dto.LogisticsPageQuery;
import com.zhul.erp.modules.logistics.dto.LogisticsVO;
import com.zhul.erp.modules.logistics.dto.PendingGroupVO;
import com.zhul.erp.modules.logistics.dto.SaveLogisticsRequest;
import com.zhul.erp.modules.logistics.dto.ShipRequest;
import com.zhul.erp.modules.logistics.service.DocGroupService;
import com.zhul.erp.modules.logistics.service.LogisticsShipmentService;
import com.zhul.erp.modules.warehouse.dto.ReasonRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
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
import java.util.List;

/** 出运单：组单、直发确认、登记出运、单证组（按业务员数据权限） */
@RestController
@RequestMapping("/api/v1/logistics/shipments")
@RequiredArgsConstructor
public class LogisticsShipmentController {

    private static final String MENU = "@perm.canAccessMenu('/logistics/shipments')";
    private static final String EDIT = MENU + " and @perm.has('logistics:shipment:edit')";

    private final LogisticsShipmentService service;
    private final DocGroupService docs;

    /** 待出运的货：已交货代、还没放进出运单的出库单与直发货 */
    @GetMapping("/pending")
    @PreAuthorize(MENU)
    public Result<List<PendingGroupVO>> pending() {
        return Result.ok(service.pending());
    }

    @PostMapping("/page")
    @PreAuthorize(MENU)
    public Result<PageResult<LogisticsVO>> page(@RequestBody LogisticsPageQuery query) {
        return Result.ok(service.page(query));
    }

    @GetMapping("/{id}")
    @PreAuthorize(MENU)
    public Result<LogisticsVO> detail(@PathVariable Long id) {
        return Result.ok(service.detail(id));
    }

    @PostMapping
    @PreAuthorize(EDIT)
    public Result<LogisticsVO> create(@Valid @RequestBody SaveLogisticsRequest req) {
        return Result.ok(service.create(req));
    }

    @PutMapping("/{id}/items")
    @PreAuthorize(EDIT)
    public Result<LogisticsVO> updateItems(@PathVariable Long id, @Valid @RequestBody SaveLogisticsRequest req) {
        return Result.ok(service.updateItems(id, req));
    }

    @PostMapping("/{id}/void")
    @PreAuthorize(EDIT)
    public Result<LogisticsVO> voidShipment(@PathVariable Long id, @Valid @RequestBody ReasonRequest req) {
        return Result.ok(service.voidShipment(id, req.getReason()));
    }

    @PostMapping("/{id}/directs/{shipmentId}/confirm")
    @PreAuthorize(EDIT)
    public Result<LogisticsVO> confirmDirect(@PathVariable Long id, @PathVariable Long shipmentId, @Valid @RequestBody ConfirmDirectRequest req) {
        return Result.ok(service.confirmDirect(id, shipmentId, req));
    }

    @PostMapping("/{id}/ship")
    @PreAuthorize(EDIT)
    public Result<LogisticsVO> ship(@PathVariable Long id, @Valid @RequestBody ShipRequest req) {
        return Result.ok(service.ship(id, req));
    }

    @PutMapping("/{id}/shipping")
    @PreAuthorize(EDIT)
    public Result<LogisticsVO> updateShipping(@PathVariable Long id, @Valid @RequestBody ShipRequest req) {
        return Result.ok(service.updateShipping(id, req));
    }

    @PostMapping("/{id}/docs")
    @PreAuthorize(EDIT)
    public Result<LogisticsVO> generateDocs(@PathVariable Long id, @Valid @RequestBody GenerateDocsRequest req) {
        return Result.ok(docs.generate(id, req));
    }

    /** 导出 CI 或 PL：kind = ci / pl，format = xlsx / pdf */
    @GetMapping("/docs/{groupId}/export")
    @PreAuthorize(MENU)
    public void export(@PathVariable Long groupId, @RequestParam String kind, @RequestParam(defaultValue = "xlsx") String format,
                       HttpServletResponse response) throws IOException {
        TemplateFile file = docs.export(groupId, kind, format);
        response.setContentType(file.contentType());
        response.setContentLength(file.content().length);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''"
                + URLEncoder.encode(file.fileName(), StandardCharsets.UTF_8).replace("+", "%20"));
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.getOutputStream().write(file.content());
    }
}
