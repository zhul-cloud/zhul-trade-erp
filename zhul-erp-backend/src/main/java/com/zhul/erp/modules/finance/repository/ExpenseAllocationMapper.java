package com.zhul.erp.modules.finance.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhul.erp.modules.finance.entity.ExpenseAllocationDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ExpenseAllocationMapper extends BaseMapper<ExpenseAllocationDO> {
}
