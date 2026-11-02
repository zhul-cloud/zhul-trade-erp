package com.zhul.erp.modules.masterdata.customerprofile.controller;

import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.masterdata.customerprofile.dto.AskedRequest;
import com.zhul.erp.modules.masterdata.customerprofile.dto.ContactRequest;
import com.zhul.erp.modules.masterdata.customerprofile.dto.ContactVO;
import com.zhul.erp.modules.masterdata.customerprofile.dto.CustomerProfileVO;
import com.zhul.erp.modules.masterdata.customerprofile.dto.GradeAdjustRequest;
import com.zhul.erp.modules.masterdata.customerprofile.dto.ProfileUpdateRequest;
import com.zhul.erp.modules.masterdata.customerprofile.dto.RfmDetailVO;
import com.zhul.erp.modules.masterdata.customerprofile.service.CustomerContactService;
import com.zhul.erp.modules.masterdata.customerprofile.service.CustomerGradeService;
import com.zhul.erp.modules.masterdata.customerprofile.service.CustomerProfileService;
import com.zhul.erp.modules.masterdata.service.CustomerService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 客户画像、联系人、经营等级（spec master-data/customer-profile）。按数据权限只能操作自己范围内的客户；修改需要「编辑客户」 */
@RestController
@RequestMapping("/api/v1/masterdata/customers/{id}")
@RequiredArgsConstructor
@PreAuthorize("@perm.canAccessMenu('/customer/list')")
public class CustomerProfileController {

    private static final String EDIT = "@perm.has('partner:customer:edit')";

    private final CustomerProfileService profileService;
    private final CustomerContactService contactService;
    private final CustomerGradeService gradeService;
    private final CustomerService customerService;

    @GetMapping("/profile")
    public Result<CustomerProfileVO> profile(@PathVariable Long id) {
        return Result.ok(profileService.get(id));
    }

    @PutMapping("/profile")
    @PreAuthorize(EDIT)
    public Result<Void> updateProfile(@PathVariable Long id, @Valid @RequestBody ProfileUpdateRequest req) {
        profileService.update(id, req);
        return Result.ok();
    }

    @PostMapping("/profile/asked")
    @PreAuthorize(EDIT)
    public Result<Void> asked(@PathVariable Long id, @Valid @RequestBody AskedRequest req) {
        profileService.markAsked(id, req.getKeys(), req.getAsked());
        return Result.ok();
    }

    /** RFM 明细（对应《成交客户表》），含参与计算的订单 */
    @GetMapping("/rfm")
    public Result<RfmDetailVO> rfm(@PathVariable Long id) {
        return Result.ok(gradeService.detail(customerService.requireInScope(id)));
    }

    @PutMapping("/grade")
    @PreAuthorize(EDIT)
    public Result<Void> adjustGrade(@PathVariable Long id, @Valid @RequestBody GradeAdjustRequest req) {
        gradeService.adjust(customerService.requireInScope(id), req);
        return Result.ok();
    }

    @GetMapping("/contacts")
    public Result<List<ContactVO>> contacts(@PathVariable Long id) {
        return Result.ok(contactService.list(id));
    }

    @PostMapping("/contacts")
    @PreAuthorize(EDIT)
    public Result<ContactVO> createContact(@PathVariable Long id, @Valid @RequestBody ContactRequest req) {
        return Result.ok(contactService.create(id, req));
    }

    @PutMapping("/contacts/{contactId}")
    @PreAuthorize(EDIT)
    public Result<ContactVO> updateContact(@PathVariable Long id, @PathVariable Long contactId, @Valid @RequestBody ContactRequest req) {
        return Result.ok(contactService.update(id, contactId, req));
    }

    @PutMapping("/contacts/{contactId}/primary")
    @PreAuthorize(EDIT)
    public Result<Void> setPrimary(@PathVariable Long id, @PathVariable Long contactId) {
        contactService.setPrimary(id, contactId);
        return Result.ok();
    }

    @DeleteMapping("/contacts/{contactId}")
    @PreAuthorize(EDIT)
    public Result<Void> deleteContact(@PathVariable Long id, @PathVariable Long contactId) {
        contactService.delete(id, contactId);
        return Result.ok();
    }
}
