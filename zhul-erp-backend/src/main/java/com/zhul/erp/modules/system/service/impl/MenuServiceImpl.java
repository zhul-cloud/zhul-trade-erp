package com.zhul.erp.modules.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.security.EffectivePermissionResolver;
import com.zhul.erp.modules.system.dto.DeleteCheckVO;
import com.zhul.erp.modules.system.dto.MenuVO;
import com.zhul.erp.modules.system.dto.SaveMenuRequest;
import com.zhul.erp.modules.system.entity.ResourceDO;
import com.zhul.erp.modules.system.entity.RoleDO;
import com.zhul.erp.modules.system.entity.RoleResourceDO;
import com.zhul.erp.modules.system.repository.ResourceMapper;
import com.zhul.erp.modules.system.repository.RoleMapper;
import com.zhul.erp.modules.system.repository.RoleResourceMapper;
import com.zhul.erp.modules.system.service.MenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MenuServiceImpl implements MenuService {

    private final ResourceMapper resourceMapper;
    private final RoleResourceMapper roleResourceMapper;
    private final RoleMapper roleMapper;
    private final EffectivePermissionResolver permissionResolver;

    /**
     * 菜单树只返回当前账号自己能访问的部分（含上级目录）：平台超管看到全部，
     * 租户管理员只看到套餐内的菜单，所以给角色分配权限时不会出现租户管理等平台菜单。
     */
    @Override
    public List<MenuVO> getMenuTree() {
        List<ResourceDO> all = resourceMapper.selectList(
            new LambdaQueryWrapper<ResourceDO>().orderByAsc(ResourceDO::getSort)
        );
        Set<Integer> visible = visibleIds(currentUsername(), all);
        if (visible != null) {
            all = all.stream().filter(r -> visible.contains(r.getId())).collect(Collectors.toList());
        }
        return buildTree(all, 0);
    }

    /** 当前账号能访问的资源 ID 加上它们的全部上级；null 表示不受限 */
    private Set<Integer> visibleIds(String username, List<ResourceDO> all) {
        return visibleIds(username, all, false);
    }

    /** pagesOnly 为 true 时只从目录与页面往上补上级，按钮本身保留但不带出它所在的页面 */
    private Set<Integer> visibleIds(String username, List<ResourceDO> all, boolean pagesOnly) {
        Set<Integer> allowed = permissionResolver.resolveAllowedResourceIds(username);
        if (allowed == null) {
            return null;
        }
        Map<Integer, Integer> parentOf = new HashMap<>(all.size() * 2);
        Set<Integer> buttons = new HashSet<>();
        for (ResourceDO r : all) {
            parentOf.put(r.getId(), r.getPid());
            if (r.getType() != null && r.getType() == 3) {
                buttons.add(r.getId());
            }
        }
        Set<Integer> result = new HashSet<>(allowed.size() * 2);
        for (Integer id : allowed) {
            if (pagesOnly && buttons.contains(id)) {
                result.add(id);
                continue;
            }
            Integer cur = id;
            while (cur != null && cur != 0 && result.add(cur)) {
                cur = parentOf.get(cur);
            }
        }
        return result;
    }

    private static String currentUsername() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    private List<MenuVO> buildTree(List<ResourceDO> all, int pid) {
        return all.stream()
            .filter(r -> r.getPid() == pid)
            .map(r -> {
                MenuVO vo = toVO(r);
                List<MenuVO> children = buildTree(all, r.getId());
                if (!children.isEmpty()) vo.setChildren(children);
                return vo;
            })
            .collect(Collectors.toList());
    }

    @Override
    public void createMenu(SaveMenuRequest request) {
        ResourceDO menu = new ResourceDO();
        menu.setPid(request.getPid());
        menu.setTenantId(0);
        menu.setName(request.getName());
        menu.setType(request.getType());
        menu.setPath(request.getPath() != null ? request.getPath() : "");
        menu.setComponentPath(request.getComponentPath() != null ? request.getComponentPath() : "");
        menu.setPermission(request.getPermission() != null ? request.getPermission() : "");
        menu.setLightIcon(request.getLightIcon() != null ? request.getLightIcon() : "");
        menu.setLightSelectedIcon("");
        menu.setDarkIcon(request.getDarkIcon() != null ? request.getDarkIcon() : "");
        menu.setDarkSelectedIcon("");
        menu.setSort(request.getSort());
        menu.setStatus(request.getStatus());
        menu.setMicroApp(request.getMicroApp() != null ? request.getMicroApp() : "");
        menu.setIsExternal(request.getIsExternal());
        menu.setIsCache(request.getIsCache());
        menu.setIsHidden(request.getIsHidden());
        resourceMapper.insert(menu);
        menu.setCode("RS" + menu.getType() + String.format("%05d", menu.getId()));
        resourceMapper.updateById(menu);
    }

    @Override
    public void updateMenu(Integer id, SaveMenuRequest request) {
        ResourceDO menu = resourceMapper.selectById(id);
        if (menu == null) throw new BizException("菜单不存在");
        menu.setPid(request.getPid());
        menu.setName(request.getName());
        if (request.getPath() != null) menu.setPath(request.getPath());
        if (request.getComponentPath() != null) menu.setComponentPath(request.getComponentPath());
        if (request.getPermission() != null) menu.setPermission(request.getPermission());
        if (request.getLightIcon() != null) menu.setLightIcon(request.getLightIcon());
        if (request.getDarkIcon() != null) menu.setDarkIcon(request.getDarkIcon());
        menu.setSort(request.getSort());
        menu.setStatus(request.getStatus());
        if (request.getMicroApp() != null) menu.setMicroApp(request.getMicroApp());
        if (request.getIsExternal() != null) menu.setIsExternal(request.getIsExternal());
        if (request.getIsCache() != null) menu.setIsCache(request.getIsCache());
        if (request.getIsHidden() != null) menu.setIsHidden(request.getIsHidden());
        resourceMapper.updateById(menu);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMenu(Integer id) {
        long childCount = resourceMapper.selectCount(
            new LambdaQueryWrapper<ResourceDO>().eq(ResourceDO::getPid, id)
        );
        if (childCount > 0) throw new BizException("存在子菜单，无法删除");
        resourceMapper.deleteById(id);
        roleResourceMapper.delete(
            new LambdaQueryWrapper<RoleResourceDO>().eq(RoleResourceDO::getResourceId, id)
        );
    }

    @Override
    public DeleteCheckVO checkDeletable(Integer id) {
        DeleteCheckVO vo = new DeleteCheckVO();
        List<ResourceDO> children = resourceMapper.selectList(
            new LambdaQueryWrapper<ResourceDO>().eq(ResourceDO::getPid, id)
        );
        if (!children.isEmpty()) {
            vo.setBlocked(true);
            vo.setChildren(children.stream().map(c -> {
                DeleteCheckVO.ChildInfo info = new DeleteCheckVO.ChildInfo();
                info.setName(c.getName());
                info.setType(c.getType());
                if (c.getType() == 2) {
                    long buttonCount = resourceMapper.selectCount(
                        new LambdaQueryWrapper<ResourceDO>().eq(ResourceDO::getPid, c.getId())
                    );
                    info.setButtonCount(Math.toIntExact(buttonCount));
                }
                return info;
            }).collect(Collectors.toList()));
            vo.setReferencedRoles(new ArrayList<>());
            return vo;
        }

        vo.setBlocked(false);
        vo.setChildren(new ArrayList<>());
        List<String> roleCodes = roleResourceMapper.selectList(
            new LambdaQueryWrapper<RoleResourceDO>().eq(RoleResourceDO::getResourceId, id)
        ).stream().map(RoleResourceDO::getRoleCode).distinct().collect(Collectors.toList());
        if (roleCodes.isEmpty()) {
            vo.setReferencedRoles(new ArrayList<>());
        } else {
            List<RoleDO> roles = roleMapper.selectList(
                new LambdaQueryWrapper<RoleDO>().in(RoleDO::getCode, roleCodes)
            );
            vo.setReferencedRoles(roles.stream().map(RoleDO::getName).collect(Collectors.toList()));
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Integer id, Integer status) {
        resourceMapper.update(new ResourceDO(), new LambdaUpdateWrapper<ResourceDO>()
            .eq(ResourceDO::getId, id)
            .set(ResourceDO::getStatus, status));
        if (status == 0) {
            cascadeDisable(id);
        }
    }

    private void cascadeDisable(Integer parentId) {
        List<ResourceDO> children = resourceMapper.selectList(
            new LambdaQueryWrapper<ResourceDO>().eq(ResourceDO::getPid, parentId)
        );
        for (ResourceDO child : children) {
            resourceMapper.update(new ResourceDO(), new LambdaUpdateWrapper<ResourceDO>()
                .eq(ResourceDO::getId, child.getId())
                .set(ResourceDO::getStatus, 0));
            cascadeDisable(child.getId());
        }
    }

    @Override
    public void updateSort(Integer id, Integer sort) {
        resourceMapper.update(new ResourceDO(), new LambdaUpdateWrapper<ResourceDO>()
            .eq(ResourceDO::getId, id)
            .set(ResourceDO::getSort, sort));
    }

    @Override
    public List<Integer> getRoleMenuIds(String roleCode) {
        return roleResourceMapper.selectList(
            new LambdaQueryWrapper<RoleResourceDO>().eq(RoleResourceDO::getRoleCode, roleCode)
        ).stream().map(RoleResourceDO::getResourceId).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignRoleMenus(String roleCode, List<Integer> menuIds) {
        if (menuIds != null && !menuIds.isEmpty()) {
            Set<Integer> visible = visibleIds(currentUsername(), resourceMapper.selectList(null));
            if (visible != null && !visible.containsAll(menuIds)) {
                throw new BizException("包含当前账号无权分配的菜单");
            }
        }
        roleResourceMapper.delete(
            new LambdaQueryWrapper<RoleResourceDO>().eq(RoleResourceDO::getRoleCode, roleCode)
        );
        if (menuIds == null || menuIds.isEmpty()) return;
        for (Integer menuId : menuIds) {
            ResourceDO resource = resourceMapper.selectById(menuId);
            if (resource == null) continue;
            RoleResourceDO rr = new RoleResourceDO();
            rr.setRoleCode(roleCode);
            rr.setResourceId(menuId);
            rr.setResourceCode(resource.getCode());
            roleResourceMapper.insert(rr);
        }
    }

    @Override
    public List<String> getEffectiveMenuKeys(String username) {
        // 角色只勾了部分子菜单时，上级目录不一定存进了 role_resource，这里统一补上，
        // 否则侧边栏会因为目录本身不在权限里而把整组菜单隐藏。
        // 只从目录与页面往上补：只有某页面下的按钮（如「查看货源信息」挂在历史询价下）时，
        // 不能因此让这个页面出现在侧边栏，否则点进去也是无权限
        List<ResourceDO> all = resourceMapper.selectList(null);
        Set<Integer> visible = visibleIds(username, all, true);
        List<ResourceDO> resources = visible == null
            ? all
            : all.stream().filter(r -> visible.contains(r.getId())).collect(Collectors.toList());
        return resources.stream()
            .map(r -> StringUtils.hasText(r.getPath()) ? r.getPath() : r.getPermission())
            .filter(StringUtils::hasText)
            .distinct()
            .collect(Collectors.toList());
    }

    private MenuVO toVO(ResourceDO r) {
        MenuVO vo = new MenuVO();
        vo.setId(r.getId());
        vo.setPid(r.getPid());
        vo.setCode(r.getCode());
        vo.setName(r.getName());
        vo.setType(r.getType());
        vo.setSort(r.getSort());
        vo.setPath(r.getPath());
        vo.setComponentPath(r.getComponentPath());
        vo.setPermission(r.getPermission());
        vo.setLightIcon(r.getLightIcon());
        vo.setDarkIcon(r.getDarkIcon());
        vo.setMicroApp(r.getMicroApp());
        vo.setIsExternal(r.getIsExternal());
        vo.setIsCache(r.getIsCache());
        vo.setIsHidden(r.getIsHidden());
        vo.setStatus(r.getStatus());
        vo.setCreateBy(r.getCreateBy());
        vo.setCreateTime(r.getCreateTime());
        vo.setUpdateBy(r.getUpdateBy());
        vo.setUpdateTime(r.getUpdateTime());
        return vo;
    }
}
