package com.zhul.erp.modules.system.service;

import com.zhul.erp.modules.system.dto.DeleteCheckVO;
import com.zhul.erp.modules.system.dto.MenuVO;
import com.zhul.erp.modules.system.dto.SaveMenuRequest;
import java.util.List;

public interface MenuService {
    List<MenuVO> getMenuTree();
    void createMenu(SaveMenuRequest request);
    void updateMenu(Integer id, SaveMenuRequest request);
    void deleteMenu(Integer id);
    DeleteCheckVO checkDeletable(Integer id);
    void updateStatus(Integer id, Integer status);
    void updateSort(Integer id, Integer sort);
    List<Integer> getRoleMenuIds(String roleCode);
    void assignRoleMenus(String roleCode, List<Integer> menuIds);
    /** 获取用户可访问的路由 path 列表（用于前端权限控制） */
    List<String> getUserMenuPaths(String roleCode);
}
