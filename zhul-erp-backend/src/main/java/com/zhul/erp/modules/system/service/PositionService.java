package com.zhul.erp.modules.system.service;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.modules.system.dto.PositionDeleteCheckVO;
import com.zhul.erp.modules.system.dto.PositionVO;
import com.zhul.erp.modules.system.dto.SavePositionRequest;

import java.util.List;

public interface PositionService {
    PageResult<PositionVO> listAll(int page, int pageSize, String name, Integer status);
    List<PositionVO> allPositions();
    void create(SavePositionRequest req);
    void update(Integer id, SavePositionRequest req);
    void updateStatus(Integer id, Integer status);
    PositionDeleteCheckVO checkDeletable(Integer id);
    void delete(Integer id);
}
