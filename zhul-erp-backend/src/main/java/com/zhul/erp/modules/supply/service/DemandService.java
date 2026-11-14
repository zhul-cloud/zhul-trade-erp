package com.zhul.erp.modules.supply.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.inquiry.support.InquiryLookups;
import com.zhul.erp.modules.product.support.MpnNormalizer;
import com.zhul.erp.modules.sales.support.PiStore;
import com.zhul.erp.modules.supply.constants.SupplyConstants;
import com.zhul.erp.modules.supply.dto.DemandVO;
import com.zhul.erp.modules.supply.dto.SignalVO;
import com.zhul.erp.modules.supply.dto.SupplyRequests;
import com.zhul.erp.modules.supply.entity.ScDemandDO;
import com.zhul.erp.modules.supply.entity.ScDemandSignalDO;
import com.zhul.erp.modules.supply.entity.ScGapProjectItemDO;
import com.zhul.erp.modules.supply.repository.ScDemandMapper;
import com.zhul.erp.modules.supply.repository.ScDemandSignalMapper;
import com.zhul.erp.modules.supply.repository.ScGapProjectItemMapper;
import com.zhul.erp.modules.supply.support.DemandAssembler;
import com.zhul.erp.modules.supply.support.DemandRecalc;
import com.zhul.erp.modules.supply.support.DemandRegistry;
import com.zhul.erp.modules.supply.support.DemandRules;
import com.zhul.erp.modules.supply.support.SupplyLookups;
import com.zhul.erp.modules.system.constants.DocumentType;
import com.zhul.erp.modules.system.doclog.DocLogs;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 需求信号与需求对象（spec supply-chain/demand-pool；tasks 3.3–3.5）：人工录入、归类、忽略、合并、改优先级、暂不覆盖。
 * 数据只按租户隔离。写操作都在一个事务里完成：改数据 → 重算受影响的需求对象 → 写操作日志。
 */
@Service
@RequiredArgsConstructor
public class DemandService {

    private final ScDemandMapper demandMapper;
    private final ScDemandSignalMapper signalMapper;
    private final ScGapProjectItemMapper projectItemMapper;
    private final DemandRegistry registry;
    private final DemandRecalc recalc;
    private final DemandAssembler assembler;
    private final SupplyLookups lookups;
    private final InquiryLookups inquiryLookups;
    private final CurrentUserResolver currentUser;
    private final DocLogs docLogs;

    @Transactional
    public Long addSignal(SupplyRequests.ManualSignal req) {
        if (req.getPeriodStart() != null && req.getPeriodEnd() != null && req.getPeriodEnd().isBefore(req.getPeriodStart())) {
            throw new BizException("统计期结束日期不能早于开始日期");
        }
        ScDemandSignalDO s = new ScDemandSignalDO();
        s.setTenantId(PiStore.tenantId());
        s.setSourceType(req.getType());
        s.setSourceDocId(0L);
        s.setSourceDocNo("");
        s.setBrandId(req.getBrandId() == null ? 0L : req.getBrandId());
        s.setBrandRaw("");
        s.setCategoryId(req.getCategoryId() == null ? 0L : req.getCategoryId());
        s.setModelRaw(req.getModel() == null ? "" : req.getModel().trim());
        s.setModelKey(MpnNormalizer.normalize(req.getModel()));
        s.setContent(req.getContent().trim());
        s.setPlatform(req.getPlatform().trim());
        s.setOccurCount(req.getType() == SupplyConstants.SRC_SEARCH && req.getCount() != null ? req.getCount() : 1);
        s.setPeriodStart(req.getPeriodStart());
        s.setPeriodEnd(req.getPeriodEnd());
        s.setLinkUrl(req.getLink() == null ? "" : req.getLink().trim());
        s.setOccurredAt(req.getPeriodEnd() != null ? req.getPeriodEnd().atStartOfDay() : LocalDateTime.now());
        s.setOperatorId(me());
        s.setDemandId(0L);
        ScDemandDO d = target(req.getBrandId(), req.getCategoryId(), req.getModel(), false);
        if (d != null) {
            s.setDemandId(d.getId());
        }
        signalMapper.insert(s);
        if (d != null) {
            recalc.run(LocalDate.now(), Map.of(), List.of(d.getId()));
        }
        return s.getId();
    }

    /** 待归类的信号，新的在前 */
    public Page<SignalVO> unclassified(int page, int size, Integer type) {
        Page<ScDemandSignalDO> p = signalMapper.selectPage(Page.of(page, Math.min(size, 200)), new LambdaQueryWrapper<ScDemandSignalDO>()
                .eq(ScDemandSignalDO::getDemandId, 0L)
                .eq(ScDemandSignalDO::getSourceCancelled, 0)
                .eq(type != null, ScDemandSignalDO::getSourceType, type)
                .isNull(ScDemandSignalDO::getDeletedAt)
                .orderByDesc(ScDemandSignalDO::getOccurredAt).orderByDesc(ScDemandSignalDO::getId));
        Page<SignalVO> out = Page.of(p.getCurrent(), p.getSize(), p.getTotal());
        out.setRecords(signals(p.getRecords()));
        return out;
    }

    @Transactional
    public int classify(SupplyRequests.Classify req) {
        ScDemandDO d = req.getDemandId() != null ? live(req.getDemandId())
                : target(req.getBrandId(), req.getCategoryId(), req.getModel(), true);
        List<ScDemandSignalDO> rows = signalMapper.selectBatchIds(req.getSignalIds());
        Set<Long> touched = new HashSet<>();
        touched.add(d.getId());
        int n = 0;
        for (ScDemandSignalDO s : rows) {
            if (s.getDeletedAt() != null) {
                continue;
            }
            if (s.getDemandId() != null && s.getDemandId() > 0) {
                touched.add(s.getDemandId());
            }
            signalMapper.update(null, new LambdaUpdateWrapper<ScDemandSignalDO>().eq(ScDemandSignalDO::getId, s.getId())
                    .set(ScDemandSignalDO::getDemandId, d.getId())
                    .set(ScDemandSignalDO::getUpdateTime, LocalDateTime.now()));
            n++;
        }
        docLogs.add(DocumentType.SC, d.getId(), "归类", "归入 " + n + " 条信号");
        recalc.run(LocalDate.now(), Map.of(), touched);
        return n;
    }

    @Transactional
    public int ignore(SupplyRequests.Ignore req) {
        Set<Long> touched = new HashSet<>();
        int n = 0;
        for (ScDemandSignalDO s : signalMapper.selectBatchIds(req.getSignalIds())) {
            if (s.getDeletedAt() != null) {
                continue;
            }
            if (s.getDemandId() != null && s.getDemandId() > 0) {
                touched.add(s.getDemandId());
            }
            signalMapper.update(null, new LambdaUpdateWrapper<ScDemandSignalDO>().eq(ScDemandSignalDO::getId, s.getId())
                    .set(ScDemandSignalDO::getDeletedAt, LocalDateTime.now())
                    .set(ScDemandSignalDO::getIgnoreReason, req.getReason() == null ? "" : req.getReason().trim())
                    .set(ScDemandSignalDO::getUpdateTime, LocalDateTime.now()));
            n++;
        }
        recalc.run(LocalDate.now(), Map.of(), touched);
        return n;
    }

    /** 合并：信号、渠道、专项型号都并到保留方，被合并方软删除并指向保留方 */
    @Transactional
    public void merge(SupplyRequests.Merge req) {
        if (req.getKeepId().equals(req.getRemoveId())) {
            throw new BizException("不能和自己合并");
        }
        ScDemandDO keep = live(req.getKeepId());
        ScDemandDO gone = live(req.getRemoveId());
        signalMapper.update(null, new LambdaUpdateWrapper<ScDemandSignalDO>()
                .eq(ScDemandSignalDO::getDemandId, gone.getId())
                .set(ScDemandSignalDO::getDemandId, keep.getId())
                .set(ScDemandSignalDO::getUpdateTime, LocalDateTime.now()));
        projectItemMapper.update(null, new LambdaUpdateWrapper<ScGapProjectItemDO>()
                .eq(ScGapProjectItemDO::getDemandId, gone.getId()).isNull(ScGapProjectItemDO::getDeletedAt)
                .set(ScGapProjectItemDO::getDeletedAt, LocalDateTime.now()));
        demandMapper.update(null, new LambdaUpdateWrapper<ScDemandDO>().eq(ScDemandDO::getId, gone.getId())
                .set(ScDemandDO::getMergedIntoId, keep.getId())
                .set(ScDemandDO::getDeletedAt, LocalDateTime.now())
                .set(ScDemandDO::getUpdateTime, LocalDateTime.now()));
        String goneName = name(gone);
        docLogs.add(DocumentType.SC, keep.getId(), "合并", "并入 " + goneName + " 的全部信号");
        docLogs.add(DocumentType.SC, gone.getId(), "合并", "并到 " + name(keep));
        recalc.run(LocalDate.now(), Map.of(), List.of(keep.getId()));
    }

    @Transactional
    public void changePriority(Long id, SupplyRequests.ChangePriority req) {
        ScDemandDO d = live(id);
        demandMapper.update(null, new LambdaUpdateWrapper<ScDemandDO>().eq(ScDemandDO::getId, id)
                .set(ScDemandDO::getPriority, req.getPriority())
                .set(ScDemandDO::getPriorityManual, 1)
                .set(ScDemandDO::getPriorityReason, req.getReason().trim())
                .set(ScDemandDO::getUpdateTime, LocalDateTime.now()));
        docLogs.add(DocumentType.SC, id, "改优先级", DemandRules.priorityName(d.getPriority()) + " → "
                + DemandRules.priorityName(req.getPriority()) + "：" + req.getReason().trim());
        recalc.run(LocalDate.now(), Map.of(), List.of(id));
    }

    /** 低优先级的手动加入覆盖矩阵 */
    @Transactional
    public void joinMatrix(Long id) {
        ScDemandDO d = live(id);
        if (d.getStatus() != SupplyConstants.DEMAND_WATCHING) {
            throw new BizException("只有观察中的需求可以加入覆盖矩阵");
        }
        // 由重算按「新进入矩阵」给建议状态、标待确认并产生提醒
        recalc.enterMatrix(id, LocalDate.now());
    }

    @Transactional
    public void pause(Long id, SupplyRequests.Reason req) {
        ScDemandDO d = live(id);
        if (d.getStatus() == SupplyConstants.DEMAND_PAUSED) {
            return;
        }
        demandMapper.update(null, new LambdaUpdateWrapper<ScDemandDO>().eq(ScDemandDO::getId, id)
                .set(ScDemandDO::getStatus, SupplyConstants.DEMAND_PAUSED)
                .set(ScDemandDO::getPauseReason, req.getReason().trim())
                .set(ScDemandDO::getUpdateTime, LocalDateTime.now()));
        projectItemMapper.update(null, new LambdaUpdateWrapper<ScGapProjectItemDO>()
                .eq(ScGapProjectItemDO::getDemandId, id).isNull(ScGapProjectItemDO::getDeletedAt)
                .set(ScGapProjectItemDO::getDeletedAt, LocalDateTime.now()));
        docLogs.add(DocumentType.SC, id, "暂不覆盖", req.getReason().trim());
    }

    /** 恢复：回到观察中，由重算按优先级决定是否重新进入矩阵 */
    @Transactional
    public void resume(Long id) {
        ScDemandDO d = live(id);
        if (d.getStatus() != SupplyConstants.DEMAND_PAUSED) {
            return;
        }
        demandMapper.update(null, new LambdaUpdateWrapper<ScDemandDO>().eq(ScDemandDO::getId, id)
                .set(ScDemandDO::getStatus, SupplyConstants.DEMAND_WATCHING)
                .set(ScDemandDO::getPauseReason, "")
                .set(ScDemandDO::getCoverageStatus, SupplyConstants.COVER_NONE)
                .set(ScDemandDO::getUpdateTime, LocalDateTime.now()));
        docLogs.add(DocumentType.SC, id, "恢复", "恢复为观察中");
        recalc.run(LocalDate.now(), Map.of(), List.of(id));
    }

    public List<SignalVO> signalsOf(Long demandId) {
        return signals(signalMapper.selectList(new LambdaQueryWrapper<ScDemandSignalDO>()
                .eq(ScDemandSignalDO::getDemandId, demandId)
                .isNull(ScDemandSignalDO::getDeletedAt)
                .orderByDesc(ScDemandSignalDO::getOccurredAt).orderByDesc(ScDemandSignalDO::getId)
                .last("LIMIT 300")));
    }

    public DemandVO row(Long id) {
        return assembler.toVOs(List.of(live(id))).get(0);
    }

    public ScDemandDO live(Long id) {
        ScDemandDO d = id == null ? null : demandMapper.selectById(id);
        if (d == null || d.getDeletedAt() != null) {
            throw new BizException("需求对象不存在或已合并");
        }
        return d;
    }

    /** 品牌 + 型号，或品牌 + 品类；required 时缺信息直接报错，否则返回 null 进入待归类 */
    private ScDemandDO target(Long brandId, Long categoryId, String model, boolean required) {
        String key = MpnNormalizer.normalize(model);
        boolean ok = brandId != null && brandId > 0 && lookups.brandExists(brandId)
                && (StringUtils.hasText(key) || categoryId != null && categoryId > 0);
        if (!ok) {
            if (required) {
                throw new BizException("请选择品牌，并填写型号或选择品类");
            }
            return null;
        }
        if (!StringUtils.hasText(key) && !lookups.isLevel2(categoryId)) {
            throw new BizException("请选择细分品类");
        }
        return registry.findOrCreate(brandId, StringUtils.hasText(key) && !lookups.isLevel2(categoryId) ? 0L : categoryId,
                key, model);
    }

    private List<SignalVO> signals(List<ScDemandSignalDO> rows) {
        Map<Long, String> brands = lookups.brandNames(rows.stream().map(ScDemandSignalDO::getBrandId).toList());
        Map<Long, String> users = inquiryLookups.userNames(rows.stream().map(ScDemandSignalDO::getOperatorId)
                .filter(i -> i != null && i > 0).toList());
        List<SignalVO> out = new ArrayList<>(rows.size());
        for (ScDemandSignalDO s : rows) {
            SignalVO v = new SignalVO();
            org.springframework.beans.BeanUtils.copyProperties(s, v);
            v.setBrandName(brands.get(s.getBrandId()));
            v.setCancelled(s.getSourceCancelled() != null && s.getSourceCancelled() == 1);
            v.setOperatorName(users.get(s.getOperatorId()));
            out.add(v);
        }
        return out;
    }

    private String name(ScDemandDO d) {
        String brand = lookups.brandNames(List.of(d.getBrandId())).getOrDefault(d.getBrandId(), "");
        if (StringUtils.hasText(d.getModel())) {
            return brand + " " + d.getModel();
        }
        return brand + " · " + lookups.categoryNames(List.of(d.getCategoryId())).getOrDefault(d.getCategoryId(), "未分类");
    }

    private Long me() {
        Long id = currentUser.resolve();
        return id == null ? 0L : id;
    }
}
