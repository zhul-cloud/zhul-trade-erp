package com.zhul.erp.modules.inquiry.sourcing.repository;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.PriceHistoryGroupVO;
import com.zhul.erp.modules.inquiry.sourcing.entity.SourcingQuoteDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 询价记录（历史询价） */
@Mapper
public interface SourcingQuoteMapper extends BaseMapper<SourcingQuoteDO> {

    /** 历史询价按品牌 + 归一化型号分组；无货记录计入记录数，不参与最低 / 最高价 */
    @Select("SELECT brand_key AS brandKey, model_key AS modelKey, MAX(brand) AS brand, MAX(model) AS model, "
            + "MIN(unit_price_cny) AS minPriceCny, MAX(unit_price_cny) AS maxPriceCny, COUNT(*) AS recordCount, "
            + "MAX(quoted_at) AS lastQuotedAt FROM sourcing_quote ${ew.customSqlSegment} "
            + "GROUP BY brand_key, model_key ORDER BY lastQuotedAt DESC")
    IPage<PriceHistoryGroupVO> selectGroups(IPage<PriceHistoryGroupVO> page, @Param(Constants.WRAPPER) Wrapper<SourcingQuoteDO> ew);
}
