package com.zhul.erp.modules.crm.followup.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.security.DataScope;
import com.zhul.erp.framework.security.DataScopeResolver;
import com.zhul.erp.framework.security.PermissionChecker;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.aftersales.constants.AfterSalesConstants;
import com.zhul.erp.modules.aftersales.entity.AfterSalesDO;
import com.zhul.erp.modules.aftersales.repository.AfterSalesMapper;
import com.zhul.erp.modules.crm.followup.constants.FollowupConstants;
import com.zhul.erp.modules.crm.followup.dto.CustomerGroupVO;
import com.zhul.erp.modules.crm.followup.dto.CustomerGroupListVO;
import com.zhul.erp.modules.crm.followup.dto.CustomerFollowupVO;
import com.zhul.erp.modules.crm.followup.dto.FollowupItemVO;
import com.zhul.erp.modules.crm.followup.dto.FollowupListVO;
import com.zhul.erp.modules.crm.followup.dto.FollowupOverviewVO;
import com.zhul.erp.modules.crm.followup.dto.FollowupQuery;
import com.zhul.erp.modules.crm.followup.dto.FollowupRecordVO;
import com.zhul.erp.modules.crm.followup.dto.FollowupTodayVO;
import com.zhul.erp.modules.crm.followup.dto.NurtureRequest;
import com.zhul.erp.modules.crm.followup.dto.OrderPartsVO;
import com.zhul.erp.modules.crm.followup.dto.RecordFollowupRequest;
import com.zhul.erp.modules.crm.followup.dto.RecordResultVO;
import com.zhul.erp.modules.crm.followup.dto.SopCardVO;
import com.zhul.erp.modules.crm.followup.entity.CustomerFollowupDO;
import com.zhul.erp.modules.crm.followup.entity.CustomerFollowupItemDO;
import com.zhul.erp.modules.crm.followup.entity.FollowupItemDO;
import com.zhul.erp.modules.crm.followup.entity.SopBlockerDO;
import com.zhul.erp.modules.crm.followup.entity.SopStageDO;
import com.zhul.erp.modules.crm.followup.repository.CustomerFollowupItemMapper;
import com.zhul.erp.modules.crm.followup.repository.CustomerFollowupMapper;
import com.zhul.erp.modules.crm.followup.repository.FollowupItemFilter;
import com.zhul.erp.modules.crm.followup.repository.FollowupItemMapper;
import com.zhul.erp.modules.crm.followup.repository.SopBlockerMapper;
import com.zhul.erp.modules.crm.followup.repository.SopStageMapper;
import com.zhul.erp.modules.crm.followup.support.AccountStageRecorder;
import com.zhul.erp.modules.crm.followup.support.Cadence;
import com.zhul.erp.modules.crm.followup.support.CustomerRollup;
import com.zhul.erp.modules.crm.followup.support.DevelopStageRecorder;
import com.zhul.erp.modules.crm.followup.support.DevelopCadence;
import com.zhul.erp.modules.crm.followup.support.DevelopStageDeriver;
import com.zhul.erp.modules.masterdata.customerbackground.entity.CustomerBackgroundReportDO;
import com.zhul.erp.modules.masterdata.customerbackground.repository.CustomerBackgroundReportMapper;
import com.zhul.erp.modules.crm.followup.support.FollowupSettings;
import com.zhul.erp.modules.crm.followup.support.FollowupSync;
import com.zhul.erp.modules.crm.followup.support.FulfilDeriver;
import com.zhul.erp.modules.crm.followup.support.OrderFacts;
import com.zhul.erp.modules.crm.followup.support.OrderFollowupSync;
import com.zhul.erp.modules.crm.followup.support.OrderStageRecorder;
import com.zhul.erp.modules.crm.service.OpportunityService;
import com.zhul.erp.modules.crm.support.OpportunityStageRules;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.masterdata.customerprofile.service.CustomerProfileService;
import com.zhul.erp.modules.masterdata.customerprofile.support.CustomerGradeSettings;
import com.zhul.erp.modules.masterdata.entity.CustomerDO;
import com.zhul.erp.modules.masterdata.repository.CustomerMapper;
import com.zhul.erp.modules.masterdata.service.CustomerService;
import com.zhul.erp.modules.quotation.entity.QuotationDO;
import com.zhul.erp.modules.quotation.repository.QuotationMapper;
import com.zhul.erp.modules.sales.entity.ProformaInvoiceDO;
import com.zhul.erp.modules.sales.repository.ProformaInvoiceMapper;
import com.zhul.erp.modules.system.entity.UserBasicDO;
import com.zhul.erp.modules.system.repository.UserBasicMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 客户跟进（openspec change customer-followup，spec crm/customer-followup）：链路计数、跟进列表、今日待办、
 * 延后 / 转培育 / 激活、记一次跟进、SOP 作战卡、客户的跟进时间线。
 * <p>
 * 可见范围：业务员只看自己负责的跟进对象；有「查看团队」权限的按数据权限看。跟进记录跟随客户的可见范围。
 * 事务边界：写操作各自一个事务；记一次跟进按 design D9 在一个事务内完成，任一步失败整体回滚。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FollowupService {

    private static final String SCOPE_ME = "me";
    private static final String SCOPE_ALL = "all";
    /** 今日待办每组最多列出的客户数 */
    private static final int TODAY_LIMIT = 200;
    /** 今日待办最多取的到期单据数 */
    private static final int TODAY_ITEMS_LIMIT = 2000;
    private static final int RECORD_PAGE = 5;
    private static final int RECORD_PAGE_MAX = 50;

    private final FollowupItemMapper itemMapper;
    private final CustomerFollowupMapper recordMapper;
    private final CustomerFollowupItemMapper recordItemMapper;
    private final SopStageMapper sopStageMapper;
    private final SopBlockerMapper sopBlockerMapper;
    private final AfterSalesMapper afterSalesMapper;
    private final CustomerMapper customerMapper;
    private final QuotationMapper quotationMapper;
    private final ProformaInvoiceMapper piMapper;
    private final UserBasicMapper userBasicMapper;
    private final CustomerService customerService;
    private final CustomerProfileService profileService;
    private final OpportunityService opportunityService;
    private final OpportunityStageRules stageRules;
    private final FollowupSync sync;
    private final OrderStageRecorder orderStageRecorder;
    private final OrderFollowupSync orderSync;
    private final FollowupSettings settings;
    private final CustomerGradeSettings gradeSettings;
    private final DataScopeResolver dataScopeResolver;
    private final PermissionChecker perm;
    private final CurrentUserResolver currentUser;
    private final ObjectMapper objectMapper;
    private final CustomerBackgroundReportMapper reportMapper;

    // ---------------------------------------------------------------- 链路卡片

    public FollowupOverviewVO overview(String ownerScope) {
        Collection<Long> owners = owners(ownerScope);
        LocalDate today = LocalDate.now();
        OpportunityStageRules.Stages stages = stageRules.load();
        List<FollowupItemDO> rows = owners != null && owners.isEmpty() ? List.of()
                : itemMapper.selectList(new LambdaQueryWrapper<FollowupItemDO>()
                .select(FollowupItemDO::getId, FollowupItemDO::getCustomerId, FollowupItemDO::getBoard,
                        FollowupItemDO::getStageGroup, FollowupItemDO::getStageCode, FollowupItemDO::getNextActionDate,
                        FollowupItemDO::getNurtureUntil, FollowupItemDO::getNextActionSource, FollowupItemDO::getExceptionFlags,
                        FollowupItemDO::getDormantSince, FollowupItemDO::getLastFollowupAt)
                .isNull(FollowupItemDO::getClosedAt)
                .isNull(FollowupItemDO::getDeletedAt)
                .in(owners != null, FollowupItemDO::getOwnerId, owners));
        FollowupOverviewVO vo = new FollowupOverviewVO();
        List<FollowupItemDO> active = rows.stream().filter(r -> r.getNurtureUntil() == null).toList();
        // 今日待办按客户计：有逾期单据的算逾期，否则有今天到期且今天还没跟进的算今天（不含跟着售后单走的 A3、A4）
        int[] todayCounts = new int[4];
        CustomerRollup.byCustomer(active).values().forEach(l -> todayCounts[CustomerRollup.classify(l, today)]++);
        vo.setTodayOverdue(todayCounts[CustomerRollup.OVERDUE]);
        vo.setTodayDue(todayCounts[CustomerRollup.TODAY]);
        Map<String, String> orderStages = orderStageNames();
        for (int b = 1; b <= FollowupConstants.BOARD_NAMES.size(); b++) {
            int board = b;
            FollowupOverviewVO.Board card = new FollowupOverviewVO.Board();
            card.setBoard(board);
            card.setStep(board);
            card.setName(FollowupConstants.BOARD_NAMES.get(board - 1));
            card.setAvailable(board >= FollowupConstants.BOARD_DEVELOP && board <= FollowupConstants.BOARD_ACCOUNT);
            List<FollowupItemDO> in = active.stream().filter(r -> r.getBoard() == board).toList();
            card.setCustomers((int) in.stream().map(FollowupItemDO::getCustomerId).distinct().count());
            card.setTotal(in.size());
            // 逾期、培育中都按客户计
            card.setOverdue((int) in.stream().filter(r -> CustomerRollup.classify(r, today) == CustomerRollup.OVERDUE)
                    .map(FollowupItemDO::getCustomerId).distinct().count());
            card.setNurture((int) rows.stream().filter(r -> r.getBoard() == board && r.getNurtureUntil() != null)
                    .map(FollowupItemDO::getCustomerId).distinct().count());
            if (board == FollowupConstants.BOARD_DEVELOP) {
                card.setGroups(groups(in, DEVELOP_GROUPS, c -> orderStages.getOrDefault(c, c)));
            } else if (board == FollowupConstants.BOARD_SALES) {
                card.setGroups(groups(in, SALES_GROUPS, stages::nameOf));
            } else if (board == FollowupConstants.BOARD_FULFIL) {
                card.setGroups(groups(in, FULFIL_GROUPS, c -> orderStages.getOrDefault(c, c)));
                card.setExceptions((int) in.stream().filter(r -> nz(r.getExceptionFlags()) > 0)
                    .map(FollowupItemDO::getCustomerId).distinct().count());
            } else if (board == FollowupConstants.BOARD_AFTER_SALES) {
                card.setGroups(groups(in, AFTER_SALES_GROUPS, c -> orderStages.getOrDefault(c, c)));
            } else if (board == FollowupConstants.BOARD_ACCOUNT) {
                card.setGroups(groups(in, ACCOUNT_GROUPS, c -> orderStages.getOrDefault(c, c)));
                card.setDormant((int) in.stream().filter(r -> r.getDormantSince() != null)
                        .map(FollowupItemDO::getCustomerId).distinct().count());
            }
            vo.getBoards().add(card);
        }
        return vo;
    }

    /** 主动开发：找客户（D1–D2）、找人触达（D3–D5）、挖需求（D6–D7） */
    private static final Map<Integer, List<String>> DEVELOP_GROUPS = orderedGroups(
            FollowupConstants.GROUP_FIND, List.of("D1", "D2"), FollowupConstants.GROUP_REACH, List.of("D3", "D4", "D5"),
            FollowupConstants.GROUP_DISCOVER, List.of("D6", "D7"));
    private static final Map<Integer, List<String>> SALES_GROUPS = orderedGroups(
            FollowupConstants.GROUP_BEFORE_QUOTE, List.of("S1", "S2", "S3"),
            FollowupConstants.GROUP_AFTER_QUOTE, List.of(FollowupConstants.S4, FollowupConstants.S5, FollowupConstants.S6),
            FollowupConstants.GROUP_AFTER_PI, List.of(FollowupConstants.S7));
    /** 订单履约：采购中（F1–F3）、验收发货（F4–F5）、在途（F6） */
    private static final Map<Integer, List<String>> FULFIL_GROUPS = orderedGroups(
            FollowupConstants.GROUP_PURCHASING, List.of("F1", "F2", "F3"),
            FollowupConstants.GROUP_SHIPPING, List.of("F4", "F5"),
            FollowupConstants.GROUP_IN_TRANSIT, List.of("F6"));
    /** 售后评价：签收（A1）、使用（A2）、问题（A3–A4）、满意评价（A5–A6） */
    private static final Map<Integer, List<String>> AFTER_SALES_GROUPS = orderedGroups(
            FollowupConstants.GROUP_SIGN, List.of("A1"), FollowupConstants.GROUP_USE, List.of("A2"),
            FollowupConstants.GROUP_ISSUE, List.of("A3", "A4"), FollowupConstants.GROUP_REVIEW, List.of("A5", "A6"));

    /** 客户经营：需求挖掘（G4）、复购机会（G5）、交叉销售（G6）、长期合作（G7） */
    private static final Map<Integer, List<String>> ACCOUNT_GROUPS = orderedGroups(
            FollowupConstants.GROUP_NEED, List.of(FollowupConstants.G4), FollowupConstants.GROUP_REPEAT, List.of(FollowupConstants.G5),
            FollowupConstants.GROUP_CROSS, List.of(FollowupConstants.G6), FollowupConstants.GROUP_LONG_TERM, List.of(FollowupConstants.G7));

    private static Map<Integer, List<String>> orderedGroups(Object... kv) {
        Map<Integer, List<String>> out = new LinkedHashMap<>(8);
        for (int i = 0; i < kv.length; i += 2) {
            @SuppressWarnings("unchecked")
            List<String> codes = (List<String>) kv[i + 1];
            out.put((Integer) kv[i], codes);
        }
        return out;
    }

    /** 分组和阶段的数字按客户计：每个客户只算在名下单据最靠后的阶段，各分组之和等于板块客户数 */
    private static List<FollowupOverviewVO.Group> groups(List<FollowupItemDO> in, Map<Integer, List<String>> byGroup,
                                                         Function<String, String> nameOf) {
        Map<String, Integer> byStage = new HashMap<>(16);
        CustomerRollup.byCustomer(in).values().forEach(l -> byStage.merge(CustomerRollup.furthestStage(l), 1, Integer::sum));
        List<FollowupOverviewVO.Group> out = new ArrayList<>(byGroup.size());
        for (Map.Entry<Integer, List<String>> e : byGroup.entrySet()) {
            FollowupOverviewVO.Group g = new FollowupOverviewVO.Group();
            g.setGroup(e.getKey());
            g.setName(FollowupConstants.GROUP_NAMES.get(e.getKey()));
            int count = 0;
            for (String code : e.getValue()) {
                FollowupOverviewVO.Stage st = new FollowupOverviewVO.Stage();
                st.setCode(code);
                st.setName(nameOf.apply(code));
                st.setCount(byStage.getOrDefault(code, 0));
                count += st.getCount();
                g.getStages().add(st);
            }
            g.setCount(count);
            out.add(g);
        }
        return out;
    }

    // ---------------------------------------------------------------- 列表

    public FollowupListVO items(FollowupQuery q) {
        Collection<Long> owners = owners(q.getOwnerScope());
        FollowupListVO vo = new FollowupListVO();
        if (owners != null && owners.isEmpty()) {
            vo.setTotal(0L);
            return vo;
        }
        int size = q.getPageSize() == null || q.getPageSize() <= 0 ? FollowupConstants.PAGE_SIZE
                : Math.min(q.getPageSize(), 100);
        int page = q.getPage() == null || q.getPage() <= 0 ? 1 : q.getPage();
        LocalDate today = LocalDate.now();
        FollowupItemFilter f = filter(owners, today);
        f.setBoard(q.getBoard() == null ? FollowupConstants.BOARD_SALES : q.getBoard());
        f.setNurture(Boolean.TRUE.equals(q.getNurture()));
        if (!Boolean.TRUE.equals(q.getNurture())) {
            f.setGroup(q.getGroup());
            f.setStage(StringUtils.hasText(q.getStage()) ? q.getStage() : null);
        }
        f.setBlocker(StringUtils.hasText(q.getBlocker()) ? q.getBlocker() : null);
        f.setExceptionOnly(Boolean.TRUE.equals(q.getException()) && f.getBoard() == FollowupConstants.BOARD_FULFIL);
        f.setDormantOnly(Boolean.TRUE.equals(q.getDormant()) && f.getBoard() == FollowupConstants.BOARD_ACCOUNT);
        f.setCountry(StringUtils.hasText(q.getCountry()) ? q.getCountry().trim() : null);
        // LIKE 的通配符按普通字符查
        f.setKeyword(StringUtils.hasText(q.getKeyword())
                ? q.getKeyword().trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") : null);
        vo.setTotal(itemMapper.countBy(f));
        if (Objects.equals(f.getGroup(), FollowupConstants.GROUP_AFTER_PI)
                || FollowupConstants.S7.equals(f.getStage())) {
            for (Map<String, Object> row : itemMapper.unpaidByCurrency(f)) {
                FollowupListVO.CurrencyAmount a = new FollowupListVO.CurrencyAmount();
                a.setCurrencyCode(String.valueOf(row.get("currencyCode")));
                a.setAmount(row.get("amount") instanceof BigDecimal d ? d : BigDecimal.ZERO);
                vo.getUnpaid().add(a);
            }
        }
        f.setOffset((page - 1) * size);
        f.setSize(size);
        List<Long> ids = f.getBoard() == FollowupConstants.BOARD_DEVELOP ? itemMapper.rankedDevelopIds(f) : itemMapper.rankedIds(f);
        vo.setRecords(toVos(byRank(ids), today, owners == null || owners.size() > 1));
        return vo;
    }

    // ---------------------------------------------------------------- 按客户分组的列表

    /**
     * 按客户分组的跟进列表（商机成交、订单履约、售后评价）：阶段 / 分组筛选作用在客户所处阶段上，
     * 卡点、关键字、有异常作用在名下单据上（有一张满足即列出客户）；每页 20 个客户，带名下全部单据。
     */
    public CustomerGroupListVO groups(FollowupQuery q) {
        int board = q.getBoard() == null ? FollowupConstants.BOARD_SALES : q.getBoard();
        if (!CustomerRollup.GROUPED_BOARDS.contains(board)) {
            throw new BizException("这个板块不按客户分组");
        }
        Collection<Long> owners = owners(q.getOwnerScope());
        CustomerGroupListVO vo = new CustomerGroupListVO();
        vo.setTotal(0L);
        vo.setDocTotal(0L);
        if (owners != null && owners.isEmpty()) {
            return vo;
        }
        int size = q.getPageSize() == null || q.getPageSize() <= 0 ? FollowupConstants.PAGE_SIZE
                : Math.min(q.getPageSize(), 100);
        int page = q.getPage() == null || q.getPage() <= 0 ? 1 : q.getPage();
        LocalDate today = LocalDate.now();
        boolean nurture = Boolean.TRUE.equals(q.getNurture());
        FollowupItemFilter f = filter(owners, today);
        f.setBoard(board);
        f.setNurture(nurture);
        f.setCountry(StringUtils.hasText(q.getCountry()) ? q.getCountry().trim() : null);
        List<String> scope = nurture ? null : scopeStages(board, q.getGroup(), q.getStage());
        f.setStageRanks(scope == null ? null : scope.stream().map(CustomerRollup::rank).toList());
        f.setMatchBlocker(StringUtils.hasText(q.getBlocker()) ? q.getBlocker() : null);
        f.setMatchKeyword(StringUtils.hasText(q.getKeyword())
                ? q.getKeyword().trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") : null);
        f.setMatchException(Boolean.TRUE.equals(q.getException()) && board == FollowupConstants.BOARD_FULFIL);
        Map<String, Object> counts = itemMapper.countCustomers(f);
        vo.setTotal(counts == null ? 0L : ((Number) counts.getOrDefault("customers", 0)).longValue());
        vo.setDocTotal(counts == null || counts.get("docs") == null ? 0L : ((Number) counts.get("docs")).longValue());
        if (!nurture && (Objects.equals(q.getGroup(), FollowupConstants.GROUP_AFTER_PI) || FollowupConstants.S7.equals(q.getStage()))) {
            FollowupItemFilter u = filter(owners, today);
            u.setBoard(board);
            u.setNurture(false);
            u.setStage(FollowupConstants.S7);
            u.setCountry(f.getCountry());
            for (Map<String, Object> row : itemMapper.unpaidByCurrency(u)) {
                FollowupListVO.CurrencyAmount a = new FollowupListVO.CurrencyAmount();
                a.setCurrencyCode(String.valueOf(row.get("currencyCode")));
                a.setAmount(row.get("amount") instanceof BigDecimal d ? d : BigDecimal.ZERO);
                vo.getUnpaid().add(a);
            }
        }
        f.setOffset((page - 1) * size);
        f.setSize(size);
        List<Long> customerIds = itemMapper.rankedCustomerIds(f);
        if (customerIds.isEmpty()) {
            return vo;
        }
        // 页内客户在本板块的全部单据，按客户价值排序
        FollowupItemFilter d = filter(owners, today);
        d.setBoard(board);
        d.setNurture(nurture);
        d.setCustomerIds(customerIds);
        List<FollowupItemDO> rows = byRank(itemMapper.rankedIds(d));
        List<FollowupItemVO> vos = toVos(rows, today, owners == null || owners.size() > 1);
        Map<Long, List<FollowupItemVO>> byCustomer = new HashMap<>(customerIds.size() * 2);
        Map<Long, List<FollowupItemDO>> doByCustomer = CustomerRollup.byCustomer(rows);
        for (FollowupItemVO v : vos) {
            byCustomer.computeIfAbsent(v.getCustomerId(), k -> new ArrayList<>(4)).add(v);
        }
        Map<String, String> names = stageNames();
        for (Long id : customerIds) {
            List<FollowupItemVO> items = byCustomer.getOrDefault(id, List.of());
            if (items.isEmpty()) {
                continue;
            }
            FollowupItemVO lead = items.get(0);
            CustomerGroupVO g = new CustomerGroupVO();
            g.setCustomerId(id);
            g.setCustomerName(lead.getCustomerName());
            g.setCountry(lead.getCountry());
            g.setBizGrade(lead.getBizGrade());
            g.setPotentialRating(lead.getPotentialRating());
            g.setOwnerName(lead.getOwnerName());
            String code = CustomerRollup.furthestStage(doByCustomer.getOrDefault(id, List.of()));
            FollowupItemVO at = items.stream().filter(i -> code.equals(i.getStageCode())).findFirst().orElse(lead);
            g.setStageCode(code);
            g.setStageName(StringUtils.hasText(at.getStageName()) ? at.getStageName() : names.getOrDefault(code, code));
            g.setStageGroup(at.getStageGroup());
            g.setDocCount(items.size());
            items.forEach(i -> g.getTypeCounts().merge(nz(i.getTargetType()), 1, Integer::sum));
            g.setOverdueDays(items.stream().map(i -> nz(i.getOverdueDays())).max(Integer::compare).orElse(0));
            g.setLead(lead);
            g.setItems(items);
            if (scope != null) {
                g.setInScope(items.stream().filter(i -> scope.contains(i.getStageCode())).map(FollowupItemVO::getId).toList());
            }
            vo.getRecords().add(g);
        }
        return vo;
    }

    /** 阶段 / 分组筛选对应的阶段码；都没传时为 null（不筛选） */
    private static List<String> scopeStages(int board, Integer group, String stage) {
        if (StringUtils.hasText(stage)) {
            return List.of(stage);
        }
        if (group == null) {
            return null;
        }
        Map<Integer, List<String>> groups = board == FollowupConstants.BOARD_SALES ? SALES_GROUPS
                : board == FollowupConstants.BOARD_FULFIL ? FULFIL_GROUPS : AFTER_SALES_GROUPS;
        return groups.getOrDefault(group, List.of());
    }

    // ---------------------------------------------------------------- 订单履约分部分进度

    /** 订单履约行展开：按采购单分部分的进度、已告诉客户的节点、每批出运的预计送达（第二期 tasks 5.5） */
    public OrderPartsVO parts(Long id) {
        FollowupItemDO item = requireOpen(id);
        if (!Objects.equals(item.getBoard(), FollowupConstants.BOARD_FULFIL)) {
            throw new BizException("只有订单履约可以查看分部分进度");
        }
        OrderFacts f = orderSync.facts(item.getTargetId());
        OrderPartsVO vo = new OrderPartsVO();
        if (f == null) {
            return vo;
        }
        LocalDate today = LocalDate.now();
        Map<String, String> names = orderStageNames();
        Map<String, Map<String, String>> actions = orderSync.defaultActions();
        for (FulfilDeriver.Part p : FulfilDeriver.parts(f, today)) {
            OrderPartsVO.Part v = new OrderPartsVO.Part();
            v.setLabel(p.label());
            v.setGoods(p.goods());
            v.setPoId(p.poId());
            v.setPoNo(p.poNo());
            v.setSupplier(p.supplier());
            v.setStageCode(p.stage());
            v.setStageName(names.getOrDefault(p.stage(), p.stage()));
            v.setExpectedShipDate(p.expectedShipDate());
            v.setArrival(p.arrival());
            v.setSituation(p.situation());
            v.setTell(actions.getOrDefault(p.stage(), Map.of()).getOrDefault("default", ""));
            vo.getParts().add(v);
        }
        for (CustomerFollowupDO r : recordMapper.selectList(new LambdaQueryWrapper<CustomerFollowupDO>()
                .select(CustomerFollowupDO::getId, CustomerFollowupDO::getCreateTime, CustomerFollowupDO::getNoticeMask,
                        CustomerFollowupDO::getAiSummary, CustomerFollowupDO::getNextAction, CustomerFollowupDO::getChannel)
                .eq(CustomerFollowupDO::getCustomerId, item.getCustomerId())
                .eq(CustomerFollowupDO::getTargetType, FollowupConstants.TARGET_ORDER)
                .eq(CustomerFollowupDO::getTargetId, item.getTargetId())
                .eq(CustomerFollowupDO::getRecordType, FollowupConstants.RECORD_USER)
                .isNull(CustomerFollowupDO::getDeletedAt)
                .orderByDesc(CustomerFollowupDO::getId))) {
            int mask = nz(r.getNoticeMask());
            String node = FollowupConstants.NOTICE_TOLD_NAMES.entrySet().stream().filter(e -> (mask & e.getKey()) != 0)
                    .sorted(Map.Entry.comparingByKey()).map(Map.Entry::getValue).collect(Collectors.joining("、"));
            if (node.isEmpty()) {
                continue;
            }
            OrderPartsVO.Told t = new OrderPartsVO.Told();
            t.setDate(r.getCreateTime());
            t.setNode(node);
            t.setContent(StringUtils.hasText(r.getAiSummary()) ? r.getAiSummary() : "");
            t.setChannelName(FollowupConstants.CHANNEL_NAMES.getOrDefault(nz(r.getChannel()), ""));
            vo.getTold().add(t);
        }
        FollowupSettings.OrderParams p = settings.orderParams(TenantContext.getTenantId());
        f.shipments().stream().filter(s -> s.shippedDate() != null)
                .sorted(Comparator.comparing(OrderFacts.Shipment::shippedDate).thenComparing(OrderFacts.Shipment::id))
                .forEach(s -> {
                    OrderPartsVO.Batch b = new OrderPartsVO.Batch();
                    b.setShipmentId(s.id());
                    b.setShNo(s.shNo());
                    b.setCarrier(s.carrier());
                    b.setWaybillNo(s.waybillNo());
                    b.setShippedDate(s.shippedDate());
                    b.setEtaDate(s.shippedDate().plusDays(p.transitDays(s.carrier())));
                    vo.getBatches().add(b);
                });
        return vo;
    }

    // ---------------------------------------------------------------- 今日待办

    public FollowupTodayVO today(String ownerScope) {
        Collection<Long> owners = owners(ownerScope);
        FollowupTodayVO vo = new FollowupTodayVO();
        vo.setWeekRemaining(0);
        vo.setTomorrow(0);
        if (owners != null && owners.isEmpty()) {
            return vo;
        }
        LocalDate today = LocalDate.now();
        boolean team = !isMe(ownerScope);
        // 到期单据按客户价值排序后按客户归并：客户的先后取它排在最前的那张单据
        FollowupItemFilter f = filter(owners, today);
        f.setNurture(false);
        f.setExcludeAfterSalesNext(true);
        f.setDueTo(today);
        f.setOffset(0);
        f.setSize(TODAY_ITEMS_LIMIT);
        List<FollowupItemDO> due = byRank(itemMapper.rankedIds(f));
        Map<Long, FollowupItemVO> vos = new HashMap<>(Math.max(16, due.size() * 2));
        List<FollowupItemVO> list = toVos(due, today, team);
        for (int i = 0; i < due.size(); i++) {
            vos.put(due.get(i).getId(), list.get(i));
        }
        Map<Long, List<FollowupItemDO>> byCustomer = CustomerRollup.byCustomer(due);
        Map<String, String> stageNames = stageNames();
        Map<String, String> furthest = furthestStages(byCustomer.keySet(), owners);
        for (Map.Entry<Long, List<FollowupItemDO>> e : byCustomer.entrySet()) {
            int kind = CustomerRollup.classify(e.getValue(), today);
            List<FollowupItemDO> shown = e.getValue().stream().filter(i -> kind == CustomerRollup.HANDLED
                    ? CustomerRollup.classify(i, today) == CustomerRollup.HANDLED
                    : CustomerRollup.classify(i, today) != CustomerRollup.HANDLED).toList();
            List<FollowupTodayVO.Customer> target = kind == CustomerRollup.OVERDUE ? vo.getOverdue()
                    : kind == CustomerRollup.TODAY ? vo.getToday() : vo.getHandled();
            if (kind == CustomerRollup.NONE || shown.isEmpty() || target.size() >= TODAY_LIMIT) {
                continue;
            }
            FollowupItemVO first = vos.get(shown.get(0).getId());
            FollowupTodayVO.Customer c = new FollowupTodayVO.Customer();
            c.setCustomerId(e.getKey());
            c.setCustomerName(first.getCustomerName());
            c.setCountry(first.getCountry());
            c.setBizGrade(first.getBizGrade());
            c.setPotentialRating(first.getPotentialRating());
            c.setOwnerName(first.getOwnerName());
            c.setOverdueDays(shown.stream().map(i -> nz(vos.get(i.getId()).getOverdueDays())).max(Integer::compare).orElse(0));
            Map<Integer, List<FollowupItemDO>> byBoard = new LinkedHashMap<>(4);
            shown.forEach(i -> byBoard.computeIfAbsent(i.getBoard(), k -> new ArrayList<>(4)).add(i));
            for (Map.Entry<Integer, List<FollowupItemDO>> be : byBoard.entrySet()) {
                FollowupTodayVO.Board b = new FollowupTodayVO.Board();
                b.setBoard(be.getKey());
                String code = furthest.getOrDefault(e.getKey() + ":" + be.getKey(), CustomerRollup.furthestStage(be.getValue()));
                b.setStageCode(code);
                b.setStageName(stageNames.getOrDefault(code, code));
                be.getValue().forEach(i -> b.getItems().add(vos.get(i.getId())));
                b.setLead(b.getItems().get(0));
                c.getBoards().add(b);
            }
            target.add(c);
        }
        // 明天、本周还有：按客户计，不含今天已经在待办里的客户
        LocalDate sunday = today.with(DayOfWeek.SUNDAY);
        LocalDate until = sunday.isAfter(today) ? sunday : today.plusDays(1);
        List<FollowupItemDO> ahead = itemMapper.selectList(new LambdaQueryWrapper<FollowupItemDO>()
                .select(FollowupItemDO::getCustomerId, FollowupItemDO::getNextActionDate)
                .isNull(FollowupItemDO::getClosedAt)
                .isNull(FollowupItemDO::getDeletedAt)
                .isNull(FollowupItemDO::getNurtureUntil)
                .ne(FollowupItemDO::getNextActionSource, FollowupConstants.NEXT_AFTER_SALES)
                .gt(FollowupItemDO::getNextActionDate, today)
                .le(FollowupItemDO::getNextActionDate, until)
                .in(owners != null, FollowupItemDO::getOwnerId, owners));
        Set<Long> pending = new HashSet<>(byCustomer.keySet());
        vo.setTomorrow((int) ahead.stream().filter(i -> today.plusDays(1).equals(i.getNextActionDate()))
                .map(FollowupItemDO::getCustomerId).filter(id -> !pending.contains(id)).distinct().count());
        if (sunday.isAfter(today)) {
            vo.setWeekRemaining((int) ahead.stream().map(FollowupItemDO::getCustomerId)
                    .filter(id -> !pending.contains(id)).distinct().count());
        }
        return vo;
    }

    /** 「A1 签收确认」这样的阶段标签，批量跟进的跳过原因用 */
    public String stageLabel(String code) {
        String name = stageNames().get(code);
        return StringUtils.hasText(name) && !name.equals(code) ? code + " " + name : code;
    }

    /** 客户在各板块所处的阶段：键为「客户ID:板块」，取不在培育中的未结束单据中最靠后的阶段 */
    private Map<String, String> furthestStages(Collection<Long> customerIds, Collection<Long> owners) {
        if (customerIds.isEmpty()) {
            return Map.of();
        }
        List<FollowupItemDO> rows = itemMapper.selectList(new LambdaQueryWrapper<FollowupItemDO>()
                .select(FollowupItemDO::getCustomerId, FollowupItemDO::getBoard, FollowupItemDO::getStageCode)
                .in(FollowupItemDO::getCustomerId, customerIds)
                .isNull(FollowupItemDO::getClosedAt)
                .isNull(FollowupItemDO::getDeletedAt)
                .isNull(FollowupItemDO::getNurtureUntil)
                .in(owners != null, FollowupItemDO::getOwnerId, owners));
        Map<String, String> out = new HashMap<>(Math.max(16, rows.size()));
        for (FollowupItemDO r : rows) {
            out.merge(r.getCustomerId() + ":" + r.getBoard(), r.getStageCode(),
                    (x, y) -> CustomerRollup.rank(y) > CustomerRollup.rank(x) ? y : x);
        }
        return out;
    }

    /** 阶段名称：商机成交按商机阶段规则，其余板块按 SOP 阶段 */
    private Map<String, String> stageNames() {
        Map<String, String> out = new HashMap<>(orderStageNames());
        OpportunityStageRules.Stages stages = stageRules.load();
        for (String code : CustomerRollup.STAGE_ORDER) {
            if (code.startsWith("S")) {
                out.put(code, stages.nameOf(code));
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- 延后 / 转培育 / 激活

    /** 延后：只改提醒日期，不写跟进记录，延后次数加 1；系统提醒延后后日期固定，不再按节奏刷新 */
    @Transactional(rollbackFor = Exception.class)
    public void snooze(Long id, LocalDate date) {
        FollowupItemDO item = requireOpen(id);
        if (item.getNurtureUntil() != null) {
            throw new BizException("培育中的跟进不能延后，请先激活");
        }
        doSnooze(item, date);
    }

    /** 延后一个客户的多张单据（今日待办的客户卡片、批量延后）：同一事务；培育中的单据跳过 */
    @Transactional(rollbackFor = Exception.class)
    public int snoozeMany(Long customerId, List<Long> itemIds, LocalDate date) {
        int n = 0;
        for (Long id : itemIds == null ? List.<Long>of() : itemIds.stream().distinct().toList()) {
            FollowupItemDO item = requireOpen(id);
            if (!Objects.equals(item.getCustomerId(), customerId)) {
                throw new BizException("跟进对象不存在");
            }
            if (item.getNurtureUntil() == null) {
                doSnooze(item, date);
                n++;
            }
        }
        return n;
    }

    private void doSnooze(FollowupItemDO item, LocalDate date) {
        if (date == null || !date.isAfter(LocalDate.now())) {
            throw new BizException("请选择今天以后的日期");
        }
        if (Objects.equals(item.getNextActionSource(), FollowupConstants.NEXT_SYSTEM)) {
            item.setNextActionSource(FollowupConstants.NEXT_SYSTEM_ONCE);
        }
        item.setNextActionDate(date);
        item.setSnoozeCount(nz(item.getSnoozeCount()) + 1);
        itemMapper.updateById(item);
    }

    /** 转培育：必须选原因（SOP 卡点）和激活日期；不改变报价单状态和商机阶段 */
    @Transactional(rollbackFor = Exception.class)
    public void nurture(Long id, NurtureRequest req) {
        FollowupItemDO item = requireOpen(id);
        if (item.getNurtureUntil() != null) {
            throw new BizException("这条跟进已经在培育中");
        }
        if (!StringUtils.hasText(req.getReason()) || !blockers().containsKey(req.getReason())) {
            throw new BizException("请选择培育原因");
        }
        if (req.getUntil() == null || !req.getUntil().isAfter(LocalDate.now())) {
            throw new BizException("激活日期要在今天以后");
        }
        item.setNurtureUntil(req.getUntil());
        item.setNurtureReason(req.getReason());
        item.setNurtureNote(req.getNote() == null ? "" : req.getNote().trim());
        item.setNextActionDate(null);
        itemMapper.updateById(item);
    }

    /** 手动激活：回到原阶段，生成一条今天的系统提醒 */
    @Transactional(rollbackFor = Exception.class)
    public void activate(Long id) {
        FollowupItemDO item = requireOpen(id);
        if (item.getNurtureUntil() == null) {
            throw new BizException("这条跟进不在培育中");
        }
        item.setNurtureUntil(null);
        item.setNurtureReason("");
        item.setNurtureNote("");
        item.setNextActionSource(FollowupConstants.NEXT_SYSTEM_ONCE);
        item.setNextAction("重新激活，联系客户");
        item.setNextActionDate(LocalDate.now());
        item.setSnoozeCount(0);
        itemMapper.updateById(item);
    }

    // ---------------------------------------------------------------- 记一次跟进

    /**
     * 记一次跟进（design D9）：写跟进记录 → 更新跟进对象 → 推进商机或跟进对象阶段 → 写入勾选的档案字段 → 关闭当前待办。
     * 同一事务，任一步失败整体回滚；幂等键重复时返回第一次的结果。
     */
    @Transactional(rollbackFor = Exception.class)
    public RecordResultVO record(RecordFollowupRequest req) {
        CustomerFollowupDO dup = byIdempotencyKey(req.getIdempotencyKey());
        if (dup != null) {
            return duplicateOf(dup);
        }
        CustomerDO customer = customerService.requireInScope(req.getCustomerId());
        LocalDate today = LocalDate.now();
        String nextAction = req.getNextAction() == null ? "" : req.getNextAction().trim();
        if (nextAction.isEmpty()) {
            throw new BizException("请写下一步要做什么");
        }
        if (req.getNextActionDate() == null || req.getNextActionDate().isBefore(today)) {
            throw new BizException("下一步日期不能早于今天");
        }
        int channel = req.getChannel() == null ? 0 : req.getChannel();
        if (channel != 0 && !FollowupConstants.CHANNEL_NAMES.containsKey(channel)) {
            throw new BizException("跟进方式不正确");
        }
        int situation = req.getSituation() == null ? 0 : req.getSituation()
                & (FollowupConstants.SITUATION_FEEDBACK | FollowupConstants.SITUATION_NEGOTIATING
                | FollowupConstants.SITUATION_NO_REPLY);
        List<FollowupItemDO> items = resolveItems(customer.getId(), req);
        FollowupItemDO item = items.isEmpty() ? null : items.get(0);
        String blocker = req.getBlockerCode() == null ? "" : req.getBlockerCode().trim();
        if (!blocker.isEmpty() && !blockers(item == null ? FollowupConstants.BOARD_SALES : item.getBoard()).containsKey(blocker)) {
            throw new BizException("卡点不存在，请重新选择");
        }
        requireSameStageForAnswers(items, req);
        boolean orderStage = OrderStageRecorder.handles(item);
        boolean account = AccountStageRecorder.handles(item);
        boolean develop = DevelopStageRecorder.handles(item);
        List<FollowupItemDO> checked = items.isEmpty() ? Collections.singletonList(null) : items;
        for (FollowupItemDO it : checked) {
            OrderStageRecorder.validate(req, it, today);
            AccountStageRecorder.validate(req, it, nz(customer.getStrategic()) == 1);
            DevelopStageRecorder.validate(req, it, channel);
        }
        if (account) {
            situation = AccountStageRecorder.situation(req);
        }
        if (develop) {
            situation = DevelopStageRecorder.situation(req);
        }
        Long me = currentUser.resolve();

        // 1. 跟进记录
        CustomerFollowupDO r = new CustomerFollowupDO();
        r.setTenantId(TenantContext.getTenantId());
        r.setRecordType(FollowupConstants.RECORD_USER);
        r.setCustomerId(customer.getId());
        r.setFollowupItemId(item == null ? 0L : item.getId());
        r.setTargetType(item == null ? 0 : item.getTargetType());
        r.setTargetId(item == null ? 0L : item.getTargetId());
        r.setOpportunityId(item == null ? 0L : nzl(item.getOpportunityId()));
        r.setTitle("");
        r.setSituation(situation);
        r.setBlockerCode(blocker);
        r.setNextAction(nextAction);
        r.setNextActionDate(req.getNextActionDate());
        r.setChannel(channel);
        r.setRawContent(tail(req.getRawContent()));
        r.setAiSummary(req.getSummary() == null ? "" : req.getSummary().trim());
        r.setAiTaskId(req.getAiTaskId() == null ? 0L : req.getAiTaskId());
        String before = item == null ? "" : item.getStageCode();
        r.setStageBefore(before);
        r.setStageAfter(before);
        boolean pi = item != null && item.getTargetType() == FollowupConstants.TARGET_PI;
        r.setExpectedPayDate(pi ? req.getExpectedPayDate() : null);
        r.setOperatorId(me == null ? 0L : me);
        r.setIdempotencyKey(req.getIdempotencyKey().trim());
        OrderStageRecorder.fill(r, req);
        AccountStageRecorder.fill(r, req);
        DevelopStageRecorder.fill(r, req);
        r.setDueDate(item == null ? null : item.getNextActionDate());
        try {
            recordMapper.insert(r);
        } catch (DuplicateKeyException e) {
            // 并发重复提交：另一次提交已经写入，本次不再处理
            log.info("记跟进重复提交，idempotencyKey={}", req.getIdempotencyKey());
            RecordResultVO vo = new RecordResultVO();
            vo.setDuplicate(true);
            vo.setOpportunityAdvanced(false);
            return vo;
        }

        // 2–3. 涉及的每张单据：更新下一步、上次跟进，按勾选推进阶段（同一个商机只推进一次）
        for (FollowupItemDO it : items) {
            CustomerFollowupItemDO link = new CustomerFollowupItemDO();
            link.setTenantId(TenantContext.getTenantId());
            link.setFollowupId(r.getId());
            link.setFollowupItemId(it.getId());
            link.setDocNo(it.getDocNo() == null ? "" : it.getDocNo());
            link.setStageCode(it.getStageCode() == null ? "" : it.getStageCode());
            link.setDueDate(it.getNextActionDate());
            recordItemMapper.insert(link);
        }
        boolean advanced = false;
        String after = before;
        Boolean orderCompleted = null;
        boolean ended = false;
        boolean wantsAdvance = false;
        String target = (situation & FollowupConstants.SITUATION_NEGOTIATING) != 0 ? FollowupConstants.S6
                : (situation & FollowupConstants.SITUATION_FEEDBACK) != 0 ? FollowupConstants.S5 : null;
        for (FollowupItemDO it : items) {
            ItemOutcome o = applyRecord(it, req, r.getId(), situation, target, nextAction, blocker, customer, today);
            wantsAdvance |= o.wantsAdvance();
            if (it == item) {
                after = o.after();
                orderCompleted = o.orderCompleted();
                ended = o.ended();
            }
        }
        if (wantsAdvance) {
            advanced = opportunityService.autoAdvance(customer.getId(), target, FollowupConstants.S4,
                    "记跟进，自动推进") != null;
            if (advanced) {
                // 新客户：跟进对象随商机同步
                sync.resync(customer.getId(), today, false);
                FollowupItemDO fresh = itemMapper.selectById(item.getId());
                after = fresh == null ? target : fresh.getStageCode();
            }
        }

        // 4. 档案：只补空值，来源记为跟进记录
        Map<String, String> applied = profileService.applyFollowup(customer.getId(), req.getProfile(), r.getId());
        r.setStageAfter(after);
        r.setProfileFields(applied.isEmpty() ? null : json(applied));
        recordMapper.updateById(r);

        RecordResultVO vo = new RecordResultVO();
        vo.setRecordId(r.getId());
        vo.setStageBefore(before);
        vo.setStageAfter(after);
        vo.setOpportunityAdvanced(advanced);
        vo.setOrderCompleted(orderCompleted);
        vo.setEnded(ended);
        vo.setDuplicate(false);
        log.info("记一次跟进，customerId={}, itemId={}, recordId={}, stage={}→{}", customer.getId(),
                item == null ? null : item.getId(), r.getId(), before, after);
        return vo;
    }

    /** 单张单据的处理结果：记录后的阶段；订单是否确认客户已收货；售后是否结束；是否要推进商机 */
    private record ItemOutcome(String after, Boolean orderCompleted, boolean ended, boolean wantsAdvance) {
    }

    /** 记跟进对一张单据的处理：卡点、下一步、上次跟进；当前待办随之关闭；按勾选推进阶段（商机由调用方统一推进） */
    private ItemOutcome applyRecord(FollowupItemDO item, RecordFollowupRequest req, Long recordId, int situation, String target,
                                    String nextAction, String blocker, CustomerDO customer, LocalDate today) {
        boolean pi = item.getTargetType() == FollowupConstants.TARGET_PI;
        boolean orderStage = OrderStageRecorder.handles(item);
        boolean account = AccountStageRecorder.handles(item);
        boolean develop = DevelopStageRecorder.handles(item);
        String before = item.getStageCode();
        String after = before;
        Boolean orderCompleted = null;
        boolean ended = false;
        // 2. 跟进对象：卡点、下一步、上次跟进；当前待办随之关闭（下一步日期换成业务员定的）
        item.setBlockerCode(blocker);
        item.setNextAction(nextAction);
        item.setNextActionDate(req.getNextActionDate());
        item.setNextActionSource(FollowupConstants.NEXT_USER);
        item.setSnoozeCount(0);
        item.setLastFollowupId(recordId);
        item.setLastFollowupAt(LocalDateTime.now());
        boolean replied = (situation & (FollowupConstants.SITUATION_FEEDBACK | FollowupConstants.SITUATION_NEGOTIATING)) != 0;
        if (replied) {
            item.setNoReplyStreak(0);
            item.setFeedbackSeen(1);
        } else if ((situation & FollowupConstants.SITUATION_NO_REPLY) != 0) {
            item.setNoReplyStreak(nz(item.getNoReplyStreak()) + 1);
        }
        if (pi && req.getExpectedPayDate() != null) {
            item.setExpectedPayDate(req.getExpectedPayDate());
        }
        if (orderStage && OrderStageRecorder.applyToItem(item, req, today) && !replied) {
            item.setNoReplyStreak(nz(item.getNoReplyStreak()) + 1);
        }
        if (account) {
            AccountStageRecorder.applyToItem(item, req);
            if (situation != FollowupConstants.SITUATION_NO_REPLY) {
                item.setNoReplyStreak(0);
            }
        }
        if (develop) {
            DevelopStageRecorder.applyToItem(item, req, today);
        }
        // 3. 阶段：报价后勾「给了反馈」→ S5，「在谈条件」→ S6，只进不退
        boolean hasOpportunity = nzl(item.getOpportunityId()) > 0;
        if (target != null && Objects.equals(item.getStageGroup(), FollowupConstants.GROUP_AFTER_QUOTE)) {
            if (!hasOpportunity) {
                OpportunityStageRules.Stages stages = stageRules.load();
                if (stages.orderOf(target) > stages.orderOf(item.getStageCode())) {
                    item.setStageCode(target);
                    after = target;
                }
            }
        }
        itemMapper.updateById(item);
        if (account) {
            // 客户经营：季度回顾更新回顾日期；阶段按新标记重新推导（G5 / G6 / G4 / G7）
            if (Boolean.TRUE.equals(req.getQuarterReview())) {
                customerMapper.update(null, new LambdaUpdateWrapper<CustomerDO>()
                        .eq(CustomerDO::getId, customer.getId())
                        .set(CustomerDO::getLastReviewDate, today)
                        .set(CustomerDO::getUpdateTime, LocalDateTime.now()));
            }
            sync.resync(customer.getId(), today, false);
            FollowupItemDO fresh = itemMapper.selectById(item.getId());
            after = fresh == null ? before : fresh.getStageCode();
        }
        if (develop) {
            // 主动开发：阶段按触达次数、回复、采购信号重新推导（只进不退）
            sync.resync(customer.getId(), today, false);
            FollowupItemDO fresh = itemMapper.selectById(item.getId());
            after = fresh == null ? before : fresh.getStageCode();
        }
        if (orderStage) {
            // 订单履约、售后评价：阶段由单据和答案重新推导（进入 A2、A5、A6 或售后结束）
            orderCompleted = orderStageRecorder.afterSave(item, req, today);
            FollowupItemDO fresh = itemMapper.selectById(item.getId());
            after = fresh == null ? before : fresh.getStageCode();
            boolean closed = fresh != null && fresh.getClosedAt() != null;
            if (closed && Objects.equals(item.getBoard(), FollowupConstants.BOARD_FULFIL)) {
                // 履约行关闭是转入售后评价（如改预计送达到今天），不是售后结束：阶段记为新的售后行
                FollowupItemDO next = itemMapper.selectOne(new LambdaQueryWrapper<FollowupItemDO>()
                        .select(FollowupItemDO::getStageCode)
                        .eq(FollowupItemDO::getBoard, FollowupConstants.BOARD_AFTER_SALES)
                        .eq(FollowupItemDO::getTargetId, item.getTargetId())
                        .isNull(FollowupItemDO::getClosedAt)
                        .isNull(FollowupItemDO::getDeletedAt)
                        .last("LIMIT 1"));
                if (next != null) {
                    after = next.getStageCode();
                }
            } else {
                ended = closed;
            }
        }
        boolean wantsAdvance = target != null && hasOpportunity
                && Objects.equals(item.getStageGroup(), FollowupConstants.GROUP_AFTER_QUOTE);
        return new ItemOutcome(after, orderCompleted, ended, wantsAdvance);
    }

    private CustomerFollowupDO byIdempotencyKey(String key) {
        if (!StringUtils.hasText(key)) {
            throw new BizException("缺少幂等键，请刷新后重试");
        }
        return recordMapper.selectOne(new LambdaQueryWrapper<CustomerFollowupDO>()
                .eq(CustomerFollowupDO::getIdempotencyKey, key.trim())
                .last("LIMIT 1"));
    }

    private static RecordResultVO duplicateOf(CustomerFollowupDO r) {
        RecordResultVO vo = new RecordResultVO();
        vo.setRecordId(r.getId());
        vo.setStageBefore(r.getStageBefore());
        vo.setStageAfter(r.getStageAfter());
        vo.setOpportunityAdvanced(false);
        vo.setDuplicate(true);
        return vo;
    }

    /** 指定了跟进对象就用它（必须属于这个客户、未结束）；没指定取这位客户最紧急的一个；都没有返回 null */
    FollowupItemDO resolveItem(Long customerId, Long itemId) {
        if (itemId != null && itemId > 0) {
            FollowupItemDO item = itemMapper.selectById(itemId);
            if (item == null || item.getDeletedAt() != null || item.getClosedAt() != null
                    || !Objects.equals(item.getCustomerId(), customerId)) {
                throw new BizException("跟进对象已结束，请刷新后重试");
            }
            return item;
        }
        FollowupItemFilter f = filter(null, LocalDate.now());
        f.setCustomerId(customerId);
        f.setBoard(FollowupConstants.BOARD_SALES);
        f.setOffset(0);
        f.setSize(1);
        List<Long> ids = itemMapper.rankedIds(f);
        return ids.isEmpty() ? null : itemMapper.selectById(ids.get(0));
    }

    /**
     * 这次跟进涉及的单据：传了 itemIds 用它们（同一客户、同一板块、未结束，按传入顺序，第一张为主单据）；
     * 只传 itemId 时为这一张；都没传时取这位客户最紧急的一张；都没有返回空列表（只写跟进记录）
     */
    List<FollowupItemDO> resolveItems(Long customerId, RecordFollowupRequest req) {
        List<Long> ids = req.getItemIds() == null ? List.of()
                : req.getItemIds().stream().filter(i -> i != null && i > 0).distinct().toList();
        if (ids.isEmpty()) {
            FollowupItemDO one = resolveItem(customerId, req.getItemId());
            return one == null ? List.of() : List.of(one);
        }
        if (ids.size() > FollowupConstants.RECORD_ITEMS_MAX) {
            throw new BizException("一次跟进最多选 " + FollowupConstants.RECORD_ITEMS_MAX + " 张单据");
        }
        Map<Long, FollowupItemDO> byId = itemMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(FollowupItemDO::getId, Function.identity()));
        List<FollowupItemDO> out = new ArrayList<>(ids.size());
        for (Long id : ids) {
            FollowupItemDO it = byId.get(id);
            if (it == null || it.getDeletedAt() != null || it.getClosedAt() != null
                    || !Objects.equals(it.getCustomerId(), customerId)) {
                throw new BizException("有单据已结束，请刷新后重试");
            }
            if (!out.isEmpty() && !Objects.equals(out.get(0).getBoard(), it.getBoard())) {
                throw new BizException("一次跟进只能选同一个板块的单据");
            }
            out.add(it);
        }
        return out;
    }

    /** 选了多张不同阶段的单据时，不能回答阶段问题；有必答阶段（A1、A2、A5、A6）的不能保存 */
    private static void requireSameStageForAnswers(List<FollowupItemDO> items, RecordFollowupRequest req) {
        if (items.stream().map(FollowupItemDO::getStageCode).distinct().count() <= 1) {
            return;
        }
        boolean answers = items.stream().anyMatch(i -> CustomerRollup.ANSWER_STAGES.contains(i.getStageCode()))
                || req.getNoticeMask() != null || req.getEtaDate() != null || req.getReceiptAnswer() != null
                || req.getUseAnswer() != null || (req.getIssueTypes() != null && !req.getIssueTypes().isEmpty())
                || req.getSatisfaction() != null || req.getReviewStatus() != null;
        if (answers) {
            throw BizException.of(FollowupConstants.ERROR_STAGE_MISMATCH, "要回答阶段问题，请只选同一阶段的单据");
        }
    }

    private static String tail(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.strip();
        return s.length() > FollowupConstants.RAW_MAX ? s.substring(s.length() - FollowupConstants.RAW_MAX) : s;
    }

    private String json(Map<String, String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException e) {
            throw new BizException("档案字段保存失败", e);
        }
    }

    // ---------------------------------------------------------------- SOP

    public SopCardVO sop(int board, String stage) {
        SopStageDO s = sopStageMapper.selectList(new LambdaQueryWrapper<SopStageDO>()
                        .eq(SopStageDO::getBoard, board)
                        .eq(SopStageDO::getStageCode, stage)
                        .eq(SopStageDO::getStatus, 1)).stream()
                .max(Comparator.comparing(SopStageDO::getTenantId))
                .orElseThrow(() -> new BizException("这个阶段还没有作战卡"));
        SopCardVO vo = new SopCardVO();
        vo.setBoard(board);
        vo.setStageCode(s.getStageCode());
        vo.setName(s.getName());
        vo.setGoal(s.getGoal());
        vo.setChecklist(list(s.getChecklist()));
        vo.setGateText(s.getGateText());
        vo.setAutoHint(s.getAutoHint());
        vo.setExampleDialog(s.getExampleDialog());
        vo.setBlockers(blockerVos(board));
        return vo;
    }

    /** 商机成交的卡点字典（输单原因、记跟进的卡点选项） */
    /** 某个板块的卡点（履约异常、售后问题的应对办法也在这里） */
    /** @param board null 时取全部板块 */
    public List<SopCardVO.Blocker> blockerVos(Integer board) {
        return blockers(board).values().stream().map(b -> {
            SopCardVO.Blocker x = new SopCardVO.Blocker();
            x.setCode(b.getCode());
            x.setName(b.getName());
            x.setLostReason(b.getLostReason());
            x.setPlaybook(list(b.getPlaybook()));
            return x;
        }).toList();
    }

    /** 商机成交的卡点字典：编码 → 卡点（租户自己的覆盖平台的），按排序 */
    private Map<String, SopBlockerDO> blockers() {
        return blockers(FollowupConstants.BOARD_SALES);
    }

    /** board 为 null 时取全部板块（翻译记录、列表上的卡点名称用） */
    private Map<String, SopBlockerDO> blockers(Integer board) {
        List<SopBlockerDO> rows = sopBlockerMapper.selectList(new LambdaQueryWrapper<SopBlockerDO>()
                .eq(board != null, SopBlockerDO::getBoard, board)
                .eq(SopBlockerDO::getStatus, 1));
        Map<String, SopBlockerDO> byCode = new HashMap<>(16);
        rows.stream().sorted(Comparator.comparing(SopBlockerDO::getTenantId)).forEach(b -> byCode.put(b.getCode(), b));
        return byCode.values().stream().sorted(Comparator.comparing(SopBlockerDO::getSortOrder))
                .collect(Collectors.toMap(SopBlockerDO::getCode, Function.identity(), (a, b) -> a, LinkedHashMap::new));
    }

    private List<String> list(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() { });
        } catch (JsonProcessingException e) {
            log.warn("SOP 内容格式不正确，已忽略：{}", e.getOriginalMessage());
            return new ArrayList<>();
        }
    }

    // ---------------------------------------------------------------- 客户详情「跟进」分区

    /**
     * 客户的跟进时间线（按时间倒序）；type：all（默认）、user、system；blockerOnly 只看有卡点的。
     * 可见范围跟随客户。
     */
    public CustomerFollowupVO customerRecords(Long customerId, String type, Boolean blockerOnly, Integer page,
                                              Integer pageSize) {
        customerService.requireInScope(customerId);
        int size = pageSize == null || pageSize <= 0 ? RECORD_PAGE : Math.min(pageSize, RECORD_PAGE_MAX);
        int p = page == null || page <= 0 ? 1 : page;
        LambdaQueryWrapper<CustomerFollowupDO> w = new LambdaQueryWrapper<CustomerFollowupDO>()
                .eq(CustomerFollowupDO::getCustomerId, customerId)
                .isNull(CustomerFollowupDO::getDeletedAt)
                .eq("user".equals(type), CustomerFollowupDO::getRecordType, FollowupConstants.RECORD_USER)
                .eq("system".equals(type), CustomerFollowupDO::getRecordType, FollowupConstants.RECORD_SYSTEM)
                .ne(Boolean.TRUE.equals(blockerOnly), CustomerFollowupDO::getBlockerCode, "")
                .orderByDesc(CustomerFollowupDO::getId);
        IPage<CustomerFollowupDO> rows = recordMapper.selectPage(new Page<>(p, size), w);
        CustomerFollowupVO vo = new CustomerFollowupVO();
        vo.setTotal(rows.getTotal());
        vo.setRecords(recordVos(customerId, rows.getRecords()));
        return vo;
    }

    /** 客户详情「跟进」分区：当前跟进对象与下一步、概况，以及时间线第一页 */
    public CustomerFollowupVO customerSummary(Long customerId) {
        CustomerFollowupVO vo = customerRecords(customerId, null, false, 1, RECORD_PAGE);
        LocalDate today = LocalDate.now();
        FollowupItemFilter f = filter(null, today);
        f.setCustomerId(customerId);
        List<FollowupItemDO> open = byRank(itemMapper.rankedIds(f));
        if (!open.isEmpty()) {
            vo.setCurrent(toVos(List.of(open.get(0)), today, false).get(0));
            int board = open.get(0).getBoard();
            List<FollowupItemDO> same = CustomerRollup.GROUPED_BOARDS.contains(board)
                    ? open.stream().filter(i -> i.getBoard() == board && i.getNurtureUntil() == null)
                    .limit(FollowupConstants.RECORD_ITEMS_MAX).toList()
                    : List.of(open.get(0));
            vo.setCurrentItems(toVos(same, today, false));
            vo.setNoReplyStreak(nz(open.get(0).getNoReplyStreak()));
        } else {
            vo.setNoReplyStreak(0);
        }
        vo.setStep(open.isEmpty() ? 0 : open.get(0).getBoard());
        vo.setBoards(open.stream().map(FollowupItemDO::getBoard).distinct().sorted().toList());
        vo.setQuoteCount((int) open.stream().filter(i -> i.getTargetType() == FollowupConstants.TARGET_QUOTATION).count());
        vo.setPiCount((int) open.stream().filter(i -> i.getTargetType() == FollowupConstants.TARGET_PI).count());
        Map<String, BigDecimal> amounts = new TreeMap<>();
        for (FollowupItemDO i : open) {
            if (StringUtils.hasText(i.getCurrencyCode()) && i.getTargetType() != FollowupConstants.TARGET_INQUIRY
                    && i.getAmount() != null && i.getAmount().signum() > 0) {
                amounts.merge(i.getCurrencyCode(), i.getAmount(), BigDecimal::add);
            }
        }
        amounts.forEach((cur, amt) -> {
            CustomerFollowupVO.Amount a = new CustomerFollowupVO.Amount();
            a.setCurrencyCode(cur);
            a.setAmount(amt);
            vo.getOpenAmounts().add(a);
        });
        CustomerFollowupDO last = recordMapper.selectOne(new LambdaQueryWrapper<CustomerFollowupDO>()
                .eq(CustomerFollowupDO::getCustomerId, customerId)
                .eq(CustomerFollowupDO::getRecordType, FollowupConstants.RECORD_USER)
                .isNull(CustomerFollowupDO::getDeletedAt)
                .orderByDesc(CustomerFollowupDO::getId)
                .last("LIMIT 1"));
        if (last != null) {
            vo.setLastContactAt(last.getCreateTime());
            vo.setLastChannelName(FollowupConstants.CHANNEL_NAMES.getOrDefault(last.getChannel(), ""));
        }
        Long recent = recordMapper.selectCount(new LambdaQueryWrapper<CustomerFollowupDO>()
                .eq(CustomerFollowupDO::getCustomerId, customerId)
                .eq(CustomerFollowupDO::getRecordType, FollowupConstants.RECORD_USER)
                .isNull(CustomerFollowupDO::getDeletedAt)
                .ge(CustomerFollowupDO::getCreateTime, today.minusDays(30).atStartOfDay()));
        vo.setRecent30(recent == null ? 0 : recent.intValue());
        return vo;
    }

    private List<FollowupRecordVO> recordVos(Long customerId, List<CustomerFollowupDO> rows) {
        if (rows.isEmpty()) {
            return new ArrayList<>();
        }
        Map<String, SopBlockerDO> blockers = blockers(null);
        Map<Long, String> names = userNames(rows.stream().map(CustomerFollowupDO::getOperatorId).toList());
        Map<Long, String> docNos = new HashMap<>(rows.size() * 2);
        Set<Long> itemIds = rows.stream().map(CustomerFollowupDO::getFollowupItemId).filter(i -> i != null && i > 0)
                .collect(Collectors.toSet());
        if (!itemIds.isEmpty()) {
            itemMapper.selectBatchIds(itemIds).forEach(i -> docNos.put(i.getId(), i.getDocNo()));
        }
        // 一次跟进涉及多张单据时列出单号
        Map<Long, List<String>> linked = new HashMap<>(rows.size() * 2);
        recordItemMapper.selectList(new LambdaQueryWrapper<CustomerFollowupItemDO>()
                        .select(CustomerFollowupItemDO::getFollowupId, CustomerFollowupItemDO::getDocNo)
                        .in(CustomerFollowupItemDO::getFollowupId, rows.stream().map(CustomerFollowupDO::getId).toList())
                        .isNull(CustomerFollowupItemDO::getDeletedAt)
                        .orderByAsc(CustomerFollowupItemDO::getId))
                .forEach(l -> linked.computeIfAbsent(l.getFollowupId(), k -> new ArrayList<>(4)).add(l.getDocNo()));
        CustomerFollowupDO latestUser = recordMapper.selectOne(new LambdaQueryWrapper<CustomerFollowupDO>()
                .select(CustomerFollowupDO::getId)
                .eq(CustomerFollowupDO::getCustomerId, customerId)
                .eq(CustomerFollowupDO::getRecordType, FollowupConstants.RECORD_USER)
                .isNull(CustomerFollowupDO::getDeletedAt)
                .orderByDesc(CustomerFollowupDO::getId)
                .last("LIMIT 1"));
        long latestUserId = latestUser == null ? 0 : latestUser.getId();
        List<FollowupRecordVO> out = new ArrayList<>(rows.size());
        for (CustomerFollowupDO r : rows) {
            FollowupRecordVO vo = new FollowupRecordVO();
            vo.setId(r.getId());
            vo.setRecordType(r.getRecordType());
            vo.setTitle(r.getTitle());
            vo.setSituation(r.getSituation());
            int s = nz(r.getSituation());
            if ((s & FollowupConstants.SITUATION_FEEDBACK) != 0) {
                vo.getSituationLabels().add("客户给了反馈");
            }
            if ((s & FollowupConstants.SITUATION_NEGOTIATING) != 0) {
                vo.getSituationLabels().add("客户在谈条件");
            }
            if ((s & FollowupConstants.SITUATION_NO_REPLY) != 0) {
                vo.getSituationLabels().add("没回复");
            }
            vo.setBlockerCode(r.getBlockerCode());
            SopBlockerDO b = blockers.get(r.getBlockerCode());
            vo.setBlockerName(b == null ? "" : b.getName());
            vo.setNextAction(r.getNextAction());
            vo.setNextActionDate(r.getNextActionDate());
            vo.setNextDone(r.getRecordType() == FollowupConstants.RECORD_USER && r.getId() < latestUserId);
            vo.setChannel(r.getChannel());
            vo.setChannelName(FollowupConstants.CHANNEL_NAMES.getOrDefault(r.getChannel(), ""));
            vo.setSummary(r.getAiSummary());
            vo.setRawContent(r.getRawContent());
            vo.setStageBefore(r.getStageBefore());
            vo.setStageAfter(r.getStageAfter());
            vo.setProfileFields(profileFields(r.getProfileFields()));
            vo.setOperatorName(r.getRecordType() == FollowupConstants.RECORD_SYSTEM ? "系统"
                    : names.getOrDefault(r.getOperatorId(), ""));
            vo.setTargetType(r.getTargetType());
            vo.setDocNo(docNos.getOrDefault(r.getFollowupItemId(), ""));
            List<String> nos = linked.getOrDefault(r.getId(), List.of());
            if (nos.size() > 1) {
                vo.setDocNos(nos);
            }
            vo.setCreateTime(r.getCreateTime());
            out.add(vo);
        }
        return out;
    }

    private List<FollowupRecordVO.ProfileField> profileFields(String json) {
        List<FollowupRecordVO.ProfileField> out = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return out;
        }
        try {
            Map<String, String> m = objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, String>>() { });
            m.forEach((k, v) -> {
                FollowupRecordVO.ProfileField pf = new FollowupRecordVO.ProfileField();
                pf.setField(k);
                pf.setLabel(FollowupConstants.PROFILE_LABELS.getOrDefault(k, k));
                pf.setValue(v);
                out.add(pf);
            });
        } catch (JsonProcessingException e) {
            log.warn("跟进记录的档案字段格式不正确，已忽略：{}", e.getOriginalMessage());
        }
        return out;
    }

    // ---------------------------------------------------------------- 行数据

    private List<FollowupItemVO> toVos(List<FollowupItemDO> rows, LocalDate today, boolean withOwner) {
        if (rows.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, CustomerDO> customers = customerMapper.selectBatchIds(
                        rows.stream().map(FollowupItemDO::getCustomerId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(CustomerDO::getId, Function.identity()));
        Map<Long, QuotationDO> quotes = idsOf(rows, FollowupConstants.TARGET_QUOTATION).isEmpty() ? Map.of()
                : quotationMapper.selectBatchIds(idsOf(rows, FollowupConstants.TARGET_QUOTATION)).stream()
                .collect(Collectors.toMap(QuotationDO::getId, Function.identity()));
        Map<Long, ProformaInvoiceDO> pis = idsOf(rows, FollowupConstants.TARGET_PI).isEmpty() ? Map.of()
                : piMapper.selectBatchIds(idsOf(rows, FollowupConstants.TARGET_PI)).stream()
                .collect(Collectors.toMap(ProformaInvoiceDO::getId, Function.identity()));
        Set<Long> lastIds = rows.stream().map(FollowupItemDO::getLastFollowupId).filter(i -> i != null && i > 0)
                .collect(Collectors.toSet());
        Map<Long, CustomerFollowupDO> lasts = lastIds.isEmpty() ? Map.of()
                : recordMapper.selectBatchIds(lastIds).stream()
                .collect(Collectors.toMap(CustomerFollowupDO::getId, Function.identity()));
        Map<Long, String> owners = withOwner ? userNames(rows.stream().map(FollowupItemDO::getOwnerId).toList()) : Map.of();
        Map<String, SopBlockerDO> blockers = blockers(null);
        OpportunityStageRules.Stages stages = stageRules.load();
        Map<String, String> orderStages = orderStageNames();
        Set<Long> asIds = rows.stream().map(FollowupItemDO::getAfterSalesId).filter(x -> x != null && x > 0).collect(Collectors.toSet());
        Map<Long, AfterSalesDO> afterSales = asIds.isEmpty() ? Map.of() : afterSalesMapper.selectBatchIds(asIds).stream()
                .collect(Collectors.toMap(AfterSalesDO::getId, Function.identity()));
        Map<String, Map<String, String>> actions = new HashMap<>(sync.defaultActions());
        actions.putAll(orderSync.defaultActions());
        FollowupSettings.Params params = settings.params(TenantContext.getTenantId());
        boolean anyAccount = rows.stream().anyMatch(AccountStageRecorder::handles);
        int[] followDays = anyAccount ? gradeSettings.params(TenantContext.getTenantId()).followDays() : null;
        Map<Long, LocalDate> contacts = anyAccount ? lastContacts(rows.stream().filter(AccountStageRecorder::handles)
                .map(FollowupItemDO::getCustomerId).collect(Collectors.toSet())) : Map.of();
        boolean anyDevelop = rows.stream().anyMatch(DevelopStageRecorder::handles);
        List<Integer> sequence = anyDevelop ? settings.developSequence(TenantContext.getTenantId()) : List.of();
        Map<Long, Integer> priorities = anyDevelop ? reportPriorities(rows.stream().filter(DevelopStageRecorder::handles)
                .map(FollowupItemDO::getCustomerId).collect(Collectors.toSet())) : Map.of();
        List<FollowupItemVO> out = new ArrayList<>(rows.size());
        for (FollowupItemDO i : rows) {
            FollowupItemVO vo = new FollowupItemVO();
            vo.setId(i.getId());
            vo.setBoard(i.getBoard());
            vo.setTargetType(i.getTargetType());
            vo.setTargetId(i.getTargetId());
            vo.setDocNo(i.getDocNo());
            vo.setCustomerId(i.getCustomerId());
            CustomerDO c = customers.get(i.getCustomerId());
            if (c != null) {
                vo.setCustomerName(c.getName());
                vo.setCountry(c.getCountry());
                vo.setBizGrade(gradeName(c.getBizGrade()));
                vo.setPotentialRating(nz(c.getPotentialRating()));
            }
            vo.setStageCode(i.getStageCode());
            vo.setStageName(OrderStageRecorder.handles(i) || AccountStageRecorder.handles(i) || DevelopStageRecorder.handles(i)
                    ? orderStages.getOrDefault(i.getStageCode(), i.getStageCode())
                    : stages.nameOf(i.getStageCode()));
            vo.setStageGroup(i.getStageGroup());
            vo.setCurrencyCode(i.getCurrencyCode());
            vo.setAmount(i.getAmount());
            vo.setReceivedAmount(i.getReceivedAmount());
            vo.setUnpaidAmount(i.getAmount() == null ? null
                    : i.getAmount().subtract(i.getReceivedAmount() == null ? BigDecimal.ZERO : i.getReceivedAmount()));
            vo.setAmountCny(i.getAmountCny());
            vo.setSentAt(i.getSentAt());
            vo.setSentDays(i.getSentAt() == null ? null
                    : (int) ChronoUnit.DAYS.between(i.getSentAt().toLocalDate(), today));
            if (i.getTargetType() == FollowupConstants.TARGET_QUOTATION && quotes.containsKey(i.getTargetId())) {
                vo.setVersionNo(quotes.get(i.getTargetId()).getCurrentVersionNo());
            }
            if (i.getTargetType() == FollowupConstants.TARGET_PI && pis.containsKey(i.getTargetId())) {
                vo.setVersionNo(pis.get(i.getTargetId()).getCurrentVersionNo());
                vo.setReceiptStatus(pis.get(i.getTargetId()).getReceiptStatus());
            }
            vo.setExpectedPayDate(i.getExpectedPayDate());
            vo.setBlockerCode(i.getBlockerCode());
            SopBlockerDO b = blockers.get(i.getBlockerCode());
            vo.setBlockerName(b == null ? "" : b.getName());
            CustomerFollowupDO last = lasts.get(i.getLastFollowupId());
            vo.setLastFollowupAt(i.getLastFollowupAt());
            if (last != null) {
                vo.setLastChannelName(FollowupConstants.CHANNEL_NAMES.getOrDefault(last.getChannel(), ""));
                vo.setLastSummary(StringUtils.hasText(last.getAiSummary()) ? last.getAiSummary() : last.getNextAction());
            }
            vo.setNextAction(i.getNextAction());
            vo.setNextActionDate(i.getNextActionDate());
            vo.setNextActionSource(i.getNextActionSource());
            vo.setOverdueDays(due(i) && i.getNextActionDate().isBefore(today)
                    ? (int) ChronoUnit.DAYS.between(i.getNextActionDate(), today) : 0);
            vo.setSnoozeCount(nz(i.getSnoozeCount()));
            vo.setManySnoozes(withOwner && nz(i.getSnoozeCount()) >= FollowupConstants.SNOOZE_WARN_TIMES);
            vo.setNoReplyStreak(nz(i.getNoReplyStreak()));
            vo.setNurtureUntil(i.getNurtureUntil());
            vo.setNurtureReason(i.getNurtureReason());
            SopBlockerDO nb = blockers.get(i.getNurtureReason());
            vo.setNurtureReasonName(nb == null ? "" : nb.getName());
            vo.setSuggestNurture(i.getNurtureUntil() == null && Cadence.suggestNurture(nz(i.getStageGroup()),
                    i.getSentAt() == null ? i.getCadenceFrom() : i.getSentAt().toLocalDate(),
                    nz(i.getFeedbackSeen()) > 0, nz(i.getNoReplyStreak()), today, params));
            vo.setSopHint(actions.getOrDefault(i.getStageCode(), Map.of()).getOrDefault("default", ""));
            vo.setOwnerId(i.getOwnerId());
            vo.setOwnerName(owners.getOrDefault(i.getOwnerId(), ""));
            vo.setOpportunityId(i.getOpportunityId());
            if (OrderStageRecorder.handles(i)) {
                fillOrder(vo, i, today, orderStages, afterSales);
            }
            if (AccountStageRecorder.handles(i)) {
                fillAccount(vo, i, c, today, followDays, contacts);
            }
            if (DevelopStageRecorder.handles(i)) {
                fillDevelop(vo, i, today, sequence, priorities);
                if (c != null) {
                    vo.setContactName(c.getContactName());
                    vo.setContactTitle(c.getContactTitle());
                    vo.setContactEmail(c.getContactEmail());
                }
            }
            out.add(vo);
        }
        return out;
    }

    /** 订单履约、售后评价行的列（第二期 spec「订单履约列表与分部分进度」「售后评价跟进对象与阶段」） */
    private static void fillOrder(FollowupItemVO vo, FollowupItemDO i, LocalDate today, Map<String, String> orderStages,
                                  Map<Long, AfterSalesDO> afterSales) {
        vo.setSituationText(i.getSituationText());
        vo.setPartCount(nz(i.getPartCount()));
        int ex = nz(i.getExceptionFlags());
        vo.setExceptionFlags(ex);
        vo.setExceptionText(FollowupConstants.EXCEPTION_NAMES.entrySet().stream().filter(e -> (ex & e.getKey()) != 0)
                .sorted(Map.Entry.comparingByKey()).map(Map.Entry::getValue).collect(Collectors.joining("、")));
        vo.setExceptionSince(i.getExceptionSince());
        vo.setEtaDate(i.getEtaDate());
        vo.setEtaSource(nz(i.getEtaSource()));
        vo.setEtaNote(i.getEtaNote());
        vo.setSignedDate(i.getSignedDate());
        vo.setSignedDays(i.getSignedDate() == null ? null : (int) ChronoUnit.DAYS.between(i.getSignedDate(), today));
        vo.setSatisfaction(nz(i.getSatisfaction()));
        vo.setSatisfactionName(FollowupConstants.SATISFACTION_NAMES.getOrDefault(nz(i.getSatisfaction()), ""));
        vo.setReviewStatus(nz(i.getReviewStatus()));
        vo.setReviewStatusName(FollowupConstants.REVIEW_NAMES.getOrDefault(nz(i.getReviewStatus()), ""));
        vo.setReviewPlatform(i.getReviewPlatform());
        AfterSalesDO a = afterSales.get(i.getAfterSalesId());
        if (a != null) {
            vo.setAfterSalesId(a.getId());
            vo.setAfterSalesNo(a.getAsNo());
            vo.setAfterSalesStatusName(AfterSalesConstants.AS_STATUS_NAMES.getOrDefault(a.getStatus(), ""));
        }
        vo.setStageSince(i.getStageSince());
    }

    /** 客户经营行的列（第三期 spec「客户经营列表与作战卡」）：上次联系超过等级间隔时标红 */
    private static void fillAccount(FollowupItemVO vo, FollowupItemDO i, CustomerDO c, LocalDate today, int[] followDays,
                                    Map<Long, LocalDate> contacts) {
        vo.setPatternText(i.getPatternText());
        vo.setPatternBrands(i.getPatternBrands());
        vo.setPatternNextDate(i.getPatternNextDate());
        vo.setRecentOrderCount(nz(i.getRecentOrderCount()));
        vo.setLastOrderDate(i.getLastOrderDate());
        vo.setAccountReason(i.getAccountReason());
        vo.setAccountNeed(nz(i.getAccountNeed()));
        vo.setCrossCategory(i.getCrossCategory());
        vo.setDormantSince(i.getDormantSince());
        vo.setDormantDays(i.getDormantSince() == null || i.getLastOrderDate() == null ? null
                : (int) ChronoUnit.DAYS.between(i.getLastOrderDate(), today));
        int grade = c == null ? 0 : nz(c.getBizGrade());
        vo.setStrategic(c != null && nz(c.getStrategic()) == 1);
        Integer days = grade >= 1 && grade <= 4 && followDays != null && followDays.length >= grade ? followDays[grade - 1] : null;
        vo.setFollowDays(days);
        LocalDate last = contacts.get(i.getCustomerId());
        if (last == null || (i.getLastOrderDate() != null && i.getLastOrderDate().isAfter(last))) {
            last = i.getLastOrderDate();
        }
        vo.setLastContactDate(last);
        vo.setContactOverdue(days != null && last != null && ChronoUnit.DAYS.between(last, today) > days);
    }

    /** 主动开发行的列（第四期 design D9）：为什么开发、触达次数、序列第几天、采购信号、背调优先级 */
    private static void fillDevelop(FollowupItemVO vo, FollowupItemDO i, LocalDate today, List<Integer> sequence,
                                    Map<Long, Integer> priorities) {
        vo.setProspectReason(i.getProspectReason());
        vo.setProspectSourceName(FollowupConstants.PROSPECT_SOURCES.getOrDefault(nz(i.getProspectSource()), ""));
        vo.setTouchCount(nz(i.getTouchCount()));
        vo.setFirstTouchDate(i.getFirstTouchDate());
        vo.setSignalText(i.getSignalText());
        vo.setReportPriority(priorities.get(i.getCustomerId()));
        vo.setSequenceDay(0);
        if ("D5".equals(i.getStageCode()) && i.getFirstTouchDate() != null) {
            DevelopCadence.Reminder r = DevelopCadence.of(
                    new DevelopCadence.Input("D5", false, null, i.getFirstTouchDate(), sequence, null), today);
            vo.setSequenceDay(r.day());
            vo.setSuggestNurture(i.getNurtureUntil() == null && r.suggestNurture());
        } else {
            vo.setSuggestNurture(false);
        }
    }

    /** 每个客户最近一份已完成背调的跟进优先级（1-立即重点跟进 … 4-不建议跟进） */
    private Map<Long, Integer> reportPriorities(Set<Long> customerIds) {
        if (customerIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Integer> out = new HashMap<>(customerIds.size() * 2);
        reportMapper.selectList(new LambdaQueryWrapper<CustomerBackgroundReportDO>()
                        .select(CustomerBackgroundReportDO::getCustomerId, CustomerBackgroundReportDO::getPriority)
                        .in(CustomerBackgroundReportDO::getCustomerId, customerIds)
                        .eq(CustomerBackgroundReportDO::getStatus, DevelopStageDeriver.REPORT_DONE)
                        .between(CustomerBackgroundReportDO::getPriority, 1, 4)
                        .isNull(CustomerBackgroundReportDO::getDeletedAt)
                        .orderByDesc(CustomerBackgroundReportDO::getId))
                .forEach(r -> out.putIfAbsent(r.getCustomerId(), r.getPriority()));
        return out;
    }

    /** 每个客户最近一条跟进记录的日期（任意板块，含系统记录） */
    private Map<Long, LocalDate> lastContacts(Set<Long> customerIds) {
        if (customerIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, LocalDate> out = new HashMap<>(customerIds.size() * 2);
        for (Map<String, Object> m : recordMapper.selectMaps(new QueryWrapper<CustomerFollowupDO>()
                .select("customer_id AS customerId", "MAX(create_time) AS lastAt")
                .in("customer_id", customerIds).isNull("deleted_at").groupBy("customer_id"))) {
            Object t = m.get("lastAt");
            LocalDate d = t instanceof LocalDateTime ldt ? ldt.toLocalDate()
                    : t instanceof java.sql.Timestamp ts ? ts.toLocalDateTime().toLocalDate() : null;
            if (d != null && m.get("customerId") instanceof Number n) {
                out.put(n.longValue(), d);
            }
        }
        return out;
    }

    /** 订单履约、售后评价、客户经营的阶段名（F1–F6、A1–A6、G4–G7，租户自己的覆盖平台的） */
    private Map<String, String> orderStageNames() {
        Map<String, String> out = new HashMap<>(16);
        sopStageMapper.selectList(new LambdaQueryWrapper<SopStageDO>()
                        .in(SopStageDO::getBoard, FollowupConstants.BOARD_DEVELOP, FollowupConstants.BOARD_FULFIL,
                                FollowupConstants.BOARD_AFTER_SALES, FollowupConstants.BOARD_ACCOUNT)
                        .eq(SopStageDO::getStatus, 1)).stream()
                .sorted(Comparator.comparing(SopStageDO::getTenantId))
                .forEach(st -> out.put(st.getStageCode(), st.getName()));
        return out;
    }

    private static List<Long> idsOf(List<FollowupItemDO> rows, int type) {
        return rows.stream().filter(r -> r.getTargetType() == type).map(FollowupItemDO::getTargetId).toList();
    }

    private static String gradeName(Integer g) {
        if (g == null) {
            return "";
        }
        return switch (g) {
            case 1 -> "S";
            case 2 -> "A";
            case 3 -> "B";
            case 4 -> "C";
            default -> "";
        };
    }

    /** 按排序后的 ID 取出跟进对象，保持顺序 */
    private List<FollowupItemDO> byRank(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, FollowupItemDO> m = itemMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(FollowupItemDO::getId, Function.identity()));
        return ids.stream().map(m::get).filter(Objects::nonNull).toList();
    }

    private FollowupItemFilter filter(Collection<Long> owners, LocalDate today) {
        FollowupSettings.Params p = settings.params(TenantContext.getTenantId());
        List<BigDecimal> t = p.amountTiers();
        FollowupItemFilter f = new FollowupItemFilter();
        f.setOwnerIds(owners);
        f.setToday(today);
        f.setTier1(t.size() > 0 ? t.get(0) : BigDecimal.ZERO);
        f.setTier2(t.size() > 1 ? t.get(1) : f.getTier1());
        f.setTier3(t.size() > 2 ? t.get(2) : f.getTier2());
        return f;
    }

    // ---------------------------------------------------------------- 可见范围

    /**
     * 负责人范围 → 负责人 ID 集合；null 表示不限（数据权限为全部）；空集合表示什么都看不到。
     * 没有「查看团队」权限时只能看自己。
     */
    private Collection<Long> owners(String ownerScope) {
        Long me = currentUser.resolve();
        if (isMe(ownerScope) || !perm.has(FollowupConstants.PERM_TEAM)) {
            return me == null ? List.of() : List.of(me);
        }
        DataScope scope = dataScopeResolver.current();
        if (SCOPE_ALL.equals(ownerScope)) {
            return scope.isAll() ? null : scope.ownerIds();
        }
        long owner;
        try {
            owner = Long.parseLong(ownerScope.trim());
        } catch (NumberFormatException e) {
            throw new BizException("负责人范围不正确", e);
        }
        if (!scope.canSee(owner) && !Objects.equals(owner, me)) {
            throw new BizException("没有权限查看这位业务员的跟进");
        }
        return List.of(owner);
    }

    private static boolean isMe(String ownerScope) {
        return !StringUtils.hasText(ownerScope) || SCOPE_ME.equals(ownerScope);
    }

    /** 未结束、在可见范围内（自己负责的，或有「查看团队」权限且在数据权限内的） */
    private FollowupItemDO requireOpen(Long id) {
        FollowupItemDO item = id == null ? null : itemMapper.selectById(id);
        if (item == null || item.getDeletedAt() != null) {
            throw new BizException("跟进对象不存在");
        }
        Long me = currentUser.resolve();
        boolean visible = Objects.equals(item.getOwnerId(), me)
                || (perm.has(FollowupConstants.PERM_TEAM) && dataScopeResolver.current().canSee(item.getOwnerId()));
        if (!visible) {
            throw new BizException("跟进对象不存在");
        }
        if (item.getClosedAt() != null) {
            throw new BizException("这条跟进已经结束，请刷新后重试");
        }
        return item;
    }

    private Map<Long, String> userNames(Collection<Long> ids) {
        Set<Integer> keys = ids.stream().filter(i -> i != null && i > 0).map(Long::intValue).collect(Collectors.toSet());
        Map<Long, String> names = new HashMap<>(keys.size() * 2 + 1);
        if (!keys.isEmpty()) {
            for (UserBasicDO u : userBasicMapper.selectBatchIds(keys)) {
                names.put(u.getId().longValue(), u.getName());
            }
        }
        return names;
    }

    private static boolean due(FollowupItemDO r) {
        return r.getNextActionDate() != null;
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }

    private static long nzl(Long v) {
        return v == null ? 0 : v;
    }
}
