package com.zhul.erp.modules.system.service;

import com.zhul.erp.modules.system.dto.DeptDeleteCheckVO;
import com.zhul.erp.modules.system.dto.DeptVO;
import com.zhul.erp.modules.system.dto.SaveDeptRequest;

import java.util.List;

public interface DeptService {
    List<DeptVO> listAll();
    List<DeptVO> getDeptTree();
    void createDept(SaveDeptRequest req);
    void updateDept(Integer id, SaveDeptRequest req);
    void updateStatus(Integer id, Integer status);
    DeptDeleteCheckVO checkDeletable(Integer id);
    void deleteDept(Integer id);
}
