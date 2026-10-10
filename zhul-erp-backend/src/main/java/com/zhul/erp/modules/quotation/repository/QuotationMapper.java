package com.zhul.erp.modules.quotation.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhul.erp.modules.quotation.entity.QuotationDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface QuotationMapper extends BaseMapper<QuotationDO> {

    /** 锁住报价单行：状态变更串行执行 */
    @Select("SELECT id FROM quotation WHERE id = #{id} FOR UPDATE")
    Long lockById(@Param("id") Long id);
}
