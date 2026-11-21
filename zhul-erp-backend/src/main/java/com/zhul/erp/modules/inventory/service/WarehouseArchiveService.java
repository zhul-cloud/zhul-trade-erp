package com.zhul.erp.modules.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.inventory.constants.InvConstants;
import com.zhul.erp.modules.inventory.dto.InvWarehouseVO;
import com.zhul.erp.modules.inventory.entity.InvWarehouseDO;
import com.zhul.erp.modules.inventory.repository.InvWarehouseMapper;
import com.zhul.erp.modules.sales.support.PiStore;
import com.zhul.erp.modules.system.service.LogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 仓库档案与库存模式（spec warehouse/inventory「仓库档案」「库存模式」） */
@Service
@RequiredArgsConstructor
public class WarehouseArchiveService {

    private static final String MENU = "仓库档案";
    private static final Map<String, String> MODE_NAMES = Map.of(InvConstants.MODE_DROPSHIP, "无货源", InvConstants.MODE_STOCK, "库存模式");

    private final InvWarehouseMapper mapper;
    private final InventoryWarehouses warehouses;
    private final LogService logService;

    /** 全部仓库，按更新时间倒序；自有仓不存在时先建 */
    @Transactional(rollbackFor = Exception.class)
    public List<InvWarehouseVO> list() {
        warehouses.own();
        List<InvWarehouseDO> list = mapper.selectList(new LambdaQueryWrapper<InvWarehouseDO>()
                .eq(InvWarehouseDO::getTenantId, PiStore.tenantId())
                .isNull(InvWarehouseDO::getDeletedAt)
                .orderByDesc(InvWarehouseDO::getUpdateTime)
                .orderByDesc(InvWarehouseDO::getId));
        Map<Long, Integer> onHand = warehouses.onHandOf(list.stream().map(InvWarehouseDO::getId).toList());
        return list.stream().map(w -> {
            InvWarehouseVO v = new InvWarehouseVO();
            v.setId(w.getId());
            v.setCode(w.getCode());
            v.setName(w.getName());
            v.setType(w.getType());
            v.setTypeName(InvConstants.WH_TYPE_NAMES.get(w.getType()));
            v.setForwarderId(w.getForwarderId());
            v.setOnHand(onHand.getOrDefault(w.getId(), 0));
            v.setStatus(w.getStatus());
            v.setStatusName(w.getStatus() == 1 ? "启用" : "停用");
            v.setCreateTime(w.getCreateTime());
            v.setCreateBy(w.getCreateBy());
            v.setUpdateTime(w.getUpdateTime());
            v.setUpdateBy(w.getUpdateBy());
            return v;
        }).toList();
    }

    /** 自有仓改名；货代仓名称跟着货代 */
    @Transactional(rollbackFor = Exception.class)
    public void rename(Long id, String name) {
        InvWarehouseDO w = warehouses.require(id);
        if (w.getType() != InvConstants.WH_OWN) {
            throw new BizException("货代仓名称跟着货代，请在货代管理修改");
        }
        String n = name.trim();
        if (Objects.equals(w.getName(), n)) {
            return;
        }
        String before = w.getName();
        w.setName(n);
        w.setUpdateTime(LocalDateTime.now());
        mapper.updateById(w);
        logService.recordOperateLog(MENU, "修改仓库名称", Map.of("name", before), Map.of("name", n));
    }

    public Map<String, String> mode() {
        String m = warehouses.mode();
        return Map.of("mode", m, "modeName", MODE_NAMES.get(m));
    }

    /** 修改库存模式（前端二次确认），写操作日志 */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, String> setMode(String mode) {
        if (!MODE_NAMES.containsKey(mode)) {
            throw new BizException("库存模式不正确");
        }
        String before = warehouses.mode();
        if (!before.equals(mode)) {
            warehouses.saveConfig(InvConstants.CONFIG_MODE, "库存模式", mode);
            logService.recordOperateLog(MENU, "修改库存模式", Map.of("mode", MODE_NAMES.get(before)), Map.of("mode", MODE_NAMES.get(mode)));
        }
        return mode();
    }
}
