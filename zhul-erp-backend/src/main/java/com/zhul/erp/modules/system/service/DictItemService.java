package com.zhul.erp.modules.system.service;

import com.zhul.erp.modules.system.dto.DictItemVO;
import com.zhul.erp.modules.system.dto.SaveDictItemRequest;

import java.util.List;

public interface DictItemService {
    List<DictItemVO> listByDictTypeId(Integer dictTypeId);
    void create(SaveDictItemRequest req);
    void update(Integer id, SaveDictItemRequest req);
    void delete(Integer id);
}
