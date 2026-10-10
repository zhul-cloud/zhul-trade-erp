package com.zhul.erp.modules.logistics.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhul.erp.modules.logistics.entity.ForwarderStatementDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ForwarderStatementMapper extends BaseMapper<ForwarderStatementDO> {
    @org.apache.ibatis.annotations.Select("SELECT id FROM forwarder_statement WHERE id = #{id} FOR UPDATE")
    Long lockById(@org.apache.ibatis.annotations.Param("id") Long id);
}
