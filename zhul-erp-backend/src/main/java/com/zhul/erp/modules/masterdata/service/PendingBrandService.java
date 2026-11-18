package com.zhul.erp.modules.masterdata.service;

import com.zhul.erp.modules.masterdata.dto.PendingBrandVO;
import com.zhul.erp.modules.product.dto.SaveBrandRequest;

import java.util.List;

/**
 * 待确认品牌（供应商主营产品里手填、以及询盘、订单、采购单里认不出的品牌写法）的汇总与确认，仅平台账号。
 * 确认后单据里的这个写法统一成正式品牌名（已发给客户的报价单、PI 不改），见 openspec brand-unify。
 */
public interface PendingBrandService {

    List<PendingBrandVO> list();

    /** 设为已有品牌的别名：关联使用该名称的供应商主营产品，并把单据里的写法统一成正式品牌名 */
    void linkAsAlias(String pendingKey, Long brandId);

    /** 批量设为同一品牌的别名；返回处理的写法数 */
    int linkAsAlias(List<String> pendingKeys, Long brandId);

    /** 新建为品牌（名称改过时原名自动成为别名），关联供应商主营产品并统一单据写法；返回新品牌 ID */
    Long createBrand(String pendingKey, SaveBrandRequest req);

    /** 标为不是品牌：不再出现在待确认列表里，单据保持原样；返回处理的写法数 */
    int ignore(List<String> pendingKeys);
}
