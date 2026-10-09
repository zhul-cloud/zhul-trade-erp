package com.zhul.erp.modules.masterdata.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.masterdata.constants.SupplierConstants;
import com.zhul.erp.modules.masterdata.dto.AbstractSupplierRequest;
import com.zhul.erp.modules.masterdata.dto.CreateSupplierFromChannelRequest;
import com.zhul.erp.modules.masterdata.dto.SaveSupplierRequest;
import com.zhul.erp.modules.masterdata.dto.SupplierAttachmentFile;
import com.zhul.erp.modules.masterdata.dto.SupplierAttachmentUploadVO;
import com.zhul.erp.modules.masterdata.dto.SupplierBankAccountVO;
import com.zhul.erp.modules.masterdata.dto.SupplierBatchDeleteResultVO;
import com.zhul.erp.modules.masterdata.dto.SupplierCreateResultVO;
import com.zhul.erp.modules.masterdata.dto.SupplierFormVO;
import com.zhul.erp.modules.masterdata.dto.SupplierPageQuery;
import com.zhul.erp.modules.masterdata.dto.SupplierProductScopeRequest;
import com.zhul.erp.modules.masterdata.dto.SupplierProductScopeVO;
import com.zhul.erp.modules.masterdata.dto.SupplierVO;
import com.zhul.erp.modules.masterdata.dto.UpdateSupplierRequest;
import com.zhul.erp.modules.masterdata.entity.SupplierAttachmentDO;
import com.zhul.erp.modules.masterdata.entity.SupplierDO;
import com.zhul.erp.modules.masterdata.repository.SupplierMapper;
import com.zhul.erp.modules.masterdata.service.SupplierService;
import com.zhul.erp.modules.masterdata.support.PaymentTerms;
import com.zhul.erp.modules.masterdata.support.SupplierAttachmentStorage;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SupplierServiceImpl implements SupplierService {

    private static final int SEARCH_LIMIT = 20;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String[] EXPORT_HEADERS = {
        "供应商编码", "供应商名称", "供应商简称", "供应商类型", "所属行业", "统一社会信用代码", "法人代表",
        "注册资本（万元）", "成立日期", "联系人", "联系电话", "联系邮箱", "微信", "所在地区", "详细地址",
        "收款账户", "状态", "备注", "创建时间", "创建人", "更新时间", "更新人", "主营产品"
    };

    private final SupplierMapper supplierMapper;
    private final SupplierProductScopeSync scopeSync;
    private final SupplierBankAccountSync accountSync;
    private final SupplierAttachmentSync attachmentSync;
    private final SupplierAttachmentStorage attachmentStorage;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SupplierCreateResultVO create(SaveSupplierRequest req) {
        int tenantId = currentTenantId();
        SupplierCreateResultVO result = new SupplierCreateResultVO();

        checkCreditCodeUnique(tenantId, normalizeUpper(req.getCreditCode()), null);
        List<SupplierProductScopeSync.Scope> scopes =
                req.getProductScopes() == null ? null : scopeSync.normalize(req.getProductScopes());

        SupplierDO existing = findActiveByName(tenantId, req.getName());
        if (existing != null && !req.isForce()) {
            result.setDuplicate(true);
            result.setExistingSupplier(toVo(existing));
            return result;
        }

        SupplierDO supplier = new SupplierDO();
        supplier.setTenantId(tenantId);
        applyFields(supplier, req);
        supplier.setSupplierType(req.getSupplierType() != null ? req.getSupplierType() : SupplierConstants.UNSET);
        supplier.setVolumeDivisor(req.getVolumeDivisor() != null ? req.getVolumeDivisor() : 5000);
        supplier.setStatus(req.getStatus() != null ? req.getStatus() : 1);
        // 编码一律自动生成：先插入拿到主键，再按 SUP + 5 位补零主键回填
        supplierMapper.insert(supplier);
        supplier.setSupplierCode(generateCode(tenantId, supplier.getId()));
        supplierMapper.updateById(supplier);
        if (scopes != null) {
            scopeSync.replace(tenantId, supplier.getId(), scopes);
        }
        saveAccountsAndAttachments(tenantId, supplier.getId(), req);

        result.setDuplicate(false);
        result.setCreatedSupplier(toVo(supplier));
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SupplierCreateResultVO createFromChannel(CreateSupplierFromChannelRequest req) {
        SaveSupplierRequest saveReq = new SaveSupplierRequest();
        saveReq.setName(req.getChannelName());
        saveReq.setForce(req.isForce());
        if (req.getBrandNames() != null) {
            saveReq.setProductScopes(req.getBrandNames().stream().filter(StringUtils::hasText).map(name -> {
                SupplierProductScopeRequest scope = new SupplierProductScopeRequest();
                scope.setBrandName(name);
                return scope;
            }).toList());
        }
        return create(saveReq);
    }

    @Override
    public List<SupplierVO> search(String keyword) {
        LambdaQueryWrapper<SupplierDO> wrapper = new LambdaQueryWrapper<SupplierDO>()
                .eq(SupplierDO::getTenantId, currentTenantId())
                .isNull(SupplierDO::getDeletedAt)
                .eq(SupplierDO::getStatus, 1);
        if (StringUtils.hasText(keyword)) {
            wrapper.like(SupplierDO::getName, keyword);
        }
        wrapper.last("LIMIT " + SEARCH_LIMIT);
        List<SupplierDO> list = supplierMapper.selectList(wrapper);
        List<SupplierVO> result = new ArrayList<>(list.size());
        for (SupplierDO supplier : list) {
            result.add(toVo(supplier));
        }
        return result;
    }

    @Override
    public SupplierVO getById(Long id) {
        SupplierDO supplier = supplierMapper.selectById(id);
        if (supplier == null || supplier.getDeletedAt() != null) {
            return null;
        }
        SupplierVO vo = toVo(supplier);
        fillDetail(vo, id, false);
        return vo;
    }

    @Override
    public SupplierFormVO getFormById(Long id) {
        SupplierDO supplier = getActiveOrThrow(id);
        SupplierFormVO vo = new SupplierFormVO();
        fillVo(vo, supplier);
        fillDetail(vo, id, true);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SupplierDO supplier = getActiveOrThrow(id);
        supplier.setDeletedAt(LocalDateTime.now());
        supplierMapper.updateById(supplier);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SupplierBatchDeleteResultVO batchDelete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BizException("请选择要删除的供应商");
        }
        List<Long> distinctIds = new ArrayList<>(new LinkedHashSet<>(ids));
        List<SupplierDO> suppliers = supplierMapper.selectList(new LambdaQueryWrapper<SupplierDO>()
                .eq(SupplierDO::getTenantId, currentTenantId())
                .in(SupplierDO::getId, distinctIds)
                .isNull(SupplierDO::getDeletedAt));
        LocalDateTime now = LocalDateTime.now();
        for (SupplierDO supplier : suppliers) {
            supplier.setDeletedAt(now);
            supplierMapper.updateById(supplier);
        }
        SupplierBatchDeleteResultVO result = new SupplierBatchDeleteResultVO();
        result.setDeleted(suppliers.size());
        result.setSkipped(distinctIds.size() - suppliers.size());
        return result;
    }

    @Override
    public PageResult<SupplierVO> page(SupplierPageQuery query) {
        Page<SupplierDO> pageResult = supplierMapper.selectPage(
                new Page<>(query.getPage(), query.getPageSize()), buildQueryWrapper(query));
        Map<Long, List<SupplierProductScopeVO>> scopes =
                scopeSync.load(pageResult.getRecords().stream().map(SupplierDO::getId).toList());
        List<SupplierVO> records = new ArrayList<>(pageResult.getRecords().size());
        for (SupplierDO supplier : pageResult.getRecords()) {
            SupplierVO vo = toVo(supplier);
            vo.setProductScopes(scopes.getOrDefault(supplier.getId(), List.of()));
            records.add(vo);
        }
        return PageResult.of(pageResult.getTotal(), records);
    }

    @Override
    public Workbook export(SupplierPageQuery query) {
        LambdaQueryWrapper<SupplierDO> wrapper = buildQueryWrapper(query);
        long count = supplierMapper.selectCount(wrapper);
        if (count > SupplierConstants.EXPORT_MAX_ROWS) {
            throw new BizException("导出数据超过" + SupplierConstants.EXPORT_MAX_ROWS + "条，请缩小筛选范围后再导出");
        }
        List<SupplierDO> suppliers = supplierMapper.selectList(wrapper);
        List<Long> ids = suppliers.stream().map(SupplierDO::getId).toList();
        Map<Long, List<SupplierProductScopeVO>> scopes = scopeSync.load(ids);
        Map<Long, List<SupplierBankAccountVO>> accounts = accountSync.load(ids, false);

        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("供应商基础信息");
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < EXPORT_HEADERS.length; i++) {
            headerRow.createCell(i).setCellValue(EXPORT_HEADERS[i]);
        }
        int rowIdx = 1;
        for (SupplierDO s : suppliers) {
            Row row = sheet.createRow(rowIdx++);
            String[] values = {
                s.getSupplierCode(), s.getName(), s.getShortName(),
                label(SupplierConstants.SUPPLIER_TYPE_LABELS, s.getSupplierType(), "未设置"),
                label(SupplierConstants.INDUSTRY_LABELS, s.getIndustry(), ""),
                s.getCreditCode(), s.getLegalRepresentative(),
                // 金额以字符串写入，避免经过 double
                s.getRegisteredCapital() == null ? "" : s.getRegisteredCapital().toPlainString(),
                s.getEstablishedDate() == null ? "" : s.getEstablishedDate().format(DATE_FMT),
                s.getContactName(), s.getContactPhone(), s.getContactEmail(), s.getWechat(), s.getRegion(),
                s.getAddress(), SupplierBankAccountSync.toText(accounts.get(s.getId())),
                Objects.equals(s.getStatus(), 1) ? "启用" : "禁用",
                s.getRemark(),
                s.getCreateTime() == null ? "" : s.getCreateTime().format(TIME_FMT), s.getCreateBy(),
                s.getUpdateTime() == null ? "" : s.getUpdateTime().format(TIME_FMT), s.getUpdateBy(),
                SupplierProductScopeSync.toText(scopes.get(s.getId()))
            };
            for (int i = 0; i < values.length; i++) {
                row.createCell(i).setCellValue(values[i] == null ? "" : values[i]);
            }
        }
        for (int i = 0; i < EXPORT_HEADERS.length; i++) {
            sheet.autoSizeColumn(i);
        }
        return workbook;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, UpdateSupplierRequest req) {
        SupplierDO supplier = getActiveOrThrow(id);
        SupplierDO duplicate = findActiveByName(supplier.getTenantId(), req.getName());
        if (duplicate != null && !duplicate.getId().equals(id)) {
            throw new BizException("该名称已存在");
        }
        checkCreditCodeUnique(supplier.getTenantId(), normalizeUpper(req.getCreditCode()), id);
        List<SupplierProductScopeSync.Scope> scopes =
                req.getProductScopes() == null ? null : scopeSync.normalize(req.getProductScopes());
        applyFields(supplier, req);
        if (req.getVolumeDivisor() != null) {
            supplier.setVolumeDivisor(req.getVolumeDivisor());
        }
        if (req.getSupplierType() != null) {
            supplier.setSupplierType(req.getSupplierType());
        }
        if (req.getStatus() != null) {
            supplier.setStatus(req.getStatus());
        }
        supplierMapper.updateById(supplier);
        if (scopes != null) {
            scopeSync.replace(supplier.getTenantId(), id, scopes);
        }
        saveAccountsAndAttachments(supplier.getTenantId(), id, req);
    }

    /** 收款账户、附件：请求里为 null 表示不修改 */
    private void saveAccountsAndAttachments(int tenantId, long supplierId, AbstractSupplierRequest req) {
        if (req.getAccounts() != null) {
            accountSync.merge(tenantId, supplierId, req.getAccounts());
        }
        if (req.getAttachments() != null) {
            attachmentSync.merge(tenantId, supplierId, req.getAttachments());
        }
    }

    /** 详情与编辑取数共用：主营产品、收款账户（plain 决定账号是否明文）、附件 */
    private void fillDetail(SupplierVO vo, Long id, boolean plain) {
        vo.setProductScopes(scopeSync.load(List.of(id)).getOrDefault(id, List.of()));
        vo.setAccounts(accountSync.load(List.of(id), plain).getOrDefault(id, List.of()));
        vo.setAttachments(attachmentSync.load(List.of(id)).getOrDefault(id, List.of()));
    }

    @Override
    public SupplierAttachmentUploadVO uploadAttachment(MultipartFile file) {
        return attachmentStorage.store(currentTenantId(), file);
    }

    @Override
    public SupplierAttachmentFile getAttachmentFile(Long supplierId, Long attachmentId) {
        SupplierAttachmentDO row = attachmentSync.getForDownload(currentTenantId(), supplierId, attachmentId);
        return new SupplierAttachmentFile(attachmentSync.fileOf(row), row.getFileName(), row.getContentType());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        SupplierDO supplier = getActiveOrThrow(id);
        supplier.setStatus(status);
        supplierMapper.updateById(supplier);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int backfillProductScopes() {
        List<SupplierDO> legacy = supplierMapper.selectList(new LambdaQueryWrapper<SupplierDO>()
                .select(SupplierDO::getId, SupplierDO::getTenantId, SupplierDO::getMainBrands)
                .ne(SupplierDO::getMainBrands, "")
                .isNull(SupplierDO::getDeletedAt)
                .notExists("SELECT 1 FROM supplier_product_scope s WHERE s.supplier_id = supplier.id"));
        int done = 0;
        for (SupplierDO s : legacy) {
            List<SupplierProductScopeSync.Scope> scopes = scopeSync.fromLegacy(s.getMainBrands());
            if (!scopes.isEmpty()) {
                scopeSync.replace(s.getTenantId(), s.getId(), scopes);
                done++;
            }
        }
        return done;
    }

    /**
     * 写入新增 / 更新共用的字段；文本统一去首尾空格、null 存空串。country 为 null 时保持原值；
     * main_brands 已由主营产品取代、bank_name / bank_account 已由收款账户取代，均不再写入
     */
    private void applyFields(SupplierDO supplier, AbstractSupplierRequest req) {
        if (req.getPaymentTerms() != null) {
            supplier.setPaymentTerms(PaymentTerms.toJson(PaymentTerms.normalize(req.getPaymentTerms())));
        } else if (supplier.getPaymentTerms() == null) {
            supplier.setPaymentTerms("");
        }
        supplier.setName(req.getName().trim());
        supplier.setShortName(normalize(req.getShortName()));
        supplier.setIndustry(req.getIndustry() != null ? req.getIndustry() : SupplierConstants.UNSET);
        supplier.setCreditCode(normalizeUpper(req.getCreditCode()));
        supplier.setLegalRepresentative(normalize(req.getLegalRepresentative()));
        supplier.setRegisteredCapital(req.getRegisteredCapital());
        supplier.setEstablishedDate(req.getEstablishedDate());
        supplier.setContactName(normalize(req.getContactName()));
        supplier.setContactPhone(normalize(req.getContactPhone()));
        supplier.setContactEmail(normalize(req.getContactEmail()));
        supplier.setWechat(normalize(req.getWechat()));
        supplier.setRegion(normalize(req.getRegion()));
        supplier.setAddress(normalize(req.getAddress()));
        supplier.setRemark(normalize(req.getRemark()));
        if (req.getCountry() != null) {
            supplier.setCountry(req.getCountry().trim());
        }
    }

    /** SUP + 5 位补零主键；与早期手工录入的编码撞上时追加数字后缀直到唯一 */
    private String generateCode(int tenantId, Long id) {
        String base = SupplierConstants.CODE_PREFIX + String.format("%05d", id);
        String candidate = base;
        int suffix = 1;
        while (findActiveByCode(tenantId, candidate) != null) {
            candidate = base + suffix++;
        }
        return candidate;
    }

    private void checkCreditCodeUnique(int tenantId, String creditCode, Long selfId) {
        if (creditCode.isEmpty()) {
            return;
        }
        SupplierDO holder = supplierMapper.selectOne(new LambdaQueryWrapper<SupplierDO>()
                .eq(SupplierDO::getTenantId, tenantId)
                .eq(SupplierDO::getCreditCode, creditCode)
                .isNull(SupplierDO::getDeletedAt)
                .ne(selfId != null, SupplierDO::getId, selfId)
                .last("LIMIT 1"));
        if (holder != null) {
            throw BizException.of(SupplierConstants.ERROR_CREDIT_CODE_DUPLICATE,
                    "该统一社会信用代码已被供应商「" + holder.getName() + "」使用，请核实后再提交");
        }
    }

    private LambdaQueryWrapper<SupplierDO> buildQueryWrapper(SupplierPageQuery query) {
        String creditCode = normalizeUpper(query.getCreditCode());
        LambdaQueryWrapper<SupplierDO> wrapper = new LambdaQueryWrapper<SupplierDO>()
                .eq(SupplierDO::getTenantId, currentTenantId())
                .isNull(SupplierDO::getDeletedAt)
                .eq(query.getStatus() != null, SupplierDO::getStatus, query.getStatus())
                .eq(StringUtils.hasText(query.getCountry()), SupplierDO::getCountry, query.getCountry())
                .like(StringUtils.hasText(query.getKeyword()), SupplierDO::getName, query.getKeyword())
                .like(StringUtils.hasText(query.getSupplierCode()), SupplierDO::getSupplierCode,
                        normalize(query.getSupplierCode()))
                .like(StringUtils.hasText(query.getName()), SupplierDO::getName, normalize(query.getName()))
                .eq(!creditCode.isEmpty(), SupplierDO::getCreditCode, creditCode)
                .eq(query.getSupplierType() != null, SupplierDO::getSupplierType, query.getSupplierType())
                .orderByDesc(SupplierDO::getUpdateTime);
        if (query.getBrandId() != null || query.getCategoryId() != null) {
            Set<Long> ids = scopeSync.supplierIdsMatching(currentTenantId(), query.getBrandId(), query.getCategoryId());
            if (ids.isEmpty()) {
                wrapper.apply("1 = 0");
            } else {
                wrapper.in(SupplierDO::getId, ids);
            }
        }
        return wrapper;
    }

    private SupplierDO getActiveOrThrow(Long id) {
        SupplierDO supplier = supplierMapper.selectById(id);
        if (supplier == null || supplier.getDeletedAt() != null) {
            throw new BizException("供应商不存在");
        }
        return supplier;
    }

    private SupplierDO findActiveByName(int tenantId, String name) {
        return supplierMapper.selectOne(new LambdaQueryWrapper<SupplierDO>()
                .eq(SupplierDO::getTenantId, tenantId)
                .eq(SupplierDO::getName, name)
                .isNull(SupplierDO::getDeletedAt)
                .last("LIMIT 1"));
    }

    private SupplierDO findActiveByCode(int tenantId, String code) {
        return supplierMapper.selectOne(new LambdaQueryWrapper<SupplierDO>()
                .eq(SupplierDO::getTenantId, tenantId)
                .eq(SupplierDO::getSupplierCode, code)
                .isNull(SupplierDO::getDeletedAt)
                .last("LIMIT 1"));
    }

    /** Map.of 不允许用 null 查找，这里先判空 */
    private static String label(Map<Integer, String> labels, Integer code, String fallback) {
        return code == null ? fallback : labels.getOrDefault(code, fallback);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeUpper(String value) {
        return normalize(value).toUpperCase(Locale.ROOT);
    }

    private int currentTenantId() {
        Integer tenantId = TenantContext.getTenantId();
        return tenantId != null ? tenantId : 0;
    }

    private SupplierVO toVo(SupplierDO supplier) {
        SupplierVO vo = new SupplierVO();
        fillVo(vo, supplier);
        return vo;
    }

    /** 基础字段；列表不加载收款账户和附件（给空列表），详情与编辑取数由 fillDetail 补齐 */
    private void fillVo(SupplierVO vo, SupplierDO supplier) {
        vo.setId(supplier.getId());
        vo.setSupplierCode(supplier.getSupplierCode());
        vo.setName(supplier.getName());
        vo.setShortName(supplier.getShortName());
        vo.setSupplierType(supplier.getSupplierType());
        vo.setVolumeDivisor(supplier.getVolumeDivisor());
        vo.setIndustry(supplier.getIndustry());
        vo.setCreditCode(supplier.getCreditCode());
        vo.setLegalRepresentative(supplier.getLegalRepresentative());
        vo.setRegisteredCapital(supplier.getRegisteredCapital());
        vo.setEstablishedDate(supplier.getEstablishedDate());
        vo.setCountry(supplier.getCountry());
        vo.setContactName(supplier.getContactName());
        vo.setContactPhone(supplier.getContactPhone());
        vo.setContactEmail(supplier.getContactEmail());
        vo.setWechat(supplier.getWechat());
        vo.setRegion(supplier.getRegion());
        vo.setPaymentTerms(PaymentTerms.fromJson(supplier.getPaymentTerms()));
        vo.setPaymentTermsText(PaymentTerms.text(vo.getPaymentTerms()));
        vo.setAddress(supplier.getAddress());
        vo.setAccounts(List.of());
        vo.setAttachments(List.of());
        vo.setRemark(supplier.getRemark());
        vo.setStatus(supplier.getStatus());
        vo.setCreateTime(supplier.getCreateTime());
        vo.setCreateBy(supplier.getCreateBy());
        vo.setUpdateTime(supplier.getUpdateTime());
        vo.setUpdateBy(supplier.getUpdateBy());
    }
}
