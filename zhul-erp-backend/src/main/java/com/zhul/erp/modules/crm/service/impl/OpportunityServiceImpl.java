package com.zhul.erp.modules.crm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.framework.security.DataScope;
import com.zhul.erp.framework.security.DataScopeResolver;
import com.zhul.erp.framework.storage.PrivateFileStorage;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.crm.constants.OpportunityConstants;
import com.zhul.erp.modules.crm.dto.ChangeStageRequest;
import com.zhul.erp.modules.crm.dto.CloseOpportunityRequest;
import com.zhul.erp.modules.crm.dto.OpportunityAttachmentRequest;
import com.zhul.erp.modules.crm.dto.OpportunityAttachmentVO;
import com.zhul.erp.modules.crm.dto.OpportunityDetailVO;
import com.zhul.erp.modules.crm.dto.OpportunityPageQuery;
import com.zhul.erp.modules.crm.dto.OpportunityStageVO;
import com.zhul.erp.modules.crm.dto.OpportunityStatRow;
import com.zhul.erp.modules.crm.dto.OpportunityStatsVO;
import com.zhul.erp.modules.crm.dto.OpportunitySummaryVO;
import com.zhul.erp.modules.crm.dto.OpportunityUploadVO;
import com.zhul.erp.modules.crm.dto.OpportunityVO;
import com.zhul.erp.modules.crm.dto.RegisterOpportunityRequest;
import com.zhul.erp.modules.crm.dto.StageLogVO;
import com.zhul.erp.modules.crm.dto.UpdateOpportunityRequest;
import com.zhul.erp.modules.crm.entity.OpportunityAttachmentDO;
import com.zhul.erp.modules.crm.entity.OpportunityDO;
import com.zhul.erp.modules.crm.entity.OpportunityStageDO;
import com.zhul.erp.modules.crm.entity.OpportunityStageLogDO;
import com.zhul.erp.modules.crm.repository.OpportunityAttachmentMapper;
import com.zhul.erp.modules.crm.repository.OpportunityMapper;
import com.zhul.erp.modules.crm.repository.OpportunityStageLogMapper;
import com.zhul.erp.modules.crm.service.OpportunityService;
import com.zhul.erp.modules.crm.support.OpportunityStageRules;
import com.zhul.erp.modules.masterdata.constants.CustomerConstants;
import com.zhul.erp.modules.masterdata.dto.CustomerLeadCommand;
import com.zhul.erp.modules.masterdata.entity.CustomerDO;
import com.zhul.erp.modules.masterdata.repository.CustomerMapper;
import com.zhul.erp.modules.masterdata.service.CustomerService;
import com.zhul.erp.modules.system.entity.UserBasicDO;
import com.zhul.erp.modules.system.repository.UserBasicMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OpportunityServiceImpl implements OpportunityService {

    private static final DateTimeFormatter CODE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    /** 关键词先匹配客户，最多取这么多个客户再过滤商机 */
    private static final int KEYWORD_CUSTOMER_LIMIT = 500;

    private final OpportunityMapper opportunityMapper;
    private final OpportunityStageLogMapper logMapper;
    private final OpportunityAttachmentMapper attachmentMapper;
    private final OpportunityStageRules stageRules;
    private final CustomerService customerService;
    private final CustomerMapper customerMapper;
    private final UserBasicMapper userBasicMapper;
    private final DataScopeResolver dataScopeResolver;
    private final PrivateFileStorage fileStorage;

    // ---------------------------------------------------------------- 阶段配置

    @Override
    public List<OpportunityStageVO> stages() {
        return stageRules.load().byCode().values().stream().map(s -> {
            OpportunityStageVO vo = new OpportunityStageVO();
            vo.setCode(s.getCode());
            vo.setName(s.getName());
            vo.setCategory(s.getCategory());
            vo.setCountsAsValid(Objects.equals(s.getCountsAsValid(), 1));
            vo.setSortOrder(s.getSortOrder());
            return vo;
        }).toList();
    }

    // ---------------------------------------------------------------- 登记 / 编辑

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long register(RegisterOpportunityRequest req) {
        int tenantId = currentTenantId();
        DataScope scope = dataScopeResolver.current();
        OpportunityStageRules.Stages stages = stageRules.load();
        // 查重在客户服务里做（客户是查重对象）；命中时直接抛 CUSTOMER_DUPLICATE
        Long customerId = customerService.createLead(new CustomerLeadCommand(req.getCustomerName(),
                req.getContactName().trim(), req.getCountry(), req.getSourceChannel(), req.getEmail(),
                req.getWhatsapp(), req.getPhone(), req.getWebsite()));

        OpportunityDO o = new OpportunityDO();
        o.setTenantId(tenantId);
        o.setOpportunityCode(nextCode(tenantId));
        o.setCustomerId(customerId);
        o.setSourceChannel(req.getSourceChannel());
        o.setFirstContactDate(req.getFirstContactDate());
        o.setOwnerId(scope.selfId() != null ? scope.selfId() : 0L);
        o.setStageCode(stages.first());
        o.setStageChangedAt(LocalDateTime.now());
        o.setReopenStageCode("");
        o.setCloseReason(0);
        o.setCloseNote("");
        o.setReachedValid(stages.countsAsValid(stages.first()) ? 1 : 0);
        o.setDemandSummary(text(req.getDemandSummary()));
        opportunityMapper.insert(o);
        log(o, "", o.getStageCode(), OpportunityConstants.ACTION_REGISTER, 0, "");
        if (req.getAttachments() != null) {
            mergeAttachments(o, req.getAttachments());
        }
        return o.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, UpdateOpportunityRequest req) {
        OpportunityDO o = getInScope(id);
        o.setSourceChannel(req.getSourceChannel());
        o.setFirstContactDate(req.getFirstContactDate());
        o.setDemandSummary(text(req.getDemandSummary()));
        opportunityMapper.updateById(o);
        if (req.getAttachments() != null) {
            mergeAttachments(o, req.getAttachments());
        }
    }

    // ---------------------------------------------------------------- 阶段流转

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStage(Long id, ChangeStageRequest req) {
        OpportunityDO o = getInScope(id);
        OpportunityStageRules.Stages stages = stageRules.load();
        String to = req.getToStage().trim();
        stages.checkChange(o.getStageCode(), to);
        String from = o.getStageCode();
        moveTo(o, to, stages);
        opportunityMapper.updateById(o);
        log(o, from, to, OpportunityConstants.ACTION_CHANGE, 0, text(req.getNote()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void close(Long id, CloseOpportunityRequest req) {
        OpportunityDO o = getInScope(id);
        OpportunityStageRules.Stages stages = stageRules.load();
        String result = req.getResult().trim();
        stages.checkClose(o.getStageCode(), result, req.getReason());
        String from = o.getStageCode();
        int reason = stages.get(result).getCategory() == OpportunityConstants.CATEGORY_WON || req.getReason() == null
                ? 0 : req.getReason();
        o.setReopenStageCode(from);
        o.setCloseReason(reason);
        o.setCloseNote(text(req.getNote()));
        o.setClosedAt(LocalDateTime.now());
        moveTo(o, result, stages);
        opportunityMapper.updateById(o);
        log(o, from, result, OpportunityConstants.ACTION_CLOSE, reason, o.getCloseNote());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reopen(Long id) {
        OpportunityDO o = getInScope(id);
        OpportunityStageRules.Stages stages = stageRules.load();
        if (stages.isActive(o.getStageCode())) {
            throw new BizException("商机还在进行中，不需要重新打开");
        }
        String from = o.getStageCode();
        String to = stages.isActive(o.getReopenStageCode()) ? o.getReopenStageCode() : stages.first();
        o.setReopenStageCode("");
        o.setCloseReason(0);
        o.setCloseNote("");
        o.setClosedAt(null);
        moveTo(o, to, stages);
        opportunityMapper.updateById(o);
        log(o, from, to, OpportunityConstants.ACTION_REOPEN, 0, "");
    }

    /** 切换阶段；进入计为有效的阶段时记下「曾有效」，之后不会再清掉 */
    private static void moveTo(OpportunityDO o, String to, OpportunityStageRules.Stages stages) {
        o.setStageCode(to);
        o.setStageChangedAt(LocalDateTime.now());
        if (stages.countsAsValid(to)) {
            o.setReachedValid(1);
        }
    }

    private void log(OpportunityDO o, String from, String to, int action, int reason, String note) {
        OpportunityStageLogDO row = new OpportunityStageLogDO();
        row.setTenantId(o.getTenantId());
        row.setOpportunityId(o.getId());
        row.setFromStage(from);
        row.setToStage(to);
        row.setAction(action);
        row.setReason(reason);
        row.setNote(note);
        logMapper.insert(row);
    }

    // ---------------------------------------------------------------- 查询

    @Override
    public PageResult<OpportunityVO> page(OpportunityPageQuery q) {
        LambdaQueryWrapper<OpportunityDO> w = scoped();
        OpportunityStageRules.Stages stages = stageRules.load();
        if (StringUtils.hasText(q.getKeyword())) {
            List<Long> customerIds = customerIdsMatching(q.getKeyword().trim());
            if (customerIds.isEmpty()) {
                return PageResult.of(0L, List.of());
            }
            w.in(OpportunityDO::getCustomerId, customerIds);
        }
        w.eq(q.getSourceChannel() != null, OpportunityDO::getSourceChannel, q.getSourceChannel())
                .eq(q.getOwnerId() != null, OpportunityDO::getOwnerId, q.getOwnerId())
                .ge(q.getFrom() != null, OpportunityDO::getFirstContactDate, q.getFrom())
                .le(q.getTo() != null, OpportunityDO::getFirstContactDate, q.getTo());
        if (StringUtils.hasText(q.getStage())) {
            List<String> active = stages.byCode().values().stream()
                    .filter(s -> s.getCategory() == OpportunityConstants.CATEGORY_ACTIVE).map(OpportunityStageDO::getCode).toList();
            switch (q.getStage()) {
                case "ACTIVE" -> w.in(OpportunityDO::getStageCode, active);
                case "CLOSED" -> w.notIn(OpportunityDO::getStageCode, active);
                default -> w.eq(OpportunityDO::getStageCode, q.getStage());
            }
        }
        w.orderByDesc(OpportunityDO::getFirstContactDate).orderByDesc(OpportunityDO::getId);
        Page<OpportunityDO> page = opportunityMapper.selectPage(new Page<>(q.getPage(), q.getPageSize()), w);
        return PageResult.of(page.getTotal(), toVos(page.getRecords(), stages));
    }

    @Override
    public OpportunitySummaryVO summary() {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(DayOfWeek.MONDAY);
        LocalDate monthStart = today.withDayOfMonth(1);
        OpportunityStageRules.Stages stages = stageRules.load();
        OpportunitySummaryVO vo = new OpportunitySummaryVO();
        vo.setTodayNew(count(scoped().eq(OpportunityDO::getFirstContactDate, today)));
        vo.setTodayInvalid(count(scoped().eq(OpportunityDO::getFirstContactDate, today)
                .eq(OpportunityDO::getStageCode, OpportunityConstants.STAGE_INVALID)));
        vo.setWeekNew(count(scoped().between(OpportunityDO::getFirstContactDate, weekStart, today)));
        vo.setLastWeekNew(count(scoped().between(OpportunityDO::getFirstContactDate, weekStart.minusWeeks(1),
                weekStart.minusDays(1))));
        String first = stages.first();
        vo.setFirstStageCount(count(scoped().eq(OpportunityDO::getStageCode, first)));
        vo.setFirstStageStale(count(scoped().eq(OpportunityDO::getStageCode, first)
                .lt(OpportunityDO::getStageChangedAt, LocalDateTime.now().minusDays(OpportunityConstants.STALE_DAYS))));
        vo.setMonthNew(count(scoped().between(OpportunityDO::getFirstContactDate, monthStart, today)));
        vo.setMonthValid(count(scoped().between(OpportunityDO::getFirstContactDate, monthStart, today)
                .eq(OpportunityDO::getReachedValid, 1)));
        return vo;
    }

    @Override
    public OpportunityDetailVO detail(Long id) {
        OpportunityDO o = getInScope(id);
        OpportunityStageRules.Stages stages = stageRules.load();
        CustomerDO c = customerMapper.selectById(o.getCustomerId());
        OpportunityDetailVO vo = new OpportunityDetailVO();
        fill(vo, o, c, ownerNames(List.of(o.getOwnerId())), stages);
        if (c != null) {
            vo.setCustomerCode(c.getCustomerCode());
            vo.setContactName(c.getContactName());
            vo.setEmail(c.getContactEmail());
            vo.setWhatsapp(c.getWhatsapp());
            vo.setPhone(c.getContactPhone());
            vo.setWebsite(c.getWebsite());
        }
        vo.setReopenStageCode(o.getReopenStageCode());
        vo.setCloseReason(o.getCloseReason());
        vo.setCloseReasonLabel(OpportunityStageRules.Stages.reasonLabel(o.getCloseReason()));
        vo.setCloseNote(o.getCloseNote());
        vo.setAttachments(activeAttachments(o.getId()).stream().map(OpportunityServiceImpl::toAttachmentVo).toList());
        vo.setStageLogs(logMapper.selectList(new LambdaQueryWrapper<OpportunityStageLogDO>()
                        .eq(OpportunityStageLogDO::getOpportunityId, o.getId())
                        .orderByDesc(OpportunityStageLogDO::getId))
                .stream().map(l -> toLogVo(l, stages)).toList());
        vo.setInquiries(opportunityMapper.selectLinkedInquiries(o.getTenantId(), o.getId()));
        return vo;
    }

    @Override
    public OpportunityStatsVO stats(LocalDate from, LocalDate to, String groupBy) {
        if (from == null || to == null || from.isAfter(to)) {
            throw new BizException("请选择正确的日期范围");
        }
        if (ChronoUnit.DAYS.between(from, to) >= OpportunityConstants.STATS_MAX_DAYS) {
            throw new BizException("统计范围最长 " + OpportunityConstants.STATS_MAX_DAYS + " 天");
        }
        LambdaQueryWrapper<OpportunityDO> w = scoped().between(OpportunityDO::getFirstContactDate, from, to);
        String by = groupBy == null ? "channel" : groupBy;
        List<OpportunityStatRow> rows;
        Function<String, String> labeler;
        switch (by) {
            case "channel" -> {
                rows = opportunityMapper.statsByChannel(w);
                labeler = k -> CustomerConstants.SOURCE_LABELS.getOrDefault(Integer.valueOf(k), "未设置");
            }
            case "owner" -> {
                rows = opportunityMapper.statsByOwner(w);
                Map<Long, String> names = ownerNames(rows.stream().map(r -> Long.valueOf(r.getGroupKey())).toList());
                labeler = k -> names.getOrDefault(Long.valueOf(k), "未分配");
            }
            case "date" -> {
                rows = opportunityMapper.statsByDate(w);
                labeler = k -> k;
            }
            default -> throw new BizException("不支持的分组方式");
        }
        OpportunityStatsVO vo = new OpportunityStatsVO();
        vo.setGroupBy(by);
        OpportunityStatsVO.Row sum = new OpportunityStatsVO.Row();
        sum.setKey("total");
        sum.setLabel("合计");
        List<OpportunityStatsVO.Row> out = new ArrayList<>(rows.size());
        for (OpportunityStatRow r : rows) {
            OpportunityStatsVO.Row row = new OpportunityStatsVO.Row();
            row.setKey(r.getGroupKey());
            row.setLabel(labeler.apply(r.getGroupKey()));
            row.setTotal(nz(r.getTotal()));
            row.setInvalid(nz(r.getInvalid()));
            row.setValid(nz(r.getValid()));
            row.setWon(nz(r.getWon()));
            row.setLost(nz(r.getLost()));
            out.add(row);
            sum.setTotal(sum.getTotal() + row.getTotal());
            sum.setInvalid(sum.getInvalid() + row.getInvalid());
            sum.setValid(sum.getValid() + row.getValid());
            sum.setWon(sum.getWon() + row.getWon());
            sum.setLost(sum.getLost() + row.getLost());
        }
        // 日期升序；渠道、业务员按新增数从多到少
        out.sort("date".equals(by) ? Comparator.comparing(OpportunityStatsVO.Row::getKey)
                : Comparator.comparingLong(OpportunityStatsVO.Row::getTotal).reversed());
        vo.setRows(out);
        vo.setSummary(sum);
        return vo;
    }

    // ---------------------------------------------------------------- 附件

    @Override
    public OpportunityUploadVO uploadAttachment(MultipartFile file) {
        PrivateFileStorage.StoredFile f = fileStorage.store(OpportunityConstants.ATTACHMENT_MODULE, currentTenantId(), file,
                OpportunityConstants.ATTACHMENT_EXTS, OpportunityConstants.ATTACHMENT_MAX_BYTES,
                OpportunityConstants.ATTACHMENT_TYPE_MESSAGE);
        OpportunityUploadVO vo = new OpportunityUploadVO();
        vo.setFileKey(f.fileKey());
        vo.setFileName(f.fileName());
        vo.setFileSize(f.fileSize());
        vo.setContentType(f.contentType());
        return vo;
    }

    @Override
    public AttachmentFile attachmentFile(Long id, Long attachmentId) {
        OpportunityDO o = getInScope(id);
        OpportunityAttachmentDO a = attachmentMapper.selectById(attachmentId);
        if (a == null || a.getDeletedAt() != null || !Objects.equals(a.getOpportunityId(), o.getId())) {
            throw new BizException("附件不存在");
        }
        Path path = fileStorage.resolveOwned(OpportunityConstants.ATTACHMENT_MODULE, a.getFileKey(), o.getTenantId());
        return new AttachmentFile(path, a.getFileName(), a.getContentType());
    }

    /** 按 id 合并：带 id 的保留，不带的按上传返回的 fileKey 新增，未出现的软删除 */
    private void mergeAttachments(OpportunityDO o, List<OpportunityAttachmentRequest> reqs) {
        if (reqs.size() > OpportunityConstants.MAX_ATTACHMENTS) {
            throw new BizException("每条商机最多 " + OpportunityConstants.MAX_ATTACHMENTS + " 个附件");
        }
        Map<Long, OpportunityAttachmentDO> existing = new HashMap<>(16);
        for (OpportunityAttachmentDO a : activeAttachments(o.getId())) {
            existing.put(a.getId(), a);
        }
        Set<Long> kept = new HashSet<>(16);
        for (OpportunityAttachmentRequest req : reqs) {
            if (req.getId() != null) {
                if (!existing.containsKey(req.getId()) || !kept.add(req.getId())) {
                    throw new BizException("附件不存在或已被删除，请刷新后重试");
                }
                continue;
            }
            Path file = fileStorage.resolveOwned(OpportunityConstants.ATTACHMENT_MODULE, req.getFileKey(), o.getTenantId());
            OpportunityAttachmentDO a = new OpportunityAttachmentDO();
            a.setTenantId(o.getTenantId());
            a.setOpportunityId(o.getId());
            a.setFileKey(req.getFileKey());
            a.setFileName(PrivateFileStorage.cleanFileName(req.getFileName(), PrivateFileStorage.extOf(req.getFileKey())));
            a.setContentType(PrivateFileStorage.contentTypeOf(req.getFileKey()));
            try {
                a.setFileSize(Files.size(file));
            } catch (IOException e) {
                throw new UncheckedIOException("读取附件失败", e);
            }
            attachmentMapper.insert(a);
        }
        LocalDateTime now = LocalDateTime.now();
        for (OpportunityAttachmentDO a : existing.values()) {
            if (!kept.contains(a.getId())) {
                a.setDeletedAt(now);
                attachmentMapper.updateById(a);
            }
        }
    }

    private List<OpportunityAttachmentDO> activeAttachments(Long opportunityId) {
        return attachmentMapper.selectList(new LambdaQueryWrapper<OpportunityAttachmentDO>()
                .eq(OpportunityAttachmentDO::getOpportunityId, opportunityId)
                .isNull(OpportunityAttachmentDO::getDeletedAt)
                .orderByAsc(OpportunityAttachmentDO::getId));
    }

    // ---------------------------------------------------------------- 询盘关联

    @Override
    public Long customerIdOf(Long opportunityId) {
        return getInScope(opportunityId).getCustomerId();
    }

    @Override
    public OpportunityVO findVisible(Long id) {
        OpportunityDO o = id == null ? null : opportunityMapper.selectById(id);
        if (o == null || o.getDeletedAt() != null || !Objects.equals(o.getTenantId(), currentTenantId())
                || !dataScopeResolver.current().canSee(o.getOwnerId())) {
            return null;
        }
        return toVos(List.of(o), stageRules.load()).get(0);
    }

    // ---------------------------------------------------------------- 工具

    /** 本租户、未删除、数据权限内的商机，否则抛「商机不存在」 */
    private OpportunityDO getInScope(Long id) {
        OpportunityDO o = id == null ? null : opportunityMapper.selectById(id);
        if (o == null || o.getDeletedAt() != null || !Objects.equals(o.getTenantId(), currentTenantId())
                || !dataScopeResolver.current().canSee(o.getOwnerId())) {
            throw new BizException("商机不存在");
        }
        return o;
    }

    /** 本租户、未删除、数据权限内 */
    private LambdaQueryWrapper<OpportunityDO> scoped() {
        LambdaQueryWrapper<OpportunityDO> w = new LambdaQueryWrapper<OpportunityDO>()
                .eq(OpportunityDO::getTenantId, currentTenantId())
                .isNull(OpportunityDO::getDeletedAt);
        return dataScopeResolver.current().apply(w, OpportunityDO::getOwnerId);
    }

    private long count(LambdaQueryWrapper<OpportunityDO> w) {
        Long n = opportunityMapper.selectCount(w);
        return n == null ? 0 : n;
    }

    private List<Long> customerIdsMatching(String kw) {
        return customerMapper.selectList(new LambdaQueryWrapper<CustomerDO>()
                        .select(CustomerDO::getId)
                        .eq(CustomerDO::getTenantId, currentTenantId())
                        .isNull(CustomerDO::getDeletedAt)
                        .and(x -> x.like(CustomerDO::getName, kw).or().like(CustomerDO::getContactName, kw)
                                .or().like(CustomerDO::getContactEmail, kw).or().like(CustomerDO::getWhatsapp, kw))
                        .last("LIMIT " + KEYWORD_CUSTOMER_LIMIT))
                .stream().map(CustomerDO::getId).toList();
    }

    private String nextCode(int tenantId) {
        String prefix = OpportunityConstants.CODE_PREFIX + LocalDate.now().format(CODE_DATE);
        Integer max = opportunityMapper.selectMaxSequenceByPrefix(tenantId, prefix);
        return prefix + String.format("%03d", (max == null ? 0 : max) + 1);
    }

    private List<OpportunityVO> toVos(List<OpportunityDO> list, OpportunityStageRules.Stages stages) {
        if (list.isEmpty()) {
            return List.of();
        }
        Map<Long, CustomerDO> customers = customerMapper.selectBatchIds(
                list.stream().map(OpportunityDO::getCustomerId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(CustomerDO::getId, Function.identity()));
        Map<Long, String> owners = ownerNames(list.stream().map(OpportunityDO::getOwnerId).toList());
        List<OpportunityVO> out = new ArrayList<>(list.size());
        for (OpportunityDO o : list) {
            OpportunityVO vo = new OpportunityVO();
            fill(vo, o, customers.get(o.getCustomerId()), owners, stages);
            out.add(vo);
        }
        return out;
    }

    private static void fill(OpportunityVO vo, OpportunityDO o, CustomerDO c, Map<Long, String> owners,
                             OpportunityStageRules.Stages stages) {
        vo.setId(o.getId());
        vo.setOpportunityCode(o.getOpportunityCode());
        vo.setCustomerId(o.getCustomerId());
        if (c != null) {
            boolean missing = !StringUtils.hasText(c.getName());
            vo.setCustomerName(missing ? c.getContactName() : c.getName());
            vo.setCustomerNameMissing(missing);
            vo.setCountry(c.getCountry());
        }
        vo.setSourceChannel(o.getSourceChannel());
        vo.setFirstContactDate(o.getFirstContactDate());
        vo.setOwnerId(o.getOwnerId());
        vo.setOwnerName(owners.get(o.getOwnerId()));
        vo.setStageCode(o.getStageCode());
        OpportunityStageDO s = stages.byCode().get(o.getStageCode());
        vo.setStageName(s != null ? s.getName() : o.getStageCode());
        vo.setStageCategory(s != null ? s.getCategory() : null);
        vo.setDemandSummary(o.getDemandSummary());
        vo.setCreateTime(o.getCreateTime());
        vo.setCreateBy(o.getCreateBy());
        vo.setUpdateTime(o.getUpdateTime());
        vo.setUpdateBy(o.getUpdateBy());
    }

    private static OpportunityAttachmentVO toAttachmentVo(OpportunityAttachmentDO a) {
        OpportunityAttachmentVO vo = new OpportunityAttachmentVO();
        vo.setId(a.getId());
        vo.setFileName(a.getFileName());
        vo.setFileSize(a.getFileSize());
        vo.setContentType(a.getContentType());
        vo.setCreateBy(a.getCreateBy());
        vo.setCreateTime(a.getCreateTime());
        return vo;
    }

    private static StageLogVO toLogVo(OpportunityStageLogDO l, OpportunityStageRules.Stages stages) {
        StageLogVO vo = new StageLogVO();
        vo.setId(l.getId());
        vo.setAction(l.getAction());
        vo.setFromStage(l.getFromStage());
        vo.setFromStageName(StringUtils.hasText(l.getFromStage()) ? stages.nameOf(l.getFromStage()) : "");
        vo.setToStage(l.getToStage());
        vo.setToStageName(stages.nameOf(l.getToStage()));
        vo.setReason(l.getReason());
        vo.setReasonLabel(OpportunityStageRules.Stages.reasonLabel(l.getReason()));
        vo.setNote(l.getNote());
        vo.setOperator(l.getCreateBy());
        vo.setCreateTime(l.getCreateTime());
        return vo;
    }

    private Map<Long, String> ownerNames(Collection<Long> ownerIds) {
        Set<Integer> ids = ownerIds.stream().filter(i -> i != null && i > 0).map(Long::intValue).collect(Collectors.toSet());
        Map<Long, String> names = new HashMap<>(ids.size() * 2 + 1);
        if (!ids.isEmpty()) {
            for (UserBasicDO u : userBasicMapper.selectBatchIds(ids)) {
                names.put(u.getId().longValue(), u.getName());
            }
        }
        return names;
    }

    private static long nz(Long v) {
        return v == null ? 0 : v;
    }

    private static String text(String v) {
        return v == null ? "" : v.trim();
    }

    private int currentTenantId() {
        Integer tenantId = TenantContext.getTenantId();
        return tenantId != null ? tenantId : 0;
    }
}
