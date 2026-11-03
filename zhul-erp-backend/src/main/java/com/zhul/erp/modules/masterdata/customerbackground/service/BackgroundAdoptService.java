package com.zhul.erp.modules.masterdata.customerbackground.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.masterdata.customerbackground.dto.BackgroundSuggestion;
import com.zhul.erp.modules.masterdata.customerbackground.entity.CustomerBackgroundReportDO;
import com.zhul.erp.modules.masterdata.customerbackground.repository.CustomerBackgroundReportMapper;
import com.zhul.erp.modules.masterdata.customerbackground.support.BackgroundAdoption;
import com.zhul.erp.modules.masterdata.customerbackground.support.BackgroundFields;
import com.zhul.erp.modules.masterdata.customerprofile.dto.ContactRequest;
import com.zhul.erp.modules.masterdata.customerprofile.dto.ProfileUpdateRequest;
import com.zhul.erp.modules.masterdata.customerprofile.entity.CustomerContactDO;
import com.zhul.erp.modules.masterdata.customerprofile.service.CustomerContactService;
import com.zhul.erp.modules.masterdata.customerprofile.service.CustomerProfileService;
import com.zhul.erp.modules.masterdata.customerprofile.support.FieldSources;
import com.zhul.erp.modules.masterdata.customerprofile.support.ProfileFacts;
import com.zhul.erp.modules.system.service.LogService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.zhul.erp.modules.masterdata.customerbackground.constants.BackgroundConstants.STATUS_DONE;

/**
 * 字段建议的采纳与忽略（spec crm/customer-background「字段建议与采纳」）。
 * 事务边界：锁报告行 → 按客户画像、联系人原有规则写入（来源记为这份报告，完整度随之刷新）→ 更新建议状态，一起提交；
 * 任何一条校验不过，整批不写入。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BackgroundAdoptService {

    private final CustomerBackgroundService backgroundService;
    private final CustomerBackgroundReportMapper reportMapper;
    private final BackgroundAdoption adoption;
    private final CustomerProfileService profileService;
    private final CustomerContactService contactService;
    private final ProfileFacts facts;
    private final CurrentUserResolver currentUser;
    private final Validator validator;
    private final LogService logService;
    private final ObjectMapper objectMapper;

    @Transactional(rollbackFor = Exception.class)
    public void adopt(Long customerId, Long reportId, Collection<Integer> indexes) {
        CustomerBackgroundReportDO r = lock(customerId, reportId);
        List<BackgroundSuggestion> all = backgroundService.suggestions(r);
        List<CustomerContactDO> contacts = facts.contacts(customerId);

        ProfileUpdateRequest profile = new ProfileUpdateRequest();
        profile.setFields(new ArrayList<>());
        // 联系人建议按联系人分组：已有联系人用 ID，新联系人用邮箱或姓名
        Map<String, ContactRequest> contactReqs = new LinkedHashMap<>();
        Map<String, Long> contactIds = new LinkedHashMap<>();
        List<String> labels = new ArrayList<>();
        for (int i : distinct(indexes, all.size())) {
            BackgroundSuggestion s = all.get(i);
            if (BackgroundSuggestion.ADOPTED.equals(s.getState())) {
                throw new BizException("「" + BackgroundFields.label(s.getField()) + "」已经采纳过");
            }
            BackgroundAdoption.Resolved v = adoption.resolve(s, contacts);
            if (!v.ok()) {
                throw new BizException("「" + BackgroundFields.label(s.getField()) + "」" + v.reason());
            }
            labels.add(BackgroundFields.label(s.getField()));
            if (!BackgroundFields.isContact(s.getField())) {
                if (profile.getFields().contains(s.getField())) {
                    throw new BizException("「" + BackgroundFields.label(s.getField()) + "」只能采纳一条");
                }
                BackgroundAdoption.put(profile, s.getField(), v.value());
                continue;
            }
            CustomerContactDO matched = BackgroundAdoption.matchContact(contacts, s.getContact());
            String group = matched != null ? "id:" + matched.getId() : "new:" + newContactKey(s.getContact());
            ContactRequest req = contactReqs.computeIfAbsent(group, g -> {
                if (matched != null) {
                    contactIds.put(g, matched.getId());
                    return BackgroundAdoption.requestOf(matched);
                }
                ContactRequest n = new ContactRequest();
                n.setName(s.getContact().getName().trim());
                n.setEmail(s.getContact().getEmail());
                n.setTitle(s.getContact().getTitle());
                return n;
            });
            BackgroundAdoption.put(req, s.getField(), v.value());
        }
        // 先写入档案，全部成功后才改建议状态
        if (!profile.getFields().isEmpty()) {
            validate(profile);
            profileService.update(customerId, profile, FieldSources.REPORT, r.getId(), "采纳背调建议");
        }
        for (Map.Entry<String, ContactRequest> e : contactReqs.entrySet()) {
            validate(e.getValue());
            Long contactId = contactIds.get(e.getKey());
            if (contactId != null) {
                contactService.update(customerId, contactId, e.getValue(), FieldSources.REPORT, r.getId());
            } else {
                contactService.create(customerId, e.getValue(), FieldSources.REPORT, r.getId());
            }
        }
        for (int i : distinct(indexes, all.size())) {
            markDone(all.get(i), BackgroundSuggestion.ADOPTED);
        }
        save(r, all);
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("报告编号", r.getReportNo());
        after.put("采纳字段", String.join("、", labels));
        logService.recordOperateLog("客户管理", "采纳背调建议", null, after);
        log.info("采纳背调建议，reportNo={}, count={}", r.getReportNo(), labels.size());
    }

    @Transactional(rollbackFor = Exception.class)
    public void ignore(Long customerId, Long reportId, Collection<Integer> indexes) {
        CustomerBackgroundReportDO r = lock(customerId, reportId);
        List<BackgroundSuggestion> all = backgroundService.suggestions(r);
        for (int i : distinct(indexes, all.size())) {
            BackgroundSuggestion s = all.get(i);
            if (BackgroundSuggestion.ADOPTED.equals(s.getState())) {
                throw new BizException("「" + BackgroundFields.label(s.getField()) + "」已经采纳，不能忽略");
            }
            markDone(s, BackgroundSuggestion.IGNORED);
        }
        save(r, all);
    }

    private CustomerBackgroundReportDO lock(Long customerId, Long reportId) {
        backgroundService.require(customerId, reportId);
        reportMapper.lockById(reportId);
        CustomerBackgroundReportDO r = backgroundService.require(customerId, reportId);
        if (r.getStatus() != STATUS_DONE) {
            throw new BizException("报告还没有完成，不能处理字段建议");
        }
        return r;
    }

    private static Set<Integer> distinct(Collection<Integer> indexes, int size) {
        if (indexes == null || indexes.isEmpty()) {
            throw new BizException("请选择要处理的建议");
        }
        Set<Integer> out = new LinkedHashSet<>();
        for (Integer i : indexes) {
            if (i == null || i < 0 || i >= size) {
                throw new BizException("建议不存在，请刷新后重试");
            }
            out.add(i);
        }
        return out;
    }

    private static String newContactKey(BackgroundSuggestion.Contact ct) {
        String email = ct.getEmail() == null ? "" : ct.getEmail().trim().toLowerCase();
        return email.isEmpty() ? "name:" + ct.getName().trim().toLowerCase() : "email:" + email;
    }

    private void markDone(BackgroundSuggestion s, String state) {
        s.setState(state);
        s.setOperatorId(currentUser.resolve());
        s.setAt(LocalDateTime.now());
    }

    private <T> void validate(T req) {
        Set<ConstraintViolation<T>> errors = validator.validate(req);
        if (!errors.isEmpty()) {
            throw new BizException(errors.iterator().next().getMessage());
        }
    }

    private void save(CustomerBackgroundReportDO r, List<BackgroundSuggestion> all) {
        CustomerBackgroundReportDO u = new CustomerBackgroundReportDO();
        u.setId(r.getId());
        try {
            u.setSuggestions(objectMapper.writeValueAsString(all));
        } catch (JsonProcessingException e) {
            throw new BizException("保存建议状态失败", e);
        }
        reportMapper.updateById(u);
    }
}
