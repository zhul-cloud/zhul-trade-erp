package com.zhul.erp.modules.masterdata.customerprofile.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.masterdata.customerprofile.dto.ContactRequest;
import com.zhul.erp.modules.masterdata.customerprofile.dto.ContactVO;
import com.zhul.erp.modules.masterdata.customerprofile.entity.CustomerContactDO;
import com.zhul.erp.modules.masterdata.customerprofile.repository.CustomerContactMapper;
import com.zhul.erp.modules.masterdata.customerprofile.support.FieldSources;
import com.zhul.erp.modules.masterdata.customerprofile.support.PrimaryContactSync;
import com.zhul.erp.modules.masterdata.customerprofile.support.ProfileFacts;
import com.zhul.erp.modules.masterdata.customerprofile.support.ProfileField;
import com.zhul.erp.modules.masterdata.entity.CustomerDO;
import com.zhul.erp.modules.masterdata.service.CustomerService;
import com.zhul.erp.modules.system.service.DictItemService;
import com.zhul.erp.modules.system.service.LogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 客户联系人与决策人画像（spec master-data/customer-profile「客户联系人与决策人画像」） */
@Service
@RequiredArgsConstructor
public class CustomerContactService {

    private final CustomerService customerService;
    private final CustomerContactMapper contactMapper;
    private final PrimaryContactSync primarySync;
    private final ProfileFacts facts;
    private final FieldSources sources;
    private final DictItemService dictItemService;
    private final CurrentUserResolver currentUser;
    private final LogService logService;

    public List<ContactVO> list(Long customerId) {
        customerService.requireInScope(customerId);
        return facts.contacts(customerId).stream().map(CustomerContactService::toVo).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public ContactVO create(Long customerId, ContactRequest req) {
        CustomerDO c = customerService.requireInScope(customerId);
        List<CustomerContactDO> existing = facts.contacts(customerId);
        CustomerContactDO x = new CustomerContactDO();
        x.setTenantId(c.getTenantId());
        x.setCustomerId(customerId);
        x.setIsPrimary(0);
        List<String> changed = apply(x, req);
        boolean primary = existing.isEmpty() || Boolean.TRUE.equals(req.getPrimary());
        contactMapper.insert(x);
        if (primary) {
            makePrimary(customerId, x);
        }
        sources.record(c.getTenantId(), customerId, keys(x.getId(), changed), FieldSources.MANUAL, 0L, currentUser.resolve());
        facts.refresh(c);
        logService.recordOperateLog("客户管理", "新增联系人", null, Map.of("customerCode", c.getCustomerCode(), "联系人", x.getName()));
        return toVo(x);
    }

    @Transactional(rollbackFor = Exception.class)
    public ContactVO update(Long customerId, Long contactId, ContactRequest req) {
        CustomerDO c = customerService.requireInScope(customerId);
        CustomerContactDO x = require(customerId, contactId);
        List<String> changed = apply(x, req);
        contactMapper.updateById(x);
        if (Boolean.TRUE.equals(req.getPrimary()) && !isPrimary(x)) {
            makePrimary(customerId, x);
        } else if (isPrimary(x)) {
            primarySync.toCustomer(customerId, x);
        }
        sources.record(c.getTenantId(), customerId, keys(contactId, changed), FieldSources.MANUAL, 0L, currentUser.resolve());
        facts.refresh(c);
        logService.recordOperateLog("客户管理", "修改联系人", null, Map.of("customerCode", c.getCustomerCode(), "联系人", x.getName()));
        return toVo(x);
    }

    @Transactional(rollbackFor = Exception.class)
    public void setPrimary(Long customerId, Long contactId) {
        CustomerDO c = customerService.requireInScope(customerId);
        CustomerContactDO x = require(customerId, contactId);
        if (!isPrimary(x)) {
            makePrimary(customerId, x);
            logService.recordOperateLog("客户管理", "设为主联系人", null, Map.of("customerCode", c.getCustomerCode(), "联系人", x.getName()));
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long customerId, Long contactId) {
        CustomerDO c = customerService.requireInScope(customerId);
        CustomerContactDO x = require(customerId, contactId);
        boolean last = facts.contacts(customerId).size() == 1;
        if (isPrimary(x) && !last) {
            throw new BizException("请先把其他联系人设为主联系人");
        }
        contactMapper.update(null, new LambdaUpdateWrapper<CustomerContactDO>()
                .eq(CustomerContactDO::getId, contactId)
                .set(CustomerContactDO::getDeletedAt, LocalDateTime.now())
                .set(CustomerContactDO::getIsPrimary, 0));
        if (isPrimary(x)) {
            primarySync.toCustomer(customerId, null);
        }
        facts.refresh(c);
        logService.recordOperateLog("客户管理", "删除联系人", Map.of("customerCode", c.getCustomerCode(), "联系人", x.getName()), null);
    }

    private void makePrimary(Long customerId, CustomerContactDO x) {
        contactMapper.update(null, new LambdaUpdateWrapper<CustomerContactDO>()
                .eq(CustomerContactDO::getCustomerId, customerId)
                .ne(CustomerContactDO::getId, x.getId())
                .set(CustomerContactDO::getIsPrimary, 0));
        contactMapper.update(null, new LambdaUpdateWrapper<CustomerContactDO>()
                .eq(CustomerContactDO::getId, x.getId())
                .set(CustomerContactDO::getIsPrimary, 1));
        x.setIsPrimary(1);
        primarySync.toCustomer(customerId, x);
    }

    /** 写入请求的值，返回改了的画像字段（用于记来源）；选项型字段校验字典（沿用原值时不校验） */
    private List<String> apply(CustomerContactDO x, ContactRequest req) {
        x.setName(text(req.getName()));
        x.setTitle(text(req.getTitle()));
        x.setEmail(text(req.getEmail()));
        x.setPhone(text(req.getPhone()));
        x.setWhatsapp(text(req.getWhatsapp()));
        x.setOtherIm(text(req.getOtherIm()));
        PrimaryContactSync.applyKeys(x);
        List<String> changed = new ArrayList<>();
        x.setJobRole(dict(ProfileField.CONTACT_JOB_ROLE, x.getJobRole(), req.getJobRole(), changed));
        x.setDecisionRole(dict(ProfileField.CONTACT_DECISION_ROLE, x.getDecisionRole(), req.getDecisionRole(), changed));
        x.setCommStyle(dict(ProfileField.CONTACT_COMM_STYLE, x.getCommStyle(), req.getCommStyle(), changed));
        x.setDisc(dict(ProfileField.CONTACT_DISC, x.getDisc(), req.getDisc(), changed));
        x.setChinaAttitude(dict(ProfileField.CONTACT_CHINA_ATTITUDE, x.getChinaAttitude(), req.getChinaAttitude(), changed));
        String hours = text(req.getActiveHours());
        if (!hours.equals(text(x.getActiveHours()))) {
            changed.add(ProfileField.CONTACT_ACTIVE_HOURS.key());
        }
        x.setActiveHours(hours);
        String likes = text(req.getLikes());
        if (!likes.equals(text(x.getLikes()))) {
            changed.add(ProfileField.CONTACT_LIKES.key());
        }
        x.setLikes(likes);
        return changed;
    }

    private Integer dict(ProfileField f, Integer old, Integer value, List<String> changed) {
        int v = value == null ? 0 : value;
        int o = old == null ? 0 : old;
        if (v != o) {
            if (v != 0) {
                dictItemService.requireEnabledValue(f.dictType(), v, f.label() + "不正确");
            }
            changed.add(f.key());
        }
        return v;
    }

    private static List<String> keys(Long contactId, List<String> fields) {
        return fields.stream().map(k -> "contact." + contactId + "." + k).toList();
    }

    private CustomerContactDO require(Long customerId, Long contactId) {
        CustomerContactDO x = contactId == null ? null : contactMapper.selectById(contactId);
        if (x == null || x.getDeletedAt() != null || !Objects.equals(x.getCustomerId(), customerId)) {
            throw new BizException("联系人不存在");
        }
        return x;
    }

    private static boolean isPrimary(CustomerContactDO x) {
        return x.getIsPrimary() != null && x.getIsPrimary() == 1;
    }

    static ContactVO toVo(CustomerContactDO x) {
        ContactVO vo = new ContactVO();
        vo.setId(x.getId());
        vo.setPrimary(isPrimary(x));
        vo.setName(x.getName());
        vo.setTitle(x.getTitle());
        vo.setEmail(x.getEmail());
        vo.setPhone(x.getPhone());
        vo.setWhatsapp(x.getWhatsapp());
        vo.setOtherIm(x.getOtherIm());
        vo.setJobRole(x.getJobRole());
        vo.setDecisionRole(x.getDecisionRole());
        vo.setCommStyle(x.getCommStyle());
        vo.setDisc(x.getDisc());
        vo.setChinaAttitude(x.getChinaAttitude());
        vo.setActiveHours(x.getActiveHours());
        vo.setLikes(x.getLikes());
        vo.setUpdateTime(x.getUpdateTime());
        return vo;
    }

    private static String text(String s) {
        return s == null ? "" : s.trim();
    }
}
