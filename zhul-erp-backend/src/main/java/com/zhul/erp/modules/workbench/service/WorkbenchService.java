package com.zhul.erp.modules.workbench.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.security.PermissionChecker;
import com.zhul.erp.framework.security.SecurityUtils;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.system.entity.AccountDO;
import com.zhul.erp.modules.system.entity.RoleDO;
import com.zhul.erp.modules.system.repository.AccountMapper;
import com.zhul.erp.modules.system.repository.RoleMapper;
import com.zhul.erp.modules.workbench.dto.WorkbenchTemplateVO;
import com.zhul.erp.modules.workbench.dto.WorkbenchVO;
import com.zhul.erp.modules.workbench.entity.WorkbenchTemplateDO;
import com.zhul.erp.modules.workbench.repository.WorkbenchTemplateMapper;
import com.zhul.erp.modules.workbench.widget.WorkbenchWidget;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 工作台（spec system/workbench）：按用户的角色取工作台模板，组件按菜单权限过滤；组件数据只给出现在用户看板里的组件。
 * 组件由 Spring 注入的全部 {@link WorkbenchWidget} 组成，模板只引用编码，新增模板或组件都不改这里。
 */
@Service
public class WorkbenchService {

    public static final String GENERAL = "general";
    private static final TypeReference<List<WorkbenchVO.Widget>> WIDGET_LIST = new TypeReference<>() {
    };

    private final Map<String, WorkbenchWidget> widgets;
    private final WorkbenchTemplateMapper templateMapper;
    private final AccountMapper accountMapper;
    private final RoleMapper roleMapper;
    private final PermissionChecker perm;
    private final ObjectMapper objectMapper;

    public WorkbenchService(List<WorkbenchWidget> widgets, WorkbenchTemplateMapper templateMapper, AccountMapper accountMapper,
                            RoleMapper roleMapper, PermissionChecker perm, ObjectMapper objectMapper) {
        this.widgets = widgets.stream().collect(Collectors.toMap(WorkbenchWidget::key, Function.identity()));
        this.templateMapper = templateMapper;
        this.accountMapper = accountMapper;
        this.roleMapper = roleMapper;
        this.perm = perm;
        this.objectMapper = objectMapper;
    }

    /** 当前用户的看板：角色对应的模板去重，按职级、模板顺序排列；没有设置模板的用通用 */
    public WorkbenchVO current() {
        List<WorkbenchTemplateDO> templates = templatesOf(templateCodes());
        WorkbenchTemplateDO general = templateMapper.selectOne(new LambdaQueryWrapper<WorkbenchTemplateDO>()
                .eq(WorkbenchTemplateDO::getCode, GENERAL).last("LIMIT 1"));
        WorkbenchVO vo = new WorkbenchVO();
        for (WorkbenchTemplateDO t : templates) {
            WorkbenchVO.Board b = new WorkbenchVO.Board();
            b.setCode(t.getCode());
            b.setName(t.getName());
            b.setWidgets(visible(t));
            b.setFallback(false);
            if (b.getWidgets().isEmpty() && general != null && !GENERAL.equals(t.getCode())) {
                // 这个岗位看板的组件还没上线或都没有权限：先显示通用看板的组件
                b.setWidgets(visible(general));
                b.setFallback(true);
            }
            vo.getBoards().add(b);
        }
        vo.setDefaultCode(vo.getBoards().isEmpty() ? null : vo.getBoards().get(0).getCode());
        return vo;
    }

    /** 组件数据：组件必须出现在当前用户的某个看板里（已按权限过滤） */
    public Object widget(String key) {
        WorkbenchWidget w = widgets.get(key);
        if (w == null) {
            throw new BizException("看板组件不存在");
        }
        boolean mine = current().getBoards().stream()
                .anyMatch(b -> b.getWidgets().stream().anyMatch(x -> key.equals(x.getKey())));
        if (!mine) {
            throw new BizException("没有这个看板组件的权限");
        }
        return w.load();
    }

    /** 全部启用的模板（角色管理下拉用） */
    public List<WorkbenchTemplateVO> templates() {
        return templateMapper.selectList(new LambdaQueryWrapper<WorkbenchTemplateDO>()
                        .eq(WorkbenchTemplateDO::getStatus, 1)
                        .orderByAsc(WorkbenchTemplateDO::getSort))
                .stream().map(t -> {
                    WorkbenchTemplateVO vo = new WorkbenchTemplateVO();
                    vo.setCode(t.getCode());
                    vo.setName(t.getName());
                    vo.setDescription(t.getDescription());
                    return vo;
                }).toList();
    }

    /** 模板编码是否存在且启用（保存角色时校验） */
    public boolean exists(String code) {
        return templateMapper.selectCount(new LambdaQueryWrapper<WorkbenchTemplateDO>()
                .eq(WorkbenchTemplateDO::getCode, code).eq(WorkbenchTemplateDO::getStatus, 1)) > 0;
    }

    private Set<String> templateCodes() {
        AccountDO account = accountMapper.selectOne(new LambdaQueryWrapper<AccountDO>()
                .eq(AccountDO::getUsername, SecurityUtils.getCurrentUsername()).last("LIMIT 1"));
        Set<String> codes = new LinkedHashSet<>();
        if (account != null) {
            List<String> roleCodes = templateMapper.roleCodes(account.getId());
            if (!roleCodes.isEmpty()) {
                Integer tenantId = TenantContext.getTenantId();
                roleMapper.selectList(new LambdaQueryWrapper<RoleDO>()
                                .in(RoleDO::getCode, roleCodes)
                                .in(RoleDO::getTenantId, tenantId == null ? List.of(0) : List.of(0, tenantId))
                                .eq(RoleDO::getStatus, 1))
                        .forEach(r -> codes.add(r.getWorkbenchTemplate() == null ? GENERAL : r.getWorkbenchTemplate()));
            }
        }
        if (codes.isEmpty()) {
            codes.add(GENERAL);
        }
        return codes;
    }

    private List<WorkbenchTemplateDO> templatesOf(Set<String> codes) {
        List<WorkbenchTemplateDO> list = templateMapper.selectList(new LambdaQueryWrapper<WorkbenchTemplateDO>()
                .in(WorkbenchTemplateDO::getCode, codes)
                .eq(WorkbenchTemplateDO::getStatus, 1));
        if (list.isEmpty()) {
            list = templateMapper.selectList(new LambdaQueryWrapper<WorkbenchTemplateDO>()
                    .eq(WorkbenchTemplateDO::getCode, GENERAL));
        }
        return list.stream()
                .sorted(Comparator.comparing(WorkbenchTemplateDO::getSeniority).thenComparing(WorkbenchTemplateDO::getSort))
                .toList();
    }

    /** 模板里已上线、且当前用户有菜单权限的组件 */
    private List<WorkbenchVO.Widget> visible(WorkbenchTemplateDO t) {
        List<WorkbenchVO.Widget> list;
        try {
            list = objectMapper.readValue(t.getWidgets(), WIDGET_LIST);
        } catch (JsonProcessingException e) {
            throw new BizException("工作台模板「" + t.getName() + "」的组件清单格式不对", e);
        }
        return list.stream()
                .filter(w -> widgets.containsKey(w.getKey()))
                .filter(w -> widgets.get(w.getKey()).menus().stream().anyMatch(perm::canAccessMenu))
                .toList();
    }
}
