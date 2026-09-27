package com.zhul.erp.modules.product.service;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.modules.product.dto.CreateSeriesRequest;
import com.zhul.erp.modules.product.dto.SeriesOptionVO;
import com.zhul.erp.modules.product.dto.SeriesQuery;
import com.zhul.erp.modules.product.dto.SeriesVO;
import com.zhul.erp.modules.product.dto.UpdateSeriesRequest;

import java.util.List;

public interface SeriesService {

    PageResult<SeriesVO> page(SeriesQuery query);

    /** 启用的系列，brandId 不为空时只返回该品牌下的 */
    List<SeriesOptionVO> options(Long brandId);

    SeriesVO create(CreateSeriesRequest req);

    SeriesVO update(Long id, UpdateSeriesRequest req);

    void updateStatus(Long id, Integer status);

    /** 软删除；下面仍有未删除商品时拒绝 */
    void delete(Long id);
}
