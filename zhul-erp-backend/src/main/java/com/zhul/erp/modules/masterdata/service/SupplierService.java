package com.zhul.erp.modules.masterdata.service;

import com.zhul.erp.modules.masterdata.dto.CreateSupplierFromChannelRequest;
import com.zhul.erp.modules.masterdata.dto.SaveSupplierRequest;
import com.zhul.erp.modules.masterdata.dto.SupplierCreateResultVO;
import com.zhul.erp.modules.masterdata.dto.SupplierVO;

import java.util.List;

public interface SupplierService {
    SupplierCreateResultVO create(SaveSupplierRequest req);
    SupplierCreateResultVO createFromChannel(CreateSupplierFromChannelRequest req);
    List<SupplierVO> search(String keyword);
    /** 未找到（不存在或已软删除）时返回 null，供跨模块引用方（如报价对比回显供应商名）自行处理 */
    SupplierVO getById(Long id);
    void delete(Long id);
}
