package com.zhul.erp.modules.masterdata.service;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.modules.masterdata.dto.CreateSupplierFromChannelRequest;
import com.zhul.erp.modules.masterdata.dto.SaveSupplierRequest;
import com.zhul.erp.modules.masterdata.dto.SupplierBatchDeleteResultVO;
import com.zhul.erp.modules.masterdata.dto.SupplierCreateResultVO;
import com.zhul.erp.modules.masterdata.dto.SupplierFormVO;
import com.zhul.erp.modules.masterdata.dto.SupplierPageQuery;
import com.zhul.erp.modules.masterdata.dto.SupplierVO;
import com.zhul.erp.modules.masterdata.dto.UpdateSupplierRequest;
import org.apache.poi.ss.usermodel.Workbook;

import java.util.List;

public interface SupplierService {
    SupplierCreateResultVO create(SaveSupplierRequest req);
    SupplierCreateResultVO createFromChannel(CreateSupplierFromChannelRequest req);
    List<SupplierVO> search(String keyword);
    /** 未找到（不存在或已软删除）时返回 null，供跨模块引用方（如报价对比回显供应商名）自行处理 */
    SupplierVO getById(Long id);
    /** 编辑页取数，银行账号为明文；不存在时抛业务异常 */
    SupplierFormVO getFormById(Long id);
    void delete(Long id);
    SupplierBatchDeleteResultVO batchDelete(List<Long> ids);
    PageResult<SupplierVO> page(SupplierPageQuery query);
    /** 按分页查询的筛选条件导出全部结果（不分页），银行账号脱敏 */
    Workbook export(SupplierPageQuery query);
    void update(Long id, UpdateSupplierRequest req);
    void updateStatus(Long id, Integer status);
    /** 把存量自由文本主营品牌迁移为主营产品（启动时调用，幂等：只处理还没有主营产品的供应商），返回处理的供应商数 */
    int backfillProductScopes();
}
