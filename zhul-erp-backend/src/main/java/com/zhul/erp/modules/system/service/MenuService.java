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
    /**
     * 计算某账号最终能看到的菜单 path + 按钮 permission code 混合列表，给前端一次性
     * 判断权限用（已经把平台超管/租户套餐/角色的限制都算进去，口径与
     * {@link com.zhul.erp.framework.security.PermissionChecker} 一致）。
     */
    List<String> getEffectiveMenuKeys(String username);
}
