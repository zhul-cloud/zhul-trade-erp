package com.zhul.erp.modules.purchase.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.framework.security.DataScope;
import com.zhul.erp.framework.security.DataScopeResolver;
import com.zhul.erp.modules.inquiry.support.InquiryLookups;
import com.zhul.erp.modules.masterdata.entity.CustomerDO;
import com.zhul.erp.modules.masterdata.entity.SupplierDO;
import com.zhul.erp.modules.masterdata.repository.CustomerMapper;
import com.zhul.erp.modules.masterdata.repository.SupplierMapper;
import com.zhul.erp.modules.purchase.constants.PurchaseConstants;
import com.zhul.erp.modules.purchase.dto.AssignRequirementsRequest;
import com.zhul.erp.modules.purchase.dto.GeneratePreviewVO;
import com.zhul.erp.modules.purchase.dto.GeneratePurchaseRequest;
import com.zhul.erp.modules.purchase.dto.RequirementPageQuery;
import com.zhul.erp.modules.purchase.dto.RequirementStatsVO;
import com.zhul.erp.modules.purchase.dto.RequirementVO;
import com.zhul.erp.modules.purchase.dto.SplitRequirementRequest;
import com.zhul.erp.modules.purchase.entity.PurchaseOrderDO;
import com.zhul.erp.modules.purchase.entity.PurchaseOrderItemDO;
import com.zhul.erp.modules.purchase.entity.PurchaseRequirementDO;
import com.zhul.erp.modules.purchase.repository.PurchaseOrderItemMapper;
import com.zhul.erp.modules.purchase.repository.PurchaseOrderMapper;
import com.zhul.erp.modules.purchase.repository.PurchaseRequirementMapper;
import com.zhul.erp.modules.purchase.service.PurchaseRequirementService;
import com.zhul.erp.modules.purchase.support.PurchaseDrafts;
import com.zhul.erp.modules.purchase.support.RequirementLifecycle;
import com.zhul.erp.modules.purchase.support.RequirementQty;
import com.zhul.erp.modules.sales.entity.SalesOrderDO;
import com.zhul.erp.modules.sales.entity.SalesOrderItemDO;
import com.zhul.erp.modules.sales.repository.SalesOrderItemMapper;
import com.zhul.erp.modules.sales.repository.SalesOrderMapper;
import com.zhul.erp.modules.sales.support.PiStore;
import com.zhul.erp.modules.system.service.LogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class PurchaseRequirementServiceImpl implements PurchaseRequirementService {

    private static final int KEYWORD_LIMIT = 500;
    /** 需求在已下单（2）或草稿 + 已下单（1, 2）采购单上的数量 */
    private static final String PLACED_SQL = "(SELECT COALESCE(SUM(pi.quantity), 0) FROM purchase_order_item pi "
            + "JOIN purchase_order po ON po.id = pi.po_id WHERE pi.requirement_id = purchase_requirement.id "
            + "AND pi.deleted_at IS NULL AND po.deleted_at IS NULL AND po.status IN (%s))";

    private final PurchaseRequirementMapper requirementMapper;
    private final PurchaseOrderMapper orderMapper;
    private final PurchaseOrderItemMapper itemMapper;
    private final SalesOrderMapper soMapper;
    private final SalesOrderItemMapper soItemMapper;
    private final CustomerMapper customerMapper;
    private final SupplierMapper supplierMapper;
    private final RequirementQty qty;
    private final PurchaseDrafts drafts;
    private final RequirementLifecycle lifecycle;
    private final DataScopeResolver dataScopeResolver;
    private final CurrentUserResolver currentUser;
    private final InquiryLookups lookups;
    private final LogService logService;

    // ---------------------------------------------------------------- 列表与统计

    @Override
    public PageResult<RequirementVO> page(RequirementPageQuery q) {
        int tenant = PiStore.tenantId();
        LambdaQueryWrapper<PurchaseRequirementDO> w = scoped();
        String view = StringUtils.hasText(q.getView()) ? q.getView().trim() : "open";
        if ("open".equals(view)) {
            w.eq(PurchaseRequirementDO::getStatus, PurchaseConstants.REQ_ACTIVE)
                    .apply("purchase_requirement.quantity > " + PLACED_SQL.formatted("2"));
        } else if ("need".equals(view)) {
            w.eq(PurchaseRequirementDO::getStatus, PurchaseConstants.REQ_ACTIVE)
                    .apply("purchase_requirement.quantity > " + PLACED_SQL.formatted("1, 2"));
        }
        if (q.getPurchaserId() != null) {
            w.eq(PurchaseRequirementDO::getPurchaserId, q.getPurchaserId());
        }
        if (q.getSupplierId() != null) {
            w.eq(PurchaseRequirementDO::getSuggestedSupplierId, q.getSupplierId());
        }
        if (q.getStockType() != null) {
            w.apply("EXISTS (SELECT 1 FROM sales_order_item si WHERE si.id = purchase_requirement.so_item_id AND si.stock_type = {0})",
                    q.getStockType());
        }
        if (StringUtils.hasText(q.getKeyword())) {
            String kw = q.getKeyword().trim();
            List<Long> customerIds = customerMapper.selectList(new LambdaQueryWrapper<CustomerDO>()
                            .select(CustomerDO::getId)
                            .eq(CustomerDO::getTenantId, tenant)
                            .and(x -> x.like(CustomerDO::getName, kw).or().like(CustomerDO::getShortName, kw))
                            .last("LIMIT " + KEYWORD_LIMIT))
                    .stream().map(CustomerDO::getId).toList();
            List<Long> soIds = soMapper.selectList(new LambdaQueryWrapper<SalesOrderDO>()
                            .select(SalesOrderDO::getId)
                            .eq(SalesOrderDO::getTenantId, tenant)
                            .and(x -> {
                                x.like(SalesOrderDO::getSoNo, kw);
                                if (!customerIds.isEmpty()) {
                                    x.or().in(SalesOrderDO::getCustomerId, customerIds);
                                }
                            })
                            .last("LIMIT " + KEYWORD_LIMIT))
                    .stream().map(SalesOrderDO::getId).toList();
            w.and(x -> {
                x.like(PurchaseRequirementDO::getModel, kw);
                if (!soIds.isEmpty()) {
                    x.or().in(PurchaseRequirementDO::getSoId, soIds);
                }
            });
        }
        int size = q.getPageSize() == null || q.getPageSize() <= 0 ? 20 : Math.min(q.getPageSize(), 100);
        int page = Math.max(1, q.getPage() == null ? 1 : q.getPage());
        long total = requirementMapper.selectCount(w);
        if (total == 0) {
            return PageResult.of(0L, List.of());
        }
        w.last("ORDER BY (SELECT so.sales_date FROM sales_order so WHERE so.id = purchase_requirement.so_id) ASC, id ASC LIMIT "
                + (long) (page - 1) * size + ", " + size);
        return PageResult.of(total, toVos(requirementMapper.selectList(w)));
    }

    @Override
    public RequirementStatsVO stats() {
        RequirementStatsVO vo = new RequirementStatsVO();
        vo.setOpen(requirementMapper.selectCount(scoped().eq(PurchaseRequirementDO::getStatus, PurchaseConstants.REQ_ACTIVE)
                .apply("purchase_requirement.quantity > " + PLACED_SQL.formatted("2"))));
        vo.setNeed(requirementMapper.selectCount(scoped().eq(PurchaseRequirementDO::getStatus, PurchaseConstants.REQ_ACTIVE)
                .apply("purchase_requirement.quantity > " + PLACED_SQL.formatted("1, 2"))));
        vo.setInDraft(vo.getOpen() - vo.getNeed());
        DataScope scope = dataScopeResolver.current();
        vo.setUnassigned(scope.isAll() ? requirementMapper.selectCount(scoped()
                .eq(PurchaseRequirementDO::getStatus, PurchaseConstants.REQ_ACTIVE)
                .isNull(PurchaseRequirementDO::getPurchaserId)
                .apply("purchase_requirement.quantity > " + PLACED_SQL.formatted("1, 2"))) : 0L);
        LambdaQueryWrapper<PurchaseOrderDO> d = new LambdaQueryWrapper<PurchaseOrderDO>()
                .eq(PurchaseOrderDO::getTenantId, PiStore.tenantId())
                .eq(PurchaseOrderDO::getStatus, PurchaseConstants.PO_DRAFT)
                .isNull(PurchaseOrderDO::getDeletedAt);
        scope.apply(d, PurchaseOrderDO::getPurchaserId);
        vo.setDrafts(orderMapper.selectCount(d));
        return vo;
    }

    private List<RequirementVO> toVos(List<PurchaseRequirementDO> rows) {
        List<Long> ids = rows.stream().map(PurchaseRequirementDO::getId).toList();
        Map<Long, RequirementQty.Qty> q = qty.byRequirement(ids);
        Map<Long, SalesOrderDO> orders = new HashMap<>();
        soMapper.selectBatchIds(rows.stream().map(PurchaseRequirementDO::getSoId).distinct().toList()).forEach(o -> orders.put(o.getId(), o));
        Map<Long, Integer> stock = new HashMap<>();
        soItemMapper.selectBatchIds(rows.stream().map(PurchaseRequirementDO::getSoItemId).distinct().toList())
                .forEach(i -> stock.put(i.getId(), i.getStockType()));
        Map<Long, CustomerDO> customers = lookups.customers(orders.values().stream().map(SalesOrderDO::getCustomerId).toList());
        Map<Long, List<PurchaseOrderItemDO>> lines = ids.isEmpty() ? Map.of()
                : itemMapper.selectList(new LambdaQueryWrapper<PurchaseOrderItemDO>()
                        .in(PurchaseOrderItemDO::getRequirementId, ids)
                        .isNull(PurchaseOrderItemDO::getDeletedAt))
                .stream().collect(Collectors.groupingBy(PurchaseOrderItemDO::getRequirementId));
        Map<Long, PurchaseOrderDO> pos = new HashMap<>();
        Set<Long> poIds = lines.values().stream().flatMap(List::stream).map(PurchaseOrderItemDO::getPoId).collect(Collectors.toSet());
        if (!poIds.isEmpty()) {
            orderMapper.selectBatchIds(poIds).stream()
                    .filter(p -> p.getDeletedAt() == null && p.getStatus() != PurchaseConstants.PO_CANCELLED)
                    .forEach(p -> pos.put(p.getId(), p));
        }
        Map<Long, String> suppliers = supplierNames(Stream.concat(rows.stream().map(PurchaseRequirementDO::getSuggestedSupplierId),
                pos.values().stream().map(PurchaseOrderDO::getSupplierId)).toList());
        Map<Long, String> users = lookups.userNames(Stream.concat(rows.stream().map(PurchaseRequirementDO::getPurchaserId),
                pos.values().stream().map(PurchaseOrderDO::getPurchaserId)).toList());
        List<RequirementVO> out = new ArrayList<>(rows.size());
        for (PurchaseRequirementDO r : rows) {
            RequirementQty.Qty x = q.getOrDefault(r.getId(), RequirementQty.Qty.ZERO);
            RequirementVO vo = new RequirementVO();
            vo.setId(r.getId());
            vo.setSoId(r.getSoId());
            SalesOrderDO o = orders.get(r.getSoId());
            if (o != null) {
                vo.setSoNo(o.getSoNo());
                vo.setSalesDate(o.getSalesDate());
                CustomerDO c = customers.get(o.getCustomerId());
                vo.setCustomerName(InquiryLookups.customerName(c));
                vo.setCustomerCountry(c == null ? null : c.getCountry());
            }
            vo.setModel(r.getModel());
            vo.setBrand(r.getBrand());
            vo.setQuantity(r.getQuantity());
            vo.setDraftQty(x.draft());
            vo.setOrderedQty(x.ordered());
            vo.setAvailableQty(r.getStatus() == PurchaseConstants.REQ_ACTIVE ? RequirementQty.available(r, x) : 0);
            vo.setTargetPrice(r.getTargetPrice());
            vo.setPurchaserId(r.getPurchaserId());
            vo.setPurchaserName(users.get(r.getPurchaserId()));
            vo.setSuggestedSupplierId(r.getSuggestedSupplierId());
            vo.setSuggestedSupplierName(suppliers.get(r.getSuggestedSupplierId()));
            vo.setSuggestedChannel(r.getSuggestedChannel());
            vo.setSuggestedChannelName(PurchaseConstants.CHANNEL_NAMES.get(r.getSuggestedChannel()));
            vo.setSuggestedShopName(r.getSuggestedShopName());
            vo.setStockType(stock.get(r.getSoItemId()));
            String[] st = statusOf(r, x);
            vo.setStatus(st[0]);
            vo.setStatusName(st[1]);
            List<RequirementVO.PoRef> refs = new ArrayList<>();
            for (PurchaseOrderItemDO i : lines.getOrDefault(r.getId(), List.of())) {
                PurchaseOrderDO p = pos.get(i.getPoId());
                if (p == null) {
                    continue;
                }
                RequirementVO.PoRef ref = new RequirementVO.PoRef();
                ref.setId(p.getId());
                ref.setPoNo(p.getPoNo());
                ref.setStatus(p.getStatus());
                ref.setSupplierName(suppliers.get(p.getSupplierId()));
                ref.setPurchaserName(users.get(p.getPurchaserId()));
                ref.setQuantity(i.getQuantity());
                refs.add(ref);
            }
            vo.setPurchaseOrders(refs);
            out.add(vo);
        }
        return out;
    }

    /** 显示状态：由数量得出 */
    static String[] statusOf(PurchaseRequirementDO r, RequirementQty.Qty x) {
        if (r.getStatus() == PurchaseConstants.REQ_CLOSED) {
            return new String[]{"closed", PurchaseConstants.REQ_VIEW_CLOSED};
        }
        if (r.getStatus() == PurchaseConstants.REQ_ORDER_CANCELLED) {
            return new String[]{"orderCancelled", PurchaseConstants.REQ_VIEW_ORDER_CANCELLED};
        }
        if (x.ordered() >= r.getQuantity()) {
            return new String[]{"ordered", PurchaseConstants.REQ_VIEW_ORDERED};
        }
        if (x.ordered() > 0) {
            return new String[]{"partial", PurchaseConstants.REQ_VIEW_PARTIAL};
        }
        if (x.draft() > 0) {
            return new String[]{"draft", PurchaseConstants.REQ_VIEW_DRAFT};
        }
        return new String[]{"pending", PurchaseConstants.REQ_VIEW_PENDING};
    }

    private Map<Long, String> supplierNames(Collection<Long> ids) {
        List<Long> keys = ids.stream().filter(Objects::nonNull).distinct().toList();
        Map<Long, String> map = new HashMap<>(keys.size() * 2 + 1);
        if (!keys.isEmpty()) {
            supplierMapper.selectBatchIds(keys).forEach(s -> map.put(s.getId(), s.getName()));
        }
        return map;
    }

    // ---------------------------------------------------------------- 拆分与指派

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void split(Long id, SplitRequirementRequest req) {
        PurchaseRequirementDO r = lockVisible(List.of(id)).get(0);
        if (r.getStatus() != PurchaseConstants.REQ_ACTIVE) {
            throw new BizException("需求已关闭，不能拆分");
        }
        if (req.getPurchaserId() != null) {
            requireUser(req.getPurchaserId());
        }
        if (req.getSupplierId() != null) {
            drafts.requireSupplier(req.getSupplierId());
        }
        RequirementQty.Qty x = qty.of(id);
        int notOrdered = r.getQuantity() - x.ordered();
        int max = x.ordered() > 0 ? notOrdered : r.getQuantity() - 1;
        int n = req.getQuantity();
        if (max <= 0) {
            throw new BizException("这条需求已经全部下单，不能拆分");
        }
        if (n > max) {
            throw new BizException("最多能拆出 " + max + " 个");
        }
        Long operator = currentUser.resolve();
        int fromDraft = Math.max(0, n - RequirementQty.available(r, x));
        Set<Long> touched = new LinkedHashSet<>();
        if (fromDraft > 0) {
            drafts.takeFromDrafts(r.getId(), fromDraft, touched);
        }
        r.setQuantity(r.getQuantity() - n);
        requirementMapper.updateById(r);
        PurchaseRequirementDO y = new PurchaseRequirementDO();
        y.setTenantId(r.getTenantId());
        y.setSoId(r.getSoId());
        y.setSoItemId(r.getSoItemId());
        y.setQuotationItemId(r.getQuotationItemId());
        y.setModel(r.getModel());
        y.setBrand(r.getBrand());
        y.setQuantity(n);
        y.setTargetPrice(r.getTargetPrice());
        y.setPurchaserId(req.getPurchaserId() != null ? req.getPurchaserId() : r.getPurchaserId());
        if (req.getSupplierId() != null) {
            y.setSuggestedSupplierId(req.getSupplierId());
            y.setSuggestedChannel(PurchaseConstants.CHANNEL_SUPPLIER);
            y.setSuggestedShopName("");
        } else {
            y.setSuggestedSupplierId(r.getSuggestedSupplierId());
            y.setSuggestedChannel(r.getSuggestedChannel());
            y.setSuggestedShopName(r.getSuggestedShopName());
        }
        y.setStatus(PurchaseConstants.REQ_ACTIVE);
        requirementMapper.insert(y);
        drafts.settle(touched, operator);
        lifecycle.placeAvailable(List.of(y), "拆分需求", operator);
        logService.recordOperateLog(PurchaseConstants.MENU_REQUIREMENT, "拆分采购需求", null,
                Map.of("model", r.getModel(), "remain", r.getQuantity(), "split", n));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assign(AssignRequirementsRequest req) {
        if (req.getPurchaserId() != null) {
            requireUser(req.getPurchaserId());
        }
        List<PurchaseRequirementDO> rows = lockVisible(req.getIds());
        Map<Long, RequirementQty.Qty> q = qty.byRequirement(req.getIds());
        List<PurchaseRequirementDO> open = rows.stream()
                .filter(r -> r.getStatus() == PurchaseConstants.REQ_ACTIVE
                        && q.getOrDefault(r.getId(), RequirementQty.Qty.ZERO).ordered() < r.getQuantity())
                .toList();
        if (open.isEmpty()) {
            throw new BizException("选中的需求都已下单或已关闭");
        }
        Map<Long, String> names = lookups.userNames(Stream.concat(open.stream().map(PurchaseRequirementDO::getPurchaserId),
                Stream.of(req.getPurchaserId())).toList());
        List<String> changes = open.stream().filter(r -> !Objects.equals(r.getPurchaserId(), req.getPurchaserId()))
                .map(r -> r.getModel() + " " + names.getOrDefault(r.getPurchaserId(), "未指定") + " → "
                        + names.getOrDefault(req.getPurchaserId(), "未指定"))
                .toList();
        lifecycle.reassign(open, req.getPurchaserId(), currentUser.resolve());
        syncOrderPurchasers(open);
        logService.recordOperateLog(PurchaseConstants.MENU_REQUIREMENT, "指派采购员", null, Map.of("items", changes));
    }

    /** 订单型号行的采购员跟着第一条有效需求，保持旧的显示与筛选一致 */
    private void syncOrderPurchasers(List<PurchaseRequirementDO> reqs) {
        for (Long soItemId : reqs.stream().map(PurchaseRequirementDO::getSoItemId).collect(Collectors.toCollection(LinkedHashSet::new))) {
            PurchaseRequirementDO first = requirementMapper.selectOne(new LambdaQueryWrapper<PurchaseRequirementDO>()
                    .eq(PurchaseRequirementDO::getSoItemId, soItemId)
                    .eq(PurchaseRequirementDO::getStatus, PurchaseConstants.REQ_ACTIVE)
                    .isNull(PurchaseRequirementDO::getDeletedAt)
                    .orderByAsc(PurchaseRequirementDO::getId)
                    .last("LIMIT 1"));
            SalesOrderItemDO i = soItemMapper.selectById(soItemId);
            if (first != null && i != null && !Objects.equals(i.getPurchaserId(), first.getPurchaserId())) {
                i.setPurchaserId(first.getPurchaserId());
                soItemMapper.updateById(i);
            }
        }
    }

    // ---------------------------------------------------------------- 需求池生成采购单

    @Override
    public GeneratePreviewVO preview(List<Long> ids) {
        List<PurchaseRequirementDO> rows = visibleAvailable(ids, false);
        Map<Long, RequirementQty.Qty> q = qty.byRequirement(ids);
        Map<Long, String> suppliers = supplierNames(rows.stream().map(PurchaseRequirementDO::getSuggestedSupplierId).toList());
        Map<Long, String> soNos = new HashMap<>();
        soMapper.selectBatchIds(rows.stream().map(PurchaseRequirementDO::getSoId).distinct().toList())
                .forEach(o -> soNos.put(o.getId(), o.getSoNo()));
        Map<Long, String> users = lookups.userNames(rows.stream().map(PurchaseRequirementDO::getPurchaserId).toList());
        Map<String, GeneratePreviewVO.Group> groups = new LinkedHashMap<>();
        for (PurchaseRequirementDO r : rows) {
            String key;
            if (r.getSuggestedSupplierId() != null) {
                key = "supplier:" + r.getSuggestedSupplierId();
            } else if (StringUtils.hasText(r.getSuggestedShopName())) {
                key = "shop:" + r.getSuggestedChannel() + ":" + r.getSuggestedShopName();
            } else {
                key = "none";
            }
            GeneratePreviewVO.Group g = groups.computeIfAbsent(key, k -> {
                GeneratePreviewVO.Group x = new GeneratePreviewVO.Group();
                x.setKey(k);
                x.setLines(new ArrayList<>());
                x.setMatchedByName(false);
                if (r.getSuggestedSupplierId() != null) {
                    x.setSupplierId(r.getSuggestedSupplierId());
                    x.setSupplierName(suppliers.get(r.getSuggestedSupplierId()));
                    x.setTitle(x.getSupplierName());
                } else if (StringUtils.hasText(r.getSuggestedShopName())) {
                    x.setChannel(r.getSuggestedChannel());
                    x.setShopName(r.getSuggestedShopName());
                    String channel = PurchaseConstants.CHANNEL_NAMES.get(r.getSuggestedChannel());
                    x.setTitle((channel == null ? "" : channel + " · ") + r.getSuggestedShopName());
                    SupplierDO same = activeByName(r.getSuggestedShopName());
                    if (same != null) {
                        x.setSupplierId(same.getId());
                        x.setSupplierName(same.getName());
                        x.setMatchedByName(true);
                    }
                } else {
                    x.setTitle("没有建议供应商");
                }
                return x;
            });
            GeneratePreviewVO.Line line = new GeneratePreviewVO.Line();
            line.setRequirementId(r.getId());
            line.setModel(r.getModel());
            line.setBrand(r.getBrand());
            line.setSoNo(soNos.get(r.getSoId()));
            line.setAvailableQty(RequirementQty.available(r, q.getOrDefault(r.getId(), RequirementQty.Qty.ZERO)));
            line.setPurchaserName(users.get(r.getPurchaserId()));
            g.getLines().add(line);
        }
        GeneratePreviewVO vo = new GeneratePreviewVO();
        vo.setGroups(new ArrayList<>(groups.values()));
        return vo;
    }

    private SupplierDO activeByName(String name) {
        return supplierMapper.selectOne(new LambdaQueryWrapper<SupplierDO>()
                .eq(SupplierDO::getTenantId, PiStore.tenantId())
                .eq(SupplierDO::getName, name.trim())
                .eq(SupplierDO::getStatus, 1)
                .isNull(SupplierDO::getDeletedAt)
                .last("LIMIT 1"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Long> generate(GeneratePurchaseRequest req) {
        List<Long> all = req.getGroups().stream().flatMap(g -> g.getRequirementIds().stream()).toList();
        if (new HashSet<>(all).size() != all.size()) {
            throw new BizException("同一条需求不能放进两组");
        }
        Long me = currentUser.resolve();
        if (me == null) {
            throw new BizException("请先登录");
        }
        List<PurchaseRequirementDO> rows = visibleAvailable(all, true);
        Map<Long, PurchaseRequirementDO> byId = rows.stream().collect(Collectors.toMap(PurchaseRequirementDO::getId, r -> r));
        Map<Long, RequirementQty.Qty> q = qty.byRequirement(all);
        List<PurchaseDrafts.Placement> list = new ArrayList<>(all.size());
        for (GeneratePurchaseRequest.Group g : req.getGroups()) {
            drafts.requireSupplier(g.getSupplierId());
            for (Long id : g.getRequirementIds()) {
                PurchaseRequirementDO r = byId.get(id);
                if (r.getPurchaserId() == null) {
                    r.setPurchaserId(me);
                    requirementMapper.updateById(r);
                }
                list.add(new PurchaseDrafts.Placement(r, me, g.getSupplierId(),
                        RequirementQty.available(r, q.getOrDefault(id, RequirementQty.Qty.ZERO))));
            }
        }
        syncOrderPurchasers(rows);
        List<PurchaseOrderDO> pos = drafts.place(list, null, "由需求池生成", me);
        logService.recordOperateLog(PurchaseConstants.MENU_REQUIREMENT, "生成采购单", null,
                Map.of("requirements", all.size(), "drafts", pos.size()));
        return pos.stream().map(PurchaseOrderDO::getId).distinct().toList();
    }

    // ---------------------------------------------------------------- 通用

    private LambdaQueryWrapper<PurchaseRequirementDO> scoped() {
        LambdaQueryWrapper<PurchaseRequirementDO> w = new LambdaQueryWrapper<PurchaseRequirementDO>()
                .eq(PurchaseRequirementDO::getTenantId, PiStore.tenantId())
                .isNull(PurchaseRequirementDO::getDeletedAt);
        dataScopeResolver.current().apply(w, PurchaseRequirementDO::getPurchaserId);
        return w;
    }

    /** 当前用户数据范围内的需求（加锁，按传入顺序）；没有采购员的只有数据权限为全部时可见 */
    private List<PurchaseRequirementDO> lockVisible(List<Long> ids) {
        List<Long> keys = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (keys.isEmpty()) {
            throw new BizException("请选择需求");
        }
        requirementMapper.lockByIds(keys);
        Map<Long, PurchaseRequirementDO> found = requirementMapper.selectList(scoped().in(PurchaseRequirementDO::getId, keys))
                .stream().collect(Collectors.toMap(PurchaseRequirementDO::getId, r -> r));
        if (found.size() != keys.size()) {
            throw new BizException("需求不存在");
        }
        return keys.stream().map(found::get).toList();
    }

    /** 可见、有效且还有可下单数量的需求 */
    private List<PurchaseRequirementDO> visibleAvailable(List<Long> ids, boolean lock) {
        List<PurchaseRequirementDO> rows = lock ? lockVisible(ids) : requirementMapper.selectList(scoped().in(PurchaseRequirementDO::getId,
                ids.isEmpty() ? List.of(0L) : ids));
        if (rows.size() != new HashSet<>(ids).size()) {
            throw new BizException("需求不存在");
        }
        Map<Long, RequirementQty.Qty> q = qty.byRequirement(ids);
        for (PurchaseRequirementDO r : rows) {
            if (r.getStatus() != PurchaseConstants.REQ_ACTIVE
                    || RequirementQty.available(r, q.getOrDefault(r.getId(), RequirementQty.Qty.ZERO)) <= 0) {
                throw new BizException(r.getModel() + " 已经全部排入采购单，不能再生成");
            }
        }
        return rows;
    }

    private void requireUser(Long id) {
        if (lookups.userNames(List.of(id)).isEmpty()) {
            throw new BizException("采购员不存在");
        }
    }
}
