package com.zhul.erp.modules.masterdata.customerprofile.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhul.erp.modules.aitask.translation.ContactNameTranslations;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.masterdata.customerprofile.entity.CustomerContactDO;
import com.zhul.erp.modules.masterdata.customerprofile.repository.CustomerContactMapper;
import com.zhul.erp.modules.masterdata.entity.CustomerDO;
import com.zhul.erp.modules.masterdata.repository.CustomerMapper;
import com.zhul.erp.modules.masterdata.support.ContactKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 主联系人与客户的「主联系人」字段（customer.contact_*）双向同步（spec master-data/customer-profile「客户联系人与决策人画像」）：
 * 客户表单、商机登记改了主联系人 → 写到联系人列表的主联系人；联系人列表改了主联系人 → 写回客户表，询盘、PI 照常使用。
 */
@Component
@RequiredArgsConstructor
public class PrimaryContactSync {

    private final CustomerContactMapper contactMapper;
    private final CustomerMapper customerMapper;
    private final ContactNameTranslations contactNames;
    private final CurrentUserResolver currentUser;

    /** 客户表单或商机登记保存后：把主联系人字段写到主联系人（没有主联系人且字段不全空时新建） */
    public void fromCustomer(CustomerDO c) {
        CustomerContactDO primary = contactMapper.selectOne(new LambdaQueryWrapper<CustomerContactDO>()
                .eq(CustomerContactDO::getCustomerId, c.getId())
                .eq(CustomerContactDO::getIsPrimary, 1)
                .isNull(CustomerContactDO::getDeletedAt)
                .last("LIMIT 1"));
        boolean empty = !StringUtils.hasText(c.getContactName()) && !StringUtils.hasText(c.getContactEmail())
                && !StringUtils.hasText(c.getContactPhone()) && !StringUtils.hasText(c.getWhatsapp())
                && !StringUtils.hasText(c.getOtherIm());
        if (primary == null && empty) {
            return;
        }
        if (primary == null) {
            primary = new CustomerContactDO();
            primary.setTenantId(c.getTenantId());
            primary.setCustomerId(c.getId());
            primary.setIsPrimary(1);
        }
        // 新建、改了英文名、或清空了中文名（想重新翻译）时才提交翻译，避免每次保存客户都重复翻译
        boolean renamed = primary.getId() == null || !text(c.getContactName()).equals(primary.getName())
                || StringUtils.hasText(primary.getNameCn()) && !StringUtils.hasText(c.getContactNameCn());
        primary.setName(text(c.getContactName()));
        primary.setNameCn(text(c.getContactNameCn()));
        primary.setTitle(text(c.getContactTitle()));
        primary.setEmail(text(c.getContactEmail()));
        primary.setPhone(text(c.getContactPhone()));
        primary.setWhatsapp(text(c.getWhatsapp()));
        primary.setOtherIm(text(c.getOtherIm()));
        applyKeys(primary);
        if (primary.getId() == null) {
            contactMapper.insert(primary);
        } else {
            contactMapper.updateById(primary);
        }
        if (renamed) {
            contactNames.submitContact(primary.getId(), primary.getName(), primary.getNameCn(), currentUser.resolve());
        }
    }

    /** 联系人列表改了主联系人（或删掉了最后一个联系人，primary 为 null）后写回客户表 */
    public void toCustomer(Long customerId, CustomerContactDO primary) {
        CustomerContactDO p = primary == null ? new CustomerContactDO() : primary;
        customerMapper.update(null, new LambdaUpdateWrapper<CustomerDO>()
                .eq(CustomerDO::getId, customerId)
                .set(CustomerDO::getContactName, text(p.getName()))
                .set(CustomerDO::getContactNameCn, text(p.getNameCn()))
                .set(CustomerDO::getContactTitle, text(p.getTitle()))
                .set(CustomerDO::getContactEmail, text(p.getEmail()))
                .set(CustomerDO::getContactPhone, text(p.getPhone()))
                .set(CustomerDO::getWhatsapp, text(p.getWhatsapp()))
                .set(CustomerDO::getOtherIm, text(p.getOtherIm()))
                .set(CustomerDO::getEmailKey, ContactKeys.email(text(p.getEmail())))
                .set(CustomerDO::getWhatsappKey, ContactKeys.phone(text(p.getWhatsapp())))
                .set(CustomerDO::getPhoneKey, ContactKeys.phone(text(p.getPhone()))));
    }

    public static void applyKeys(CustomerContactDO x) {
        x.setEmailKey(ContactKeys.email(x.getEmail()));
        x.setWhatsappKey(ContactKeys.phone(x.getWhatsapp()));
        x.setPhoneKey(ContactKeys.phone(x.getPhone()));
    }

    private static String text(String s) {
        return s == null ? "" : s.trim();
    }
}
