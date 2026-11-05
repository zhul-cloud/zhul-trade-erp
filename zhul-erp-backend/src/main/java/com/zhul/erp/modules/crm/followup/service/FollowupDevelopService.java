package com.zhul.erp.modules.crm.followup.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.crm.entity.OpportunityDO;
import com.zhul.erp.modules.crm.followup.constants.FollowupConstants;
import com.zhul.erp.modules.crm.followup.dto.ConvertRequest;
import com.zhul.erp.modules.crm.followup.dto.DevelopCardVO;
import com.zhul.erp.modules.crm.followup.dto.JoinDevelopRequest;
import com.zhul.erp.modules.crm.followup.dto.ProspectRequest;
import com.zhul.erp.modules.crm.followup.dto.StopDevelopRequest;
import com.zhul.erp.modules.crm.followup.entity.FollowupItemDO;
import com.zhul.erp.modules.crm.followup.entity.SopStageDO;
import com.zhul.erp.modules.crm.followup.repository.FollowupItemMapper;
import com.zhul.erp.modules.crm.followup.repository.SopStageMapper;
import com.zhul.erp.modules.crm.followup.support.DevelopCadence;
import com.zhul.erp.modules.crm.followup.support.DevelopFollowupSync;
import com.zhul.erp.modules.crm.followup.support.FollowupSettings;
import com.zhul.erp.modules.crm.followup.support.FollowupSync;
import com.zhul.erp.modules.crm.followup.support.OrderFollowupSync;
import com.zhul.erp.modules.crm.service.OpportunityService;
import com.zhul.erp.modules.masterdata.customerbackground.entity.CustomerBackgroundReportDO;
import com.zhul.erp.modules.masterdata.customerbackground.repository.CustomerBackgroundReportMapper;
import com.zhul.erp.modules.masterdata.customerprofile.entity.CustomerContactDO;
import com.zhul.erp.modules.masterdata.customerprofile.repository.CustomerContactMapper;
import com.zhul.erp.modules.masterdata.dto.CustomerLeadCommand;
import com.zhul.erp.modules.masterdata.entity.CustomerDO;
import com.zhul.erp.modules.masterdata.repository.CustomerMapper;
import com.zhul.erp.modules.masterdata.service.CustomerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 主动开发（第四期 design D2、D3、D7、D8）：新建开发对象、加入开发、重新开发、转商机、停止开发、作战卡。
 * 写操作各自一个事务：开发记录、商机、系统跟进记录与重新推导一起提交，任一步失败整体回滚。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FollowupDevelopService {

    public static final String ERROR_NOT_ELIGIBLE = "DEVELOP_NOT_ELIGIBLE";
    private static final String STATE_OPEN = "open";
    private static final String STATE_STOPPED = "stopped";
    private static final String STATE_ELIGIBLE = "eligible";
    private static final String STATE_NONE = "none";

    private final CustomerService customerService;
    private final CustomerMapper customerMapper;
    private final CustomerContactMapper contactMapper;
    private final CustomerBackgroundReportMapper reportMapper;
    private final FollowupItemMapper itemMapper;
    private final SopStageMapper sopStageMapper;
    private final DevelopFollowupSync developSync;
    private final OrderFollowupSync orderSync;
    private final FollowupSync sync;
    private final FollowupSettings settings;
    private final OpportunityService opportunityService;

    // ---------------------------------------------------------------- 加入

    /** 新建开发对象：建客户（来源「主动开发」，查重沿用登记规则，命中抛 CUSTOMER_DUPLICATE）并生成开发记录 */
    @Transactional(rollbackFor = Exception.class)
    public DevelopCardVO create(ProspectRequest req) {
        checkSource(req.getSource());
        Long customerId = customerService.createLead(new CustomerLeadCommand(req.getName().trim(), text(req.getContactName()),
                req.getCountry(), FollowupConstants.SOURCE_CHANNEL_DEVELOP, null, text(req.getEmail()), text(req.getWhatsapp()),
                "", text(req.getWebsite()), req.getBusinessUnitId()));
        CustomerDO c = customerMapper.selectById(customerId);
        FollowupItemDO row = developSync.open(c, req.getReason().trim(), req.getSource(), LocalDate.now(), orderSync.defaultActions());
        log.info("新建开发对象，customerId={}, itemId={}", customerId, row.getId());
        return card(customerId);
    }

    /** 把已有客户加入主动开发 */
    @Transactional(rollbackFor = Exception.class)
    public DevelopCardVO join(Long customerId, JoinDevelopRequest req) {
        checkSource(req.getSource());
        CustomerDO c = customerService.requireInScope(customerId);
        FollowupItemDO row = developSync.find(customerId);
        if (row != null) {
            throw notEligible(row.getClosedAt() == null ? "这个客户已在主动开发中" : "这个客户开发过，请用「重新开发」");
        }
        requireEligible(c);
        row = developSync.open(c, req.getReason().trim(), req.getSource(), LocalDate.now(), orderSync.defaultActions());
        developSync.writeSystemRecord(row, "加入主动开发");
        log.info("加入主动开发，customerId={}, itemId={}", customerId, row.getId());
        return card(customerId);
    }

    /** 重新开发：重新打开已停止的开发记录，阶段重新推导，触达次数保留 */
    @Transactional(rollbackFor = Exception.class)
    public DevelopCardVO reopen(Long customerId) {
        CustomerDO c = customerService.requireInScope(customerId);
        FollowupItemDO row = developSync.find(customerId);
        if (row == null || row.getClosedAt() == null || !Objects.equals(row.getCloseReason(), FollowupConstants.CLOSE_STOPPED)) {
            throw notEligible(row != null && row.getClosedAt() == null ? "这个客户已在主动开发中" : "只有停止开发的客户才能重新开发");
        }
        requireEligible(c);
        row = developSync.open(c, row.getProspectReason(), row.getProspectSource(), LocalDate.now(), orderSync.defaultActions());
        developSync.writeSystemRecord(row, "重新开发");
        log.info("重新开发，customerId={}, itemId={}", customerId, row.getId());
        return card(customerId);
    }

    // ---------------------------------------------------------------- 结束

    /**
     * 转商机：先结束开发记录（转商机），再为客户建商机（S1、来源主动开发），写系统跟进记录，最后重新推导，
     * 客户随即出现在商机成交。已有进行中的商机时由商机服务抛 OPPORTUNITY_ALREADY_ACTIVE，开发记录不变（整体回滚）。
     */
    @Transactional(rollbackFor = Exception.class)
    public DevelopCardVO convert(Long itemId, ConvertRequest req) {
        FollowupItemDO row = requireOpen(itemId);
        if (FollowupConstants.DEVELOP_STAGES.indexOf(row.getStageCode()) < FollowupConstants.DEVELOP_STAGES.indexOf("D4")) {
            throw new BizException("找到联系人、开始触达以后才能转商机");
        }
        String summary = StringUtils.hasText(req.getDemandSummary()) ? req.getDemandSummary().trim() : text(row.getSignalText());
        LocalDate first = req.getFirstContactDate() != null ? req.getFirstContactDate()
                : row.getFirstTouchDate() != null ? row.getFirstTouchDate() : LocalDate.now();
        // 先按「转商机」结束，避免建商机后任何重新推导把它当成「已进入商机成交」结束
        developSync.close(row, FollowupConstants.CLOSE_CONVERTED, null);
        OpportunityDO o = opportunityService.createForCustomer(row.getCustomerId(), first, summary, req.getOwnerId());
        developSync.writeSystemRecord(row, "转为商机 " + o.getOpportunityCode());
        sync.resync(row.getCustomerId(), LocalDate.now(), false);
        log.info("开发对象转商机，itemId={}, opportunityId={}", itemId, o.getId());
        return card(row.getCustomerId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void stop(Long itemId, StopDevelopRequest req) {
        FollowupItemDO row = requireOpen(itemId);
        String reason = req.getReason().trim();
        if (!FollowupConstants.STOP_REASONS.containsKey(reason)) {
            throw new BizException("停止原因不正确");
        }
        String note = text(req.getNote());
        if (FollowupConstants.STOP_OTHER.equals(reason) && note.isEmpty()) {
            throw new BizException("选「其他」时请写明原因");
        }
        row.setStopReason(reason);
        row.setStopNote(note);
        String title = "停止开发：" + FollowupConstants.STOP_REASONS.get(reason) + (note.isEmpty() ? "" : "（" + note + "）");
        developSync.close(row, FollowupConstants.CLOSE_STOPPED, title);
        log.info("停止开发，itemId={}, reason={}", itemId, reason);
    }

    // ---------------------------------------------------------------- 作战卡与客户详情

    public DevelopCardVO card(Long customerId) {
        CustomerDO c = customerService.requireInScope(customerId);
        FollowupItemDO row = developSync.find(customerId);
        DevelopCardVO vo = new DevelopCardVO();
        vo.setCustomerId(customerId);
        vo.setPotentialRating(nz(c.getPotentialRating()));
        String blocked = ineligibleReason(c);
        if (row != null && row.getClosedAt() == null) {
            vo.setState(STATE_OPEN);
        } else if (blocked != null) {
            vo.setState(STATE_NONE);
            vo.setReason(blocked);
        } else {
            vo.setState(row != null && Objects.equals(row.getCloseReason(), FollowupConstants.CLOSE_STOPPED)
                    ? STATE_STOPPED : row == null ? STATE_ELIGIBLE : STATE_NONE);
            if (STATE_NONE.equals(vo.getState())) {
                vo.setReason("这个客户已转过商机");
            }
        }
        if (row != null) {
            fill(vo, row);
        }
        vo.setReport(report(customerId));
        vo.setContacts(contacts(c));
        return vo;
    }

    private void fill(DevelopCardVO vo, FollowupItemDO row) {
        vo.setItemId(row.getId());
        vo.setStageCode(row.getStageCode());
        vo.setStageName(stageName(row.getStageCode()));
        vo.setProspectReason(row.getProspectReason());
        vo.setProspectSource(nz(row.getProspectSource()));
        vo.setProspectSourceName(FollowupConstants.PROSPECT_SOURCES.getOrDefault(nz(row.getProspectSource()), ""));
        vo.setTouchCount(nz(row.getTouchCount()));
        vo.setFirstTouchDate(row.getFirstTouchDate());
        List<Integer> seq = settings.developSequence(TenantContext.getTenantId());
        vo.setSequence(seq);
        int day = 0;
        if ("D5".equals(row.getStageCode())) {
            day = DevelopCadence.of(new DevelopCadence.Input("D5", false, null, row.getFirstTouchDate(), seq, null),
                    LocalDate.now()).day();
        }
        vo.setCurrentDay(day);
        vo.setSignalText(row.getSignalText());
        vo.setStopReason(row.getStopReason());
        vo.setStopReasonName(FollowupConstants.STOP_REASONS.getOrDefault(text(row.getStopReason()), ""));
        vo.setStopNote(row.getStopNote());
        vo.setCanConvert(row.getClosedAt() == null
                && FollowupConstants.DEVELOP_STAGES.indexOf(row.getStageCode()) >= FollowupConstants.DEVELOP_STAGES.indexOf("D4"));
    }

    private DevelopCardVO.Report report(Long customerId) {
        CustomerBackgroundReportDO r = reportMapper.selectOne(new LambdaQueryWrapper<CustomerBackgroundReportDO>()
                .select(CustomerBackgroundReportDO::getId, CustomerBackgroundReportDO::getStatus,
                        CustomerBackgroundReportDO::getPriority, CustomerBackgroundReportDO::getScore,
                        CustomerBackgroundReportDO::getKeyFinding, CustomerBackgroundReportDO::getFirstAction)
                .eq(CustomerBackgroundReportDO::getCustomerId, customerId)
                .isNull(CustomerBackgroundReportDO::getDeletedAt)
                .orderByDesc(CustomerBackgroundReportDO::getId)
                .last("LIMIT 1"));
        if (r == null) {
            return null;
        }
        DevelopCardVO.Report vo = new DevelopCardVO.Report();
        vo.setId(r.getId());
        vo.setStatus(r.getStatus());
        vo.setPriority(r.getPriority());
        vo.setScore(r.getScore());
        vo.setKeyFinding(r.getKeyFinding());
        vo.setFirstAction(r.getFirstAction());
        return vo;
    }

    private List<DevelopCardVO.Contact> contacts(CustomerDO c) {
        List<CustomerContactDO> rows = contactMapper.selectList(new LambdaQueryWrapper<CustomerContactDO>()
                .select(CustomerContactDO::getName, CustomerContactDO::getTitle, CustomerContactDO::getEmail,
                        CustomerContactDO::getWhatsapp, CustomerContactDO::getPhone, CustomerContactDO::getIsPrimary)
                .eq(CustomerContactDO::getCustomerId, c.getId())
                .isNull(CustomerContactDO::getDeletedAt));
        rows.sort(Comparator.comparing((CustomerContactDO x) -> nz(x.getIsPrimary())).reversed());
        List<DevelopCardVO.Contact> out = new ArrayList<>(rows.size());
        for (CustomerContactDO x : rows) {
            DevelopCardVO.Contact vo = new DevelopCardVO.Contact();
            vo.setName(x.getName());
            vo.setTitle(x.getTitle());
            vo.setEmail(x.getEmail());
            vo.setWhatsapp(x.getWhatsapp());
            vo.setPhone(x.getPhone());
            vo.setPrimary(nz(x.getIsPrimary()) == 1);
            out.add(vo);
        }
        return out;
    }

    private String stageName(String code) {
        SopStageDO s = sopStageMapper.selectOne(new LambdaQueryWrapper<SopStageDO>()
                .select(SopStageDO::getName)
                .eq(SopStageDO::getBoard, FollowupConstants.BOARD_DEVELOP)
                .eq(SopStageDO::getStageCode, code)
                .orderByDesc(SopStageDO::getTenantId)
                .last("LIMIT 1"));
        return s == null ? code : s.getName();
    }

    // ---------------------------------------------------------------- 校验

    /** 加入条件（design D2）：未成交、没有进行中的询盘、报价、PI、商机，也没有销售订单 */
    private String ineligibleReason(CustomerDO c) {
        if (c.getBizGrade() != null && c.getBizGrade() > 0) {
            return "成交客户在「客户经营」里跟进";
        }
        if (developSync.enteredSales(c)) {
            return "这个客户已有询盘、商机或订单，在「商机成交」里跟进";
        }
        return null;
    }

    private void requireEligible(CustomerDO c) {
        String reason = ineligibleReason(c);
        if (reason != null) {
            throw notEligible(reason);
        }
    }

    private FollowupItemDO requireOpen(Long itemId) {
        FollowupItemDO row = itemMapper.selectById(itemId);
        if (row == null || row.getDeletedAt() != null || !Objects.equals(row.getBoard(), FollowupConstants.BOARD_DEVELOP)) {
            throw new BizException("开发对象不存在");
        }
        customerService.requireInScope(row.getCustomerId());
        if (row.getClosedAt() != null) {
            throw BizException.of(FollowupConstants.ERROR_STAGE_MISMATCH, "开发对象已结束，请刷新后重试");
        }
        return row;
    }

    private static void checkSource(Integer source) {
        if (source != null && source != 0 && !FollowupConstants.PROSPECT_SOURCES.containsKey(source)) {
            throw new BizException("来源方式不正确");
        }
    }

    private static BizException notEligible(String message) {
        return BizException.of(ERROR_NOT_ELIGIBLE, message);
    }

    private static String text(String s) {
        return s == null ? "" : s.trim();
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }
}
