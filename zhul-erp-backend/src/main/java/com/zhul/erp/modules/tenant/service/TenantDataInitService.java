package com.zhul.erp.modules.tenant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.product.constants.ProductConstants;
import com.zhul.erp.modules.product.entity.ProductBrandDO;
import com.zhul.erp.modules.product.entity.ProductCategoryDO;
import com.zhul.erp.modules.product.entity.ProductSeriesDO;
import com.zhul.erp.modules.product.repository.ProductBrandMapper;
import com.zhul.erp.modules.product.repository.ProductCategoryMapper;
import com.zhul.erp.modules.product.repository.ProductSeriesMapper;
import com.zhul.erp.modules.product.support.IndustryScope;
import com.zhul.erp.modules.system.entity.DepartmentDO;
import com.zhul.erp.modules.system.entity.ExchangeRateDO;
import com.zhul.erp.modules.system.entity.PositionDO;
import com.zhul.erp.modules.system.entity.ResourceDO;
import com.zhul.erp.modules.system.entity.RoleDO;
import com.zhul.erp.modules.system.entity.RoleOrgDO;
import com.zhul.erp.modules.system.entity.RoleResourceDO;
import com.zhul.erp.modules.system.entity.UserBasicDO;
import com.zhul.erp.modules.system.repository.DepartmentMapper;
import com.zhul.erp.modules.system.repository.ExchangeRateMapper;
import com.zhul.erp.modules.system.repository.PositionMapper;
import com.zhul.erp.modules.system.repository.ResourceMapper;
import com.zhul.erp.modules.system.repository.RoleMapper;
import com.zhul.erp.modules.system.repository.RoleOrgMapper;
import com.zhul.erp.modules.system.repository.RoleResourceMapper;
import com.zhul.erp.modules.system.repository.UserBasicMapper;
import com.zhul.erp.modules.system.service.BusinessUnitService;
import com.zhul.erp.modules.system.service.ExchangeRateService;
import com.zhul.erp.modules.system.service.LogService;
import com.zhul.erp.modules.tenant.dto.TenantInitResultVO;
import com.zhul.erp.modules.tenant.entity.TenantDO;
import com.zhul.erp.modules.tenant.repository.TenantMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 租户数据初始化（spec system/tenant-data-init）：平台为租户写入福唯模板的部门、岗位、角色，租户管理员归入总经办，
 * 按平台汇率写入初始汇率。可重复执行，只补缺的：已有的不改不删，已有角色不覆盖菜单权限。
 * 在目标租户的上下文里执行，复用事业部标记（含第一个事业部的单据归属）与汇率保存逻辑；整个过程一个事务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantDataInitService {

    private static final String PRESET = "tenant-init/fouwell.json";
    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private static final int PLATFORM = 0;

    private final ObjectMapper objectMapper;
    private final TenantMapper tenantMapper;
    private final DepartmentMapper departmentMapper;
    private final PositionMapper positionMapper;
    private final RoleMapper roleMapper;
    private final RoleResourceMapper roleResourceMapper;
    private final RoleOrgMapper roleOrgMapper;
    private final ResourceMapper resourceMapper;
    private final UserBasicMapper userBasicMapper;
    private final ExchangeRateMapper exchangeRateMapper;
    private final ExchangeRateService exchangeRateService;
    private final BusinessUnitService businessUnitService;
    private final IndustryScope industryScope;
    private final ProductBrandMapper brandMapper;
    private final ProductCategoryMapper categoryMapper;
    private final ProductSeriesMapper seriesMapper;
    private final LogService logService;

    /** 预设数据（resources/tenant-init/fouwell.json） */
    public record Preset(String template, String name, String adminDept, List<Dept> departments,
                         List<Position> positions, List<Role> roles) {
    }

    public record Dept(String key, String parent, String name, String allName, int sort, boolean businessUnit,
                       String remark) {
    }

    public record Position(String code, String name, int sort, String remark) {
    }

    public record Role(String key, String name, int permissionScope, String workbenchTemplate, String customDept,
                       String remark, List<Integer> menuIds) {
    }

    @Transactional(rollbackFor = Exception.class)
    public TenantInitResultVO initialize(Integer tenantId) {
        Integer caller = TenantContext.getTenantId();
        if (caller == null || caller != PLATFORM) {
            throw new BizException("仅平台账号可以执行数据初始化");
        }
        TenantDO tenant = tenantId == null || tenantId == PLATFORM ? null : tenantMapper.selectById(tenantId);
        if (tenant == null) {
            throw new BizException("租户不存在");
        }
        Preset preset = loadPreset();
        TenantInitResultVO result = new TenantInitResultVO();
        result.setTenantId(tenantId);
        result.setTenantName(tenant.getName());
        TenantContext.setTenantId(tenantId);
        try {
            Map<String, DepartmentDO> depts = new HashMap<>();
            result.getItems().add(initDepartments(tenant, preset, depts));
            result.getItems().add(initPositions(tenantId, preset));
            result.getItems().add(initRoles(tenantId, preset, depts));
            assignAdminDept(tenantId, depts.get(preset.adminDept()));
            result.getItems().add(initExchangeRates(tenantId));
        } finally {
            TenantContext.setTenantId(caller);
        }
        fillProductScope(tenant, result);
        result.getTodos().add("业务设置 → 收款账户：填写公司收款账户");
        result.getTodos().add("用户管理：创建员工账号，分配部门、岗位与角色");
        result.getTodos().add("导入历史数据后，在部门管理里调整事业部的开始核算月份");
        if (result.getIndustryCode() == null) {
            result.getTodos().add(0, "编辑租户：选择行业，租户才能看到对应行业的品牌、品类、系列");
        }
        logService.recordOperateLog("租户列表", "数据初始化（" + tenant.getName() + "）", null, result);
        log.info("租户数据初始化完成，tenantId={}，items={}", tenantId, result.getItems());
        return result;
    }

    // ---------------------------------------------------------------- 部门

    private TenantInitResultVO.Item initDepartments(TenantDO tenant, Preset preset, Map<String, DepartmentDO> byKey) {
        int tenantId = tenant.getId();
        int created = 0;
        int existed = 0;
        List<String> newUnits = new ArrayList<>();
        String month = YearMonth.now().toString();
        for (Dept d : preset.departments()) {
            DepartmentDO parent = d.parent() == null ? null : byKey.get(d.parent());
            DepartmentDO found = d.parent() == null ? findRoot(tenant) : findChild(tenantId, parent.getId(), d.name());
            if (found != null) {
                existed++;
                byKey.put(d.key(), found);
                continue;
            }
            DepartmentDO dept = new DepartmentDO();
            dept.setTenantId(tenantId);
            dept.setPid(parent == null ? 0 : parent.getId());
            dept.setCode("");
            dept.setName(parent == null ? tenant.getName() : d.name());
            dept.setAllName(parent == null ? tenant.getName() : d.allName());
            dept.setLeaderId(0);
            dept.setLevel(parent == null ? 1 : parent.getLevel() + 1);
            dept.setHeadcount(0);
            dept.setPhone("");
            dept.setRemark(d.remark());
            dept.setSort(d.sort());
            dept.setStatus(1);
            dept.setIsBusinessUnit(0);
            departmentMapper.insert(dept);
            departmentMapper.update(null, new LambdaUpdateWrapper<DepartmentDO>()
                    .set(DepartmentDO::getCode, "DP" + dept.getId()).eq(DepartmentDO::getId, dept.getId()));
            dept.setCode("DP" + dept.getId());
            byKey.put(d.key(), dept);
            created++;
            if (d.businessUnit()) {
                // 与部门管理里标记一致：公司第一次有事业部时，已有单据归入它
                businessUnitService.mark(dept.getId(), true, month);
                newUnits.add(dept.getName());
            }
        }
        TenantInitResultVO.Item item = item("部门", created, existed);
        if (!newUnits.isEmpty()) {
            item.setNote(String.join("、", newUnits) + " 已标记为独立核算，" + month + " 起核算");
        }
        return item;
    }

    /** 公司根节点：只有一个顶级部门时用它；有多个时用与租户同名的；都没有就新建 */
    private DepartmentDO findRoot(TenantDO tenant) {
        List<DepartmentDO> roots = departmentMapper.selectList(new LambdaQueryWrapper<DepartmentDO>()
                .eq(DepartmentDO::getTenantId, tenant.getId())
                .eq(DepartmentDO::getPid, 0)
                .isNull(DepartmentDO::getDeletedAt)
                .orderByAsc(DepartmentDO::getId));
        if (roots.size() == 1) {
            return roots.get(0);
        }
        return roots.stream().filter(r -> tenant.getName().equals(r.getName())).findFirst().orElse(null);
    }

    private DepartmentDO findChild(int tenantId, Integer pid, String name) {
        return departmentMapper.selectOne(new LambdaQueryWrapper<DepartmentDO>()
                .eq(DepartmentDO::getTenantId, tenantId)
                .eq(DepartmentDO::getPid, pid)
                .eq(DepartmentDO::getName, name)
                .isNull(DepartmentDO::getDeletedAt)
                .orderByAsc(DepartmentDO::getId)
                .last("LIMIT 1"));
    }

    // ---------------------------------------------------------------- 岗位

    private TenantInitResultVO.Item initPositions(int tenantId, Preset preset) {
        int created = 0;
        int existed = 0;
        for (Position p : preset.positions()) {
            Long count = positionMapper.selectCount(new LambdaQueryWrapper<PositionDO>()
                    .eq(PositionDO::getTenantId, tenantId)
                    .eq(PositionDO::getCode, p.code())
                    .isNull(PositionDO::getDeletedAt));
            if (count > 0) {
                existed++;
                continue;
            }
            PositionDO position = new PositionDO();
            position.setTenantId(tenantId);
            position.setCode(p.code());
            position.setName(p.name());
            position.setSort(p.sort());
            position.setStatus(1);
            position.setRemark(p.remark());
            positionMapper.insert(position);
            created++;
        }
        return item("岗位", created, existed);
    }

    // ---------------------------------------------------------------- 角色

    private TenantInitResultVO.Item initRoles(int tenantId, Preset preset, Map<String, DepartmentDO> depts) {
        int created = 0;
        int existed = 0;
        Map<Integer, String> menus = resourceMapper.selectList(new LambdaQueryWrapper<ResourceDO>()
                        .eq(ResourceDO::getStatus, 1))
                .stream().collect(Collectors.toMap(ResourceDO::getId, ResourceDO::getCode));
        long code = System.currentTimeMillis() % 100000000;
        for (Role r : preset.roles()) {
            Long count = roleMapper.selectCount(new LambdaQueryWrapper<RoleDO>()
                    .eq(RoleDO::getTenantId, tenantId)
                    .eq(RoleDO::getName, r.name()));
            if (count > 0) {
                existed++;
                continue;
            }
            // 与角色管理页新建一致用「R + 数字」，连续新建时逐个往后找没被占用的编码
            String roleCode;
            do {
                roleCode = "R" + code++;
            } while (roleMapper.selectCount(new LambdaQueryWrapper<RoleDO>().eq(RoleDO::getCode, roleCode)) > 0);
            RoleDO role = new RoleDO();
            role.setTenantId(tenantId);
            role.setCode(roleCode);
            role.setName(r.name());
            role.setPermissionScope(r.permissionScope());
            role.setWorkbenchTemplate(r.workbenchTemplate());
            role.setStatus(1);
            role.setIsBuiltIn(0);
            role.setRemark(r.remark());
            roleMapper.insert(role);
            for (Integer menuId : r.menuIds()) {
                String menuCode = menus.get(menuId);
                if (menuCode == null) {
                    continue;
                }
                RoleResourceDO rr = new RoleResourceDO();
                rr.setRoleCode(roleCode);
                rr.setResourceId(menuId);
                rr.setResourceCode(menuCode);
                roleResourceMapper.insert(rr);
            }
            DepartmentDO custom = r.customDept() == null ? null : depts.get(r.customDept());
            if (custom != null) {
                RoleOrgDO org = new RoleOrgDO();
                org.setRoleCode(roleCode);
                org.setOrgCode(custom.getCode());
                roleOrgMapper.insert(org);
            }
            created++;
        }
        TenantInitResultVO.Item item = item("角色", created, existed);
        if (created > 0) {
            item.setNote("合伙人角色只看本事业部；套餐外的菜单不可用");
        }
        return item;
    }

    /** 租户管理员没有部门时归入总经办 */
    private void assignAdminDept(int tenantId, DepartmentDO adminDept) {
        if (adminDept == null) {
            return;
        }
        userBasicMapper.update(null, new LambdaUpdateWrapper<UserBasicDO>()
                .set(UserBasicDO::getDeptId, adminDept.getId())
                .eq(UserBasicDO::getTenantId, tenantId)
                .eq(UserBasicDO::getRoleCode, ROLE_ADMIN)
                .and(w -> w.isNull(UserBasicDO::getDeptId).or().eq(UserBasicDO::getDeptId, 0)));
    }

    // ---------------------------------------------------------------- 汇率

    /** 租户还没有的币种按平台当前汇率写入（经汇率服务保存，留变更记录） */
    private TenantInitResultVO.Item initExchangeRates(int tenantId) {
        Set<String> own = exchangeRateMapper.selectList(new LambdaQueryWrapper<ExchangeRateDO>()
                        .eq(ExchangeRateDO::getTenantId, tenantId))
                .stream().filter(r -> r.getRate() != null && r.getRate().signum() > 0)
                .map(ExchangeRateDO::getCurrencyCode).collect(Collectors.toSet());
        List<ExchangeRateDO> platform = exchangeRateMapper.selectList(new LambdaQueryWrapper<ExchangeRateDO>()
                .eq(ExchangeRateDO::getTenantId, PLATFORM)
                .orderByAsc(ExchangeRateDO::getCurrencyCode));
        int created = 0;
        List<String> written = new ArrayList<>();
        for (ExchangeRateDO p : platform) {
            if (p.getRate() == null || p.getRate().signum() <= 0 || own.contains(p.getCurrencyCode())) {
                continue;
            }
            exchangeRateService.save(p.getCurrencyCode(), p.getRate());
            written.add(p.getCurrencyCode() + " " + p.getRate().toPlainString());
            created++;
        }
        TenantInitResultVO.Item item = item("汇率", created, own.size());
        if (!written.isEmpty()) {
            item.setNote(String.join("、", written) + "（平台汇率）");
        } else if (own.isEmpty()) {
            item.setNote("平台还没有维护汇率，请到汇率设置里录入");
        }
        return item;
    }

    // ---------------------------------------------------------------- 商品主数据（不复制，只统计）

    private void fillProductScope(TenantDO tenant, TenantInitResultVO result) {
        String industry = tenant.getIndustryCode();
        if (!StringUtils.hasText(industry)) {
            return;
        }
        result.setIndustryCode(industry);
        result.setIndustryName(industryScope.enabledIndustries().getOrDefault(industry, industry));
        Set<Long> brands = industryScope.brandIdsOf(industry);
        Set<Long> categories = industryScope.categoryIdsOf(industry);
        result.setBrandCount(brands.isEmpty() ? 0 : brandMapper.selectCount(new LambdaQueryWrapper<ProductBrandDO>()
                .in(ProductBrandDO::getId, brands)
                .eq(ProductBrandDO::getStatus, ProductConstants.STATUS_ENABLED)
                .isNull(ProductBrandDO::getDeletedAt)));
        result.setCategoryCount(categories.isEmpty() ? 0 : categoryMapper.selectCount(new LambdaQueryWrapper<ProductCategoryDO>()
                .in(ProductCategoryDO::getId, categories)
                .eq(ProductCategoryDO::getStatus, ProductConstants.STATUS_ENABLED)
                .isNull(ProductCategoryDO::getDeletedAt)));
        result.setSeriesCount(brands.isEmpty() ? 0 : seriesMapper.selectCount(new LambdaQueryWrapper<ProductSeriesDO>()
                .in(ProductSeriesDO::getBrandId, brands)
                .eq(ProductSeriesDO::getStatus, ProductConstants.STATUS_ENABLED)
                .isNull(ProductSeriesDO::getDeletedAt)));
    }

    private static TenantInitResultVO.Item item(String type, int created, int existed) {
        TenantInitResultVO.Item item = new TenantInitResultVO.Item();
        item.setType(type);
        item.setCreated(created);
        item.setExisted(existed);
        return item;
    }

    private Preset loadPreset() {
        try (InputStream in = new ClassPathResource(PRESET).getInputStream()) {
            return objectMapper.readValue(in, Preset.class);
        } catch (IOException e) {
            throw new UncheckedIOException("读取租户初始化预设数据失败：" + PRESET, e);
        }
    }
}
