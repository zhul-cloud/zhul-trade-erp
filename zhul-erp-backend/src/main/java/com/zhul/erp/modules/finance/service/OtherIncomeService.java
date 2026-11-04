package com.zhul.erp.modules.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.utils.MigratedGuard;
import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.modules.attachment.dto.AttachmentVO;
import com.zhul.erp.modules.attachment.service.AttachmentService;
import com.zhul.erp.modules.finance.constants.FinanceConstants;
import com.zhul.erp.modules.finance.dto.OtherIncomeQuery;
import com.zhul.erp.modules.finance.dto.OtherIncomeVO;
import com.zhul.erp.modules.finance.dto.SaveOtherIncomeRequest;
import com.zhul.erp.modules.finance.entity.OtherIncomeDO;
import com.zhul.erp.modules.finance.repository.OtherIncomeMapper;
import com.zhul.erp.modules.sales.support.PiStore;
import com.zhul.erp.modules.system.constants.DocumentType;
import com.zhul.erp.modules.system.service.DictItemService;
import com.zhul.erp.modules.system.service.DocumentNumberService;
import com.zhul.erp.modules.system.service.LogService;
import com.zhul.erp.modules.system.service.BusinessUnitService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 其他收入（spec finance/other-income）：人民币，登记即生效，可修改、作废；按日期所在月份计入经营统计 */
@Service
@RequiredArgsConstructor
public class OtherIncomeService {

    private static final String MENU = "其他收入";

    private final BusinessUnitService businessUnitService;
    private final OtherIncomeMapper mapper;
    private final DocumentNumberService documentNumberService;
    private final AttachmentService attachments;
    private final DictItemService dictItemService;
    private final LogService logService;

    @Transactional(rollbackFor = Exception.class)
    public OtherIncomeVO create(SaveOtherIncomeRequest req) {
        OtherIncomeDO x = new OtherIncomeDO();
        x.setTenantId(PiStore.tenantId());
        x.setIncomeNo(documentNumberService.next(DocumentType.OI));
        x.setStatus(FinanceConstants.RECORD_VALID);
        x.setVoidReason("");
        fill(x, req);
        x.setBusinessUnitId(businessUnitService.forOptional(req.getBusinessUnitId()));
        mapper.insert(x);
        attachments.sync(AttachmentService.OTHER_INCOME, x.getId(), req.getAttachmentIds());
        logService.recordOperateLog(MENU, "登记其他收入", null, Map.of("incomeNo", x.getIncomeNo(), "amount", x.getAmount().toPlainString()));
        return detail(x.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public OtherIncomeVO update(Long id, SaveOtherIncomeRequest req) {
        OtherIncomeDO x = lock(id);
        MigratedGuard.requireEditable(x.getMigrated());
        if (x.getStatus() == FinanceConstants.RECORD_VOID) {
            throw new BizException("已作废，不能修改");
        }
        requireManual(x);
        BigDecimal before = x.getAmount();
        fill(x, req);
        mapper.updateById(x);
        // 可在「公共」与某事业部之间改，为空也要写回
        mapper.update(null, new LambdaUpdateWrapper<OtherIncomeDO>()
                .set(OtherIncomeDO::getBusinessUnitId, businessUnitService.forOptional(req.getBusinessUnitId()))
                .eq(OtherIncomeDO::getId, id));
        attachments.sync(AttachmentService.OTHER_INCOME, id, req.getAttachmentIds());
        logService.recordOperateLog(MENU, "修改其他收入", Map.of("incomeNo", x.getIncomeNo(), "amount", before.toPlainString()),
                Map.of("incomeNo", x.getIncomeNo(), "amount", x.getAmount().toPlainString()));
        return detail(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public OtherIncomeVO voidIncome(Long id, String reason) {
        String why = reason == null ? "" : reason.trim();
        if (why.isEmpty()) {
            throw new BizException("请填写作废原因");
        }
        OtherIncomeDO x = lock(id);
        MigratedGuard.requireEditable(x.getMigrated());
        if (x.getStatus() == FinanceConstants.RECORD_VOID) {
            throw new BizException("已作废");
        }
        requireManual(x);
        x.setStatus(FinanceConstants.RECORD_VOID);
        x.setVoidReason(why.length() > 200 ? why.substring(0, 200) : why);
        mapper.updateById(x);
        logService.recordOperateLog(MENU, "作废其他收入", Map.of("incomeNo", x.getIncomeNo(), "status", "有效"),
                Map.of("incomeNo", x.getIncomeNo(), "status", "已作废", "reason", x.getVoidReason()));
        return detail(id);
    }

    public PageResult<OtherIncomeVO> page(OtherIncomeQuery q) {
        int size = q.getPageSize() == null || q.getPageSize() <= 0 ? 20 : Math.min(q.getPageSize(), 100);
        Page<OtherIncomeDO> page = mapper.selectPage(new Page<>(Math.max(1, q.getPage() == null ? 1 : q.getPage()), size),
                filter(q).orderByDesc(OtherIncomeDO::getUpdateTime).orderByDesc(OtherIncomeDO::getId));
        return PageResult.of(page.getTotal(), toVos(page.getRecords()));
    }

    public BigDecimal total(OtherIncomeQuery q) {
        return mapper.selectList(filter(q)).stream().map(OtherIncomeDO::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public OtherIncomeVO detail(Long id) {
        return toVos(List.of(require(id))).get(0);
    }

    public Map<String, String> categoryNames() {
        Map<String, String> m = new LinkedHashMap<>();
        dictItemService.listByDictType(FinanceConstants.DICT_INCOME_CATEGORY).stream().filter(d -> Objects.equals(d.getStatus(), 1))
                .forEach(d -> m.put(d.getItemCode(), d.getItemName()));
        return m;
    }

    private void fill(OtherIncomeDO x, SaveOtherIncomeRequest req) {
        if (req.getIncomeDate().isAfter(LocalDate.now())) {
            throw new BizException("日期不能晚于今天");
        }
        if (categoryNames().get(req.getCategory()) == null) {
            throw new BizException("类别不存在");
        }
        if (FinanceConstants.INCOME_FX_GAIN.equals(req.getCategory())) {
            throw new BizException("汇兑损益由余额提现自动生成，不能手动登记");
        }
        x.setIncomeDate(req.getIncomeDate());
        x.setCategory(req.getCategory());
        x.setAmount(req.getAmount().setScale(2, RoundingMode.HALF_UP));
        x.setRemark(req.getRemark().trim());
    }

    /**
     * 余额提现的汇兑损益（spec finance/other-income「系统生成的汇兑损益」）：金额 = 余额人民币价值 − 实际转出人民币，可为负数，
     * 为 0 时不生成；与来源退款单在同一事务内生成，随它作废。
     */
    @Transactional(rollbackFor = Exception.class)
    public OtherIncomeDO createFxGain(java.time.LocalDate date, BigDecimal amount, Integer businessUnitId, Long refundId, String remark) {
        BigDecimal v = amount.setScale(2, RoundingMode.HALF_UP);
        if (v.signum() == 0) {
            return null;
        }
        OtherIncomeDO x = new OtherIncomeDO();
        x.setTenantId(PiStore.tenantId());
        x.setIncomeNo(documentNumberService.next(DocumentType.OI));
        x.setStatus(FinanceConstants.RECORD_VALID);
        x.setVoidReason("");
        x.setIncomeDate(date);
        x.setCategory(FinanceConstants.INCOME_FX_GAIN);
        x.setAmount(v);
        x.setRemark(remark);
        x.setBusinessUnitId(businessUnitId);
        x.setSourceType(FinanceConstants.SRC_REFUND);
        x.setSourceId(refundId);
        mapper.insert(x);
        return x;
    }

    /** 来源退款单作废时一起作废它生成的汇兑损益 */
    @Transactional(rollbackFor = Exception.class)
    public void voidBySource(Long refundId, String reason) {
        mapper.update(null, new LambdaUpdateWrapper<OtherIncomeDO>()
                .eq(OtherIncomeDO::getSourceType, FinanceConstants.SRC_REFUND)
                .eq(OtherIncomeDO::getSourceId, refundId)
                .eq(OtherIncomeDO::getStatus, FinanceConstants.RECORD_VALID)
                .set(OtherIncomeDO::getStatus, FinanceConstants.RECORD_VOID)
                .set(OtherIncomeDO::getVoidReason, reason));
    }

    private static void requireManual(OtherIncomeDO x) {
        if (x.getSourceType() != null && !x.getSourceType().isEmpty()) {
            throw new BizException("汇兑损益由余额提现自动生成，请作废来源退款单");
        }
    }

    /** 修改与作废先锁行再判断状态：两个请求交错时，后到的读到最新状态，旧的编辑不会把已作废记录写回有效（audit BUS-012） */
    private OtherIncomeDO lock(Long id) {
        OtherIncomeDO x = id == null ? null : mapper.selectOne(new LambdaQueryWrapper<OtherIncomeDO>()
                .eq(OtherIncomeDO::getId, id)
                .last("FOR UPDATE"));
        if (x == null || x.getDeletedAt() != null || !Objects.equals(x.getTenantId(), PiStore.tenantId())) {
            throw new BizException("其他收入不存在");
        }
        return x;
    }

    private OtherIncomeDO require(Long id) {
        OtherIncomeDO x = mapper.selectById(id);
        if (x == null || x.getDeletedAt() != null || !Objects.equals(x.getTenantId(), PiStore.tenantId())) {
            throw new BizException("其他收入不存在");
        }
        return x;
    }

    private LambdaQueryWrapper<OtherIncomeDO> filter(OtherIncomeQuery q) {
        LambdaQueryWrapper<OtherIncomeDO> w = new LambdaQueryWrapper<OtherIncomeDO>()
                .eq(OtherIncomeDO::getTenantId, PiStore.tenantId())
                .isNull(OtherIncomeDO::getDeletedAt);
        if (!Boolean.TRUE.equals(q.getShowVoid())) {
            w.eq(OtherIncomeDO::getStatus, FinanceConstants.RECORD_VALID);
        }
        if (q.getBusinessUnitId() != null) {
            if (q.getBusinessUnitId() == 0) {
                w.isNull(OtherIncomeDO::getBusinessUnitId);
            } else {
                w.eq(OtherIncomeDO::getBusinessUnitId, q.getBusinessUnitId());
            }
        }
        if (StringUtils.hasText(q.getCategory())) {
            w.eq(OtherIncomeDO::getCategory, q.getCategory());
        }
        if (q.getDateFrom() != null) {
            w.ge(OtherIncomeDO::getIncomeDate, q.getDateFrom());
        }
        if (q.getDateTo() != null) {
            w.le(OtherIncomeDO::getIncomeDate, q.getDateTo());
        }
        return w;
    }

    private List<OtherIncomeVO> toVos(List<OtherIncomeDO> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, List<AttachmentVO>> files = attachments.list(AttachmentService.OTHER_INCOME, rows.stream().map(OtherIncomeDO::getId).toList());
        Map<String, String> names = categoryNames();
        List<OtherIncomeVO> out = new ArrayList<>(rows.size());
        for (OtherIncomeDO x : rows) {
            OtherIncomeVO vo = new OtherIncomeVO();
            vo.setMigrated(MigratedGuard.isMigrated(x.getMigrated()));
            vo.setId(x.getId());
            vo.setIncomeNo(x.getIncomeNo());
            vo.setIncomeDate(x.getIncomeDate());
            vo.setBusinessUnitId(x.getBusinessUnitId());
            vo.setCategory(x.getCategory());
            vo.setCategoryName(names.getOrDefault(x.getCategory(), x.getCategory()));
            vo.setAmount(x.getAmount());
            vo.setRemark(x.getRemark());
            vo.setStatus(x.getStatus());
            vo.setVoidReason(x.getVoidReason());
            vo.setSourceType(x.getSourceType());
            vo.setSourceId(x.getSourceId());
            vo.setAttachments(PayableQueryService.refs(files.getOrDefault(x.getId(), List.of())));
            vo.setCreateTime(x.getCreateTime());
            vo.setCreateBy(x.getCreateBy());
            vo.setUpdateTime(x.getUpdateTime());
            vo.setUpdateBy(x.getUpdateBy());
            out.add(vo);
        }
        return out;
    }
}
