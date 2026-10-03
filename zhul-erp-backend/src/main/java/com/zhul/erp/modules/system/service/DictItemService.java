package com.zhul.erp.modules.system.service;

import com.zhul.erp.modules.system.dto.DictItemVO;
import com.zhul.erp.modules.system.dto.SaveDictItemRequest;

import java.util.List;
import java.util.Map;

public interface DictItemService {
    List<DictItemVO> listByDictTypeId(Integer dictTypeId);
    void create(SaveDictItemRequest req);
    void update(Integer id, SaveDictItemRequest req);
    void delete(Integer id);

    /** 按字典类型编码取字典项（平台内置 + 本租户，含已停用项），供业务下拉与历史数据展示 */
    List<DictItemVO> listByDictType(String dictType);

    /** 字典值为整数的字典：码值 → 名称，含已停用项，用于展示历史数据 */
    Map<Integer, String> intLabels(String dictType);

    /** 校验整数码值是该字典的启用项，不是则抛出业务异常 */
    void requireEnabledValue(String dictType, Integer value, String message);
}
