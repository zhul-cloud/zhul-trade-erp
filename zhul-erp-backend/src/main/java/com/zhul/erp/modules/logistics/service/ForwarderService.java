package com.zhul.erp.modules.logistics.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.modules.logistics.constants.LogisticsConstants;
import com.zhul.erp.modules.logistics.dto.ForwarderCountsVO;
import com.zhul.erp.modules.logistics.dto.ForwarderPageQuery;
import com.zhul.erp.modules.logistics.dto.ForwarderRowVO;
import com.zhul.erp.modules.logistics.dto.SaveForwarderRequest;
import com.zhul.erp.modules.logistics.support.LogisticsSupport;
import com.zhul.erp.modules.masterdata.dto.AbstractSupplierRequest;
import com.zhul.erp.modules.masterdata.dto.SaveSupplierRequest;
import com.zhul.erp.modules.masterdata.dto.SupplierBankAccountVO;
import com.zhul.erp.modules.masterdata.dto.SupplierCreateResultVO;
import com.zhul.erp.modules.masterdata.dto.SupplierFormVO;
import com.zhul.erp.modules.masterdata.dto.UpdateSupplierRequest;
import com.zhul.erp.modules.masterdata.entity.CustomerDO;
import com.zhul.erp.modules.masterdata.entity.SupplierDO;
import com.zhul.erp.modules.masterdata.repository.CustomerMapper;
import com.zhul.erp.modules.masterdata.repository.SupplierMapper;
import com.zhul.erp.modules.masterdata.service.SupplierService;
import com.zhul.erp.modules.masterdata.service.impl.SupplierBankAccountSync;
import com.zhul.erp.modules.sales.support.PiStore;
import com.zhul.erp.modules.system.service.LogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 货代管理（spec logistics/forwarder）：货代就是供应商表里类型为服务商的记录，这里单独维护；
 * 增改复用供应商的保存逻辑（编码、收款账户、改名事件），另外维护归属客户（客户指定货代）。
 */
@Service
@RequiredArgsConstructor
public class ForwarderService {

    private static final String MENU = "货代管理";
    private static final int MAX_PAGE_SIZE = 100;

    private final SupplierService supplierService;
    private final SupplierMapper supplierMapper;
    private final SupplierBankAccountSync accountSync;
    private final CustomerMapper customerMapper;
    private final LogisticsSupport support;
    private final LogService logService;

    public PageResult<ForwarderRowVO> page(ForwarderPageQuery q) {
        int size = q.getPageSize() == null ? 20 : Math.min(Math.max(q.getPageSize(), 1), MAX_PAGE_SIZE);
        int page = q.getPage() == null ? 1 : Math.max(q.getPage(), 1);
        LambdaQueryWrapper<SupplierDO> w = base();
        if ("company".equals(q.getOwner())) {
            w.isNull(SupplierDO::getCustomerId);
        } else if ("customer".equals(q.getOwner())) {
            w.isNotNull(SupplierDO::getCustomerId);
        }
        w.eq(q.getCustomerId() != null, SupplierDO::getCustomerId, q.getCustomerId())
                .eq(q.getStatus() != null, SupplierDO::getStatus, q.getStatus());
        if (StringUtils.hasText(q.getKeyword())) {
            String k = q.getKeyword().trim();
            w.and(x -> x.like(SupplierDO::getName, k).or().like(SupplierDO::getShortName, k)
                    .or().like(SupplierDO::getContactName, k).or().like(SupplierDO::getContactPhone, k));
        }
        w.orderByDesc(SupplierDO::getUpdateTime).orderByDesc(SupplierDO::getId);
        Page<SupplierDO> p = supplierMapper.selectPage(new Page<>(page, size), w);
        return PageResult.of(p.getTotal(), rows(p.getRecords()));
    }

    /** 页签上的数量：全部、公司货代、客户指定 */
    public ForwarderCountsVO counts() {
        ForwarderCountsVO vo = new ForwarderCountsVO();
        vo.setAll(supplierMapper.selectCount(base()));
        vo.setCompany(supplierMapper.selectCount(base().isNull(SupplierDO::getCustomerId)));
        vo.setCustomer(vo.getAll() - vo.getCompany());
        return vo;
    }

    /** 客户详情「客户指定货代」：归属这个客户的货代（含停用的） */
    public List<ForwarderRowVO> ofCustomer(Long customerId) {
        return rows(supplierMapper.selectList(base().eq(SupplierDO::getCustomerId, customerId)
                .orderByDesc(SupplierDO::getStatus).orderByAsc(SupplierDO::getName)));
    }

    /** 编辑取数：收款账号为明文（要求编辑权限） */
    public SupplierFormVO form(Long id) {
        require(id);
        SupplierFormVO vo = supplierService.getFormById(id);
        vo.setCustomerName(customerName(vo.getCustomerId()));
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(SaveForwarderRequest req) {
        Long customerId = requireCustomer(req.getCustomerId());
        SaveSupplierRequest s = new SaveSupplierRequest();
        copy(req, s);
        s.setStatus(req.getStatus() == null ? 1 : req.getStatus());
        SupplierCreateResultVO r = supplierService.create(s);
        if (r.isDuplicate()) {
            throw new BizException("已有同名的货代或供应商「" + req.getName().trim() + "」");
        }
        Long id = r.getCreatedSupplier().getId();
        setCustomer(id, customerId);
        logService.recordOperateLog(MENU, "新增货代", null, Map.of("name", req.getName().trim(),
                "customer", customerId == null ? "公司货代" : customerName(customerId)));
        return id;
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, SaveForwarderRequest req) {
        SupplierDO before = require(id);
        Long customerId = requireCustomer(req.getCustomerId());
        UpdateSupplierRequest s = new UpdateSupplierRequest();
        copy(req, s);
        s.setStatus(req.getStatus());
        supplierService.update(id, s);
        setCustomer(id, customerId);
        if (!Objects.equals(before.getCustomerId(), customerId)) {
            logService.recordOperateLog(MENU, "修改货代归属客户",
                    Map.of("name", before.getName(), "customer", ownerText(before.getCustomerId())),
                    Map.of("name", req.getName().trim(), "customer", ownerText(customerId)));
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        require(id);
        if (status == null || (status != 0 && status != 1)) {
            throw new BizException("状态不正确");
        }
        supplierService.updateStatus(id, status);
    }

    // ---------------------------------------------------------------- 工具

    /** 本租户未删除的货代（服务商），不含历史迁移的占位 */
    private LambdaQueryWrapper<SupplierDO> base() {
        List<Long> placeholders = support.placeholderForwarders();
        return new LambdaQueryWrapper<SupplierDO>()
                .eq(SupplierDO::getTenantId, PiStore.tenantId())
                .eq(SupplierDO::getSupplierType, LogisticsConstants.SUPPLIER_SERVICE)
                .isNull(SupplierDO::getDeletedAt)
                .notIn(!placeholders.isEmpty(), SupplierDO::getId, placeholders);
    }

    private SupplierDO require(Long id) {
        SupplierDO s = id == null ? null : supplierMapper.selectById(id);
        if (s == null || s.getDeletedAt() != null || !Objects.equals(s.getTenantId(), PiStore.tenantId())
                || !Objects.equals(s.getSupplierType(), LogisticsConstants.SUPPLIER_SERVICE)) {
            throw new BizException("货代不存在");
        }
        return s;
    }

    /** 归属客户须是本租户未删除的客户；为空表示公司货代 */
    private Long requireCustomer(Long customerId) {
        if (customerId == null) {
            return null;
        }
        CustomerDO c = customerMapper.selectById(customerId);
        if (c == null || c.getDeletedAt() != null || !Objects.equals(c.getTenantId(), PiStore.tenantId())) {
            throw new BizException("归属客户不存在");
        }
        return customerId;
    }

    private void setCustomer(Long id, Long customerId) {
        supplierMapper.update(null, new LambdaUpdateWrapper<SupplierDO>()
                .set(SupplierDO::getCustomerId, customerId)
                .eq(SupplierDO::getId, id));
    }

    private static void copy(SaveForwarderRequest req, AbstractSupplierRequest s) {
        s.setName(req.getName().trim());
        s.setShortName(text(req.getShortName()));
        s.setSupplierType(LogisticsConstants.SUPPLIER_SERVICE);
        s.setContactName(text(req.getContactName()));
        s.setContactPhone(text(req.getContactPhone()));
        s.setContactEmail(text(req.getContactEmail()));
        s.setAddress(text(req.getAddress()));
        s.setVolumeDivisor(req.getVolumeDivisor() == null ? 5000 : req.getVolumeDivisor());
        s.setAccounts(req.getAccounts());
        s.setRemark(text(req.getRemark()));
    }

    private List<ForwarderRowVO> rows(List<SupplierDO> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        List<Long> ids = list.stream().map(SupplierDO::getId).toList();
        Map<Long, List<SupplierBankAccountVO>> accounts = accountSync.load(ids, false);
        List<Long> customerIds = list.stream().map(SupplierDO::getCustomerId).filter(Objects::nonNull).distinct().toList();
        Map<Long, CustomerDO> customers = customerIds.isEmpty() ? Map.of()
                : customerMapper.selectBatchIds(customerIds).stream().collect(Collectors.toMap(CustomerDO::getId, Function.identity()));
        List<ForwarderRowVO> out = new ArrayList<>(list.size());
        for (SupplierDO s : list) {
            ForwarderRowVO vo = new ForwarderRowVO();
            vo.setId(s.getId());
            vo.setCode(s.getSupplierCode());
            vo.setName(s.getName());
            vo.setShortName(s.getShortName());
            vo.setCustomerId(s.getCustomerId());
            CustomerDO c = s.getCustomerId() == null ? null : customers.get(s.getCustomerId());
            vo.setCustomerName(c == null ? null : displayName(c));
            vo.setContactName(s.getContactName());
            vo.setContactPhone(s.getContactPhone());
            vo.setContactEmail(s.getContactEmail());
            vo.setVolumeDivisor(s.getVolumeDivisor());
            List<SupplierBankAccountVO> acc = accounts.getOrDefault(s.getId(), List.of());
            vo.setAccountCount(acc.size());
            acc.stream().filter(SupplierBankAccountVO::isDefaultAccount).findFirst().or(() -> acc.stream().findFirst())
                    .ifPresent(a -> vo.setDefaultAccount(a.getBankName() + " " + tail(a.getAccountNo())));
            vo.setStatus(s.getStatus());
            vo.setCreateTime(s.getCreateTime());
            vo.setCreateBy(s.getCreateBy());
            vo.setUpdateTime(s.getUpdateTime());
            vo.setUpdateBy(s.getUpdateBy());
            out.add(vo);
        }
        return out;
    }

    private String customerName(Long customerId) {
        CustomerDO c = customerId == null ? null : customerMapper.selectById(customerId);
        return c == null ? null : displayName(c) + (c.getDeletedAt() != null ? "（客户已删除）" : "");
    }

    private String ownerText(Long customerId) {
        return customerId == null ? "公司货代" : customerName(customerId);
    }

    private static String displayName(CustomerDO c) {
        return StringUtils.hasText(c.getName()) ? c.getName() : c.getContactName();
    }

    /** 收款账号只显示后四位（列表接口已脱敏，这里再截一次） */
    private static String tail(String no) {
        String s = no == null ? "" : no.replace(" ", "").replace("*", "");
        return s.isEmpty() ? "" : "****" + (s.length() > 4 ? s.substring(s.length() - 4) : s);
    }

    private static String text(String s) {
        return s == null ? "" : s.trim();
    }
}
