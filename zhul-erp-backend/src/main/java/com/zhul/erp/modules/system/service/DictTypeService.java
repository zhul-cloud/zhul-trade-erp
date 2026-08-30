package com.zhul.erp.modules.system.service;

import com.zhul.erp.modules.system.dto.DictTypeDeleteCheckVO;
import com.zhul.erp.modules.system.dto.DictTypeVO;
import com.zhul.erp.modules.system.dto.SaveDictTypeRequest;

import java.util.List;

public interface DictTypeService {
    List<DictTypeVO> listAll(String name);
    void create(SaveDictTypeRequest req);
    void update(Integer id, SaveDictTypeRequest req);
    DictTypeDeleteCheckVO checkDeletable(Integer id);
    void delete(Integer id);
}
