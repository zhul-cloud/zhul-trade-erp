package com.zhul.erp.modules.masterdata.service;

import com.zhul.erp.modules.masterdata.dto.CustomerCreateResultVO;
import com.zhul.erp.modules.masterdata.dto.CustomerVO;
import com.zhul.erp.modules.masterdata.dto.SaveCustomerRequest;

import java.util.List;

public interface CustomerService {
    CustomerCreateResultVO create(SaveCustomerRequest req);
    List<CustomerVO> search(String keyword);
    /** 未找到（不存在或已软删除）时返回 null，供跨模块引用方（如询盘列表回显客户名）自行处理 */
    CustomerVO getById(Long id);
    void delete(Long id);
}
