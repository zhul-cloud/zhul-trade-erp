package com.zhul.erp.modules.masterdata.customerbackground.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhul.erp.modules.masterdata.customerbackground.entity.CustomerBackgroundReportDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CustomerBackgroundReportMapper extends BaseMapper<CustomerBackgroundReportDO> {

    /** 行锁：同一份报告的采纳、忽略串行执行 */
    @Select("SELECT id FROM customer_background_report WHERE id = #{id} FOR UPDATE")
    Long lockById(@Param("id") Long id);
}
