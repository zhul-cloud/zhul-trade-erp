package com.zhul.erp.modules.supply.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhul.erp.modules.supply.constants.SupplyConstants;
import com.zhul.erp.modules.supply.dto.DemandVO;
import com.zhul.erp.modules.supply.dto.MatrixVO;
import com.zhul.erp.modules.supply.dto.ProjectVO;
import com.zhul.erp.modules.supply.entity.ScDemandDO;
import com.zhul.erp.modules.supply.repository.ScDemandMapper;
import com.zhul.erp.modules.supply.support.DemandAssembler;
import com.zhul.erp.modules.supply.support.SupplyLookups;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 覆盖矩阵（tasks 4.7）：按覆盖状态或按无货汇总品牌 × 二级品类，型号清单按更新时间倒序分页。
 * 供应商名称只对有「查看货源信息」的人返回（由 DemandAssembler 处理）。只按租户隔离。
 */
@Service
@RequiredArgsConstructor
public class CoverageMatrixService {

    private final ScDemandMapper demandMapper;
    private final SupplyBoardService boardService;
    private final GapProjectService projectService;
    private final DemandAssembler assembler;
    private final SupplyLookups lookups;

    public MatrixVO matrix(String mode, Integer period, Long parentCategoryId, Long brandId, Integer priority, Long ownerId,
                           Boolean gapsOnly) {
        boolean stockoutMode = "stockout".equals(mode);
        int days = period != null && period == 90 ? 90 : 30;
        Map<Long, Integer> stockout = boardService.stockouts(days);
        Map<Long, Long> parents = lookups.categoryParents();
        List<ScDemandDO> rows = demandMapper.selectList(new LambdaQueryWrapper<ScDemandDO>()
                .eq(ScDemandDO::getStatus, SupplyConstants.DEMAND_IN_MATRIX)
                .eq(brandId != null, ScDemandDO::getBrandId, brandId)
                .eq(priority != null, ScDemandDO::getPriority, priority)
                .eq(ownerId != null, ScDemandDO::getOwnerId, ownerId)
                .isNull(ScDemandDO::getDeletedAt)).stream()
                .filter(d -> parentCategoryId == null || parentCategoryId.equals(parents.get(d.getCategoryId())))
                .toList();
        Map<String, MatrixVO.Cell> cells = new LinkedHashMap<>();
        for (ScDemandDO d : rows) {
            MatrixVO.Cell c = cells.computeIfAbsent(d.getBrandId() + ":" + d.getCategoryId(), k -> {
                MatrixVO.Cell n = new MatrixVO.Cell();
                n.setBrandId(d.getBrandId());
                n.setCategoryId(d.getCategoryId());
                n.setCoverage(new LinkedHashMap<>());
                n.setWorst(SupplyConstants.COVER_READY);
                return n;
            });
            c.setTotal(c.getTotal() + 1);
            c.getCoverage().merge(d.getCoverageStatus(), 1, Integer::sum);
            c.setWorst(Math.min(c.getWorst(), d.getCoverageStatus()));
            Integer so = stockout.get(d.getId());
            if (so != null) {
                c.setStockoutModels(c.getStockoutModels() + 1);
                c.setStockoutTimes(c.getStockoutTimes() + so);
            }
        }
        List<MatrixVO.Cell> list = new ArrayList<>(cells.values());
        if (Boolean.TRUE.equals(gapsOnly)) {
            list.removeIf(c -> c.getWorst() > SupplyConstants.COVER_LEAD);
        }
        if (stockoutMode) {
            list.removeIf(c -> c.getStockoutModels() == 0);
        }
        Map<String, ProjectVO> projects = new HashMap<>();
        projectService.active(null).forEach(p -> projects.put(p.getBrandId() + ":" + p.getCategoryId(), p));
        for (MatrixVO.Cell c : list) {
            ProjectVO p = projects.getOrDefault(c.getBrandId() + ":" + c.getCategoryId(), projects.get(c.getBrandId() + ":0"));
            if (p != null) {
                c.setProjectId(p.getId());
                c.setProjectName(p.getName());
            }
        }
        Map<Long, String> brands = lookups.brandNames(list.stream().map(MatrixVO.Cell::getBrandId).toList());
        Map<Long, String> cats = lookups.categoryNames(list.stream().map(MatrixVO.Cell::getCategoryId).toList());
        MatrixVO v = new MatrixVO();
        v.setMode(stockoutMode ? "stockout" : "cover");
        v.setDays(days);
        v.setCells(list);
        v.setBrands(axis(list, true, brands, stockoutMode));
        v.setCategories(axis(list, false, cats, stockoutMode));
        return v;
    }

    /** 型号清单：某品牌某品类下已进入矩阵的需求对象，按更新时间倒序 */
    public Page<DemandVO> models(Long brandId, Long categoryId, Integer coverage, int page, int size) {
        Page<ScDemandDO> p = demandMapper.selectPage(Page.of(page, Math.min(size, 200)), new LambdaQueryWrapper<ScDemandDO>()
                .eq(ScDemandDO::getStatus, SupplyConstants.DEMAND_IN_MATRIX)
                .eq(brandId != null, ScDemandDO::getBrandId, brandId)
                .eq(categoryId != null, ScDemandDO::getCategoryId, categoryId)
                .eq(coverage != null, ScDemandDO::getCoverageStatus, coverage)
                .isNull(ScDemandDO::getDeletedAt)
                .orderByDesc(ScDemandDO::getUpdateTime).orderByDesc(ScDemandDO::getId));
        Page<DemandVO> out = Page.of(p.getCurrent(), p.getSize(), p.getTotal());
        out.setRecords(assembler.toVOs(p.getRecords()));
        return out;
    }

    private static List<MatrixVO.Axis> axis(List<MatrixVO.Cell> cells, boolean brand, Map<Long, String> names, boolean stockout) {
        Map<Long, MatrixVO.Axis> out = new LinkedHashMap<>();
        for (MatrixVO.Cell c : cells) {
            Long id = brand ? c.getBrandId() : c.getCategoryId();
            MatrixVO.Axis a = out.computeIfAbsent(id, k -> {
                MatrixVO.Axis n = new MatrixVO.Axis();
                n.setId(k);
                n.setName(names.getOrDefault(k, brand ? "" : "未分类"));
                return n;
            });
            a.setTotal(a.getTotal() + (stockout ? c.getStockoutTimes() : c.getTotal()));
        }
        return out.values().stream().sorted(Comparator.comparing(MatrixVO.Axis::getTotal).reversed()
                .thenComparing(MatrixVO.Axis::getName)).toList();
    }
}
