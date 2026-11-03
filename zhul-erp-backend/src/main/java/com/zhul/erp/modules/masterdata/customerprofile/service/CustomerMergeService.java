package com.zhul.erp.modules.masterdata.customerprofile.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.security.DataScopeResolver;
import com.zhul.erp.modules.masterdata.customerprofile.dto.DupGroupVO;
import com.zhul.erp.modules.masterdata.customerprofile.dto.MergePreviewVO;
import com.zhul.erp.modules.masterdata.customerprofile.entity.CustomerContactDO;
import com.zhul.erp.modules.masterdata.customerprofile.entity.CustomerDupDismissDO;
import com.zhul.erp.modules.masterdata.customerprofile.repository.CustomerContactMapper;
import com.zhul.erp.modules.masterdata.customerprofile.repository.CustomerDupDismissMapper;
import com.zhul.erp.modules.masterdata.customerprofile.support.FieldSources;
import com.zhul.erp.modules.masterdata.customerprofile.support.ProfileFacts;
import com.zhul.erp.modules.masterdata.customerprofile.support.ProfileField;
import com.zhul.erp.modules.masterdata.entity.CustomerDO;
import com.zhul.erp.modules.masterdata.repository.CustomerMapper;
import com.zhul.erp.modules.masterdata.service.CustomerService;
import com.zhul.erp.modules.system.entity.UserBasicDO;
import com.zhul.erp.modules.system.repository.UserBasicMapper;
import com.zhul.erp.modules.system.service.LogService;
import com.zhul.erp.framework.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** 疑似重复客户与合并（spec master-data/customer-merge） */
@Service
@RequiredArgsConstructor
public class CustomerMergeService {

    /** 合并时改挂到保留客户的单据：表名 → 名称（表名是固定常量，不来自请求） */
    static final Map<String, String> DOCUMENTS = linked(
            "opportunity", "商机", "customer_inquiry", "询盘", "inquiry_order", "旧版询盘", "quotation", "报价",
            "proforma_invoice", "PI", "sales_order", "销售订单", "outbound_order", "出库单", "logistics_shipment", "出运单",
            "after_sales", "售后单", "customer_party", "单证主体");
    /** 有这些记录时不能合并：属于财务记录，不在合并里改 */
    static final List<String> FINANCE = List.of("fin_customer_credit_entry", "fin_customer_refund");

    private final CustomerService customerService;
    private final CustomerMapper customerMapper;
    private final CustomerContactMapper contactMapper;
    private final CustomerDupDismissMapper dismissMapper;
    private final DataScopeResolver dataScopeResolver;
    private final UserBasicMapper userBasicMapper;
    private final FieldSources sources;
    private final ProfileFacts facts;
    private final CustomerGradeService gradeService;
    private final LogService logService;
    private final JdbcTemplate jdbc;

    // ---------------------------------------------------------------- 疑似重复

    public List<DupGroupVO> groups() {
        int tenantId = tenantId();
        LambdaQueryWrapper<CustomerDO> w = new LambdaQueryWrapper<CustomerDO>()
                .eq(CustomerDO::getTenantId, tenantId)
                .isNull(CustomerDO::getDeletedAt);
        dataScopeResolver.current().apply(w, CustomerDO::getOwnerId);
        List<CustomerDO> customers = customerMapper.selectList(w);
        Map<Long, CustomerDO> byId = customers.stream().collect(Collectors.toMap(CustomerDO::getId, Function.identity()));
        Map<String, Set<Long>> name = new LinkedHashMap<>();
        Map<String, Set<Long>> email = new LinkedHashMap<>();
        Map<String, Set<Long>> phone = new LinkedHashMap<>();
        for (CustomerDO c : customers) {
            if (StringUtils.hasText(c.getNameKey()) && StringUtils.hasText(c.getCountry())) {
                name.computeIfAbsent(c.getNameKey() + "|" + c.getCountry(), k -> new TreeSet<>()).add(c.getId());
            }
            add(email, c.getEmailKey(), c.getId());
            add(phone, c.getWhatsappKey(), c.getId());
            add(phone, c.getPhoneKey(), c.getId());
        }
        if (!byId.isEmpty()) {
            for (List<Long> part : partition(new ArrayList<>(byId.keySet()))) {
                for (CustomerContactDO x : contactMapper.selectList(new LambdaQueryWrapper<CustomerContactDO>()
                        .in(CustomerContactDO::getCustomerId, part).isNull(CustomerContactDO::getDeletedAt))) {
                    add(email, x.getEmailKey(), x.getCustomerId());
                    add(phone, x.getWhatsappKey(), x.getCustomerId());
                    add(phone, x.getPhoneKey(), x.getCustomerId());
                }
            }
        }
        Set<String> dismissed = dismissMapper.selectList(new LambdaQueryWrapper<CustomerDupDismissDO>()
                        .eq(CustomerDupDismissDO::getTenantId, tenantId).isNull(CustomerDupDismissDO::getDeletedAt))
                .stream().map(CustomerDupDismissDO::getGroupKey).collect(Collectors.toSet());
        List<DupGroupVO> out = new ArrayList<>();
        Set<String> seenMembers = new HashSet<>();
        collect(out, seenMembers, dismissed, name, "NAME", "名称与国家相同");
        collect(out, seenMembers, dismissed, email, "EMAIL", "邮箱相同");
        collect(out, seenMembers, dismissed, phone, "PHONE", "WhatsApp 或电话相同");
        fillMembers(out, byId);
        return out;
    }

    @Transactional(rollbackFor = Exception.class)
    public void dismiss(String groupKey) {
        if (!StringUtils.hasText(groupKey) || groupKey.length() > 200) {
            throw new BizException("疑似重复组不正确");
        }
        CustomerDupDismissDO d = new CustomerDupDismissDO();
        d.setTenantId(tenantId());
        d.setGroupKey(groupKey);
        dismissMapper.insert(d);
    }

    // ---------------------------------------------------------------- 合并

    public MergePreviewVO preview(Long targetId, Long sourceId) {
        CustomerDO target = customerService.requireInScope(targetId);
        CustomerDO source = customerService.requireInScope(sourceId);
        if (Objects.equals(targetId, sourceId)) {
            throw new BizException("请选择两个不同的客户");
        }
        MergePreviewVO vo = new MergePreviewVO();
        Map<String, Long> docs = new LinkedHashMap<>();
        DOCUMENTS.forEach((table, label) -> {
            long n = count(table, source);
            if (n > 0) {
                docs.put(label, n);
            }
        });
        vo.setDocuments(docs);
        vo.setBlocked(blocked(source));
        Set<String> targetKeys = contactKeys(target.getId());
        for (CustomerContactDO x : facts.contacts(source.getId())) {
            if (!duplicate(x, targetKeys)) {
                vo.getContacts().add(x.getName());
            }
        }
        for (Fill f : FILLS) {
            if (empty(f.get.apply(target)) && !empty(f.get.apply(source))) {
                vo.getFilledFields().add(f.label);
            }
        }
        return vo;
    }

    /** 被合并客户的单据改挂到保留客户、联系人搬过去、空字段补上，被合并客户软删除；一个事务 */
    @Transactional(rollbackFor = Exception.class)
    public void merge(Long targetId, Long sourceId) {
        // 两个客户按 ID 顺序加行锁再校验：同时提交「A 并入 B」和「B 并入 A」时，后到的看到对方已删除而失败
        if (targetId != null && sourceId != null) {
            customerMapper.lockById(Math.min(targetId, sourceId));
            customerMapper.lockById(Math.max(targetId, sourceId));
        }
        MergePreviewVO preview = preview(targetId, sourceId);
        if (preview.getBlocked() != null) {
            throw new BizException(preview.getBlocked());
        }
        CustomerDO target = customerMapper.selectById(targetId);
        CustomerDO source = customerMapper.selectById(sourceId);
        for (String table : DOCUMENTS.keySet()) {
            jdbc.update("UPDATE `" + table + "` SET customer_id = ? WHERE customer_id = ? AND tenant_id = ?",
                    target.getId(), source.getId(), source.getTenantId());
        }
        Set<String> targetKeys = contactKeys(target.getId());
        for (CustomerContactDO x : facts.contacts(source.getId())) {
            if (duplicate(x, targetKeys)) {
                continue;
            }
            contactMapper.update(null, new LambdaUpdateWrapper<CustomerContactDO>()
                    .eq(CustomerContactDO::getId, x.getId())
                    .set(CustomerContactDO::getCustomerId, target.getId())
                    .set(CustomerContactDO::getIsPrimary, 0));
        }
        sources.moveContactKeys(source.getId(), target.getId());
        List<String> filledKeys = new ArrayList<>();
        for (Fill f : FILLS) {
            if (empty(f.get.apply(target)) && !empty(f.get.apply(source))) {
                f.copy.accept(source, target);
                filledKeys.add(f.key);
            }
        }
        if (!filledKeys.isEmpty()) {
            customerMapper.updateById(target);
            sources.moveMissing(source.getId(), target.getId(), filledKeys);
        }
        String remark = StringUtils.hasText(source.getRemark()) ? source.getRemark() + "；" : "";
        customerMapper.update(null, new LambdaUpdateWrapper<CustomerDO>()
                .eq(CustomerDO::getId, source.getId())
                .set(CustomerDO::getDeletedAt, LocalDateTime.now())
                .set(CustomerDO::getRemark, cut(remark + "已并入 " + target.getCustomerCode(), 500)));
        gradeService.detail(target);
        facts.refresh(target);
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("保留客户", target.getCustomerCode() + " " + target.getName());
        after.put("被合并客户", source.getCustomerCode() + " " + source.getName());
        after.put("改挂单据", preview.getDocuments());
        after.put("搬过去的联系人", preview.getContacts());
        after.put("补上的字段", preview.getFilledFields());
        logService.recordOperateLog("客户管理", "合并客户", null, after);
    }

    private String blocked(CustomerDO source) {
        for (String table : FINANCE) {
            if (count(table, source) > 0) {
                return "这个客户有客户余额或退款记录，请先联系财务处理";
            }
        }
        return null;
    }

    private long count(String table, CustomerDO c) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM `" + table + "` WHERE customer_id = ? AND tenant_id = ? AND deleted_at IS NULL",
                Long.class, c.getId(), c.getTenantId());
        return n == null ? 0 : n;
    }

    private Set<String> contactKeys(Long customerId) {
        Set<String> keys = new HashSet<>();
        for (CustomerContactDO x : facts.contacts(customerId)) {
            if (StringUtils.hasText(x.getEmailKey())) {
                keys.add("e:" + x.getEmailKey());
            }
            if (StringUtils.hasText(x.getWhatsappKey())) {
                keys.add("w:" + x.getWhatsappKey());
            }
        }
        return keys;
    }

    private static boolean duplicate(CustomerContactDO x, Set<String> targetKeys) {
        return (StringUtils.hasText(x.getEmailKey()) && targetKeys.contains("e:" + x.getEmailKey()))
                || (StringUtils.hasText(x.getWhatsappKey()) && targetKeys.contains("w:" + x.getWhatsappKey()));
    }

    // ---------------------------------------------------------------- 组装

    private void collect(List<DupGroupVO> out, Set<String> seenMembers, Set<String> dismissed, Map<String, Set<Long>> buckets,
                         String reason, String text) {
        for (Set<Long> ids : buckets.values()) {
            if (ids.size() < 2) {
                continue;
            }
            String members = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
            String key = reason + ":" + members;
            if (dismissed.contains(key) || !seenMembers.add(members)) {
                continue;
            }
            DupGroupVO g = new DupGroupVO();
            g.setGroupKey(key);
            g.setReason(reason);
            g.setReasonText(text);
            ids.forEach(id -> {
                DupGroupVO.Member m = new DupGroupVO.Member();
                m.setId(id);
                g.getMembers().add(m);
            });
            out.add(g);
        }
    }

    private void fillMembers(List<DupGroupVO> groups, Map<Long, CustomerDO> byId) {
        Set<Long> ids = groups.stream().flatMap(g -> g.getMembers().stream()).map(DupGroupVO.Member::getId).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, Long> inquiries = countBy("customer_inquiry", ids);
        Map<Long, Long> orders = countBy("sales_order", ids);
        Set<Long> owners = ids.stream().map(byId::get).map(CustomerDO::getOwnerId).filter(o -> o != null && o > 0).collect(Collectors.toSet());
        Map<Long, String> names = owners.isEmpty() ? Map.of() : userBasicMapper.selectBatchIds(owners).stream()
                .collect(Collectors.toMap(u -> Long.valueOf(u.getId()), UserBasicDO::getName, (a, b) -> a));
        for (DupGroupVO g : groups) {
            for (DupGroupVO.Member m : g.getMembers()) {
                CustomerDO c = byId.get(m.getId());
                m.setCustomerCode(c.getCustomerCode());
                m.setName(StringUtils.hasText(c.getName()) ? c.getName() : c.getContactName());
                m.setCountry(c.getCountry());
                m.setOwnerId(c.getOwnerId());
                m.setOwnerName(names.get(c.getOwnerId()));
                m.setProfileDone(Integer.bitCount(c.getProfileMask() == null ? 0 : c.getProfileMask()));
                m.setInquiryCount(inquiries.getOrDefault(m.getId(), 0L));
                m.setOrderCount(orders.getOrDefault(m.getId(), 0L));
            }
        }
    }

    private Map<Long, Long> countBy(String table, Collection<Long> ids) {
        Map<Long, Long> out = new HashMap<>();
        int tenantId = tenantId();
        for (List<Long> part : partition(new ArrayList<>(ids))) {
            String marks = part.stream().map(x -> "?").collect(Collectors.joining(","));
            jdbc.query("SELECT customer_id, COUNT(*) FROM `" + table + "` WHERE deleted_at IS NULL AND tenant_id = ? AND customer_id IN ("
                    + marks + ") GROUP BY customer_id", rs -> {
                out.put(rs.getLong(1), rs.getLong(2));
            }, Stream.concat(Stream.of((Object) tenantId), part.stream()).toArray());
        }
        return out;
    }

    private static void add(Map<String, Set<Long>> buckets, String key, Long id) {
        if (StringUtils.hasText(key)) {
            buckets.computeIfAbsent(key, k -> new TreeSet<>()).add(id);
        }
    }

    // ---------------------------------------------------------------- 合并时补空字段

    private record Fill(String key, String label, Function<CustomerDO, Object> get, BiConsumer<CustomerDO, CustomerDO> copy) {
    }

    private static final List<Fill> FILLS = List.of(
            fill(ProfileField.CUSTOMER_ROLE, CustomerDO::getCustomerRole, (s, t) -> t.setCustomerRole(s.getCustomerRole())),
            fill(ProfileField.INDUSTRY, CustomerDO::getIndustry, (s, t) -> t.setIndustry(s.getIndustry())),
            new Fill("website", "官网", CustomerDO::getWebsite, (s, t) -> t.setWebsite(s.getWebsite())),
            new Fill("linkedin", "LinkedIn", CustomerDO::getLinkedin, (s, t) -> t.setLinkedin(s.getLinkedin())),
            fill(ProfileField.SOCIAL, CustomerDO::getSocialUrl, (s, t) -> t.setSocialUrl(s.getSocialUrl())),
            fill(ProfileField.FOUNDED_YEAR, CustomerDO::getFoundedYear, (s, t) -> t.setFoundedYear(s.getFoundedYear())),
            fill(ProfileField.COMPANY_SIZE, CustomerDO::getCompanySize, (s, t) -> t.setCompanySize(s.getCompanySize())),
            fill(ProfileField.POTENTIAL, CustomerDO::getPotentialRating, (s, t) -> t.setPotentialRating(s.getPotentialRating())),
            fill(ProfileField.MAIN_PRODUCTS, CustomerDO::getMainProducts, (s, t) -> t.setMainProducts(s.getMainProducts())),
            fill(ProfileField.CN_SUPPLIERS, CustomerDO::getCnSuppliers, (s, t) -> t.setCnSuppliers(s.getCnSuppliers())),
            fill(ProfileField.EST_PURCHASE, CustomerDO::getEstAnnualPurchase, (s, t) -> t.setEstAnnualPurchase(s.getEstAnnualPurchase())),
            fill(ProfileField.QUALITY_REQ, CustomerDO::getQualityReq, (s, t) -> t.setQualityReq(s.getQualityReq())),
            fill(ProfileField.PRICE_SENSITIVITY, CustomerDO::getPriceSensitivity, (s, t) -> t.setPriceSensitivity(s.getPriceSensitivity())),
            fill(ProfileField.CUSTOMS_ABILITY, CustomerDO::getCustomsAbility, (s, t) -> t.setCustomsAbility(s.getCustomsAbility())),
            fill(ProfileField.PAYMENT_METHOD, CustomerDO::getPaymentMethod, (s, t) -> {
                t.setPaymentMethod(s.getPaymentMethod());
                t.setDepositRatio(s.getDepositRatio());
                t.setPaymentDays(s.getPaymentDays());
            }),
            fill(ProfileField.CURRENT_BRANDS, CustomerDO::getCurrentBrands, (s, t) -> t.setCurrentBrands(s.getCurrentBrands())),
            fill(ProfileField.DESIGN_ABILITY, CustomerDO::getDesignAbility, (s, t) -> t.setDesignAbility(s.getDesignAbility())),
            fill(ProfileField.PROTOCOLS, CustomerDO::getProtocols, (s, t) -> t.setProtocols(s.getProtocols())),
            fill(ProfileField.VOLTAGES, CustomerDO::getVoltages, (s, t) -> t.setVoltages(s.getVoltages())),
            fill(ProfileField.CERTIFICATIONS, CustomerDO::getCertifications, (s, t) -> t.setCertifications(s.getCertifications())),
            fill(ProfileField.REPAIR_ABILITY, CustomerDO::getRepairAbility, (s, t) -> t.setRepairAbility(s.getRepairAbility())),
            fill(ProfileField.CITY, CustomerDO::getCity, (s, t) -> t.setCity(s.getCity())),
            fill(ProfileField.TIMEZONE, CustomerDO::getTimezone, (s, t) -> t.setTimezone(s.getTimezone())));

    private static Fill fill(ProfileField f, Function<CustomerDO, Object> get, BiConsumer<CustomerDO, CustomerDO> copy) {
        return new Fill(f.key(), f.label(), get, copy);
    }

    private static boolean empty(Object v) {
        if (v == null) {
            return true;
        }
        if (v instanceof Integer i) {
            return i == 0;
        }
        if (v instanceof BigDecimal) {
            return false;
        }
        String s = v.toString().trim();
        return s.isEmpty() || "[]".equals(s);
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static List<List<Long>> partition(List<Long> ids) {
        List<List<Long>> parts = new ArrayList<>();
        for (int i = 0; i < ids.size(); i += 500) {
            parts.add(ids.subList(i, Math.min(ids.size(), i + 500)));
        }
        return parts;
    }

    private static Map<String, String> linked(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    private static int tenantId() {
        Integer t = TenantContext.getTenantId();
        return t == null ? 0 : t;
    }
}
