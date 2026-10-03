package com.zhul.erp.modules.inquiry.sourcing.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhul.erp.modules.inquiry.sourcing.entity.SourcingImportDO;
import org.apache.ibatis.annotations.Mapper;

/** 导入询价结果记录 */
@Mapper
public interface SourcingImportMapper extends BaseMapper<SourcingImportDO> {
}
