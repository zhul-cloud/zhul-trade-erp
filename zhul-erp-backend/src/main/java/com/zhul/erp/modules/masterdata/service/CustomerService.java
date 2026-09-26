package com.zhul.erp.modules.masterdata.service;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.modules.masterdata.dto.AssignableOwnersVO;
import com.zhul.erp.modules.masterdata.dto.CustomerBatchDeleteResultVO;
import com.zhul.erp.modules.masterdata.dto.CustomerDetailVO;
import com.zhul.erp.modules.masterdata.dto.CustomerPageQuery;
import com.zhul.erp.modules.masterdata.dto.CustomerRefVO;
import com.zhul.erp.modules.masterdata.dto.CustomerTransferRequest;
import com.zhul.erp.modules.masterdata.dto.CustomerVO;
import com.zhul.erp.modules.masterdata.dto.SaveCustomerRequest;
import com.zhul.erp.modules.masterdata.dto.UpdateCustomerRequest;
import org.apache.poi.ss.usermodel.Workbook;

import java.util.List;

/**
 * 客户主数据。带 InScope 语义的方法（客户管理页、询盘录入的客户选择器）按当前用户的数据权限过滤；
 * {@link #getById}、{@link #search} 供询盘等模块在内部校验客户、按名称筛选，不按数据权限过滤。
 */
public interface CustomerService {

    /** 新增（管理页与询盘快速创建共用）。名称 + 国家查重命中时抛 CUSTOMER_DUPLICATE */
    CustomerVO create(SaveCustomerRequest req);

    /** 内部用：按名称模糊搜索启用客户，不按数据权限过滤 */
    List<CustomerVO> search(String keyword);

    /** 客户选择器：按名称 / 中文名 / 简称模糊搜索启用客户，按数据权限过滤 */
    List<CustomerVO> searchInScope(String keyword);

    /** 内部用：未找到（不存在或已软删除）时返回 null，不按数据权限过滤 */
    CustomerVO getById(Long id);

    /** 跨模块回显名称用的引用信息，不按数据权限过滤；未找到返回 null */
    CustomerRefVO getRef(Long id);

    /** 详情 / 编辑取数，数据范围外或已删除时抛「客户不存在或无权查看」 */
    CustomerDetailVO getDetail(Long id);

    PageResult<CustomerVO> page(CustomerPageQuery query);

    void update(Long id, UpdateCustomerRequest req);

    void updateStatus(Long id, Integer status);

    void delete(Long id);

    CustomerBatchDeleteResultVO batchDelete(List<Long> ids);

    void transfer(CustomerTransferRequest req);

    AssignableOwnersVO assignableOwners();

    Workbook export(CustomerPageQuery query);

    /** 补算 name_key 为空的客户（启动时调用，幂等），返回处理条数 */
    int backfillNameKeys();
}
