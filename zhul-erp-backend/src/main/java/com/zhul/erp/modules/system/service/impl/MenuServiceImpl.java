package com.zhul.erp.modules.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhul.erp.common.exception.BizException;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MenuServiceImpl implements MenuService {

    private final ResourceMapper resourceMapper;
    private final RoleResourceMapper roleResourceMapper;
    private final RoleMapper roleMapper;

    @Override
    public List<MenuVO> getMenuTree() {
        List<ResourceDO> all = resourceMapper.selectList(
            new LambdaQueryWrapper<ResourceDO>().orderByAsc(ResourceDO::getSort)
        );
        return buildTree(all, 0);
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
        resourceMapper.update(null, new LambdaUpdateWrapper<ResourceDO>()
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
            resourceMapper.update(null, new LambdaUpdateWrapper<ResourceDO>()
                .eq(ResourceDO::getId, child.getId())
                .set(ResourceDO::getStatus, 0));
            cascadeDisable(child.getId());
        }
    }

    @Override
    public void updateSort(Integer id, Integer sort) {
        resourceMapper.update(null, new LambdaUpdateWrapper<ResourceDO>()
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
    public List<String> getUserMenuPaths(String roleCode) {
        if (!StringUtils.hasText(roleCode)) return new ArrayList<>();
        List<Integer> menuIds = getRoleMenuIds(roleCode);
        if (menuIds.isEmpty()) return new ArrayList<>();
        List<ResourceDO> resources = resourceMapper.selectBatchIds(menuIds);
        return resources.stream()
            .filter(r -> StringUtils.hasText(r.getPath()))
            .map(ResourceDO::getPath)
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
