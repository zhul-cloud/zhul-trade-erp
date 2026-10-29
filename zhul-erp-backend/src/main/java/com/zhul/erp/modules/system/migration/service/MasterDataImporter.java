package com.zhul.erp.modules.system.migration.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.modules.crm.constants.OpportunityConstants;
import com.zhul.erp.modules.crm.entity.OpportunityDO;
import com.zhul.erp.modules.crm.repository.OpportunityMapper;
import com.zhul.erp.modules.masterdata.constants.CustomerConstants;
import com.zhul.erp.modules.masterdata.constants.SupplierConstants;
import com.zhul.erp.modules.masterdata.entity.CustomerDO;
import com.zhul.erp.modules.masterdata.entity.SupplierDO;
import com.zhul.erp.modules.masterdata.repository.CustomerMapper;
import com.zhul.erp.modules.masterdata.repository.SupplierMapper;
import com.zhul.erp.modules.masterdata.support.ContactKeys;
import com.zhul.erp.modules.masterdata.support.CustomerNameNormalizer;
import com.zhul.erp.modules.system.entity.AccountDO;
import com.zhul.erp.modules.system.entity.AccountLocalAuthDO;
import com.zhul.erp.modules.system.entity.DepartmentDO;
import com.zhul.erp.modules.system.entity.PositionDO;
import com.zhul.erp.modules.system.entity.RoleDO;
import com.zhul.erp.modules.system.entity.UserBasicDO;
import com.zhul.erp.modules.system.repository.AccountLocalAuthMapper;
import com.zhul.erp.modules.system.repository.AccountMapper;
import com.zhul.erp.modules.system.repository.DepartmentMapper;
import com.zhul.erp.modules.system.repository.PositionMapper;
import com.zhul.erp.modules.system.repository.RoleMapper;
import com.zhul.erp.modules.system.repository.UserBasicMapper;
import com.zhul.erp.modules.system.service.BusinessUnitService;
import cn.hutool.crypto.digest.BCrypt;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

import static com.zhul.erp.modules.system.migration.service.MigrationRun.cut;
import static com.zhul.erp.modules.system.migration.service.MigrationRun.date;
import static com.zhul.erp.modules.system.migration.service.MigrationRun.integer;
import static com.zhul.erp.modules.system.migration.service.MigrationRun.text;
import static com.zhul.erp.modules.system.migration.service.MigrationRun.time;

/** 主数据阶段：人员、供应商（含未登记货代占位）、客户（含未登记客户占位）、商机 */
@Component
@RequiredArgsConstructor
public class MasterDataImporter {

    static final String STAGE = "主数据";
    /** 出运单必须挂货代；旧系统没有货代信息，统一挂这个占位供应商 */
    static final String FORWARDER_KEY = "MIG-FORWARDER";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final UserBasicMapper userMapper;
    private final AccountMapper accountMapper;
    private final AccountLocalAuthMapper localAuthMapper;
    private final DepartmentMapper departmentMapper;
    private final PositionMapper positionMapper;
    private final RoleMapper roleMapper;
    private final SupplierMapper supplierMapper;
    private final CustomerMapper customerMapper;
    private final OpportunityMapper opportunityMapper;
    private final BusinessUnitService businessUnitService;

    /** 迁移数据统一归入的事业部：名称含「阿里」的事业部，没有取第一个；公司没设事业部为 null */
    Integer businessUnit() {
        List<BusinessUnitService.Unit> units = businessUnitService.units();
        return units.stream().filter(u -> u.name().contains("阿里")).map(BusinessUnitService.Unit::id).findFirst()
                .orElse(units.isEmpty() ? null : units.get(0).id());
    }

    void run(MigrationRun r) {
        users(r);
        suppliers(r);
        customers(r);
        opportunities(r);
    }

    // ---------------------------------------------------------------- 人员

    private void users(MigrationRun r) {
        for (JsonNode u : r.list("users")) {
            String name = text(u, "name").trim();
            UserBasicDO existing = userMapper.selectOne(new LambdaQueryWrapper<UserBasicDO>()
                    .eq(UserBasicDO::getTenantId, r.tenantId)
                    .eq(UserBasicDO::getName, name)
                    .last("LIMIT 1"));
            if (existing != null) {
                r.userIds.put(name, existing.getId().longValue());
                r.usernames.put(name, existing.getUsername());
                r.skipped(STAGE, "人员（已有）");
                continue;
            }
            boolean buyer = "purchaser".equals(text(u, "role"));
            UserBasicDO n = new UserBasicDO();
            n.setTenantId(r.tenantId);
            n.setName(name);
            // 账号用户名全平台唯一：同名已被其他租户占用时加租户号
            n.setUsername(accountMapper.selectCount(new LambdaQueryWrapper<AccountDO>().eq(AccountDO::getUsername, name)) > 0
                    ? cut(name, 24) + "-" + r.tenantId : name);
            n.setNickname(name);
            n.setType(1);
            n.setStatus(0); // 停用：管理员启用并重置密码后才能登录
            n.setDeptId(department(r.tenantId, buyer ? "采购部" : "业务部"));
            n.setPositionId(position(r.tenantId, buyer ? "采购员" : "业务员"));
            n.setRoleCode(role(r.tenantId, buyer ? "采购员" : "业务员"));
            n.setCreateBy("migration");
            userMapper.insert(n);
            account(r, n);
            r.userIds.put(name, n.getId().longValue());
            r.usernames.put(name, n.getUsername());
            r.record("users", name, "user_basic", n.getId().longValue());
            r.created(STAGE, "人员（新建）");
        }
    }

    /**
     * 登录账号：用户名同员工档案，密码为随机串，管理员在用户管理里启用并重置密码后才能登录。
     * 登录只校验账号状态，账号建为有效，靠随机密码挡住登录。
     */
    private void account(MigrationRun r, UserBasicDO u) {
        String username = u.getUsername();
        AccountDO a = new AccountDO();
        a.setTenantId(r.tenantId);
        a.setUserId(u.getId());
        a.setUsername(username);
        a.setAdminFlag(0);
        a.setStatus(1);
        accountMapper.insert(a);
        byte[] secret = new byte[24];
        RANDOM.nextBytes(secret);
        AccountLocalAuthDO auth = new AccountLocalAuthDO();
        auth.setAccountId(a.getId());
        auth.setUsername(username);
        auth.setPassword(BCrypt.hashpw(HexFormat.of().formatHex(secret), BCrypt.gensalt()));
        auth.setSalt("");
        localAuthMapper.insert(auth);
    }

    /** 迁移事业部下的部门（如「业务部」）；找不到取同名部门；都没有为 0 */
    private Integer department(int tenantId, String name) {
        List<DepartmentDO> depts = departmentMapper.selectList(new LambdaQueryWrapper<DepartmentDO>()
                .eq(DepartmentDO::getTenantId, tenantId).isNull(DepartmentDO::getDeletedAt));
        Integer bu = businessUnit();
        return depts.stream().filter(d -> name.equals(d.getName()) && (bu == null || Objects.equals(d.getPid(), bu)))
                .map(DepartmentDO::getId).findFirst()
                .orElse(depts.stream().filter(d -> name.equals(d.getName())).map(DepartmentDO::getId).findFirst().orElse(0));
    }

    private Integer position(int tenantId, String name) {
        PositionDO p = positionMapper.selectOne(new LambdaQueryWrapper<PositionDO>()
                .eq(PositionDO::getTenantId, tenantId).eq(PositionDO::getName, name).last("LIMIT 1"));
        return p == null ? 0 : p.getId();
    }

    private String role(int tenantId, String name) {
        RoleDO role = roleMapper.selectOne(new LambdaQueryWrapper<RoleDO>()
                .eq(RoleDO::getTenantId, tenantId).eq(RoleDO::getName, name).last("LIMIT 1"));
        return role == null ? "" : role.getCode();
    }

    // ---------------------------------------------------------------- 供应商

    private void suppliers(MigrationRun r) {
        for (JsonNode s : r.list("suppliers")) {
            String key = text(s, "key");
            if (r.mapped("suppliers", key, "supplier") != null) {
                r.skipped(STAGE, "供应商");
                continue;
            }
            SupplierDO d = new SupplierDO();
            d.setTenantId(r.tenantId);
            d.setSupplierCode(tempCode());
            d.setName(cut(text(s, "name"), 128));
            d.setShortName(cut(text(s, "shortName"), 50));
            d.setContactName(cut(text(s, "contactName"), 64));
            d.setContactPhone(cut(text(s, "contactPhone"), 32));
            d.setWechat(cut(text(s, "wechat"), 64));
            d.setRegion(cut(text(s, "region"), 100));
            d.setAddress(cut(text(s, "address"), 200));
            d.setMainBrands(cut(text(s, "mainBrands"), 256));
            d.setRemark(cut(text(s, "remark"), 500));
            d.setStatus(1);
            d.setCreateBy("migration");
            supplierMapper.insert(d);
            assignSupplierCode(d);
            r.record("suppliers", key, "supplier", d.getId());
            r.created(STAGE, "供应商");
        }
        if (r.mapped("suppliers", FORWARDER_KEY, "supplier") == null) {
            SupplierDO f = new SupplierDO();
            f.setTenantId(r.tenantId);
            f.setSupplierCode(tempCode());
            f.setName("未登记货代（历史迁移）");
            f.setSupplierType(3);
            f.setRemark("旧系统没有记录货代，历史出运单统一挂在这里");
            f.setStatus(1);
            f.setCreateBy("migration");
            supplierMapper.insert(f);
            assignSupplierCode(f);
            r.record("suppliers", FORWARDER_KEY, "supplier", f.getId());
            r.created(STAGE, "货代占位");
        }
    }

    private void assignSupplierCode(SupplierDO d) {
        d.setSupplierCode(SupplierConstants.CODE_PREFIX + String.format("%05d", d.getId()));
        supplierMapper.updateById(d);
    }

    private static String tempCode() {
        return "TMP" + Long.toString(System.nanoTime(), 36).toUpperCase();
    }

    // ---------------------------------------------------------------- 客户

    private void customers(MigrationRun r) {
        Integer bu = businessUnit();
        for (JsonNode c : r.list("customers")) {
            String key = text(c, "key");
            if (r.mapped("customers", key, "customer") != null) {
                r.skipped(STAGE, "客户");
                continue;
            }
            CustomerDO d = new CustomerDO();
            d.setTenantId(r.tenantId);
            d.setBusinessUnitId(bu);
            d.setCustomerCode(tempCode());
            String name = cut(text(c, "name"), 200);
            d.setName(name.isBlank() ? "（未填写）" : name);
            d.setNameKey(CustomerNameNormalizer.normalize(d.getName()));
            d.setContactName(cut(text(c, "contactName"), 100));
            d.setCountry(cut(text(c, "country"), 64));
            d.setSourceChannel(integer(c, "sourceChannel", 0));
            d.setAliLevel(cut(text(c, "aliLevel"), 4));
            d.setCustomerRole(integer(c, "customerRole", 0));
            d.setContactEmail(cut(text(c, "email"), 100));
            d.setEmailKey(ContactKeys.email(d.getContactEmail()));
            d.setContactPhone(cut(text(c, "phone"), 32));
            d.setPhoneKey(ContactKeys.phone(d.getContactPhone()));
            d.setWhatsapp(cut(text(c, "whatsapp"), 30));
            d.setWhatsappKey(ContactKeys.phone(d.getWhatsapp()));
            d.setAddress(cut(text(c, "address"), 300));
            Long owner = r.user(text(c, "owner"));
            d.setOwnerId(owner == null ? 0L : owner);
            StringBuilder remark = new StringBuilder(text(c, "remark"));
            if (!text(c, "countryRaw").isBlank()) {
                remark.append(remark.isEmpty() ? "" : "；").append("国家原文：").append(text(c, "countryRaw"));
            }
            JsonNode others = c.get("otherNames");
            if (others != null && others.isArray() && !others.isEmpty()) {
                remark.append(remark.isEmpty() ? "" : "；").append("其他名称：");
                others.forEach(o -> remark.append(o.asText()).append(" "));
            }
            d.setRemark(cut(remark.toString().trim(), 500));
            d.setStatus(1);
            LocalDateTime created = time(c, "createTime");
            d.setCreateTime(created);
            d.setCreateBy(r.username(text(c, "createdBy")));
            customerMapper.insert(d);
            d.setCustomerCode(CustomerConstants.CODE_PREFIX + String.format("%05d", d.getId()));
            customerMapper.updateById(d);
            r.record("customers", key, "customer", d.getId());
            r.created(STAGE, "客户");
        }
    }

    // ---------------------------------------------------------------- 商机

    private void opportunities(MigrationRun r) {
        Integer bu = businessUnit();
        for (JsonNode o : r.list("opportunities")) {
            String key = text(o, "key");
            if (r.mapped("opportunities", key, "opportunity") != null) {
                r.skipped(STAGE, "商机");
                continue;
            }
            Long customerId = r.mapped("customers", text(o, "customerKey"), "customer");
            if (customerId == null) {
                r.failed(STAGE, "商机（客户没迁移）");
                continue;
            }
            LocalDate first = date(o, "firstContactDate");
            LocalDateTime created = time(o, "createTime");
            if (first == null) {
                first = created == null ? LocalDate.now() : created.toLocalDate();
            }
            String stage = text(o, "stage");
            boolean closed = "WON".equals(stage) || "LOST".equals(stage) || "INVALID".equals(stage);
            OpportunityDO d = new OpportunityDO();
            d.setTenantId(r.tenantId);
            d.setBusinessUnitId(bu);
            String prefix = OpportunityConstants.CODE_PREFIX + first.format(YMD);
            Integer max = opportunityMapper.selectMaxSequenceByPrefix(r.tenantId, prefix);
            d.setOpportunityCode(prefix + String.format("%03d", (max == null ? 0 : max) + 1));
            d.setCustomerId(customerId);
            d.setSourceChannel(integer(o, "sourceChannel", 0));
            d.setAliLevel(cut(text(o, "aliLevel"), 4));
            d.setFirstContactDate(first);
            Long owner = r.user(text(o, "owner"));
            d.setOwnerId(owner == null ? 0L : owner);
            d.setStageCode(stage);
            d.setStageChangedAt(created == null ? first.atStartOfDay() : created);
            d.setCloseReason(integer(o, "closeReason", 0));
            d.setCloseNote(cut(text(o, "closeNote"), 500));
            d.setClosedAt(closed ? d.getStageChangedAt() : null);
            d.setReachedValid(o.path("reachedValid").asBoolean() ? 1 : 0);
            d.setDemandSummary(cut(text(o, "demandSummary"), 2000));
            d.setMigrated(1);
            d.setCreateTime(created);
            d.setCreateBy(r.username(text(o, "createdBy")));
            opportunityMapper.insert(d);
            r.record("opportunities", key, "opportunity", d.getId());
            r.created(STAGE, "商机");
        }
    }
}
