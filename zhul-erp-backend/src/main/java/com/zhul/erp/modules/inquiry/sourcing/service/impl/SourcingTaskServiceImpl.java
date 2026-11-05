package com.zhul.erp.modules.inquiry.sourcing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.modules.quotation.support.QuotationLocks;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.utils.MigratedGuard;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.inquiry.constants.InquiryConstants;
import com.zhul.erp.modules.inquiry.customerinquiry.entity.CustomerInquiryDO;
import com.zhul.erp.modules.inquiry.item.entity.InquiryItemDO;
import com.zhul.erp.modules.inquiry.item.repository.InquiryItemMapper;
import com.zhul.erp.modules.inquiry.sourcing.dto.AssignRuleVO;
import com.zhul.erp.modules.inquiry.sourcing.dto.AssignRulesVO;
import com.zhul.erp.modules.inquiry.sourcing.dto.BoardStatsVO;
import com.zhul.erp.modules.inquiry.sourcing.dto.BoardTaskDetailVO;
import com.zhul.erp.modules.inquiry.sourcing.dto.BoardTaskItemVO;
import com.zhul.erp.modules.inquiry.sourcing.dto.ItemHistoryEntryVO;
import com.zhul.erp.modules.inquiry.sourcing.dto.BoardTaskVO;
import com.zhul.erp.modules.inquiry.sourcing.dto.BoardVO;
import com.zhul.erp.modules.inquiry.sourcing.dto.BrandGroupVO;
import com.zhul.erp.modules.inquiry.sourcing.dto.SalesPendingTaskVO;
import com.zhul.erp.modules.inquiry.sourcing.dto.SalesPendingVO;
import com.zhul.erp.modules.inquiry.sourcing.dto.PurchaserVO;
import com.zhul.erp.modules.inquiry.sourcing.dto.RulePreviewVO;
import com.zhul.erp.modules.inquiry.sourcing.dto.SaveAssignRuleRequest;
import com.zhul.erp.modules.inquiry.sourcing.entity.SourcingAssignRuleDO;
import com.zhul.erp.modules.inquiry.sourcing.entity.SourcingQuoteDO;
import com.zhul.erp.modules.inquiry.sourcing.entity.SourcingTaskAssigneeDO;
import com.zhul.erp.modules.inquiry.sourcing.entity.SourcingTaskDO;
import com.zhul.erp.modules.inquiry.sourcing.repository.SourcingAssignRuleMapper;
import com.zhul.erp.modules.inquiry.sourcing.repository.SourcingTaskAssigneeMapper;
import com.zhul.erp.modules.inquiry.sourcing.repository.SourcingTaskMapper;
import com.zhul.erp.modules.inquiry.sourcing.service.SourcingTaskService;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.inquiry.support.InquiryCategories;
import com.zhul.erp.modules.inquiry.support.InquiryCodeGenerator;
import com.zhul.erp.modules.inquiry.support.InquiryLookups;
import com.zhul.erp.modules.inquiry.support.PriceKeys;
import com.zhul.erp.modules.inquiry.support.SourcingSettings;
import com.zhul.erp.modules.inquiry.support.TaskTimeout;
import com.zhul.erp.modules.product.entity.ProductCategoryDO;
import com.zhul.erp.modules.product.support.CategoryMatcher;
import com.zhul.erp.modules.system.entity.RoleResourceDO;
import com.zhul.erp.modules.system.entity.SysLogDO;
import com.zhul.erp.modules.system.entity.UserBasicDO;
import com.zhul.erp.modules.system.repository.RoleResourceMapper;
import com.zhul.erp.modules.system.repository.UserBasicMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SourcingTaskServiceImpl implements SourcingTaskService {

    static final String MY_TASKS_PATH = "/inquiry/my-tasks";

    private final SourcingTaskMapper taskMapper;
    private final SourcingTaskAssigneeMapper assigneeMapper;
    private final SourcingAssignRuleMapper ruleMapper;
    private final InquiryCategories inquiryCategories;
    private final InquiryItemMapper itemMapper;
    private final UserBasicMapper userBasicMapper;
    private final RoleResourceMapper roleResourceMapper;
    private final SourcingSettings settings;
    private final PriceKeys priceKeys;
    private final CurrentUserResolver currentUser;
    private final InquiryLookups lookups;
    private final ObjectMapper objectMapper;
    private final com.zhul.erp.modules.inquiry.sourcing.repository.SourcingQuoteMapper quoteMapper;
    private final com.zhul.erp.modules.inquiry.customerinquiry.repository.CustomerInquiryMapper inquiryMapper;
    private final SourcingProgress progress;
    private final com.zhul.erp.modules.inquiry.sourcing.service.PriceHistoryService priceHistoryService;
    private final com.zhul.erp.modules.system.service.LogService logService;
    private final com.zhul.erp.modules.system.repository.SysLogMapper sysLogMapper;
    private final QuotationLocks quotationLocks;
    private final com.zhul.erp.framework.security.PermissionChecker perm;
    private final com.zhul.erp.modules.inquiry.support.TaskNameplates taskNameplates;
    private final TaskKeywordMatcher keywordMatcher;

    // ---------------------------------------------------------------- 生成与取消

    @Override
    public List<SourcingTaskDO> createTasks(CustomerInquiryDO inquiry, List<InquiryItemDO> pendingItems) {
        return createTasks(inquiry, pendingItems, 0);
    }

    @Override
    public List<SourcingTaskDO> createTasks(CustomerInquiryDO inquiry, List<InquiryItemDO> pendingItems, int startIndex) {
        // 按品牌 + 一级品类分组；没关联品类的型号按品类文字分组
        List<ProductCategoryDO> categories = inquiryCategories.categories();
        Map<String, List<InquiryItemDO>> groups = new LinkedHashMap<>();
        for (InquiryItemDO item : pendingItems) {
            groups.computeIfAbsent(InquiryCategories.groupKey(item.getBrandKey(), item.getCategoryId(), item.getCategory(), categories),
                    k -> new ArrayList<>()).add(item);
        }
        List<SourcingTaskDO> tasks = new ArrayList<>(groups.size());
        int index = startIndex;
        for (List<InquiryItemDO> items : groups.values()) {
            InquiryItemDO first = items.get(0);
            SourcingTaskDO task = new SourcingTaskDO();
            task.setTenantId(inquiry.getTenantId());
            task.setTaskCode(inquiry.getInquiryCode() + InquiryCodeGenerator.letterSuffix(index++));
            task.setCustomerInquiryId(inquiry.getId());
            task.setBrand(first.getBrand());
            task.setBrandKey(first.getBrandKey());
            ProductCategoryDO root = InquiryCategories.root(first.getCategoryId(), categories);
            task.setCategoryId(root == null ? null : root.getId());
            task.setCategory(root == null ? first.getCategory() : InquiryCategories.name(root));
            task.setItemCount(items.size());
            task.setUrgent(inquiry.getUrgent());
            LocalDateTime now = LocalDateTime.now();
            int window = settings.salesWindowMinutes(inquiry.getTenantId(), Objects.equals(inquiry.getUrgent(), 1));
            if (window > 0) {
                task.setStatus(InquiryConstants.TASK_SALES);
                task.setSalesDeadline(now.plusMinutes(window));
            } else {
                task.setStatus(InquiryConstants.TASK_UNASSIGNED);
                task.setPoolEnteredAt(now);
            }
            task.setReturnReason(0);
            task.setReturnNote("");
            taskMapper.insert(task);
            for (InquiryItemDO item : items) {
                item.setSourcingTaskId(task.getId());
                itemMapper.updateById(item);
            }
            tasks.add(task);
        }
        autoAssign(tasks.stream().filter(t -> t.getStatus() == InquiryConstants.TASK_UNASSIGNED).toList(), inquiry.getTenantId());
        return tasks;
    }

    /** 任务进入待分配时，开启自动分配就按规则分配；待业务处理和被退回的任务不走这里 */
    private void autoAssign(List<SourcingTaskDO> tasks, int tenantId) {
        if (tasks.isEmpty() || !settings.autoAssign(tenantId)) {
            return;
        }
        rulePlan(tasks, null).forEach((taskId, assigneeId) -> {
            SourcingTaskDO task = tasks.stream().filter(t -> t.getId().equals(taskId)).findFirst().orElseThrow();
            assignOne(task, assigneeId, InquiryConstants.ASSIGN_RULE, 0L);
        });
    }

    // ---------------------------------------------------------------- 业务员优先

    @Override
    public SalesPendingVO salesPending(int page, int pageSize) {
        page = Math.max(1, page);
        pageSize = Math.min(Math.max(1, pageSize), InquiryConstants.MAX_PAGE_SIZE);
        Long me = currentUser.resolve();
        SalesPendingVO result = new SalesPendingVO();
        result.setTotal(0L);
        result.setBrands(List.of());
        result.setTasks(List.of());
        List<CustomerInquiryDO> mine = inquiryMapper.selectList(new LambdaQueryWrapper<CustomerInquiryDO>()
                .select(CustomerInquiryDO::getId, CustomerInquiryDO::getInquiryCode)
                .eq(CustomerInquiryDO::getTenantId, tenantId())
                .eq(CustomerInquiryDO::getOwnerId, me)
                .isNull(CustomerInquiryDO::getDeletedAt)
                .inSql(CustomerInquiryDO::getId, "select customer_inquiry_id from sourcing_task where status = "
                        + InquiryConstants.TASK_SALES + " and deleted_at is null"));
        if (mine.isEmpty()) {
            return result;
        }
        Map<Long, String> inquiryCodes = mine.stream().collect(Collectors.toMap(CustomerInquiryDO::getId, CustomerInquiryDO::getInquiryCode));
        LocalDateTime now = LocalDateTime.now();
        Function<SourcingTaskDO, Long> remaining = t -> t.getSalesDeadline() == null ? 0L
                : Math.max(0, Duration.between(now, t.getSalesDeadline()).toMinutes());
        // 紧急在前，再按剩余时间从少到多
        List<SourcingTaskDO> all = taskMapper.selectList(new LambdaQueryWrapper<SourcingTaskDO>()
                        .eq(SourcingTaskDO::getStatus, InquiryConstants.TASK_SALES)
                        .in(SourcingTaskDO::getCustomerInquiryId, inquiryCodes.keySet())
                        .isNull(SourcingTaskDO::getDeletedAt))
                .stream().sorted(Comparator.comparing((SourcingTaskDO t) -> !Objects.equals(t.getUrgent(), 1))
                        .thenComparing(SourcingTaskDO::getSalesDeadline, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(SourcingTaskDO::getTaskCode))
                .toList();
        Map<String, BrandGroupVO> brands = new LinkedHashMap<>();
        for (SourcingTaskDO t : all) {
            BrandGroupVO g = brands.computeIfAbsent(t.getBrand(), b -> {
                BrandGroupVO v = new BrandGroupVO();
                v.setBrand(b);
                v.setTotal(0);
                v.setUrgent(0);
                v.setTimeout(0);
                return v;
            });
            g.setTotal(g.getTotal() + 1);
            g.setUrgent(g.getUrgent() + (Objects.equals(t.getUrgent(), 1) ? 1 : 0));
            long left = remaining.apply(t);
            g.setMinutes(g.getMinutes() == null ? left : Math.min(g.getMinutes(), left));
        }
        int from = Math.min((page - 1) * pageSize, all.size());
        List<SourcingTaskDO> slice = all.subList(from, Math.min(from + pageSize, all.size()));
        Map<Long, InquiryLookups.InquiryBrief> briefs = lookups.briefs(slice.stream().map(SourcingTaskDO::getCustomerInquiryId).toList(), false);
        result.setTotal((long) all.size());
        result.setBrands(List.copyOf(brands.values()));
        result.setTasks(slice.stream().map(t -> {
            InquiryLookups.InquiryBrief b = briefs.get(t.getCustomerInquiryId());
            SalesPendingTaskVO vo = new SalesPendingTaskVO();
            vo.setId(t.getId());
            vo.setTaskCode(t.getTaskCode());
            vo.setCustomerInquiryId(t.getCustomerInquiryId());
            vo.setInquiryCode(inquiryCodes.get(t.getCustomerInquiryId()));
            vo.setConfirmedAt(t.getCreateTime());
            vo.setUrgent(Objects.equals(t.getUrgent(), 1));
            vo.setBrand(t.getBrand());
            vo.setCategory(t.getCategory());
            vo.setItemCount(t.getItemCount());
            vo.setSalesDeadline(t.getSalesDeadline());
            vo.setRemainingMinutes(remaining.apply(t));
            if (b != null) {
                vo.setLevel(b.level());
                vo.setCustomerName(b.customerName());
                vo.setQuoteDeadline(b.quoteDeadline());
            }
            return vo;
        }).toList());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void claim(List<Long> taskIds) {
        Long me = currentUser.resolve();
        LocalDateTime now = LocalDateTime.now();
        for (SourcingTaskDO task : ownedSalesTasks(taskIds, me)) {
            // 带状态条件更新：和超时流转同时发生时只有一方成功
            int updated = taskMapper.update(null, new LambdaUpdateWrapper<SourcingTaskDO>()
                    .eq(SourcingTaskDO::getId, task.getId())
                    .eq(SourcingTaskDO::getStatus, InquiryConstants.TASK_SALES)
                    .set(SourcingTaskDO::getStatus, InquiryConstants.TASK_SOURCING)
                    .set(SourcingTaskDO::getSalesHandling, 1)
                    .set(SourcingTaskDO::getFirstAssignedAt, now)
                    .set(SourcingTaskDO::getUpdateTime, now));
            if (updated == 0) {
                throw new BizException("任务已转给采购");
            }
            insertAssignee(task, me, InquiryConstants.ASSIGN_SELF, me);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void transferToPurchasing(List<Long> taskIds) {
        for (SourcingTaskDO task : ownedSalesTasks(taskIds, currentUser.resolve())) {
            if (!moveToPool(task)) {
                throw new BizException("任务已转给采购");
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void moveToPoolIfDue(Long taskId) {
        SourcingTaskDO task = taskMapper.selectById(taskId);
        if (task != null && task.getStatus() == InquiryConstants.TASK_SALES && task.getSalesDeadline() != null
                && !task.getSalesDeadline().isAfter(LocalDateTime.now())) {
            moveToPool(task);
        }
    }

    /** 待业务处理进入待分配，开启自动分配时同时按规则分配；状态已变化时返回 false */
    private boolean moveToPool(SourcingTaskDO task) {
        LocalDateTime now = LocalDateTime.now();
        int updated = taskMapper.update(null, new LambdaUpdateWrapper<SourcingTaskDO>()
                .eq(SourcingTaskDO::getId, task.getId())
                .eq(SourcingTaskDO::getStatus, InquiryConstants.TASK_SALES)
                .set(SourcingTaskDO::getStatus, InquiryConstants.TASK_UNASSIGNED)
                .set(SourcingTaskDO::getPoolEnteredAt, now)
                .set(SourcingTaskDO::getUpdateTime, now));
        if (updated == 0) {
            return false;
        }
        task.setStatus(InquiryConstants.TASK_UNASSIGNED);
        task.setPoolEnteredAt(now);
        autoAssign(List.of(task), task.getTenantId());
        return true;
    }

    /** 只有询盘单当前负责人能处理；任务已不在待业务处理时提示已转走 */
    private List<SourcingTaskDO> ownedSalesTasks(List<Long> taskIds, Long me) {
        List<SourcingTaskDO> tasks = tasks(taskIds);
        Map<Long, CustomerInquiryDO> inquiries = inquiryMapper.selectBatchIds(tasks.stream().map(SourcingTaskDO::getCustomerInquiryId).distinct().toList())
                .stream().collect(Collectors.toMap(CustomerInquiryDO::getId, i -> i));
        for (SourcingTaskDO t : tasks) {
            CustomerInquiryDO inquiry = inquiries.get(t.getCustomerInquiryId());
            if (me == null || inquiry == null || !me.equals(inquiry.getOwnerId())) {
                throw new BizException("只有询盘负责人可以处理这个任务");
            }
            if (t.getStatus() != InquiryConstants.TASK_SALES) {
                throw new BizException("任务已转给采购");
            }
        }
        return tasks;
    }

    @Override
    public void cancelByInquiry(Long inquiryId) {
        List<SourcingTaskDO> tasks = taskMapper.selectList(new LambdaQueryWrapper<SourcingTaskDO>()
                .eq(SourcingTaskDO::getCustomerInquiryId, inquiryId)
                .ne(SourcingTaskDO::getStatus, InquiryConstants.TASK_CANCELLED)
                .isNull(SourcingTaskDO::getDeletedAt));
        for (SourcingTaskDO task : tasks) {
            if (task.getStatus() != InquiryConstants.TASK_DONE) {
                task.setStatus(InquiryConstants.TASK_CANCELLED);
                taskMapper.updateById(task);
            }
            deactivateAssignees(task.getId());
        }
    }

    @Override
    public void syncUrgent(Long inquiryId, int urgent) {
        taskMapper.update(null, new LambdaUpdateWrapper<SourcingTaskDO>()
                .eq(SourcingTaskDO::getCustomerInquiryId, inquiryId)
                .in(SourcingTaskDO::getStatus, InquiryConstants.TASK_UNASSIGNED, InquiryConstants.TASK_SOURCING, InquiryConstants.TASK_SALES)
                .isNull(SourcingTaskDO::getDeletedAt)
                .set(SourcingTaskDO::getUrgent, urgent)
                .set(SourcingTaskDO::getUpdateTime, LocalDateTime.now()));
        // 待业务处理的任务按新的紧急标记，从生成时间重算流转时间；时限已过的由下一次流转检查转走
        for (SourcingTaskDO t : taskMapper.selectList(new LambdaQueryWrapper<SourcingTaskDO>()
                .eq(SourcingTaskDO::getCustomerInquiryId, inquiryId)
                .eq(SourcingTaskDO::getStatus, InquiryConstants.TASK_SALES)
                .isNull(SourcingTaskDO::getDeletedAt))) {
            taskMapper.update(null, new LambdaUpdateWrapper<SourcingTaskDO>()
                    .eq(SourcingTaskDO::getId, t.getId())
                    .set(SourcingTaskDO::getSalesDeadline, t.getCreateTime().plusMinutes(settings.salesWindowMinutes(t.getTenantId(), urgent == 1))));
        }
    }

    // ---------------------------------------------------------------- 工作台

    @Override
    public com.zhul.erp.modules.inquiry.sourcing.dto.TaskNameplatesVO nameplates(Long taskId) {
        return taskNameplates.of(boardTask(taskId));
    }

    @Override
    public com.zhul.erp.framework.storage.FileStorageService.FileLink nameplateLink(Long taskId, Long attachmentId, boolean download) {
        return taskNameplates.link(boardTask(taskId), attachmentId, download);
    }

    private SourcingTaskDO boardTask(Long taskId) {
        SourcingTaskDO t = taskId == null ? null : taskMapper.selectById(taskId);
        if (t == null || t.getDeletedAt() != null || !Objects.equals(t.getTenantId(), tenantId())) {
            throw new BizException("询价任务不存在");
        }
        return t;
    }

    @Override
    public BoardTaskDetailVO taskDetail(Long taskId) {
        SourcingTaskDO t = taskMapper.selectById(taskId);
        if (t == null || t.getDeletedAt() != null || !Objects.equals(t.getTenantId(), tenantId())) {
            throw new BizException("询价任务不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        TaskTimeout limits = TaskTimeout.of(settings, tenantId());
        Map<Long, List<SourcingTaskAssigneeDO>> assignees = activeAssignees(List.of(taskId));
        Map<Long, PurchaserVO> purchaserById = purchasers(List.of(t), assignees).stream()
                .collect(Collectors.toMap(PurchaserVO::getId, p -> p));
        BoardTaskVO vo = new BoardTaskVO();
        vo.setId(t.getId());
        vo.setTaskCode(t.getTaskCode());
        vo.setCustomerInquiryId(t.getCustomerInquiryId());
        vo.setBrand(t.getBrand());
        vo.setCategory(t.getCategory());
        vo.setItemCount(t.getItemCount());
        vo.setPricedCount(pricedCount(t.getId()));
        vo.setUrgent(Objects.equals(t.getUrgent(), 1));
        vo.setStatus(t.getStatus());
        vo.setTimeout(t.getStatus() == InquiryConstants.TASK_SOURCING && limits.isTimeout(t, now));
        vo.setWaitingMinutes(t.getStatus() == InquiryConstants.TASK_UNASSIGNED ? waitingMinutes(t, now) : null);
        vo.setFirstAssignedAt(t.getFirstAssignedAt());
        vo.setReturnReason(t.getReturnReason());
        vo.setReturnReasonLabel(InquiryConstants.RETURN_REASONS.get(t.getReturnReason()));
        vo.setReturnNote(t.getReturnNote());
        vo.setReturnedByName(lookups.userNames(java.util.Collections.singletonList(t.getReturnedBy())).get(t.getReturnedBy()));
        vo.setAssignees(assignees.getOrDefault(taskId, List.of()).stream()
                .map(a -> purchaserById.get(a.getAssigneeId())).filter(Objects::nonNull).toList());
        applyBrief(vo, lookups.briefs(List.of(t.getCustomerInquiryId()), lookups.isPartTime(currentUser.resolve()))
                .get(t.getCustomerInquiryId()));
        List<InquiryItemDO> itemRows = itemMapper.selectList(new LambdaQueryWrapper<InquiryItemDO>()
                .eq(InquiryItemDO::getSourcingTaskId, taskId)
                .isNull(InquiryItemDO::getDeletedAt)
                .orderByAsc(InquiryItemDO::getLineNo));
        Map<Long, List<SourcingQuoteDO>> quotesByItem = itemRows.isEmpty() ? Map.of() : quoteMapper.selectList(new LambdaQueryWrapper<SourcingQuoteDO>()
                        .in(SourcingQuoteDO::getInquiryItemId, itemRows.stream().map(InquiryItemDO::getId).toList())
                        .eq(SourcingQuoteDO::getStatus, InquiryConstants.QUOTE_SUBMITTED)
                        .isNull(SourcingQuoteDO::getDeletedAt)
                        .orderByAsc(SourcingQuoteDO::getId))
                .stream().collect(Collectors.groupingBy(SourcingQuoteDO::getInquiryItemId));
        List<BoardTaskItemVO> items = itemRows.stream().map(i -> {
                    BoardTaskItemVO r = new BoardTaskItemVO();
                    r.setId(i.getId());
                    r.setModel(i.getConfirmedModel());
                    r.setOriginalModel(i.getOriginalModel());
                    r.setQuantity(i.getQuantity());
                    r.setUnit(i.getUnit());
                    r.setDescription(i.getDescription());
                    r.setLifecycle(i.getLifecycle());
                    r.setReplacementModel(i.getReplacementModel());
                    r.setDifficulty(i.getDifficulty());
                    r.setQuoteStatus(i.getQuoteStatus());
                    r.setSelectedQuoteId(i.getSelectedQuoteId());
                    r.setCostManual(Objects.equals(i.getCostManual(), 1));
                    r.setQuotes(priceHistoryService.toVos(quotesByItem.getOrDefault(i.getId(), List.of())));
                    r.setChangeCount(changeCount(i.getId()));
                    return r;
                }).toList();
        BoardTaskDetailVO detail = new BoardTaskDetailVO();
        detail.setTask(vo);
        detail.setItems(items);
        return detail;
    }

    @Override
    public BoardVO board(Integer status, int page, int pageSize) {
        return board(status, page, pageSize, null, null);
    }

    /**
     * 分配工作台。keyword / purchaserId 只筛选列出的任务与页签数字，顶部统计仍按全部任务
     * （spec inquiry/sourcing-task「分配工作台搜索与筛选」）。
     */
    @Override
    public BoardVO board(Integer status, int page, int pageSize, String keyword, Long purchaserId) {
        page = Math.max(1, page);
        pageSize = Math.min(Math.max(1, pageSize), InquiryConstants.MAX_PAGE_SIZE);
        int tenantId = tenantId();
        int status0 = status == null ? InquiryConstants.TASK_UNASSIGNED : status;
        List<SourcingTaskDO> open = taskMapper.selectList(new LambdaQueryWrapper<SourcingTaskDO>()
                .eq(SourcingTaskDO::getTenantId, tenantId)
                .in(SourcingTaskDO::getStatus, InquiryConstants.TASK_UNASSIGNED, InquiryConstants.TASK_SOURCING)
                .eq(SourcingTaskDO::getSalesHandling, 0)
                .isNull(SourcingTaskDO::getDeletedAt));
        // 已回价页签：任务已回齐、询盘单还在「询价中 / 可报价」（业务员没报价），回价与成本价仍可调整
        LambdaQueryWrapper<SourcingTaskDO> doneQuery = new LambdaQueryWrapper<SourcingTaskDO>()
                .eq(SourcingTaskDO::getTenantId, tenantId)
                .eq(SourcingTaskDO::getStatus, InquiryConstants.TASK_DONE)
                .isNull(SourcingTaskDO::getDeletedAt)
                .inSql(SourcingTaskDO::getCustomerInquiryId, "select id from customer_inquiry where status in ("
                        + InquiryConstants.STATUS_SOURCING + "," + InquiryConstants.STATUS_READY_TO_QUOTE + ") and deleted_at is null");
        List<SourcingTaskDO> doneTasks = taskMapper.selectList(doneQuery);
        long doneCount = doneTasks.size();
        if (status0 == InquiryConstants.TASK_DONE) {
            open = new ArrayList<>(open);
            open.addAll(doneTasks);
        }
        Map<Long, List<SourcingQuoteDO>> reviewByTask = pendingReviewByTask(tenantId);
        java.util.Set<Long> matched = matchedTasks(open, doneTasks, reviewByTask.keySet(), keyword, purchaserId);
        java.util.function.LongPredicate pass = id -> matched == null || matched.contains(id);
        if (status0 == InquiryConstants.BOARD_TAB_REVIEW) {
            // 多人比价时正式采购可能已回齐（任务已回价），兼职那份仍在审核
            java.util.Set<Long> loaded = open.stream().map(SourcingTaskDO::getId).collect(Collectors.toSet());
            List<Long> doneIds = reviewByTask.keySet().stream().filter(id -> !loaded.contains(id)).toList();
            if (!doneIds.isEmpty()) {
                open = new ArrayList<>(open);
                open.addAll(taskMapper.selectList(new LambdaQueryWrapper<SourcingTaskDO>()
                        .in(SourcingTaskDO::getId, doneIds)
                        .eq(SourcingTaskDO::getStatus, InquiryConstants.TASK_DONE)
                        .isNull(SourcingTaskDO::getDeletedAt)));
            }
        }
        // 负载与顶部统计按全部任务计算
        Map<Long, List<SourcingTaskAssigneeDO>> assignees = activeAssignees(open.stream().map(SourcingTaskDO::getId).toList());
        List<PurchaserVO> purchasers = purchasers(open, assignees);
        LocalDateTime now = LocalDateTime.now();
        TaskTimeout limits = TaskTimeout.of(settings, tenantId());

        List<SourcingTaskDO> listed = new ArrayList<>();
        java.util.Set<Long> timeoutIds = new java.util.HashSet<>();
        BoardStatsVO stats = new BoardStatsVO();
        int unassigned = 0, sourcing = 0, multi = 0, timeout = 0, returned = 0, doubt = 0;
        long longest = 0;
        for (SourcingTaskDO t : open) {
            boolean isTimeout = t.getStatus() != InquiryConstants.TASK_DONE && limits.isTimeout(t, now);
            if (isTimeout) {
                timeoutIds.add(t.getId());
            }
            if (t.getStatus() == InquiryConstants.TASK_DONE) {
                // 已回价的任务不计入待分配 / 询价中统计
            } else if (t.getStatus() == InquiryConstants.TASK_UNASSIGNED) {
                unassigned++;
                longest = Math.max(longest, waitingMinutes(t, now));
                if (t.getReturnReason() != null && t.getReturnReason() > 0) {
                    returned++;
                    if (t.getReturnReason() == InquiryConstants.RETURN_MODEL_DOUBT) {
                        doubt++;
                    }
                }
            } else if (!reviewByTask.containsKey(t.getId())) {
                // 有待审核回价的任务归「待审核」页签，不重复计入询价中
                sourcing++;
                if (assignees.getOrDefault(t.getId(), List.of()).size() > 1) {
                    multi++;
                }
                if (isTimeout) {
                    timeout++;
                }
            }
            List<SourcingQuoteDO> pendingReview = reviewByTask.get(t.getId());
            boolean isListed = status0 == InquiryConstants.BOARD_TAB_REVIEW ? pendingReview != null
                    : t.getStatus() == status0 && !(status0 == InquiryConstants.TASK_SOURCING && pendingReview != null);
            if (isListed && pass.test(t.getId())) {
                listed.add(t);
            }
        }
        BoardVO.TabCounts tabs = new BoardVO.TabCounts();
        tabs.setUnassigned((int) open.stream().filter(t -> t.getStatus() == InquiryConstants.TASK_UNASSIGNED && pass.test(t.getId())).count());
        tabs.setSourcing((int) open.stream().filter(t -> t.getStatus() == InquiryConstants.TASK_SOURCING
                && !reviewByTask.containsKey(t.getId()) && pass.test(t.getId())).count());
        tabs.setReview((int) reviewByTask.keySet().stream().filter(pass::test).count());
        tabs.setDone((int) doneTasks.stream().filter(t -> pass.test(t.getId())).count());

        // 排序要用询盘等级，询盘信息只查列出的任务
        Map<Long, InquiryLookups.InquiryBrief> briefs = lookups.briefs(listed.stream().map(SourcingTaskDO::getCustomerInquiryId).toList(),
                lookups.isPartTime(currentUser.resolve()));
        Function<SourcingTaskDO, Integer> level = t -> {
            InquiryLookups.InquiryBrief b = briefs.get(t.getCustomerInquiryId());
            return b == null ? null : b.level();
        };
        Function<SourcingTaskDO, Boolean> notUrgent = t -> !Objects.equals(t.getUrgent(), 1);
        if (status0 == InquiryConstants.BOARD_TAB_REVIEW) {
            // 紧急 → 询盘等级（S 最先）→ 最早提交的在前
            Function<SourcingTaskDO, LocalDateTime> submitted = t -> reviewByTask.get(t.getId()).stream()
                    .map(SourcingQuoteDO::getQuotedAt).filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null);
            listed.sort(Comparator.comparing(notUrgent)
                    .thenComparing(level, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(submitted, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(SourcingTaskDO::getTaskCode));
        } else {
            // 超时 → 紧急 → 询盘等级（S 最先）→ 等得久的在前
            Function<SourcingTaskDO, Long> waiting = t -> t.getStatus() == InquiryConstants.TASK_UNASSIGNED ? waitingMinutes(t, now) : null;
            listed.sort(Comparator.comparing((SourcingTaskDO t) -> !timeoutIds.contains(t.getId()))
                    .thenComparing(notUrgent)
                    .thenComparing(level, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(waiting, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(SourcingTaskDO::getFirstAssignedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(SourcingTaskDO::getTaskCode));
        }
        Map<String, BrandGroupVO> brands = new LinkedHashMap<>();
        for (SourcingTaskDO t : listed) {
            BrandGroupVO g = brands.computeIfAbsent(t.getBrand(), b -> {
                BrandGroupVO v = new BrandGroupVO();
                v.setBrand(b);
                v.setTotal(0);
                v.setUrgent(0);
                v.setTimeout(0);
                v.setMinutes(0L);
                return v;
            });
            g.setTotal(g.getTotal() + 1);
            g.setUrgent(g.getUrgent() + (Objects.equals(t.getUrgent(), 1) ? 1 : 0));
            g.setTimeout(g.getTimeout() + (timeoutIds.contains(t.getId()) ? 1 : 0));
            if (t.getStatus() == InquiryConstants.TASK_UNASSIGNED) {
                g.setMinutes(Math.max(g.getMinutes(), waitingMinutes(t, now)));
            }
        }

        // 只给当前页的任务补齐推荐、回价数、退回人等明细
        int from = Math.min((page - 1) * pageSize, listed.size());
        List<SourcingTaskDO> slice = listed.subList(from, Math.min(from + pageSize, listed.size()));
        List<Long> sliceIds = slice.stream().map(SourcingTaskDO::getId).toList();
        Map<Long, Integer> priced = pricedCounts(sliceIds);
        Map<Long, String> names = lookups.userNames(slice.stream().map(SourcingTaskDO::getReturnedBy).toList());
        Map<Long, String> selfNames = lookups.userNames(sliceIds.stream().flatMap(id -> assignees.getOrDefault(id, List.of()).stream())
                .filter(a -> Objects.equals(a.getAssignMode(), InquiryConstants.ASSIGN_SELF))
                .map(SourcingTaskAssigneeDO::getAssigneeId).distinct().toList());
        Map<String, Map<Long, Long>> familiarity = slice.stream().anyMatch(t -> t.getStatus() == InquiryConstants.TASK_UNASSIGNED)
                ? familiarity(tenantId) : Map.of();
        Map<Long, PurchaserVO> purchaserById = purchasers.stream().collect(Collectors.toMap(PurchaserVO::getId, p -> p));
        List<BoardTaskVO> rows = new ArrayList<>(slice.size());
        for (SourcingTaskDO t : slice) {
            List<SourcingTaskAssigneeDO> as = assignees.getOrDefault(t.getId(), List.of());
            BoardTaskVO vo = new BoardTaskVO();
            vo.setId(t.getId());
            vo.setTaskCode(t.getTaskCode());
            vo.setCustomerInquiryId(t.getCustomerInquiryId());
            applyBrief(vo, briefs.get(t.getCustomerInquiryId()));
            vo.setBrand(t.getBrand());
            vo.setCategory(t.getCategory());
            vo.setItemCount(t.getItemCount());
            vo.setPricedCount(priced.getOrDefault(t.getId(), 0));
            vo.setUrgent(Objects.equals(t.getUrgent(), 1));
            vo.setStatus(t.getStatus());
            vo.setTimeout(timeoutIds.contains(t.getId()));
            vo.setWaitingMinutes(t.getStatus() == InquiryConstants.TASK_UNASSIGNED ? waitingMinutes(t, now) : null);
            vo.setFirstAssignedAt(t.getFirstAssignedAt());
            vo.setReturnReason(t.getReturnReason());
            vo.setReturnReasonLabel(InquiryConstants.RETURN_REASONS.get(t.getReturnReason()));
            vo.setReturnNote(t.getReturnNote());
            vo.setReturnedByName(names.get(t.getReturnedBy()));
            vo.setAssignees(as.stream().map(a -> purchaserById.get(a.getAssigneeId())).filter(Objects::nonNull).toList());
            as.stream().filter(a -> Objects.equals(a.getAssignMode(), InquiryConstants.ASSIGN_SELF)).findFirst()
                    .ifPresent(a -> vo.setSelfSourcedBy(selfNames.get(a.getAssigneeId())));
            if (t.getStatus() == InquiryConstants.TASK_UNASSIGNED) {
                Recommendation rec = recommend(t, purchasers, familiarity);
                if (rec != null) {
                    vo.setRecommendedId(rec.purchaser.getId());
                    vo.setRecommendedName(rec.purchaser.getName());
                    vo.setRecommendReason(rec.reason);
                }
            }
            List<SourcingQuoteDO> pendingReview = reviewByTask.get(t.getId());
            if (pendingReview != null) {
                applyReview(vo, pendingReview, now);
            }
            rows.add(vo);
        }
        stats.setUnassigned(unassigned);
        stats.setLongestWaitingMinutes(longest);
        stats.setSourcing(sourcing);
        stats.setMultiAssigned(multi);
        stats.setTimeout(timeout);
        stats.setReturned(returned);
        stats.setReturnedForDoubt(doubt);
        stats.setDone((int) doneCount);
        stats.setPendingReview(reviewByTask.size());
        stats.setPendingReviewItems((int) reviewByTask.values().stream().flatMap(List::stream)
                .map(SourcingQuoteDO::getInquiryItemId).distinct().count());
        stats.setLongestReviewMinutes(reviewByTask.values().stream().flatMap(List::stream)
                .map(SourcingQuoteDO::getQuotedAt).filter(Objects::nonNull).min(Comparator.naturalOrder())
                .map(at -> Math.max(0, Duration.between(at, now).toMinutes())).orElse(0L));
        stats.setTimeoutHours(limits.normalHours());
        stats.setUrgentTimeoutHours(limits.urgentHours());
        stats.setAutoAssign(settings.autoAssign(tenantId));
        BoardVO vo = new BoardVO();
        vo.setStats(stats);
        vo.setTabs(tabs);
        vo.setTotal((long) listed.size());
        vo.setBrands(List.copyOf(brands.values()));
        vo.setTasks(rows);
        vo.setPurchasers(withSelf(purchasers, assignees));
        return vo;
    }

    private static final int HISTORY_DEFAULT_DAYS = 30;
    private static final Map<Integer, String> HISTORY_STATES = Map.of(1, "待分配", 2, "询价中", 3, "已回价", 4, "已报价", 5, "已关闭");

    @Override
    public com.zhul.erp.common.result.PageResult<com.zhul.erp.modules.inquiry.sourcing.dto.BoardHistoryRowVO> history(
            String keyword, Long purchaserId, Integer state, java.time.LocalDate dateFrom, java.time.LocalDate dateTo, int page, int pageSize) {
        page = Math.max(1, page);
        pageSize = Math.min(Math.max(1, pageSize), InquiryConstants.MAX_PAGE_SIZE);
        int tenantId = tenantId();
        java.time.LocalDate to = dateTo != null ? dateTo : java.time.LocalDate.now();
        java.time.LocalDate from = dateFrom != null ? dateFrom : to.minusDays(HISTORY_DEFAULT_DAYS - 1L);
        String closedInquiries = "select id from customer_inquiry where tenant_id = " + tenantId + " and status = " + InquiryConstants.STATUS_CANCELLED;
        LambdaQueryWrapper<SourcingTaskDO> w = new LambdaQueryWrapper<SourcingTaskDO>()
                .eq(SourcingTaskDO::getTenantId, tenantId)
                .isNull(SourcingTaskDO::getDeletedAt)
                .ge(SourcingTaskDO::getCreateTime, from.atStartOfDay())
                .lt(SourcingTaskDO::getCreateTime, to.plusDays(1).atStartOfDay());
        if (StringUtils.hasText(keyword)) {
            String trimmed = keyword.trim();
            String kw = trimmed.length() > 64 ? trimmed.substring(0, 64) : trimmed;
            String like = "%" + kw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            String key = PriceKeys.model(kw);
            String keyLike = StringUtils.hasText(key) ? "%" + key + "%" : like;
            w.and(x -> x.like(SourcingTaskDO::getTaskCode, kw).or().like(SourcingTaskDO::getBrand, kw).or().like(SourcingTaskDO::getCategory, kw)
                    .or().apply("customer_inquiry_id in (select id from customer_inquiry where tenant_id = {0} and inquiry_code like {1})",
                            tenantId, like)
                    .or().apply("id in (select sourcing_task_id from inquiry_item where tenant_id = {0} and deleted_at is null"
                            + " and (confirmed_model like {1} or original_model like {1} or model_key like {2}))", tenantId, like, keyLike));
        }
        if (purchaserId != null) {
            // 曾经分配过的都算（含被改派掉的）
            w.apply("id in (select task_id from sourcing_task_assignee where tenant_id = {0} and assignee_id = {1} and deleted_at is null)",
                    tenantId, purchaserId);
        }
        if (state != null) {
            switch (state) {
                case 1 -> w.in(SourcingTaskDO::getStatus, InquiryConstants.TASK_UNASSIGNED, InquiryConstants.TASK_SALES)
                        .notInSql(SourcingTaskDO::getCustomerInquiryId, closedInquiries);
                case 2 -> w.eq(SourcingTaskDO::getStatus, InquiryConstants.TASK_SOURCING)
                        .notInSql(SourcingTaskDO::getCustomerInquiryId, closedInquiries);
                case 3 -> w.eq(SourcingTaskDO::getStatus, InquiryConstants.TASK_DONE)
                        .inSql(SourcingTaskDO::getCustomerInquiryId, "select id from customer_inquiry where tenant_id = " + tenantId
                                + " and status in (" + InquiryConstants.STATUS_SOURCING + "," + InquiryConstants.STATUS_READY_TO_QUOTE + ")");
                case 4 -> w.eq(SourcingTaskDO::getStatus, InquiryConstants.TASK_DONE)
                        .inSql(SourcingTaskDO::getCustomerInquiryId, "select id from customer_inquiry where tenant_id = " + tenantId
                                + " and status in (" + InquiryConstants.STATUS_QUOTED + "," + InquiryConstants.STATUS_WON + ","
                                + InquiryConstants.STATUS_LOST + ")");
                case 5 -> w.and(x -> x.eq(SourcingTaskDO::getStatus, InquiryConstants.TASK_CANCELLED)
                        .or().inSql(SourcingTaskDO::getCustomerInquiryId, closedInquiries));
                default -> throw new BizException("状态不存在");
            }
        }
        w.orderByDesc(SourcingTaskDO::getUpdateTime).orderByDesc(SourcingTaskDO::getId);
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<SourcingTaskDO> p =
                taskMapper.selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, pageSize), w);
        List<SourcingTaskDO> tasks = p.getRecords();
        List<Long> ids = tasks.stream().map(SourcingTaskDO::getId).toList();
        Map<Long, CustomerInquiryDO> inquiries = tasks.isEmpty() ? Map.of() : inquiryMapper.selectList(new LambdaQueryWrapper<CustomerInquiryDO>()
                        .select(CustomerInquiryDO::getId, CustomerInquiryDO::getInquiryCode, CustomerInquiryDO::getStatus)
                        .in(CustomerInquiryDO::getId, tasks.stream().map(SourcingTaskDO::getCustomerInquiryId).distinct().toList()))
                .stream().collect(Collectors.toMap(CustomerInquiryDO::getId, i -> i));
        List<SourcingTaskAssigneeDO> as = ids.isEmpty() ? List.of() : assigneeMapper.selectList(new LambdaQueryWrapper<SourcingTaskAssigneeDO>()
                .in(SourcingTaskAssigneeDO::getTaskId, ids)
                .isNull(SourcingTaskAssigneeDO::getDeletedAt));
        Map<Long, String> names = lookups.userNames(as.stream().map(SourcingTaskAssigneeDO::getAssigneeId).distinct().toList());
        List<com.zhul.erp.modules.inquiry.sourcing.dto.BoardHistoryRowVO> rows = new ArrayList<>(tasks.size());
        for (SourcingTaskDO t : tasks) {
            CustomerInquiryDO inq = inquiries.get(t.getCustomerInquiryId());
            var vo = new com.zhul.erp.modules.inquiry.sourcing.dto.BoardHistoryRowVO();
            vo.setId(t.getId());
            vo.setTaskCode(t.getTaskCode());
            vo.setCustomerInquiryId(t.getCustomerInquiryId());
            vo.setInquiryCode(inq == null ? null : inq.getInquiryCode());
            vo.setBrand(t.getBrand());
            vo.setItemCount(t.getItemCount());
            vo.setUrgent(Objects.equals(t.getUrgent(), 1));
            vo.setPurchaserNames(as.stream().filter(a -> a.getTaskId().equals(t.getId()))
                    .sorted(Comparator.comparing((SourcingTaskAssigneeDO a) -> !Objects.equals(a.getActive(), 1)))
                    .map(a -> names.get(a.getAssigneeId())).filter(Objects::nonNull).distinct().toList());
            int st = historyState(t, inq);
            vo.setState(st);
            vo.setStateName(HISTORY_STATES.get(st));
            vo.setSalesHandling(Objects.equals(t.getSalesHandling(), 1) || Objects.equals(t.getStatus(), InquiryConstants.TASK_SALES));
            vo.setCompletedAt(t.getCompletedAt());
            vo.setCreateTime(t.getCreateTime());
            vo.setCreateBy(t.getCreateBy());
            vo.setUpdateTime(t.getUpdateTime());
            vo.setUpdateBy(t.getUpdateBy());
            rows.add(vo);
        }
        return com.zhul.erp.common.result.PageResult.of(p.getTotal(), rows);
    }

    /** 显示状态：任务作废或询盘取消为已关闭；已回价按询盘是否已报价分为已回价 / 已报价 */
    private static int historyState(SourcingTaskDO t, CustomerInquiryDO inq) {
        int inquiryStatus = inq == null || inq.getStatus() == null ? 0 : inq.getStatus();
        if (t.getStatus() == InquiryConstants.TASK_CANCELLED || inquiryStatus == InquiryConstants.STATUS_CANCELLED) {
            return 5;
        }
        if (t.getStatus() == InquiryConstants.TASK_SOURCING) {
            return 2;
        }
        if (t.getStatus() == InquiryConstants.TASK_DONE) {
            return inquiryStatus == InquiryConstants.STATUS_QUOTED || inquiryStatus == InquiryConstants.STATUS_WON
                    || inquiryStatus == InquiryConstants.STATUS_LOST ? 4 : 3;
        }
        return 1;
    }

    /** 关键词与采购员筛选命中的任务 ID；都没填时返回 null（不筛选）。采购员按当前在办（有效分配）判断 */
    private java.util.Set<Long> matchedTasks(List<SourcingTaskDO> open, List<SourcingTaskDO> done, java.util.Set<Long> reviewIds,
                                             String keyword, Long purchaserId) {
        if (!StringUtils.hasText(keyword) && purchaserId == null) {
            return null;
        }
        Map<Long, SourcingTaskDO> all = new LinkedHashMap<>();
        open.forEach(t -> all.put(t.getId(), t));
        done.forEach(t -> all.putIfAbsent(t.getId(), t));
        List<Long> missing = reviewIds.stream().filter(id -> !all.containsKey(id)).toList();
        if (!missing.isEmpty()) {
            taskMapper.selectBatchIds(missing).forEach(t -> all.putIfAbsent(t.getId(), t));
        }
        List<SourcingTaskDO> hits = keywordMatcher.matching(new ArrayList<>(all.values()), keyword);
        if (purchaserId != null && !hits.isEmpty()) {
            java.util.Set<Long> mine = assigneeMapper.selectList(new LambdaQueryWrapper<SourcingTaskAssigneeDO>()
                            .select(SourcingTaskAssigneeDO::getTaskId)
                            .in(SourcingTaskAssigneeDO::getTaskId, hits.stream().map(SourcingTaskDO::getId).toList())
                            .eq(SourcingTaskAssigneeDO::getAssigneeId, purchaserId)
                            .eq(SourcingTaskAssigneeDO::getActive, 1)
                            .isNull(SourcingTaskAssigneeDO::getDeletedAt))
                    .stream().map(SourcingTaskAssigneeDO::getTaskId).collect(Collectors.toSet());
            hits = hits.stream().filter(t -> mine.contains(t.getId())).toList();
        }
        return hits.stream().map(SourcingTaskDO::getId).collect(Collectors.toSet());
    }

    @Override
    public List<PurchaserVO> purchasers() {
        List<SourcingTaskDO> open = taskMapper.selectList(new LambdaQueryWrapper<SourcingTaskDO>()
                .eq(SourcingTaskDO::getTenantId, tenantId())
                .eq(SourcingTaskDO::getStatus, InquiryConstants.TASK_SOURCING)
                .eq(SourcingTaskDO::getSalesHandling, 0)
                .isNull(SourcingTaskDO::getDeletedAt));
        Map<Long, List<SourcingTaskAssigneeDO>> assignees = activeAssignees(open.stream().map(SourcingTaskDO::getId).toList());
        return withSelf(purchasers(open, assignees), assignees);
    }

    /**
     * 手动分配的候选人：采购人员之外，再加上正在分配的人自己（能打开「我的询价」即可，如租户管理员），
     * 让采购负责人可以把任务分给自己。推荐与预设规则仍只在采购人员里选。
     */
    private List<PurchaserVO> withSelf(List<PurchaserVO> purchasers, Map<Long, List<SourcingTaskAssigneeDO>> assignees) {
        Long me = currentUser.resolve();
        if (me == null || purchasers.stream().anyMatch(p -> p.getId().equals(me)) || !perm.canAccessMenu(MY_TASKS_PATH)) {
            return purchasers;
        }
        UserBasicDO u = userBasicMapper.selectById(me);
        if (u == null || !Objects.equals(u.getTenantId(), tenantId())) {
            return purchasers;
        }
        PurchaserVO self = new PurchaserVO();
        self.setId(me);
        self.setName(u.getName());
        self.setPartTime(false);
        self.setActiveTasks((int) assignees.values().stream().flatMap(List::stream).filter(a -> me.equals(a.getAssigneeId())).count());
        self.setTimeoutTasks(0);
        List<PurchaserVO> list = new ArrayList<>(purchasers);
        list.add(0, self);
        return list;
    }

    /** 采购人员：本租户启用的、角色带「可接询价任务」权限的账号；负载按进行中的有效分配计算 */
    private List<PurchaserVO> purchasers(List<SourcingTaskDO> open, Map<Long, List<SourcingTaskAssigneeDO>> assignees) {
        Set<String> roles = roleResourceMapper.selectList(new LambdaQueryWrapper<RoleResourceDO>()
                        .eq(RoleResourceDO::getResourceId, InquiryConstants.RESOURCE_ACCEPT_TASK))
                .stream().map(RoleResourceDO::getRoleCode).collect(Collectors.toSet());
        if (roles.isEmpty()) {
            return List.of();
        }
        List<UserBasicDO> users = userBasicMapper.selectList(new LambdaQueryWrapper<UserBasicDO>()
                .eq(UserBasicDO::getTenantId, tenantId())
                .eq(UserBasicDO::getStatus, 1)
                .in(UserBasicDO::getRoleCode, roles)
                .orderByAsc(UserBasicDO::getId));
        LocalDateTime now = LocalDateTime.now();
        TaskTimeout limits = TaskTimeout.of(settings, tenantId());
        Map<Long, int[]> load = new HashMap<>();
        for (SourcingTaskDO t : open) {
            if (t.getStatus() != InquiryConstants.TASK_SOURCING) {
                continue;
            }
            boolean timeout = limits.isTimeout(t, now);
            for (SourcingTaskAssigneeDO a : assignees.getOrDefault(t.getId(), List.of())) {
                int[] c = load.computeIfAbsent(a.getAssigneeId(), k -> new int[2]);
                c[0]++;
                if (timeout) {
                    c[1]++;
                }
            }
        }
        List<PurchaserVO> list = new ArrayList<>(users.size());
        for (UserBasicDO u : users) {
            PurchaserVO p = new PurchaserVO();
            p.setId(u.getId().longValue());
            p.setName(u.getName());
            p.setPartTime(InquiryConstants.PART_TIME_ROLE.equals(u.getRoleCode()));
            int[] c = load.getOrDefault(p.getId(), new int[2]);
            p.setActiveTasks(c[0]);
            p.setTimeoutTasks(c[1]);
            list.add(p);
        }
        return list;
    }

    private record Recommendation(PurchaserVO purchaser, String reason) {
    }

    /** 推荐：近 180 天该品牌已回价任务数多者优先，相同时进行中任务少者优先；兼职与正式一视同仁 */
    private static Recommendation recommend(SourcingTaskDO t, List<PurchaserVO> purchasers, Map<String, Map<Long, Long>> familiarity) {
        Map<Long, Long> fam = familiarity.getOrDefault(t.getBrandKey(), Map.of());
        return purchasers.stream()
                .min(Comparator.comparing((PurchaserVO p) -> -fam.getOrDefault(p.getId(), 0L))
                        .thenComparing(PurchaserVO::getActiveTasks)
                        .thenComparing(PurchaserVO::getId))
                .map(p -> {
                    long n = fam.getOrDefault(p.getId(), 0L);
                    String reason = (n > 0 ? t.getBrand() + " 询过 " + n + " 次" : "近 180 天没询过 " + t.getBrand())
                            + " · 进行中 " + p.getActiveTasks();
                    return new Recommendation(p, reason);
                })
                .orElse(null);
    }

    private Map<String, Map<Long, Long>> familiarity(int tenantId) {
        Map<String, Map<Long, Long>> map = new HashMap<>();
        for (SourcingTaskAssigneeMapper.Count c : assigneeMapper.familiarity(tenantId,
                LocalDateTime.now().minusDays(InquiryConstants.RECOMMEND_DAYS))) {
            map.computeIfAbsent(c.brandKey(), k -> new HashMap<>()).put(c.assigneeId(), c.cnt());
        }
        return map;
    }

    // ---------------------------------------------------------------- 分配

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assign(List<Long> taskIds, Long assigneeId) {
        requireAssignable(assigneeId);
        Long me = currentUser.resolve();
        for (SourcingTaskDO task : unassignedTasks(taskIds)) {
            assignOne(task, assigneeId, InquiryConstants.ASSIGN_MANUAL, me);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignByRecommend(List<Long> taskIds) {
        List<SourcingTaskDO> tasks = unassignedTasks(taskIds);
        List<PurchaserVO> purchasers = new ArrayList<>(purchasers());
        if (purchasers.isEmpty()) {
            throw new BizException("还没有采购人员：请先给账号的角色勾选「可接询价任务」权限");
        }
        Map<String, Map<Long, Long>> familiarity = familiarity(tenantId());
        Long me = currentUser.resolve();
        for (SourcingTaskDO task : tasks) {
            Recommendation rec = recommend(task, purchasers, familiarity);
            assignOne(task, rec.purchaser.getId(), InquiryConstants.ASSIGN_RECOMMEND, me);
            rec.purchaser.setActiveTasks(rec.purchaser.getActiveTasks() + 1);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reassign(Long taskId, Long assigneeId) {
        requireAssignable(assigneeId);
        SourcingTaskDO task = sourcingTask(taskId);
        deactivateAssignees(taskId);
        insertAssignee(task, assigneeId, InquiryConstants.ASSIGN_REASSIGN, currentUser.resolve());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addAssignee(Long taskId, Long assigneeId) {
        requireAssignable(assigneeId);
        SourcingTaskDO task = sourcingTask(taskId);
        boolean already = assigneeMapper.selectCount(new LambdaQueryWrapper<SourcingTaskAssigneeDO>()
                .eq(SourcingTaskAssigneeDO::getTaskId, taskId)
                .eq(SourcingTaskAssigneeDO::getAssigneeId, assigneeId)
                .eq(SourcingTaskAssigneeDO::getActive, 1)) > 0;
        if (already) {
            throw new BizException("这个任务已经分给该采购了");
        }
        insertAssignee(task, assigneeId, InquiryConstants.ASSIGN_EXTRA, currentUser.resolve());
    }

    private void assignOne(SourcingTaskDO task, Long assigneeId, int mode, Long by) {
        insertAssignee(task, assigneeId, mode, by);
        task.setStatus(InquiryConstants.TASK_SOURCING);
        if (task.getFirstAssignedAt() == null) {
            task.setFirstAssignedAt(LocalDateTime.now());
        }
        taskMapper.updateById(task);
    }

    private void insertAssignee(SourcingTaskDO task, Long assigneeId, int mode, Long by) {
        SourcingTaskAssigneeDO a = new SourcingTaskAssigneeDO();
        a.setTenantId(task.getTenantId());
        a.setTaskId(task.getId());
        a.setAssigneeId(assigneeId);
        a.setAssignedBy(by == null ? 0L : by);
        a.setAssignedAt(LocalDateTime.now());
        a.setAssignMode(mode);
        a.setActive(1);
        assigneeMapper.insert(a);
    }

    void deactivateAssignees(Long taskId) {
        assigneeMapper.update(new SourcingTaskAssigneeDO(), new LambdaUpdateWrapper<SourcingTaskAssigneeDO>()
                .eq(SourcingTaskAssigneeDO::getTaskId, taskId)
                .eq(SourcingTaskAssigneeDO::getActive, 1)
                .set(SourcingTaskAssigneeDO::getActive, 0));
    }

    private void requirePurchaser(Long assigneeId) {
        if (purchasers().stream().noneMatch(p -> p.getId().equals(assigneeId))) {
            throw new BizException("该账号不是采购人员，不能分配询价任务");
        }
    }

    /** 手动分配、改派、加人：采购人员，或分配人自己 */
    private void requireAssignable(Long assigneeId) {
        Long me = currentUser.resolve();
        if (assigneeId != null && assigneeId.equals(me) && perm.canAccessMenu(MY_TASKS_PATH)) {
            return;
        }
        if (purchasers().stream().noneMatch(p -> p.getId().equals(assigneeId))) {
            throw new BizException("该账号不是采购人员，不能分配询价任务");
        }
    }

    private List<SourcingTaskDO> unassignedTasks(List<Long> taskIds) {
        List<SourcingTaskDO> tasks = tasks(taskIds);
        for (SourcingTaskDO t : tasks) {
            if (t.getStatus() != InquiryConstants.TASK_UNASSIGNED) {
                throw new BizException("任务 " + t.getTaskCode() + " 已经分配过了，请刷新后再试");
            }
        }
        return tasks;
    }

    private SourcingTaskDO sourcingTask(Long taskId) {
        SourcingTaskDO task = tasks(List.of(taskId)).get(0);
        if (task.getStatus() != InquiryConstants.TASK_SOURCING || Objects.equals(task.getSalesHandling(), 1)) {
            throw new BizException("只有询价中的任务可以改派或追加比价");
        }
        return task;
    }

    private List<SourcingTaskDO> tasks(List<Long> taskIds) {
        List<SourcingTaskDO> tasks = taskMapper.selectList(new LambdaQueryWrapper<SourcingTaskDO>()
                .eq(SourcingTaskDO::getTenantId, tenantId())
                .in(SourcingTaskDO::getId, taskIds)
                .isNull(SourcingTaskDO::getDeletedAt));
        if (tasks.size() != Set.copyOf(taskIds).size()) {
            throw new BizException("询价任务不存在");
        }
        return tasks;
    }

    // ---------------------------------------------------------------- 规则

    @Override
    public List<RulePreviewVO> previewRules(List<Long> taskIds) {
        List<SourcingTaskDO> tasks = unassignedTasks(taskIds);
        Map<Long, String> basis = new HashMap<>();
        Map<Long, Long> plan = rulePlan(tasks, basis);
        Map<Long, String> names = lookups.userNames(plan.values());
        List<RulePreviewVO> list = new ArrayList<>(tasks.size());
        for (SourcingTaskDO t : tasks) {
            RulePreviewVO vo = new RulePreviewVO();
            vo.setTaskId(t.getId());
            vo.setTaskCode(t.getTaskCode());
            vo.setBrand(t.getBrand());
            vo.setCategory(t.getCategory());
            vo.setAssigneeId(plan.get(t.getId()));
            vo.setAssigneeName(names.get(plan.get(t.getId())));
            vo.setBasis(basis.get(t.getId()));
            list.add(vo);
        }
        return list;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void applyRules(List<Long> taskIds) {
        List<SourcingTaskDO> tasks = unassignedTasks(taskIds);
        Map<Long, Long> plan = rulePlan(tasks, null);
        Long me = currentUser.resolve();
        for (SourcingTaskDO t : tasks) {
            Long assignee = plan.get(t.getId());
            if (assignee != null) {
                assignOne(t, assignee, InquiryConstants.ASSIGN_RULE, me);
            }
        }
    }

    /** 每个任务按优先级找第一条命中且分配对象仍是采购人员的启用规则；都没命中时按推荐 */
    private Map<Long, Long> rulePlan(List<SourcingTaskDO> tasks, Map<Long, String> basis) {
        List<SourcingAssignRuleDO> rules = ruleMapper.selectList(new LambdaQueryWrapper<SourcingAssignRuleDO>()
                .eq(SourcingAssignRuleDO::getTenantId, tasks.get(0).getTenantId())
                .eq(SourcingAssignRuleDO::getStatus, 1)
                .isNull(SourcingAssignRuleDO::getDeletedAt)
                .orderByAsc(SourcingAssignRuleDO::getPriority, SourcingAssignRuleDO::getId));
        List<SourcingAssignRuleDO> all = ruleMapper.selectList(new LambdaQueryWrapper<SourcingAssignRuleDO>()
                .eq(SourcingAssignRuleDO::getTenantId, tasks.get(0).getTenantId())
                .isNull(SourcingAssignRuleDO::getDeletedAt)
                .orderByAsc(SourcingAssignRuleDO::getPriority, SourcingAssignRuleDO::getId));
        List<PurchaserVO> purchasers = new ArrayList<>(purchasers());
        Set<Long> purchaserIds = purchasers.stream().map(PurchaserVO::getId).collect(Collectors.toSet());
        Map<String, Map<Long, Long>> familiarity = familiarity(tasks.get(0).getTenantId());
        List<ProductCategoryDO> categories = inquiryCategories.categories();
        Map<Long, Long> plan = new LinkedHashMap<>();
        for (SourcingTaskDO t : tasks) {
            SourcingAssignRuleDO hit = rules.stream()
                    .filter(r -> purchaserIds.contains(r.getAssigneeId()) && matches(r, t, categories))
                    .findFirst().orElse(null);
            if (hit != null) {
                plan.put(t.getId(), hit.getAssigneeId());
                if (basis != null) {
                    basis.put(t.getId(), "规则 " + (all.indexOf(all.stream().filter(r -> r.getId().equals(hit.getId())).findFirst().orElse(hit)) + 1));
                }
                continue;
            }
            Recommendation rec = recommend(t, purchasers, familiarity);
            if (rec != null) {
                plan.put(t.getId(), rec.purchaser.getId());
                rec.purchaser.setActiveTasks(rec.purchaser.getActiveTasks() + 1);
            }
            if (basis != null) {
                basis.put(t.getId(), rec == null ? "没有采购人员可分配" : "没有命中规则，按推荐");
            }
        }
        return plan;
    }

    /** 按品类的规则：匹配值与任务品类名相同，或按品类匹配规则对应到的一级品类与任务的一级品类相同 */
    private boolean matches(SourcingAssignRuleDO rule, SourcingTaskDO task, List<ProductCategoryDO> categories) {
        List<String> values = readValues(rule.getMatchValues());
        if (rule.getMatchType() == InquiryConstants.RULE_BY_BRAND) {
            return values.stream().anyMatch(v -> priceKeys.brand(v).brandKey().equals(task.getBrandKey()));
        }
        String category = task.getCategory() == null ? "" : task.getCategory().trim();
        return values.stream().anyMatch(v -> v.trim().equalsIgnoreCase(category) || task.getCategoryId() != null
                && task.getCategoryId().equals(CategoryMatcher.rootOf(CategoryMatcher.match(v, categories).categoryId(), categories)));
    }

    @Override
    public AssignRulesVO rules() {
        int tenantId = tenantId();
        List<SourcingAssignRuleDO> rules = ruleMapper.selectList(new LambdaQueryWrapper<SourcingAssignRuleDO>()
                .eq(SourcingAssignRuleDO::getTenantId, tenantId)
                .isNull(SourcingAssignRuleDO::getDeletedAt)
                .orderByAsc(SourcingAssignRuleDO::getPriority, SourcingAssignRuleDO::getId));
        Map<Long, String> names = lookups.userNames(rules.stream().map(SourcingAssignRuleDO::getAssigneeId).toList());
        LocalDateTime since = LocalDateTime.now().minusDays(30);
        List<SourcingTaskAssigneeDO> byRule = assigneeMapper.selectList(new LambdaQueryWrapper<SourcingTaskAssigneeDO>()
                .eq(SourcingTaskAssigneeDO::getTenantId, tenantId)
                .eq(SourcingTaskAssigneeDO::getAssignMode, InquiryConstants.ASSIGN_RULE)
                .ge(SourcingTaskAssigneeDO::getAssignedAt, since));
        List<SourcingTaskDO> ruleTasks = byRule.isEmpty() ? List.of()
                : taskMapper.selectBatchIds(byRule.stream().map(SourcingTaskAssigneeDO::getTaskId).distinct().toList());
        List<ProductCategoryDO> categories = inquiryCategories.categories();
        List<AssignRuleVO> list = new ArrayList<>(rules.size());
        for (SourcingAssignRuleDO r : rules) {
            AssignRuleVO vo = new AssignRuleVO();
            vo.setId(r.getId());
            vo.setPriority(list.size() + 1);
            vo.setMatchType(r.getMatchType());
            vo.setMatchValues(readValues(r.getMatchValues()));
            vo.setAssigneeId(r.getAssigneeId());
            vo.setAssigneeName(names.get(r.getAssigneeId()));
            vo.setStatus(r.getStatus());
            vo.setHits((int) byRule.stream().filter(a -> Objects.equals(a.getAssigneeId(), r.getAssigneeId())
                    && ruleTasks.stream().anyMatch(t -> t.getId().equals(a.getTaskId()) && matches(r, t, categories))).count());
            list.add(vo);
        }
        AssignRulesVO vo = new AssignRulesVO();
        vo.setAutoAssign(settings.autoAssign(tenantId));
        vo.setRules(list);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createRule(SaveAssignRuleRequest req) {
        requirePurchaser(req.getAssigneeId());
        SourcingAssignRuleDO r = new SourcingAssignRuleDO();
        r.setTenantId(tenantId());
        Integer max = ruleMapper.selectList(new LambdaQueryWrapper<SourcingAssignRuleDO>()
                        .eq(SourcingAssignRuleDO::getTenantId, tenantId()).isNull(SourcingAssignRuleDO::getDeletedAt))
                .stream().map(SourcingAssignRuleDO::getPriority).max(Integer::compare).orElse(0);
        r.setPriority(max + 1);
        fillRule(r, req);
        ruleMapper.insert(r);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRule(Long id, SaveAssignRuleRequest req) {
        requirePurchaser(req.getAssigneeId());
        SourcingAssignRuleDO r = rule(id);
        fillRule(r, req);
        ruleMapper.updateById(r);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRule(Long id) {
        SourcingAssignRuleDO r = rule(id);
        r.setDeletedAt(LocalDateTime.now());
        ruleMapper.updateById(r);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reorderRules(List<Long> ruleIds) {
        for (int i = 0; i < ruleIds.size(); i++) {
            SourcingAssignRuleDO r = rule(ruleIds.get(i));
            r.setPriority(i + 1);
            ruleMapper.updateById(r);
        }
    }

    @Override
    public void setAutoAssign(boolean enabled) {
        settings.setAutoAssign(tenantId(), enabled);
    }

    private void fillRule(SourcingAssignRuleDO r, SaveAssignRuleRequest req) {
        List<String> values = req.getMatchValues().stream().filter(StringUtils::hasText).map(String::trim).distinct().toList();
        if (values.isEmpty()) {
            throw new BizException("请填写匹配值");
        }
        r.setMatchType(req.getMatchType());
        try {
            r.setMatchValues(objectMapper.writeValueAsString(values));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        r.setAssigneeId(req.getAssigneeId());
        r.setStatus(req.getStatus() == null ? 1 : (req.getStatus() == 0 ? 0 : 1));
    }

    private SourcingAssignRuleDO rule(Long id) {
        SourcingAssignRuleDO r = ruleMapper.selectById(id);
        if (r == null || r.getDeletedAt() != null || !Objects.equals(r.getTenantId(), tenantId())) {
            throw new BizException("分配规则不存在");
        }
        return r;
    }

    private List<String> readValues(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() { });
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    // ---------------------------------------------------------------- 工具

    private Map<Long, List<SourcingTaskAssigneeDO>> activeAssignees(List<Long> taskIds) {
        if (taskIds.isEmpty()) {
            return Map.of();
        }
        return assigneeMapper.selectList(new LambdaQueryWrapper<SourcingTaskAssigneeDO>()
                        .in(SourcingTaskAssigneeDO::getTaskId, taskIds)
                        .eq(SourcingTaskAssigneeDO::getActive, 1)
                        .isNull(SourcingTaskAssigneeDO::getDeletedAt)
                        .orderByAsc(SourcingTaskAssigneeDO::getId))
                .stream().collect(Collectors.groupingBy(SourcingTaskAssigneeDO::getTaskId));
    }

    /**
     * 有兼职回价待审核的任务 → 待审核记录（任务询价中或已回价、询盘单还没报价也没取消）。
     * 报价后回价只读，留在待审核里也审不了，所以不列出。
     */
    private Map<Long, List<SourcingQuoteDO>> pendingReviewByTask(int tenantId) {
        List<SourcingQuoteDO> pending = quoteMapper.selectList(new LambdaQueryWrapper<SourcingQuoteDO>()
                .select(SourcingQuoteDO::getTaskId, SourcingQuoteDO::getInquiryItemId, SourcingQuoteDO::getQuotedBy,
                        SourcingQuoteDO::getQuotedAt)
                .eq(SourcingQuoteDO::getTenantId, tenantId)
                .eq(SourcingQuoteDO::getStatus, InquiryConstants.QUOTE_PENDING_REVIEW)
                .isNull(SourcingQuoteDO::getDeletedAt)
                .inSql(SourcingQuoteDO::getTaskId, "select id from sourcing_task where status in ("
                        + InquiryConstants.TASK_SOURCING + "," + InquiryConstants.TASK_DONE + ") and deleted_at is null")
                .inSql(SourcingQuoteDO::getCustomerInquiryId, "select id from customer_inquiry where status in ("
                        + InquiryConstants.STATUS_SOURCING + "," + InquiryConstants.STATUS_READY_TO_QUOTE + ") and deleted_at is null"));
        return pending.stream().collect(Collectors.groupingBy(SourcingQuoteDO::getTaskId));
    }

    private void applyReview(BoardTaskVO vo, List<SourcingQuoteDO> pending, LocalDateTime now) {
        vo.setReviewItemCount((int) pending.stream().map(SourcingQuoteDO::getInquiryItemId).distinct().count());
        LocalDateTime first = pending.stream().map(SourcingQuoteDO::getQuotedAt).filter(Objects::nonNull)
                .min(Comparator.naturalOrder()).orElse(null);
        vo.setReviewSubmittedAt(first);
        vo.setReviewWaitingMinutes(first == null ? null : Math.max(0, Duration.between(first, now).toMinutes()));
        Map<Long, String> names = lookups.userNames(pending.stream().map(SourcingQuoteDO::getQuotedBy).distinct().toList());
        vo.setReviewBuyerNames(pending.stream().map(SourcingQuoteDO::getQuotedBy).distinct()
                .map(names::get).filter(Objects::nonNull).toList());
    }

    /** 一次查出多个任务的已回价型号数 */
    private Map<Long, Integer> pricedCounts(List<Long> taskIds) {
        if (taskIds.isEmpty()) {
            return Map.of();
        }
        return itemMapper.selectList(new LambdaQueryWrapper<InquiryItemDO>()
                        .select(InquiryItemDO::getSourcingTaskId)
                        .in(InquiryItemDO::getSourcingTaskId, taskIds)
                        .ne(InquiryItemDO::getQuoteStatus, InquiryConstants.ITEM_PENDING)
                        .isNull(InquiryItemDO::getDeletedAt))
                .stream().collect(Collectors.groupingBy(InquiryItemDO::getSourcingTaskId, Collectors.summingInt(i -> 1)));
    }

    private int pricedCount(Long taskId) {
        return Math.toIntExact(itemMapper.selectCount(new LambdaQueryWrapper<InquiryItemDO>()
                .eq(InquiryItemDO::getSourcingTaskId, taskId)
                .ne(InquiryItemDO::getQuoteStatus, InquiryConstants.ITEM_PENDING)
                .isNull(InquiryItemDO::getDeletedAt)));
    }

    private static long waitingMinutes(SourcingTaskDO t, LocalDateTime now) {
        LocalDateTime since = t.getPoolEnteredAt() != null ? t.getPoolEnteredAt()
                : t.getReturnedAt() != null ? t.getReturnedAt() : t.getCreateTime();
        return since == null ? 0 : Math.max(0, Duration.between(since, now).toMinutes());
    }

    private static int tenantId() {
        Integer t = TenantContext.getTenantId();
        return t == null ? 0 : t;
    }

    @Override
    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void setCostQuote(Long itemId, Long quoteId) {
        InquiryItemDO item = itemMapper.selectById(itemId);
        if (item == null || item.getDeletedAt() != null || !Objects.equals(item.getTenantId(), tenantId())
                || item.getSourcingTaskId() == null) {
            throw new BizException("型号不存在");
        }
        progress.lock(item.getCustomerInquiryId());
        CustomerInquiryDO inquiry = inquiryMapper.selectById(item.getCustomerInquiryId());
        if (inquiry != null) {
            MigratedGuard.requireEditable(inquiry.getMigrated());
        }
        if (inquiry != null && inquiry.getStatus() == InquiryConstants.STATUS_CANCELLED) {
            throw new BizException("询盘单已取消，采购成本价不能再修改");
        }
        if (quotationLocks.isLocked(itemId)) {
            throw new BizException("型号已报给客户，采购成本价不能再修改");
        }
        item = itemMapper.selectById(itemId);
        Long before = item.getSelectedQuoteId();
        if (quoteId == null) {
            itemMapper.update(null, new LambdaUpdateWrapper<InquiryItemDO>().eq(InquiryItemDO::getId, itemId)
                    .set(InquiryItemDO::getCostManual, 0).set(InquiryItemDO::getUpdateTime, LocalDateTime.now()));
        } else {
            SourcingQuoteDO q = quoteMapper.selectById(quoteId);
            if (q == null || q.getDeletedAt() != null || !Objects.equals(q.getInquiryItemId(), itemId)
                    || q.getStatus() != InquiryConstants.QUOTE_SUBMITTED || Objects.equals(q.getNoStock(), 1)) {
                throw new BizException("只能从这个型号已提交的有价询价记录中选择");
            }
            itemMapper.update(null, new LambdaUpdateWrapper<InquiryItemDO>().eq(InquiryItemDO::getId, itemId)
                    .set(InquiryItemDO::getSelectedQuoteId, quoteId).set(InquiryItemDO::getCostManual, 1)
                    .set(InquiryItemDO::getUpdateTime, LocalDateTime.now()));
        }
        progress.refreshItems(List.of(itemId));
        Long after = itemMapper.selectById(itemId).getSelectedQuoteId();
        logService.recordOperateLog("分配工作台", quoteId == null ? COST_RESET : COST_PICK,
                Map.of("itemId", itemId, "model", item.getConfirmedModel(), "quote", quoteNote(before)),
                Map.of("itemId", itemId, "model", item.getConfirmedModel(), "quote", quoteNote(after),
                        "operator", lookups.userNames(List.of(currentUser.resolve())).getOrDefault(currentUser.resolve(), "")));
    }

    private static final String COST_PICK = "指定采购成本价";
    private static final String COST_RESET = "成本价恢复自动";
    private static final List<String> LOGGED_OPERATIONS = List.of(COST_PICK, COST_RESET,
            InquiryConstants.LOG_REVIEW_APPROVE, InquiryConstants.LOG_REVIEW_REJECT);

    /** 成本价对应记录的简述，如「¥940.00 林熙」 */
    private String quoteNote(Long quoteId) {
        SourcingQuoteDO q = quoteId == null ? null : quoteMapper.selectById(quoteId);
        if (q == null) {
            return "无";
        }
        return "¥" + q.getUnitPriceCny() + " " + lookups.userNames(List.of(q.getQuotedBy())).getOrDefault(q.getQuotedBy(), "");
    }

    @Override
    public List<ItemHistoryEntryVO> itemHistory(Long itemId) {
        InquiryItemDO item = itemMapper.selectById(itemId);
        if (item == null || item.getDeletedAt() != null || !Objects.equals(item.getTenantId(), tenantId())) {
            throw new BizException("型号不存在");
        }
        List<ItemHistoryEntryVO> entries = new ArrayList<>();
        // 回价版本：同一采购同一次提交（提交时间精确到秒）的记录为一版；被替换的旧版本已软删除但仍保留
        List<SourcingQuoteDO> all = quoteMapper.selectList(new LambdaQueryWrapper<SourcingQuoteDO>()
                .eq(SourcingQuoteDO::getInquiryItemId, itemId)
                .eq(SourcingQuoteDO::getStatus, InquiryConstants.QUOTE_SUBMITTED)
                .isNotNull(SourcingQuoteDO::getQuotedAt)
                .orderByAsc(SourcingQuoteDO::getQuotedAt));
        Map<Long, String> names = lookups.userNames(all.stream().map(SourcingQuoteDO::getQuotedBy).toList());
        Map<String, List<SourcingQuoteDO>> versions = all.stream().collect(Collectors.groupingBy(
                SourcingTaskServiceImpl::versionKey, LinkedHashMap::new, Collectors.toList()));
        java.util.Set<Long> seen = new java.util.HashSet<>();
        for (List<SourcingQuoteDO> v : versions.values()) {
            SourcingQuoteDO first = v.get(0);
            ItemHistoryEntryVO e = new ItemHistoryEntryVO();
            e.setType("QUOTE");
            e.setTime(first.getQuotedAt());
            e.setOperatorName(names.get(first.getQuotedBy()));
            e.setAction(seen.add(first.getQuotedBy()) ? "首次回价" : "修改回价");
            e.setCurrent(v.stream().allMatch(q -> q.getDeletedAt() == null));
            e.setQuotes(priceHistoryService.toVos(v));
            entries.add(e);
        }
        // 采购负责人调整成本价、审核兼职回价：操作日志里记了型号 ID
        for (SysLogDO log : sysLogMapper.selectList(new LambdaQueryWrapper<SysLogDO>()
                .eq(SysLogDO::getTenantId, item.getTenantId())
                .eq(SysLogDO::getMenu, "分配工作台")
                .in(SysLogDO::getOperation, LOGGED_OPERATIONS)
                .and(w -> w.like(SysLogDO::getContent, "\"itemId\":" + itemId + ",").or()
                        .like(SysLogDO::getContent, "\"itemId\":" + itemId + "}"))
                .isNull(SysLogDO::getDeletedAt))) {
            boolean cost = COST_PICK.equals(log.getOperation()) || COST_RESET.equals(log.getOperation());
            ItemHistoryEntryVO e = new ItemHistoryEntryVO();
            e.setType(cost ? "COST" : "REVIEW");
            e.setTime(log.getOperateTime());
            e.setAction(log.getOperation());
            try {
                com.fasterxml.jackson.databind.JsonNode after = objectMapper.readTree(log.getContent()).path("after");
                e.setOperatorName(after.path("operator").asText(log.getOperatorName()));
                e.setNote(!cost ? after.path("note").asText()
                        : COST_PICK.equals(log.getOperation()) ? "指定为 " + after.path("quote").asText()
                        : "恢复按推荐报价自动取，当前为 " + after.path("quote").asText());
            } catch (JsonProcessingException ex) {
                e.setOperatorName(log.getOperatorName());
                e.setNote(log.getOperation());
            }
            entries.add(e);
        }
        entries.sort(Comparator.comparing(ItemHistoryEntryVO::getTime).reversed());
        return entries;
    }

    /** 一个回价版本：同一提交批次；旧数据没有批次时按询价人 + 提交时间（精确到秒） */
    private static String versionKey(SourcingQuoteDO q) {
        return q.getSubmitBatch() != null && !q.getSubmitBatch().isEmpty()
                ? q.getQuotedBy() + "|" + q.getSubmitBatch()
                : q.getQuotedBy() + "|" + q.getQuotedAt().withNano(0);
    }

    /** 型号被修改过的次数：修改回价的版本数 + 调整成本价次数（首次回价不算） */
    private int changeCount(Long itemId) {
        long versions = quoteMapper.selectList(new LambdaQueryWrapper<SourcingQuoteDO>()
                        .select(SourcingQuoteDO::getQuotedBy, SourcingQuoteDO::getQuotedAt, SourcingQuoteDO::getSubmitBatch)
                        .eq(SourcingQuoteDO::getInquiryItemId, itemId)
                        .eq(SourcingQuoteDO::getStatus, InquiryConstants.QUOTE_SUBMITTED)
                        .isNotNull(SourcingQuoteDO::getQuotedAt))
                .stream().map(SourcingTaskServiceImpl::versionKey).distinct().count();
        long purchasers = quoteMapper.selectList(new LambdaQueryWrapper<SourcingQuoteDO>()
                        .select(SourcingQuoteDO::getQuotedBy)
                        .eq(SourcingQuoteDO::getInquiryItemId, itemId)
                        .eq(SourcingQuoteDO::getStatus, InquiryConstants.QUOTE_SUBMITTED))
                .stream().map(SourcingQuoteDO::getQuotedBy).distinct().count();
        long costChanges = sysLogMapper.selectCount(new LambdaQueryWrapper<SysLogDO>()
                .eq(SysLogDO::getTenantId, tenantId())
                .eq(SysLogDO::getMenu, "分配工作台")
                .in(SysLogDO::getOperation, LOGGED_OPERATIONS)
                .and(w -> w.like(SysLogDO::getContent, "\"itemId\":" + itemId + ",").or()
                        .like(SysLogDO::getContent, "\"itemId\":" + itemId + "}"))
                .isNull(SysLogDO::getDeletedAt));
        return (int) Math.max(0, versions - purchasers + costChanges);
    }

    static void applyBrief(BoardTaskVO vo, InquiryLookups.InquiryBrief b) {
        if (b == null) {
            return;
        }
        vo.setSalesId(b.salesId());
        vo.setSalesName(b.salesName());
        vo.setLevel(b.level());
        vo.setCustomerType(b.customerType());
        vo.setCustomerName(b.customerName());
        vo.setQuoteDeadline(b.quoteDeadline());
        vo.setInquiryRemark(org.springframework.util.StringUtils.hasText(b.remark()) ? b.remark().trim() : null);
    }
}
