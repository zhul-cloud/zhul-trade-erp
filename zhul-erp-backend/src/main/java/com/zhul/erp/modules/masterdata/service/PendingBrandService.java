package com.zhul.erp.modules.masterdata.service;

import com.zhul.erp.modules.masterdata.dto.PendingBrandVO;
import com.zhul.erp.modules.product.dto.SaveBrandRequest;

import java.util.List;

/** 待确认品牌（供应商主营产品里手填、品牌清单中没有的名称）的汇总与确认，仅平台账号 */
public interface PendingBrandService {

    List<PendingBrandVO> list();

    /** 设为已有品牌的别名，并把使用该名称的供应商主营产品关联到该品牌 */
    void linkAsAlias(String pendingKey, Long brandId);

    /** 新建为品牌（名称改过时原名自动成为别名），并关联使用该名称的供应商主营产品；返回新品牌 ID */
    Long createBrand(String pendingKey, SaveBrandRequest req);
}
