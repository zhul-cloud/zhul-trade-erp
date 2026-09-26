package com.zhul.erp.modules.masterdata.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.framework.security.DataScope;
import com.zhul.erp.framework.security.DataScopeResolver;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.masterdata.constants.CustomerConstants;
import com.zhul.erp.modules.masterdata.dto.AbstractCustomerRequest;
import com.zhul.erp.modules.masterdata.dto.AssignableOwnersVO;
import com.zhul.erp.modules.masterdata.dto.CustomerBatchDeleteResultVO;
import com.zhul.erp.modules.masterdata.dto.CustomerDetailVO;
import com.zhul.erp.modules.masterdata.dto.CustomerPageQuery;
import com.zhul.erp.modules.masterdata.dto.CustomerPartyRequest;
import com.zhul.erp.modules.masterdata.dto.CustomerRefVO;
import com.zhul.erp.modules.masterdata.dto.CustomerTransferRequest;
import com.zhul.erp.modules.masterdata.dto.CustomerVO;
import com.zhul.erp.modules.masterdata.dto.SaveCustomerRequest;
import com.zhul.erp.modules.masterdata.dto.UpdateCustomerRequest;
import com.zhul.erp.modules.masterdata.entity.CustomerDO;
import com.zhul.erp.modules.masterdata.entity.CustomerPartyDO;
import com.zhul.erp.modules.masterdata.repository.CustomerMapper;
import com.zhul.erp.modules.masterdata.service.CustomerService;
import com.zhul.erp.modules.masterdata.support.CustomerNameNormalizer;
import com.zhul.erp.modules.product.support.CountryCatalog;
import com.zhul.erp.modules.system.entity.DepartmentDO;
import com.zhul.erp.modules.system.entity.UserBasicDO;
import com.zhul.erp.modules.system.repository.DepartmentMapper;
import com.zhul.erp.modules.system.repository.UserBasicMapper;
import com.zhul.erp.modules.system.service.LogService;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService {

    private static final int SEARCH_LIMIT = 20;
    private static final Pattern CJK = Pattern.compile("[\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}\\p{IsHangul}]");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String[] EXPORT_HEADERS = {
        "客户编码", "客户名称（英文）", "中文名称", "简称", "客户角色", "应用行业", "客户等级", "客户来源", "负责业务员",
        "小满客户编号", "国家/地区", "州/省", "城市", "邮编", "详细地址", "税号", "时区", "联系人", "职位", "邮箱",
        "电话", "WhatsApp", "默认币种", "贸易术语", "术语地点", "付款方式", "定金比例(%)", "账期(天)", "信用额度",
        "信用额度币种", "运输方式", "目的港", "状态", "备注", "创建时间", "创建人", "更新时间", "更新人"
    };

    private final CustomerMapper customerMapper;
    private final CustomerPartySync partySync;
    private final DataScopeResolver dataScopeResolver;
    private final CountryCatalog countryCatalog;
    private final UserBasicMapper userBasicMapper;
    private final DepartmentMapper departmentMapper;
    private final LogService logService;

    // ---------------------------------------------------------------- 新增 / 更新

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CustomerVO create(SaveCustomerRequest req) {
        int tenantId = currentTenantId();
        DataScope scope = dataScopeResolver.current();
        normalizeAndValidate(req);

        String manualCode = text(req.getCustomerCode()).toUpperCase(Locale.ROOT);
        if (!manualCode.isEmpty() && findActiveByCode(tenantId, manualCode) != null) {
            throw BizException.of(CustomerConstants.ERROR_CODE_DUPLICATE, "客户编码已存在，请更换");
        }
        String nameKey = CustomerNameNormalizer.normalize(req.getName());
        checkDuplicate(tenantId, req.getCountry(), nameKey, null, scope);

        // 未指定时为当前用户；没有用户档案的管理员账号为 0（未分配）
        Long ownerId = req.getOwnerId() != null ? req.getOwnerId() : (scope.selfId() != null ? scope.selfId() : 0L);
        if (req.getOwnerId() != null && !Objects.equals(req.getOwnerId(), scope.selfId())) {
            requireAssignableOwner(ownerId, scope, tenantId);
        }

        CustomerDO customer = new CustomerDO();
        customer.setTenantId(tenantId);
        applyFields(customer, req);
        customer.setNameKey(nameKey);
        customer.setOwnerId(ownerId);
        customer.setCustomerRole(req.getCustomerRole() != null ? req.getCustomerRole() : CustomerConstants.UNSET);
        customer.setStatus(req.getStatus() != null ? req.getStatus() : 1);
        if (!manualCode.isEmpty()) {
            customer.setCustomerCode(manualCode);
        }
        customerMapper.insert(customer);
        if (manualCode.isEmpty()) {
            customer.setCustomerCode(generateCode(tenantId, customer.getId()));
            customerMapper.updateById(customer);
        }
        if (req.getParties() != null) {
            partySync.sync(tenantId, customer.getId(), req.getParties());
        }
        return toVo(customer, ownerNames(List.of(customer)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, UpdateCustomerRequest req) {
        DataScope scope = dataScopeResolver.current();
        CustomerDO customer = getActiveInScope(id, scope);
        normalizeAndValidate(req);
        String nameKey = CustomerNameNormalizer.normalize(req.getName());
        checkDuplicate(customer.getTenantId(), req.getCountry(), nameKey, id, scope);

        applyFields(customer, req);
        customer.setNameKey(nameKey);
        customer.setCustomerRole(req.getCustomerRole());
        customer.setStatus(req.getStatus());
        customerMapper.updateById(customer);
        if (req.getParties() != null) {
            partySync.sync(customer.getTenantId(), id, req.getParties());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        CustomerDO customer = getActiveInScope(id, dataScopeResolver.current());
        customer.setStatus(status);
        customerMapper.updateById(customer);
    }

    /**
     * 统一规范化请求文本（去空格、null 转空串、国家转为清单里的英文名、币种等转大写），
     * 再做 Bean Validation 覆盖不到的跨字段校验，并清掉不适用的联动字段。
     */
    private void normalizeAndValidate(AbstractCustomerRequest req) {
        req.setName(text(req.getName()));
        req.setAddress(text(req.getAddress()));
        requireNoCjk(req.getName());
        requireNoCjk(req.getAddress());
        req.setCountry(canonicalCountry(req.getCountry()));

        String timezone = text(req.getTimezone());
        if (!timezone.isEmpty()) {
            try {
                ZoneId.of(timezone);
            } catch (DateTimeException e) {
                throw new BizException("时区不正确");
            }
        }
        req.setTimezone(timezone);

        req.setIncoterm(text(req.getIncoterm()).toUpperCase(Locale.ROOT));
        req.setIncotermPlace(text(req.getIncotermPlace()));
        if (req.getIncoterm().isEmpty()) {
            req.setIncotermPlace("");
        } else if (req.getIncotermPlace().isEmpty()) {
            throw new BizException("请填写术语地点，如 FOB 的装运港");
        }

        Integer payment = req.getPaymentMethod();
        if (payment != null && CustomerConstants.PAYMENT_WITH_DEPOSIT.contains(payment)) {
            if (req.getDepositRatio() == null) {
                throw new BizException("请填写定金比例");
            }
        } else {
            req.setDepositRatio(null);
        }
        if (payment != null && CustomerConstants.PAYMENT_WITH_DAYS.contains(payment)) {
            if (req.getPaymentDays() == null) {
                throw new BizException("请填写账期（天）");
            }
        } else {
            req.setPaymentDays(null);
        }

        req.setCreditCurrency(text(req.getCreditCurrency()).toUpperCase(Locale.ROOT));
        if ((req.getCreditLimit() == null) != req.getCreditCurrency().isEmpty()) {
            throw new BizException("信用额度需要同时填写金额和币种");
        }

        if (req.getParties() != null) {
            for (CustomerPartyRequest p : req.getParties()) {
                p.setCompanyName(text(p.getCompanyName()));
                p.setAddress(text(p.getAddress()));
                requireNoCjk(p.getCompanyName());
                requireNoCjk(p.getAddress());
                p.setCountry(canonicalCountry(p.getCountry()));
                p.setState(text(p.getState()));
                p.setCity(text(p.getCity()));
                p.setPostcode(text(p.getPostcode()));
                p.setContactName(text(p.getContactName()));
                p.setPhone(text(p.getPhone()));
                p.setEmail(text(p.getEmail()));
                p.setTaxId(text(p.getTaxId()));
                p.setDestinationPort(Objects.equals(p.getPartyType(), CustomerConstants.PARTY_CONSIGNEE)
                        ? text(p.getDestinationPort()) : "");
                p.setRemark(text(p.getRemark()));
            }
        }
    }

    private void applyFields(CustomerDO c, AbstractCustomerRequest req) {
        c.setName(req.getName());
        c.setNameCn(text(req.getNameCn()));
        c.setShortName(text(req.getShortName()));
        c.setIndustry(orUnset(req.getIndustry()));
        c.setWebsite(text(req.getWebsite()));
        c.setCustomerGrade(orUnset(req.getCustomerGrade()));
        c.setSourceChannel(orUnset(req.getSourceChannel()));
        c.setExternalRef(text(req.getExternalRef()));
        c.setRemark(text(req.getRemark()));
        c.setCountry(req.getCountry());
        c.setState(text(req.getState()));
        c.setCity(text(req.getCity()));
        c.setPostcode(text(req.getPostcode()));
        c.setAddress(req.getAddress());
        c.setTaxId(text(req.getTaxId()));
        c.setTimezone(req.getTimezone());
        c.setContactName(text(req.getContactName()));
        c.setContactTitle(text(req.getContactTitle()));
        c.setContactEmail(text(req.getContactEmail()));
        c.setContactPhone(text(req.getContactPhone()));
        c.setWhatsapp(text(req.getWhatsapp()));
        c.setOtherIm(text(req.getOtherIm()));
        String currency = text(req.getCurrency()).toUpperCase(Locale.ROOT);
        c.setCurrency(currency.isEmpty() ? CustomerConstants.DEFAULT_CURRENCY : currency);
        c.setIncoterm(req.getIncoterm());
        c.setIncotermPlace(req.getIncotermPlace());
        c.setPaymentMethod(orUnset(req.getPaymentMethod()));
        c.setDepositRatio(req.getDepositRatio());
        c.setPaymentDays(req.getPaymentDays());
        c.setCreditLimit(req.getCreditLimit());
        c.setCreditCurrency(req.getCreditCurrency());
        c.setShippingMethod(orUnset(req.getShippingMethod()));
        c.setDestinationPort(text(req.getDestinationPort()));
    }

    /** 规范化名称 + 国家查重，不受数据权限限制；命中时只透露负责人姓名 */
    private void checkDuplicate(int tenantId, String country, String nameKey, Long selfId, DataScope scope) {
        CustomerDO dup = customerMapper.selectOne(new LambdaQueryWrapper<CustomerDO>()
                .eq(CustomerDO::getTenantId, tenantId)
                .eq(CustomerDO::getCountry, country)
                .eq(CustomerDO::getNameKey, nameKey)
                .isNull(CustomerDO::getDeletedAt)
                .ne(selfId != null, CustomerDO::getId, selfId)
                .last("LIMIT 1"));
        if (dup == null) {
            return;
        }
        String ownerName = ownerNames(List.of(dup)).get(dup.getOwnerId());
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("existingId", dup.getId());
        detail.put("selectable", scope.canSee(dup.getOwnerId()));
        detail.put("ownerName", ownerName);
        String message = ownerName != null
                ? "该客户已存在，负责业务员：" + ownerName
                : "该客户已存在（尚未分配负责业务员）";
        throw BizException.of(CustomerConstants.ERROR_DUPLICATE, message, detail);
    }

    private String generateCode(int tenantId, Long id) {
        String base = CustomerConstants.CODE_PREFIX + String.format("%05d", id);
        String candidate = base;
        int suffix = 1;
        while (findActiveByCode(tenantId, candidate) != null) {
            candidate = base + suffix++;
        }
        return candidate;
    }

    // ---------------------------------------------------------------- 查询

    @Override
    public List<CustomerVO> search(String keyword) {
        LambdaQueryWrapper<CustomerDO> wrapper = new LambdaQueryWrapper<CustomerDO>()
                .eq(CustomerDO::getTenantId, currentTenantId())
                .isNull(CustomerDO::getDeletedAt)
                .eq(CustomerDO::getStatus, 1)
                .like(StringUtils.hasText(keyword), CustomerDO::getName, keyword)
                .last("LIMIT " + SEARCH_LIMIT);
        return toVos(customerMapper.selectList(wrapper));
    }

    @Override
    public List<CustomerVO> searchInScope(String keyword) {
        LambdaQueryWrapper<CustomerDO> wrapper = new LambdaQueryWrapper<CustomerDO>()
                .eq(CustomerDO::getTenantId, currentTenantId())
                .isNull(CustomerDO::getDeletedAt)
                .eq(CustomerDO::getStatus, 1);
        dataScopeResolver.current().apply(wrapper, CustomerDO::getOwnerId);
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(CustomerDO::getName, kw).or().like(CustomerDO::getNameCn, kw)
                    .or().like(CustomerDO::getShortName, kw));
        }
        wrapper.orderByDesc(CustomerDO::getUpdateTime).last("LIMIT " + SEARCH_LIMIT);
        return toVos(customerMapper.selectList(wrapper));
    }

    @Override
    public CustomerVO getById(Long id) {
        CustomerDO customer = customerMapper.selectById(id);
        if (customer == null || customer.getDeletedAt() != null) {
            return null;
        }
        return toVo(customer, ownerNames(List.of(customer)));
    }

    @Override
    public CustomerRefVO getRef(Long id) {
        CustomerDO customer = customerMapper.selectById(id);
        if (customer == null || customer.getDeletedAt() != null
                || !Objects.equals(customer.getTenantId(), currentTenantId())) {
            return null;
        }
        CustomerRefVO vo = new CustomerRefVO();
        vo.setId(customer.getId());
        vo.setCustomerCode(customer.getCustomerCode());
        vo.setName(customer.getName());
        vo.setCountry(customer.getCountry());
        vo.setStatus(customer.getStatus());
        return vo;
    }

    @Override
    public CustomerDetailVO getDetail(Long id) {
        CustomerDO c = getActiveInScope(id, dataScopeResolver.current());
        CustomerDetailVO vo = new CustomerDetailVO();
        fillVo(vo, c, ownerNames(List.of(c)));
        vo.setOwnerDeptName(ownerDeptName(c.getOwnerId()));
        vo.setIndustry(c.getIndustry());
        vo.setWebsite(c.getWebsite());
        vo.setExternalRef(c.getExternalRef());
        vo.setState(c.getState());
        vo.setCity(c.getCity());
        vo.setPostcode(c.getPostcode());
        vo.setAddress(c.getAddress());
        vo.setTaxId(c.getTaxId());
        vo.setTimezone(c.getTimezone());
        vo.setContactTitle(c.getContactTitle());
        vo.setWhatsapp(c.getWhatsapp());
        vo.setOtherIm(c.getOtherIm());
        vo.setCurrency(c.getCurrency());
        vo.setIncoterm(c.getIncoterm());
        vo.setIncotermPlace(c.getIncotermPlace());
        vo.setPaymentMethod(c.getPaymentMethod());
        vo.setDepositRatio(c.getDepositRatio());
        vo.setPaymentDays(c.getPaymentDays());
        vo.setCreditLimit(c.getCreditLimit());
        vo.setCreditCurrency(c.getCreditCurrency());
        vo.setShippingMethod(c.getShippingMethod());
        vo.setDestinationPort(c.getDestinationPort());
        vo.setRemark(c.getRemark());
        vo.setParties(partySync.listActive(id).stream().map(CustomerPartySync::toVo).toList());
        return vo;
    }

    @Override
    public PageResult<CustomerVO> page(CustomerPageQuery query) {
        Page<CustomerDO> result = customerMapper.selectPage(
                new Page<>(query.getPage(), query.getPageSize()), buildQueryWrapper(query));
        return PageResult.of(result.getTotal(), toVos(result.getRecords()));
    }

    private LambdaQueryWrapper<CustomerDO> buildQueryWrapper(CustomerPageQuery q) {
        LambdaQueryWrapper<CustomerDO> w = new LambdaQueryWrapper<CustomerDO>()
                .eq(CustomerDO::getTenantId, currentTenantId())
                .isNull(CustomerDO::getDeletedAt)
                .like(StringUtils.hasText(q.getCustomerCode()), CustomerDO::getCustomerCode, text(q.getCustomerCode()))
                .eq(StringUtils.hasText(q.getCountry()), CustomerDO::getCountry, q.getCountry())
                .eq(q.getCustomerRole() != null, CustomerDO::getCustomerRole, q.getCustomerRole())
                .eq(q.getCustomerGrade() != null, CustomerDO::getCustomerGrade, q.getCustomerGrade())
                .eq(q.getSourceChannel() != null, CustomerDO::getSourceChannel, q.getSourceChannel())
                .eq(q.getOwnerId() != null, CustomerDO::getOwnerId, q.getOwnerId())
                .eq(q.getStatus() != null, CustomerDO::getStatus, q.getStatus());
        String name = StringUtils.hasText(q.getName()) ? q.getName().trim()
                : (StringUtils.hasText(q.getKeyword()) ? q.getKeyword().trim() : null);
        if (name != null) {
            w.and(x -> x.like(CustomerDO::getName, name).or().like(CustomerDO::getNameCn, name)
                    .or().like(CustomerDO::getShortName, name));
        }
        dataScopeResolver.current().apply(w, CustomerDO::getOwnerId);
        return w.orderByDesc(CustomerDO::getUpdateTime);
    }

    // ---------------------------------------------------------------- 删除 / 转移

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        CustomerDO customer = getActiveInScope(id, dataScopeResolver.current());
        if (!customerMapper.selectReferencedCustomerIds(List.of(id)).isEmpty()) {
            throw new BizException("该客户已有询盘或单据记录，不能删除，可改为禁用");
        }
        customer.setDeletedAt(LocalDateTime.now());
        customerMapper.updateById(customer);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CustomerBatchDeleteResultVO batchDelete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BizException("请选择要删除的客户");
        }
        DataScope scope = dataScopeResolver.current();
        Set<Long> distinct = new LinkedHashSet<>(ids);
        List<CustomerDO> visible = new ArrayList<>(distinct.size());
        for (CustomerDO c : selectActiveByIds(distinct)) {
            if (scope.canSee(c.getOwnerId())) {
                visible.add(c);
            }
        }
        Set<Long> referenced = visible.isEmpty() ? Set.of()
                : new HashSet<>(customerMapper.selectReferencedCustomerIds(visible.stream().map(CustomerDO::getId).toList()));
        LocalDateTime now = LocalDateTime.now();
        int deleted = 0;
        for (CustomerDO c : visible) {
            if (referenced.contains(c.getId())) {
                continue;
            }
            c.setDeletedAt(now);
            customerMapper.updateById(c);
            deleted++;
        }
        CustomerBatchDeleteResultVO result = new CustomerBatchDeleteResultVO();
        result.setDeleted(deleted);
        result.setReferenced(visible.size() - deleted);
        result.setMissing(distinct.size() - visible.size());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void transfer(CustomerTransferRequest req) {
        DataScope scope = dataScopeResolver.current();
        int tenantId = currentTenantId();
        Set<Long> distinct = new LinkedHashSet<>(req.getIds());
        List<CustomerDO> customers = selectActiveByIds(distinct);
        if (customers.size() != distinct.size() || customers.stream().anyMatch(c -> !scope.canSee(c.getOwnerId()))) {
            throw new BizException("部分客户不存在或无权操作，请刷新后重试");
        }
        UserBasicDO newOwner = requireAssignableOwner(req.getOwnerId(), scope, tenantId);
        Map<Long, String> names = ownerNames(customers);
        String reason = text(req.getReason());
        for (CustomerDO c : customers) {
            if (Objects.equals(c.getOwnerId(), req.getOwnerId())) {
                continue;
            }
            Map<String, Object> before = new LinkedHashMap<>();
            before.put("customerCode", c.getCustomerCode());
            before.put("ownerId", c.getOwnerId());
            before.put("ownerName", names.get(c.getOwnerId()));
            Map<String, Object> after = new LinkedHashMap<>();
            after.put("customerCode", c.getCustomerCode());
            after.put("ownerId", req.getOwnerId());
            after.put("ownerName", newOwner.getName());
            after.put("reason", reason);
            c.setOwnerId(req.getOwnerId());
            customerMapper.updateById(c);
            logService.recordOperateLog("客户管理", "转移客户", before, after);
        }
    }

    @Override
    public AssignableOwnersVO assignableOwners() {
        DataScope scope = dataScopeResolver.current();
        LambdaQueryWrapper<UserBasicDO> w = new LambdaQueryWrapper<UserBasicDO>()
                .eq(UserBasicDO::getTenantId, currentTenantId())
                .eq(UserBasicDO::getStatus, 1)
                .orderByAsc(UserBasicDO::getName);
        List<UserBasicDO> users;
        if (scope.type() == DataScope.Type.NONE) {
            users = List.of();
        } else if (scope.isAll()) {
            users = userBasicMapper.selectList(w);
        } else {
            users = userBasicMapper.selectList(w.in(UserBasicDO::getId,
                    scope.ownerIds().stream().map(Long::intValue).toList()));
        }
        Map<Integer, String> deptNames = deptNames(users.stream().map(UserBasicDO::getDeptId).toList());
        List<AssignableOwnersVO.OwnerOptionVO> owners = new ArrayList<>(users.size());
        for (UserBasicDO u : users) {
            AssignableOwnersVO.OwnerOptionVO o = new AssignableOwnersVO.OwnerOptionVO();
            o.setId(u.getId().longValue());
            o.setName(u.getName());
            o.setDeptName(deptNames.get(u.getDeptId()));
            owners.add(o);
        }
        AssignableOwnersVO vo = new AssignableOwnersVO();
        vo.setScope(scope.type().name());
        vo.setOwners(owners);
        return vo;
    }

    /** 新负责人须为本租户在职用户，且在操作人的数据范围内 */
    private UserBasicDO requireAssignableOwner(Long ownerId, DataScope scope, int tenantId) {
        UserBasicDO user = ownerId == null ? null : userBasicMapper.selectById(ownerId.intValue());
        if (user == null || !Objects.equals(user.getTenantId(), tenantId) || !Integer.valueOf(1).equals(user.getStatus())
                || !scope.canSee(ownerId)) {
            throw new BizException("只能指定你数据范围内的在职业务员");
        }
        return user;
    }

    // ---------------------------------------------------------------- 导出

    @Override
    public Workbook export(CustomerPageQuery query) {
        LambdaQueryWrapper<CustomerDO> wrapper = buildQueryWrapper(query);
        if (customerMapper.selectCount(wrapper) > CustomerConstants.EXPORT_MAX_ROWS) {
            throw new BizException("导出数据超过" + CustomerConstants.EXPORT_MAX_ROWS + "条，请缩小筛选范围后再导出");
        }
        List<CustomerDO> customers = customerMapper.selectList(wrapper);
        Map<Long, String> owners = ownerNames(customers);

        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("客户档案");
        Row header = sheet.createRow(0);
        for (int i = 0; i < EXPORT_HEADERS.length; i++) {
            header.createCell(i).setCellValue(EXPORT_HEADERS[i]);
        }
        int rowIdx = 1;
        for (CustomerDO c : customers) {
            String[] v = {
                c.getCustomerCode(), c.getName(), c.getNameCn(), c.getShortName(),
                label(CustomerConstants.ROLE_LABELS, c.getCustomerRole(), "未设置"),
                label(CustomerConstants.INDUSTRY_LABELS, c.getIndustry(), ""),
                label(CustomerConstants.GRADE_LABELS, c.getCustomerGrade(), "未分级"),
                label(CustomerConstants.SOURCE_LABELS, c.getSourceChannel(), ""),
                owners.getOrDefault(c.getOwnerId(), ""), c.getExternalRef(),
                c.getCountry(), c.getState(), c.getCity(), c.getPostcode(), c.getAddress(), c.getTaxId(), c.getTimezone(),
                c.getContactName(), c.getContactTitle(), c.getContactEmail(), c.getContactPhone(), c.getWhatsapp(),
                c.getCurrency(), c.getIncoterm(), c.getIncotermPlace(),
                label(CustomerConstants.PAYMENT_LABELS, c.getPaymentMethod(), ""),
                c.getDepositRatio() == null ? "" : String.valueOf(c.getDepositRatio()),
                c.getPaymentDays() == null ? "" : String.valueOf(c.getPaymentDays()),
                // 金额以字符串写入，避免经过 double
                c.getCreditLimit() == null ? "" : c.getCreditLimit().toPlainString(), c.getCreditCurrency(),
                label(CustomerConstants.SHIPPING_LABELS, c.getShippingMethod(), ""), c.getDestinationPort(),
                Objects.equals(c.getStatus(), 1) ? "启用" : "禁用", c.getRemark(),
                c.getCreateTime() == null ? "" : c.getCreateTime().format(TIME_FMT), c.getCreateBy(),
                c.getUpdateTime() == null ? "" : c.getUpdateTime().format(TIME_FMT), c.getUpdateBy()
            };
            Row row = sheet.createRow(rowIdx++);
            for (int i = 0; i < v.length; i++) {
                row.createCell(i).setCellValue(v[i] == null ? "" : v[i]);
            }
        }
        return workbook;
    }

    // ---------------------------------------------------------------- 存量补算

    @Override
    public int backfillNameKeys() {
        List<CustomerDO> rows = customerMapper.selectList(new LambdaQueryWrapper<CustomerDO>()
                .select(CustomerDO::getId, CustomerDO::getName)
                .eq(CustomerDO::getNameKey, ""));
        for (CustomerDO c : rows) {
            // 只改 name_key，不触发 update_time / update_by 自动填充
            customerMapper.update(null, new LambdaUpdateWrapper<CustomerDO>()
                    .set(CustomerDO::getNameKey, CustomerNameNormalizer.normalize(c.getName()))
                    .eq(CustomerDO::getId, c.getId()));
        }
        return rows.size();
    }

    // ---------------------------------------------------------------- 工具

    private CustomerDO getActiveInScope(Long id, DataScope scope) {
        CustomerDO c = customerMapper.selectById(id);
        if (c == null || c.getDeletedAt() != null || !Objects.equals(c.getTenantId(), currentTenantId())
                || !scope.canSee(c.getOwnerId())) {
            throw new BizException(CustomerConstants.NOT_FOUND_MESSAGE);
        }
        return c;
    }

    private List<CustomerDO> selectActiveByIds(Collection<Long> ids) {
        return customerMapper.selectList(new LambdaQueryWrapper<CustomerDO>()
                .eq(CustomerDO::getTenantId, currentTenantId())
                .in(CustomerDO::getId, ids)
                .isNull(CustomerDO::getDeletedAt));
    }

    private CustomerDO findActiveByCode(int tenantId, String code) {
        return customerMapper.selectOne(new LambdaQueryWrapper<CustomerDO>()
                .eq(CustomerDO::getTenantId, tenantId)
                .eq(CustomerDO::getCustomerCode, code)
                .isNull(CustomerDO::getDeletedAt)
                .last("LIMIT 1"));
    }

    private String canonicalCountry(String input) {
        String canonical = countryCatalog.canonicalName(input);
        if (canonical == null) {
            throw new BizException("请选择系统清单中的国家/地区");
        }
        return canonical;
    }

    private static void requireNoCjk(String value) {
        if (value != null && CJK.matcher(value).find()) {
            throw new BizException(CustomerConstants.ENGLISH_ONLY_MESSAGE);
        }
    }

    /** 负责人 ID → 姓名 */
    private Map<Long, String> ownerNames(Collection<CustomerDO> customers) {
        Set<Integer> ids = new HashSet<>();
        for (CustomerDO c : customers) {
            if (c.getOwnerId() != null && c.getOwnerId() > 0) {
                ids.add(c.getOwnerId().intValue());
            }
        }
        Map<Long, String> names = new HashMap<>(ids.size() * 2);
        if (!ids.isEmpty()) {
            for (UserBasicDO u : userBasicMapper.selectBatchIds(ids)) {
                names.put(u.getId().longValue(), u.getName());
            }
        }
        return names;
    }

    private String ownerDeptName(Long ownerId) {
        if (ownerId == null || ownerId <= 0) {
            return null;
        }
        UserBasicDO u = userBasicMapper.selectById(ownerId.intValue());
        return u == null ? null : deptNames(List.of(u.getDeptId())).get(u.getDeptId());
    }

    private Map<Integer, String> deptNames(Collection<Integer> deptIds) {
        Set<Integer> ids = new HashSet<>();
        for (Integer id : deptIds) {
            if (id != null && id > 0) {
                ids.add(id);
            }
        }
        Map<Integer, String> names = new HashMap<>(ids.size() * 2);
        if (!ids.isEmpty()) {
            for (DepartmentDO d : departmentMapper.selectBatchIds(ids)) {
                names.put(d.getId(), d.getName());
            }
        }
        return names;
    }

    private List<CustomerVO> toVos(List<CustomerDO> customers) {
        Map<Long, String> owners = ownerNames(customers);
        List<CustomerVO> result = new ArrayList<>(customers.size());
        for (CustomerDO c : customers) {
            result.add(toVo(c, owners));
        }
        return result;
    }

    private static CustomerVO toVo(CustomerDO c, Map<Long, String> owners) {
        CustomerVO vo = new CustomerVO();
        fillVo(vo, c, owners);
        return vo;
    }

    private static void fillVo(CustomerVO vo, CustomerDO c, Map<Long, String> owners) {
        vo.setId(c.getId());
        vo.setCustomerCode(c.getCustomerCode());
        vo.setName(c.getName());
        vo.setNameCn(c.getNameCn());
        vo.setShortName(c.getShortName());
        vo.setCountry(c.getCountry());
        vo.setCustomerRole(c.getCustomerRole());
        vo.setCustomerGrade(c.getCustomerGrade());
        vo.setSourceChannel(c.getSourceChannel());
        vo.setOwnerId(c.getOwnerId());
        vo.setOwnerName(owners.get(c.getOwnerId()));
        vo.setContactName(c.getContactName());
        vo.setContactPhone(c.getContactPhone());
        vo.setContactEmail(c.getContactEmail());
        vo.setStatus(c.getStatus());
        vo.setCreateTime(c.getCreateTime());
        vo.setCreateBy(c.getCreateBy());
        vo.setUpdateTime(c.getUpdateTime());
        vo.setUpdateBy(c.getUpdateBy());
    }

    /** Map.of 不允许用 null 查找，这里先判空 */
    private static String label(Map<Integer, String> labels, Integer code, String fallback) {
        return code == null ? fallback : labels.getOrDefault(code, fallback);
    }

    private static int orUnset(Integer value) {
        return value != null ? value : CustomerConstants.UNSET;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    private int currentTenantId() {
        Integer tenantId = TenantContext.getTenantId();
        return tenantId != null ? tenantId : 0;
    }
}
