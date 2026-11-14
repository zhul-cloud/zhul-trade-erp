package com.zhul.erp.modules.supply.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.security.PermissionChecker;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.inquiry.support.InquiryLookups;
import com.zhul.erp.modules.sales.support.PiStore;
import com.zhul.erp.modules.supply.constants.SupplyConstants;
import com.zhul.erp.modules.supply.dto.ProjectVO;
import com.zhul.erp.modules.supply.dto.SupplyRequests;
import com.zhul.erp.modules.supply.entity.ScDemandDO;
import com.zhul.erp.modules.supply.entity.ScGapProjectDO;
import com.zhul.erp.modules.supply.entity.ScGapProjectItemDO;
import com.zhul.erp.modules.supply.repository.ScDemandMapper;
import com.zhul.erp.modules.supply.repository.ScGapProjectItemMapper;
import com.zhul.erp.modules.supply.repository.ScGapProjectMapper;
import com.zhul.erp.modules.supply.support.DemandAssembler;
import com.zhul.erp.modules.supply.support.SupplyLookups;
import com.zhul.erp.modules.system.constants.DocumentType;
import com.zhul.erp.modules.system.doclog.DocLogs;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 专项（spec supply-chain/gap-project；design 决策 14）：范围为品牌 + 二级品类（品类可空表示全部品类），
 * 进度按型号的覆盖状态自动算（需确认、可直接报价算已解决）。同一范围同时只有一个进行中的专项，由 active_scope 唯一键保证。
 */
@Service
@RequiredArgsConstructor
public class GapProjectService {

    private final ScGapProjectMapper projectMapper;
    private final ScGapProjectItemMapper itemMapper;
    private final ScDemandMapper demandMapper;
    private final DemandAssembler assembler;
    private final SupplyBoardService boardService;
    private final SupplyLookups lookups;
    private final InquiryLookups inquiryLookups;
    private final CurrentUserResolver currentUser;
    private final PermissionChecker perm;
    private final DocLogs docLogs;

    @PostConstruct
    void init() {
        boardService.setProjectCounter(this::progress);
    }

    /** 建专项弹窗预填：范围内无渠道、有线索未验证，以及近 30 天无货且不是可直接报价的型号；同范围已有进行中的专项时一并返回 */
    public Map<String, Object> candidates(Long brandId, Long categoryId) {
        Map<Long, Integer> stockout = boardService.stockouts(30);
        List<ScDemandDO> rows = inScope(brandId, categoryId).stream()
                .filter(d -> d.getCoverageStatus() <= SupplyConstants.COVER_LEAD
                        || stockout.containsKey(d.getId()) && d.getCoverageStatus() != SupplyConstants.COVER_READY)
                .toList();
        ScGapProjectDO active = projectMapper.selectOne(new LambdaQueryWrapper<ScGapProjectDO>()
                .eq(ScGapProjectDO::getActiveScope, scope(brandId, categoryId)).last("LIMIT 1"));
        String brand = lookups.brandNames(List.of(brandId)).getOrDefault(brandId, "");
        String cat = categoryId == null ? "全部品类" : lookups.categoryNames(List.of(categoryId)).getOrDefault(categoryId, "");
        return Map.of("items", assembler.toVOs(rows), "defaultName", brand + " · " + cat,
                "activeProjectId", active == null ? 0L : active.getId());
    }

    @Transactional
    public Long create(SupplyRequests.Project req) {
        if (!lookups.brandExists(req.getBrandId())) {
            throw new BizException("品牌不存在");
        }
        if (req.getCategoryId() != null && !lookups.isLevel2(req.getCategoryId())) {
            throw new BizException("请选择细分品类");
        }
        if (req.getDueDate().isBefore(LocalDate.now())) {
            throw new BizException("截止日期不能早于今天");
        }
        List<ScDemandDO> items = validItems(req.getBrandId(), req.getCategoryId(), req.getDemandIds());
        if (items.isEmpty()) {
            throw new BizException("至少包含一个型号");
        }
        ScGapProjectDO p = new ScGapProjectDO();
        p.setTenantId(PiStore.tenantId());
        p.setName(req.getName().trim());
        p.setBrandId(req.getBrandId());
        p.setCategoryId(req.getCategoryId() == null ? 0L : req.getCategoryId());
        p.setGoal(req.getGoal().trim());
        p.setOwnerId(req.getOwnerId());
        p.setDueDate(req.getDueDate());
        p.setStatus(SupplyConstants.PROJECT_ACTIVE);
        p.setActiveScope(scope(req.getBrandId(), req.getCategoryId()));
        try {
            projectMapper.insert(p);
        } catch (DuplicateKeyException e) {
            throw new BizException("这个范围已有进行中的专项", e);
        }
        items.forEach(d -> addItem(p.getId(), d.getId()));
        docLogs.add(DocumentType.GP, p.getId(), "建立", p.getName() + "，含 " + items.size() + " 个型号，截止 " + p.getDueDate());
        return p.getId();
    }

    @Transactional
    public void changeItems(Long id, SupplyRequests.ProjectItems req) {
        ScGapProjectDO p = open(id);
        Set<Long> current = new HashSet<>(itemIds(id));
        List<ScDemandDO> add = req.getAdd() == null ? List.of()
                : validItems(p.getBrandId(), p.getCategoryId() == 0 ? null : p.getCategoryId(), req.getAdd());
        List<Long> remove = req.getRemove() == null ? List.of() : req.getRemove().stream().filter(current::contains).toList();
        int after = current.size() - remove.size() + (int) add.stream().filter(d -> !current.contains(d.getId())).count();
        if (after <= 0) {
            throw new BizException("专项至少包含一个型号");
        }
        int added = 0;
        for (ScDemandDO d : add) {
            if (!current.contains(d.getId())) {
                addItem(id, d.getId());
                added++;
            }
        }
        if (!remove.isEmpty()) {
            itemMapper.update(null, new LambdaUpdateWrapper<ScGapProjectItemDO>()
                    .eq(ScGapProjectItemDO::getProjectId, id).in(ScGapProjectItemDO::getDemandId, remove)
                    .isNull(ScGapProjectItemDO::getDeletedAt)
                    .set(ScGapProjectItemDO::getDeletedAt, LocalDateTime.now()));
        }
        docLogs.add(DocumentType.GP, id, "调整型号", "加入 " + added + " 个，移出 " + remove.size() + " 个");
    }

    @Transactional
    public void close(Long id, SupplyRequests.CloseProject req) {
        ScGapProjectDO p = open(id);
        if (!canClose(p)) {
            throw new BizException("只有负责人或有「维护专项」权限的人可以结束专项");
        }
        boolean done = req.getDone();
        projectMapper.update(null, new LambdaUpdateWrapper<ScGapProjectDO>().eq(ScGapProjectDO::getId, id)
                .set(ScGapProjectDO::getStatus, done ? SupplyConstants.PROJECT_DONE : SupplyConstants.PROJECT_CANCELLED)
                .set(ScGapProjectDO::getActiveScope, null)
                .set(ScGapProjectDO::getResultNote, req.getNote().trim())
                .set(ScGapProjectDO::getClosedAt, LocalDateTime.now())
                .set(ScGapProjectDO::getClosedBy, me())
                .set(ScGapProjectDO::getUpdateTime, LocalDateTime.now()));
        docLogs.add(DocumentType.GP, id, done ? "完成" : "取消", req.getNote().trim());
    }

    public ProjectVO detail(Long id) {
        ScGapProjectDO p = projectMapper.selectById(id);
        if (p == null || p.getDeletedAt() != null) {
            throw new BizException("专项不存在");
        }
        ProjectVO v = brief(List.of(p)).get(0);
        List<ScDemandDO> rows = itemIds(id).isEmpty() ? List.of() : demandMapper.selectBatchIds(itemIds(id));
        v.setItems(assembler.toVOs(rows));
        v.setLogs(docLogs.list(DocumentType.GP, id));
        return v;
    }

    /** 专项摘要（不含型号与日志） */
    public ProjectVO detailBrief(Long id) {
        ScGapProjectDO p = projectMapper.selectById(id);
        if (p == null || p.getDeletedAt() != null) {
            throw new BizException("专项不存在");
        }
        return brief(List.of(p)).get(0);
    }

    /** 进行中的专项（团队视图、今日待办）；ownerId 不为空时只看此人负责的 */
    public List<ProjectVO> active(Long ownerId) {
        return brief(projectMapper.selectList(new LambdaQueryWrapper<ScGapProjectDO>()
                .eq(ScGapProjectDO::getStatus, SupplyConstants.PROJECT_ACTIVE)
                .eq(ownerId != null, ScGapProjectDO::getOwnerId, ownerId)
                .isNull(ScGapProjectDO::getDeletedAt)
                .orderByAsc(ScGapProjectDO::getDueDate)));
    }

    /** [已解决, 总数]：型号覆盖状态为需确认或可直接报价算已解决 */
    public int[] progress(Long projectId) {
        List<Long> ids = itemIds(projectId);
        if (ids.isEmpty()) {
            return new int[]{0, 0};
        }
        int solved = (int) demandMapper.selectBatchIds(ids).stream()
                .filter(d -> d.getDeletedAt() == null && d.getCoverageStatus() >= SupplyConstants.COVER_CONFIRM).count();
        return new int[]{solved, ids.size()};
    }

    public List<Long> itemIds(Long projectId) {
        return itemMapper.selectList(new LambdaQueryWrapper<ScGapProjectItemDO>()
                        .eq(ScGapProjectItemDO::getProjectId, projectId).isNull(ScGapProjectItemDO::getDeletedAt))
                .stream().map(ScGapProjectItemDO::getDemandId).toList();
    }

    private List<ProjectVO> brief(List<ScGapProjectDO> rows) {
        Map<Long, String> brands = lookups.brandNames(rows.stream().map(ScGapProjectDO::getBrandId).toList());
        Map<Long, String> cats = lookups.categoryNames(rows.stream().map(ScGapProjectDO::getCategoryId).toList());
        Map<Long, String> users = inquiryLookups.userNames(rows.stream().map(ScGapProjectDO::getOwnerId).toList());
        LocalDate today = LocalDate.now();
        List<ProjectVO> out = new ArrayList<>(rows.size());
        for (ScGapProjectDO p : rows) {
            ProjectVO v = new ProjectVO();
            org.springframework.beans.BeanUtils.copyProperties(p, v);
            v.setBrandName(brands.get(p.getBrandId()));
            v.setCategoryName(p.getCategoryId() == null || p.getCategoryId() == 0 ? "全部品类" : cats.get(p.getCategoryId()));
            v.setOwnerName(users.get(p.getOwnerId()));
            int[] pr = progress(p.getId());
            v.setSolved(pr[0]);
            v.setTotal(pr[1]);
            if (p.getStatus() == SupplyConstants.PROJECT_ACTIVE && p.getDueDate().isBefore(today)) {
                v.setOverdueDays((int) ChronoUnit.DAYS.between(p.getDueDate(), today));
            }
            v.setCanClose(p.getStatus() == SupplyConstants.PROJECT_ACTIVE && canClose(p));
            out.add(v);
        }
        return out;
    }

    private boolean canClose(ScGapProjectDO p) {
        return me().equals(p.getOwnerId()) || perm.has(SupplyConstants.PERM_PROJECT_EDIT);
    }

    private ScGapProjectDO open(Long id) {
        ScGapProjectDO p = projectMapper.selectById(id);
        if (p == null || p.getDeletedAt() != null) {
            throw new BizException("专项不存在");
        }
        if (p.getStatus() != SupplyConstants.PROJECT_ACTIVE) {
            throw new BizException("专项已结束，只能查看");
        }
        return p;
    }

    /** 只能加入范围内、已进入覆盖矩阵的型号 */
    private List<ScDemandDO> validItems(Long brandId, Long categoryId, List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        List<ScDemandDO> rows = demandMapper.selectBatchIds(ids);
        for (ScDemandDO d : rows) {
            if (d.getDeletedAt() != null || d.getStatus() != SupplyConstants.DEMAND_IN_MATRIX || !brandId.equals(d.getBrandId())
                    || categoryId != null && !categoryId.equals(d.getCategoryId())) {
                throw new BizException("只能加入范围内已进入覆盖矩阵的型号");
            }
        }
        return rows;
    }

    private List<ScDemandDO> inScope(Long brandId, Long categoryId) {
        return demandMapper.selectList(new LambdaQueryWrapper<ScDemandDO>()
                .eq(ScDemandDO::getBrandId, brandId)
                .eq(categoryId != null, ScDemandDO::getCategoryId, categoryId)
                .eq(ScDemandDO::getStatus, SupplyConstants.DEMAND_IN_MATRIX)
                .isNull(ScDemandDO::getDeletedAt));
    }

    private void addItem(Long projectId, Long demandId) {
        ScGapProjectItemDO i = new ScGapProjectItemDO();
        i.setTenantId(PiStore.tenantId());
        i.setProjectId(projectId);
        i.setDemandId(demandId);
        itemMapper.insert(i);
    }

    private static String scope(Long brandId, Long categoryId) {
        return brandId + ":" + (categoryId == null ? 0 : categoryId);
    }

    private Long me() {
        Long id = currentUser.resolve();
        return id == null ? 0L : id;
    }
}
