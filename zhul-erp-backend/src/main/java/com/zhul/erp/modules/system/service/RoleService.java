package com.zhul.erp.modules.system.service;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.modules.system.dto.RoleDeleteCheckVO;
import com.zhul.erp.modules.system.dto.RoleStatsVO;
import com.zhul.erp.modules.system.dto.RoleVO;
import com.zhul.erp.modules.system.dto.SaveRoleRequest;
import java.util.List;

public interface RoleService {
    PageResult<RoleVO> listRoles(Integer page, Integer pageSize, String name, String code, Integer status);
    List<RoleVO> allRoles();
    RoleStatsVO getStats();
    void createRole(SaveRoleRequest request);
    void updateRole(Integer roleId, SaveRoleRequest request);
    RoleDeleteCheckVO checkDeletable(Integer roleId);
    void deleteRole(Integer roleId);
    List<Integer> getRoleDeptIds(String roleCode);
}
